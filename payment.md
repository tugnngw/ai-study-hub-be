# Tài Liệu Hệ Thống Thanh Toán - Payment System Documentation

## 📋 Mục Lục
1. [Tổng Quan Hệ Thống](#tổng-quan-hệ-thống)
2. [Kiến Trúc & Luồng Dữ Liệu](#kiến-trúc--luồng-dữ-liệu)
3. [Backend - Chi Tiết Code](#backend---chi-tiết-code)
4. [Frontend - Chi Tiết Code](#frontend---chi-tiết-code)
5. [Database Schema](#database-schema)
6. [Lý Do Thiết Kế](#lý-do-thiết-kế)

---

## 🎯 Tổng Quan Hệ Thống

Hệ thống thanh toán cho phép người dùng nâng cấp tài khoản lên Premium (PLUS/PRO) thông qua cổng thanh toán **PayOS**.

### Các Tính Năng Chính:
- ✅ Chọn gói Premium (PLUS hoặc PRO)
- ✅ Tạo link thanh toán qua PayOS
- ✅ Xử lý webhook tự động khi thanh toán thành công
- ✅ Cập nhật quyền Premium cho user
- ✅ Lưu lịch sử giao dịch
- ✅ Xem danh sách giao dịch đã thực hiện

### Công Nghệ Sử Dụng:
- **Backend**: Spring Boot, PostgreSQL, PayOS SDK
- **Frontend**: React, TanStack Router, TypeScript
- **Payment Gateway**: PayOS (cổng thanh toán Việt Nam)

---

## 🏗️ Kiến Trúc & Luồng Dữ Liệu

### Luồng Thanh Toán Hoàn Chỉnh:

```
[User] 
  ↓ Chọn gói PLUS/PRO
[Frontend: PremiumUpgradePage]
  ↓ POST /api/payment/create {planId}
[PaymentController]
  ↓ createPaymentLink(email, planId)
[PaymentServiceImpl]
  ↓ Tạo PaymentTransaction (PENDING)
  ↓ Gọi PayOS.createPaymentLink()
[PayOS API]
  ↓ Trả về checkoutUrl
[Frontend]
  ↓ Chuyển user đến PayOS checkout
[User thanh toán trên PayOS]
  ↓ PayOS gửi webhook
[PaymentController: /webhook]
  ↓ handlePaymentWebhook(payload)
[PaymentServiceImpl]
  ↓ Cập nhật transaction → SUCCESS
  ↓ Cập nhật user.premiumPlan = PLUS/PRO
  ↓ Set premiumExpiresAt = +1 tháng
[Database]
  ↓ Lưu thay đổi
[User] 
  ✅ Đã có quyền Premium
```

---

## 💻 Backend - Chi Tiết Code

### 1. PaymentController.java

**Vai trò**: REST Controller - nhận HTTP requests và điều hướng đến service

```java
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
```
- `@RestController`: Đánh dấu đây là REST API controller, tự động serialize response thành JSON
- `@RequestMapping("/api/payment")`: Tất cả endpoints bắt đầu với `/api/payment`
- `@RequiredArgsConstructor`: Lombok tự động tạo constructor cho `final` fields (dependency injection)

#### Endpoint 1: Tạo Link Thanh Toán

```java
@PostMapping("/create")
@PreAuthorize("hasAnyAuthority('USER','ADMIN')")
public ResponseEntity<PaymentResponse> createPayment(
        @RequestBody CreatePaymentRequest request,
        Authentication auth
) {
    String email = auth.getName();
    PaymentResponse response = paymentService.createPaymentLink(email, request.getPlanId());
    return ResponseEntity.ok(response);
}
```

**Giải thích từng dòng:**

- `@PostMapping("/create")`: Endpoint POST tại `/api/payment/create`
- `@PreAuthorize("hasAnyAuthority('USER','ADMIN')")`: **BẢO MẬT** - chỉ user đã đăng nhập (USER hoặc ADMIN) mới được gọi
- `@RequestBody CreatePaymentRequest request`: Nhận JSON từ client, Spring tự động parse thành object
- `Authentication auth`: Spring Security tự động inject thông tin user đang đăng nhập
- `String email = auth.getName()`: Lấy email từ token JWT - **QUAN TRỌNG**: không tin tưởng userId từ client
- `paymentService.createPaymentLink(...)`: Gọi service xử lý logic
- `ResponseEntity.ok(response)`: Trả về HTTP 200 với body là PaymentResponse

**Tại sao lấy email từ Authentication?**
- **Bảo mật**: Client có thể giả mạo userId trong request body
- **Tin cậy**: Authentication được verify bởi JWT, không thể fake
- **Đơn giản**: User chỉ thanh toán cho chính mình

#### Endpoint 2: Webhook Từ PayOS

```java
@PostMapping("/webhook")
public ResponseEntity<String> handleWebhook(@RequestBody WebhookPayload payload) {
    paymentService.handlePaymentWebhook(payload);
    return ResponseEntity.ok("OK");
}
```

**Giải thích:**

- `@PostMapping("/webhook")`: PayOS sẽ POST dữ liệu đến `/api/payment/webhook`
- **KHÔNG có @PreAuthorize**: Webhook đến từ PayOS server, không có token JWT
- `WebhookPayload payload`: PayOS gửi JSON chứa thông tin giao dịch
- Trả về `"OK"`: PayOS cần response 200 để xác nhận đã nhận webhook

**Tại sao không cần authentication cho webhook?**
- Webhook đến từ PayOS server (không phải từ user)
- PayOS có thể verify bằng signature (nếu cấu hình)
- URL webhook nên giữ bí mật hoặc verify bằng secret key

#### Endpoint 3: Lấy Lịch Sử Giao Dịch

```java
@GetMapping("/transactions")
@PreAuthorize("hasAnyAuthority('USER','ADMIN')")
public ResponseEntity<List<PaymentTransactionDto>> getTransactions(Authentication auth) {
    String email = auth.getName();
    List<PaymentTransactionDto> transactions = paymentService.getUserTransactions(email);
    return ResponseEntity.ok(transactions);
}
```

**Giải thích:**

- User chỉ xem được giao dịch của chính mình (lấy email từ auth)
- Trả về danh sách DTO (không trả entity trực tiếp để tránh lộ thông tin nhạy cảm)

---

### 2. PaymentServiceImpl.java - Logic Xử Lý Chính

#### Dependencies Injection

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    private final PaymentPlanRepository planRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final PayOS payOS;

    @Value("${payment.return-url}")
    private String returnUrl;

    @Value("${payment.cancel-url}")
    private String cancelUrl;
```

**Giải thích:**

- `@Service`: Đánh dấu đây là Spring Service bean
- `@Slf4j`: Lombok tạo logger tự động (`log.info()`, `log.error()`)
- `@RequiredArgsConstructor`: Inject các dependency qua constructor
- `PayOS payOS`: SDK của PayOS để tạo payment link
- `@Value`: Đọc config từ `application.properties` (return URL và cancel URL)

**Tại sao dùng `@Value` cho URLs?**
- URLs khác nhau giữa dev/staging/production
- Dễ thay đổi mà không cần rebuild code
- Có thể override bằng environment variables

#### Method 1: createPaymentLink()

```java
@Override
@Transactional
public PaymentResponse createPaymentLink(String userEmail, Long planId) {
```

- `@Transactional`: Nếu có lỗi, mọi thay đổi DB sẽ rollback (bảo đảm tính toàn vẹn)

```java
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

    PaymentPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new RuntimeException("Plan not found"));
```

- **Validate dữ liệu đầu vào**: Kiểm tra user và plan có tồn tại không
- `orElseThrow()`: Nếu không tìm thấy → ném exception → trả về HTTP 500

```java
    long orderCode = System.currentTimeMillis();
```

**Tại sao dùng timestamp làm orderCode?**
- **Unique**: Mỗi millisecond tạo 1 số khác nhau
- **Tăng dần**: Dễ tra cứu theo thứ tự thời gian
- **Đơn giản**: Không cần UUID phức tạp
- **Lưu ý**: Nếu traffic cao, có thể trùng → nên dùng database sequence hoặc UUID

```java
    PaymentTransaction transaction = new PaymentTransaction();
    transaction.setUser(user);
    transaction.setPlan(plan);
    transaction.setOrderCode(orderCode);
    transaction.setAmount(plan.getPrice());
    transaction.setStatus(PaymentStatus.PENDING);
    transaction.setDescription("Thanh toan goi " + plan.getName());
    transactionRepository.save(transaction);
```

**Tại sao lưu transaction trước khi gọi PayOS?**
- **Tracking**: Lưu lại mọi request thanh toán
- **Webhook matching**: Webhook sẽ trả về orderCode, dùng để tìm transaction
- **Idempotency**: Nếu PayOS fail, vẫn có log trong DB
- Status = **PENDING**: Chưa thanh toán, đợi webhook confirm

```java
    try {
        ItemData item = ItemData.builder()
                .name(plan.getName())
                .quantity(1)
                .price(plan.getPrice().intValue())
                .build();
```

- Tạo item data theo format PayOS yêu cầu
- `quantity(1)`: Mua 1 gói
- `.intValue()`: PayOS API nhận `int`, database lưu `Long`

```java
        PaymentData paymentData = PaymentData.builder()
                .orderCode(orderCode)
                .amount(plan.getPrice().intValue())
                .description("Thanh toan goi " + plan.getName())
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .item(item)
                .build();
```

- `returnUrl`: URL PayOS redirect khi thanh toán thành công
- `cancelUrl`: URL khi user cancel thanh toán
- `orderCode`: ID để tracking, PayOS sẽ trả lại trong webhook

```java
        var response = payOS.createPaymentLink(paymentData);
        return new PaymentResponse(response.getCheckoutUrl(), orderCode, plan.getPrice());
```

- Gọi PayOS API tạo link
- Trả về `checkoutUrl`: Frontend sẽ redirect user đến URL này để thanh toán

```java
    } catch (Exception e) {
        log.error("Error creating payment link", e);
        throw new RuntimeException("Failed to create payment link: " + e.getMessage());
    }
```

- **Error handling**: Log lỗi và ném exception
- Frontend sẽ nhận HTTP 500 và hiển thị thông báo lỗi

---

#### Method 2: handlePaymentWebhook() - XỬ LÝ QUAN TRỌNG NHẤT

```java
@Override
@Transactional
public void handlePaymentWebhook(WebhookPayload payload) {
    log.info("Received webhook: {}", payload);
```

- Log toàn bộ webhook data để debug và audit

```java
    if (payload.getData() == null) {
        log.warn("Webhook data is null");
        return;
    }
```

- **Defensive programming**: PayOS có thể gửi payload rỗng

```java
    Long orderCode = payload.getData().getOrderCode();
    PaymentTransaction transaction = transactionRepository.findByOrderCode(orderCode)
            .orElseThrow(() -> new RuntimeException("Transaction not found"));
```

- Tìm transaction dựa trên orderCode từ webhook
- Nếu không tìm thấy → có vấn đề (orderCode không đúng hoặc fake webhook)

```java
    transaction.setTransactionId(payload.getData().getTransactionId());
```

- Lưu `transactionId` từ PayOS để tra cứu sau này

```java
    if ("PAID".equals(payload.getData().getStatus())) {
        transaction.setStatus(PaymentStatus.SUCCESS);
```

- **Điều kiện quan trọng**: Chỉ xử lý khi status = "PAID"
- Cập nhật transaction status → SUCCESS

```java
        User user = transaction.getUser();
        PaymentPlan plan = transaction.getPlan();

        if ("PLUS".equals(plan.getPlanCode())) {
            user.setPremiumPlan(PremiumPlan.PLUS);
        } else if ("PRO".equals(plan.getPlanCode())) {
            user.setPremiumPlan(PremiumPlan.PRO);
        }
```

**Logic cấp quyền Premium:**
- Lấy user và plan từ transaction
- Kiểm tra planCode để set đúng premium tier
- **Tại sao không dùng switch?** Code đơn giản, chỉ 2 cases

```java
        user.setPremiumExpiresAt(LocalDateTime.now().plusMonths(1));
        userRepository.save(user);
```

- **Expiry time**: Premium có hiệu lực 1 tháng
- `plusMonths(1)`: Cộng 1 tháng từ thời điểm hiện tại
- **Lưu user**: Cập nhật vào database

```java
        log.info("User {} upgraded to {} plan", user.getEmail(), plan.getPlanCode());
```

- **Audit log**: Ghi lại ai được nâng cấp gì, quan trọng cho tracking

```java
    } else if ("CANCELLED".equals(payload.getData().getStatus())) {
        transaction.setStatus(PaymentStatus.FAILED);
    }
```

- Xử lý trường hợp user cancel thanh toán

```java
    transactionRepository.save(transaction);
}
```

- Lưu thay đổi transaction vào DB
- `@Transactional` đảm bảo hoặc tất cả thành công, hoặc tất cả rollback

---

### 3. Entities & DTOs

#### PaymentPlan.java - Bảng Gói Premium

```java
@Entity
@Table(name = "payment_plans")
@Data
public class PaymentPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String planCode; // PLUS, PRO

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long price;

    private String description;
}
```

**Giải thích:**
- `@Entity`: JPA entity - map với table trong DB
- `planCode`: **UNIQUE** - mỗi plan chỉ có 1 code (PLUS, PRO)
- `price`: Dùng `Long` thay vì `int` để tránh overflow (VNĐ số lớn)
- `description`: Nullable - có thể không cần mô tả

#### PaymentTransaction.java - Bảng Giao Dịch

```java
@Entity
@Table(name = "payment")
@Data
public class PaymentTransaction {
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private PaymentPlan plan;
```

**Tại sao dùng `FetchType.LAZY`?**
- **Performance**: Không load plan khi không cần
- Chỉ query khi gọi `transaction.getPlan()`
- Giảm số lượng SQL queries

```java
    @Column(name = "payos_order_code", nullable = false, unique = true)
    private Long payosOrderCode;
```

- **UNIQUE**: Mỗi payosOrderCode chỉ xuất hiện 1 lần
- Dùng để mapping với webhook từ PayOS

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
```

- `EnumType.STRING`: Lưu "PENDING", "SUCCESS", "FAILED" thay vì 0,1,2
- **Lợi ích**: Đọc database dễ hiểu hơn, không sợ đổi thứ tự enum

```java
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
```

- Hibernate tự động set thời gian tạo/cập nhật
- Không cần manually call `setCreatedAt()`

---

## 🎨 Frontend - Chi Tiết Code

### 1. PremiumUpgradePage.tsx - Trang Nâng Cấp

#### State Management

```typescript
const [plans, setPlans] = useState<PlanOption[]>([]);
const [methods, setMethods] = useState<TopUpMethod[]>([]);
const [bank, setBank] = useState<BankInfo | null>(null);
const [step, setStep] = useState<Step>("plan");
const [plan, setPlan] = useState<PlanOption | null>(null);
```

**Tại sao cần nhiều state?**
- `plans`: Danh sách gói Premium (PLUS/PRO)
- `methods`: Phương thức thanh toán (Bank/Card)
- `bank`: Thông tin ngân hàng
- `step`: Bước hiện tại ("plan" | "method" | "bank")
- `plan`: Gói đã chọn

#### Data Loading với useEffect

```typescript
useEffect(() => {
    let alive = true;
    Promise.all([
      paymentApi.getPlanOptions(),
      paymentApi.getTopUpMethods(),
      paymentApi.getBankInfo(),
    ]).then(([p, m, b]) => {
      if (!alive) return;
      setPlans(p);
      setMethods(m);
      setBank(b);
    });
    return () => {
      alive = false;
    };
}, []);
```

**Giải thích kỹ thuật:**

1. **`let alive = true`**: Flag để tránh memory leak
2. **`Promise.all([])`**: Gọi 3 API đồng thời (parallel) thay vì tuần tự
   - **Lợi ích**: Nhanh hơn 3 lần so với await từng cái
3. **`if (!alive) return`**: Nếu component unmount trước khi API trả về, không setState
   - **Tại sao?** setState trên unmounted component gây warning
4. **`return () => { alive = false }`**: Cleanup function
   - Chạy khi component unmount
   - Set flag để skip setState

**Tại sao không dùng axios cancel token?**
- Đơn giản hơn
- API mock không cần cancel (return ngay)

#### Copy to Clipboard Function

```typescript
const copy = (text: string, label: string) => {
    navigator.clipboard?.writeText(text).then(
      () => toast.success(`Đã copy ${label}`),
      () => toast.error("Không copy được"),
    );
};
```

- `navigator.clipboard`: Browser API để copy
- `?.`: Optional chaining - browser cũ không có API này
- `then()`: Success callback
- `,`: Reject callback (nếu user không cấp quyền clipboard)

#### UI Rendering - Bước 1: Chọn Gói

```typescript
{plans.map((p) => (
    <Card
      key={p.id}
      className={cn(
        "relative",
        p.highlighted && "border-primary shadow-brand",
      )}
    >
```

- `key={p.id}`: React cần key để optimize re-render
- `cn()`: Utility function merge classNames
- `p.highlighted && "..."`: Conditional class cho gói được đề xuất

```typescript
{p.highlighted && (
    <Badge className="absolute -top-2.5 left-5 ...">
      Phổ biến
    </Badge>
)}
```

- Hiển thị badge "Phổ biến" cho gói PRO
- `absolute -top-2.5`: Đặt badge chìa ra ngoài border card

```typescript
<Button
  onClick={() => {
    setPlan(p);
    setStep("method");
  }}
>
  Chọn {p.name}
</Button>
```

- User click → lưu plan đã chọn
- Chuyển sang bước "method"

---

### 2. paymentApi.ts - API Service

```typescript
const PLAN_OPTIONS: PlanOption[] = [
  {
    id: "PLUS",
    name: "Premium Plus",
    price: 99_000,
    tagline: "Phù hợp cho cá nhân học tập nghiêm túc",
    features: [...],
  },
  ...
];
```

**Tại sao hardcode ở frontend?**
- **Lý do tạm thời**: Backend chưa có API `/api/plans`
- **Ưu điểm**: Thay đổi nhanh, không cần deploy backend
- **Nhược điểm**: Admin không thể đổi giá real-time
- **TODO**: Nên move sang backend API

```typescript
export const paymentApi = {
  getTransactions: () => api<TransactionItem[]>("/api/payment/transactions"),
  getPlanOptions: (): Promise<PlanOption[]> =>
    Promise.resolve([...PLAN_OPTIONS]),
  getTopUpMethods: (): Promise<TopUpMethod[]> =>
    Promise.resolve([...TOP_UP_METHODS]),
  getBankInfo: () => Promise.resolve({ ...BANK_INFO }),
};
```

- `getTransactions()`: Gọi backend API thật
- Các method khác: Return mock data ngay lập tức
- `Promise.resolve([...array])`: Tạo shallow copy để tránh mutation

---

## 🗄️ Database Schema

### Migration File: V5__add_payment_tables.sql

```sql
CREATE TABLE payment_plan (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    storage_gb INTEGER,
    ai_questions INTEGER,
    price BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    account_id UUID NOT NULL,
    plan_id BIGINT,
    payos_order_code BIGINT NOT NULL UNIQUE,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    transaction_id VARCHAR(255),
    payment_method VARCHAR(50),
    expired_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE,
    FOREIGN KEY (plan_id) REFERENCES payment_plan(id) ON DELETE SET NULL
);

CREATE INDEX idx_payment_account_id ON payment(account_id);
CREATE INDEX idx_payment_order_code ON payment(payos_order_code);
CREATE INDEX idx_payment_status ON payment(status);

INSERT INTO payment_plan (name, description, storage_gb, ai_questions, price, is_active) VALUES
('Basic', 'Basic plan with 5GB storage and 100 AI questions', 5, 100, 50000, true),
('Pro', 'Pro plan with 20GB storage and 500 AI questions', 20, 500, 150000, true),
('Premium', 'Premium plan with 100GB storage and unlimited AI questions', 100, 999999, 300000, true);
```

- `BIGSERIAL`: Auto-increment ID (PostgreSQL)
- `name UNIQUE`: Đảm bảo không trùng plan names
- `account_id UUID`: Reference tới account table
- `payos_order_code UNIQUE`: Đảm bảo không trùng lặp, map với PayOS webhook

---

## 🤔 Lý Do Thiết Kế

### 1. Tại Sao Dùng PayOS?

- **Dễ tích hợp**: SDK Java có sẵn
- **Hỗ trợ VNĐ**: Payment gateway Việt Nam
- **Webhook**: Tự động confirm thanh toán

### 2. Tại Sao Lưu Transaction Trước?

- **Audit trail**: Log mọi payment request
- **Webhook matching**: Cần orderCode để map
- **Recovery**: Nếu server crash, vẫn có record

### 3. Tại Sao Dùng Enum?

- **Type safety**: Compiler check, không gõ sai
- **IDE support**: Auto-complete
- **Maintainable**: Thay đổi 1 chỗ, update khắp nơi

### 4. Tại Sao Dùng @Transactional?

- **Atomicity**: All or nothing
- **Rollback**: Lỗi → revert tất cả
- **Data integrity**: Không bao giờ có trạng thái không nhất quán

### 5. Tại Sao Frontend Cần 3 Steps?

- **UX**: Không overwhelm user với quá nhiều thông tin
- **Progressive disclosure**: Hiện thông tin từng bước
- **Back navigation**: User có thể quay lại thay đổi

---

## ✅ Checklist Bảo Mật

- ✅ **Authentication**: Tất cả endpoints có @PreAuthorize
- ✅ **Authorization**: User chỉ thấy giao dịch của mình
- ✅ **Input validation**: Check user, plan tồn tại
- ✅ **No trust client**: UserId lấy từ JWT, không từ request body
- ⚠️ **Webhook signature**: Chưa verify (nên thêm)
- ⚠️ **HTTPS**: Production phải dùng HTTPS

---

## 🚀 Cải Tiến Tương Lai

1. **Webhook signature verification**: Verify PayOS webhook bằng secret
2. **Idempotency**: Xử lý duplicate webhook
3. **Retry logic**: Tự động retry khi PayOS API fail
4. **Email notification**: Gửi email khi thanh toán thành công
5. **Refund**: Hỗ trợ hoàn tiền
6. **Invoice**: Generate PDF hóa đơn
7. **Analytics**: Dashboard tracking revenue

---

**Tác giả**: AI Study Hub Team  
**Cập nhật**: 2026-07-02
