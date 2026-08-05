# Client-Side Logic Audit (v2 — Staff Engineering Review)

Audit toàn bộ `src/` của **`ai-study-hub-fe`** (`D:\Semester7\SWP\repo4SWP\ai-study-hub-fe`), theo call graph / import / flow, không chỉ grep. Bỏ logic UI thuần. Mỗi mục: mức độ **P0** (bảo mật/dữ liệu sai), **P1** (hiệu năng/kiến trúc quan trọng), **P2** (nên làm), **P3** (dọn dẹp).

> Bối cảnh: TanStack Start SSR nhưng **0 `createServerFn`** — mọi API gọi thẳng từ browser qua `fetch` (`src/lib/api.ts`) tới Spring Boot `http://localhost:4040`. Token lưu `localStorage`.

---

# 1. Business Logic

## 1.1. Tính tiền proration nâng cấp gói

**Mức độ** P1

**File** `src/features/payment/proration.ts`

**Dòng** 16-55

**Code**

```ts
export function remainingDaysUntil(expiresAt?: string | null): number {
  if (!expiresAt) return 0;
  const end = new Date(expiresAt).getTime();
  const now = Date.now();
  if (Number.isNaN(end) || end <= now) return 0;
  return Math.ceil((end - now) / 86_400_000);
}
export function computeUpgrade(current, target, expiresAt): ProrationResult {
  ...
  const amountDue = Math.max(0, target.price - rv);
  ...
}
```

**Hiện tại** Client tính `remainingDays`, `remainingValue`, `amountDue` bằng giá gói fetch từ `/api/plans` + `Date.now()` local. `PremiumUpgradePage.tsx:82-114` dùng kết quả này cho báo giá, phân loại `isUpgrade/isDowngrade` theo `tier`.

**Rủi ro** Đồng hồ client lệch → số ngày còn lại sai → hiển thị tiền sai so với số backend thực thu. Người dùng có thể sửa state/lệch timezone.

**Đề xuất** Backend trả `quote`/`amountDue` trong response của `POST /api/payment/create` (hoặc endpoint `GET /api/payment/quote?planId=`). Client chỉ hiển thị. File `proration.ts` giữ hàm format, xóa hàm tính tiền (hoặc giữ cho UX nhưng backend quyết định).

## 1.2. Doanh thu + đếm giao dịch admin (chỉ 50 giao dịch đầu)

**Mức độ** P0

**File** `src/features/admin/components/AdminPremiumPage.tsx`

**Dòng** 279-290

**Code**

```ts
const { data, isLoading } = useAdminTransactions(0, 50);
const transactions = data?.content || [];
const totalPaid = transactions.filter((t) => t.status === "PAID").length;
const totalRevenue = transactions
  .filter((t) => t.status === "PAID")
  .reduce((sum, t) => sum + t.amount, 0);
```

**Hiện tại** "Total Premium Users", "Revenue This Month", "Total Transactions" tính trên **50 bản ghi đầu** của trang 1.

**Rủi ro** Doanh thu **sai về bản chất** khi > 50 giao dịch. Số liệu tài chính phải từ DB.

**Đề xuất** Endpoint `GET /api/admin/transactions/summary` (hoặc mở rộng `/api/admin/dashboard/stats`) trả `{ totalPaid, totalRevenue, pendingCount, totalCount }`. Bỏ reduce ở client.

## 1.3. Tính remaining days xóa vĩnh viễn (30 ngày)

**Mức độ** P1

**File** `src/features/admin/services/fileApi.ts`

**Dòng** 19-22, 59, 74

**Code**

```ts
function calculateRemainingDays(deletedAt: string): number {
  const diff = 30 - Math.floor((Date.now() - new Date(deletedAt).getTime()) / (1000 * 60 * 60 * 24));
  return Math.max(0, diff);
}
```

**Hiện tại** Client tự trừ từ `deletedAt`, hardcode 30 ngày; `AdminTrashPage.tsx:214` hardcode chữ "30 ngày".

**Rủi ro** Hardcode chính sách ở client; đổi retention phía backend là vỡ hiển thị; đồng hồ client sai → countdown sai.

**Đề xuất** Backend trả `remainingDays` trong response trash (`deletedAt` + retention policy là nghiệp vụ server).

## 1.4. Tính dung lượng đã dùng (storage used/free/pct)

**Mức độ** P1

**File** `src/routes/_authenticated/cloud.tsx:17-21`, `src/components/app-shell.tsx:105-108`, `src/components/ui/AIChat.tsx:208`

**Code**

```ts
// cloud.tsx
const used = docs.data?.reduce((sum, d) => sum + (d.fileSize ?? 0), 0) ?? 0;
const total = ((quota.data?.storageGb ?? 1) * 1024 * 1024 * 1024);
const pct = Math.min((used / total) * 100, 100);
const free = total - used;
const isOverLimit = used > total;
// app-shell.tsx
const used = documents?.reduce((sum, doc) => sum + (doc.fileSize || 0), 0) || 0;
```

**Hiện tại** `useDocuments()` trả danh sách **không phân trang** (toàn bộ docs) → reduce sum fileSize → coi là "used". `quota.data` có `storageGb` nhưng **không có** `storageUsed` (xác nhận: `QuotaDetails` thiếu field).

**Rủi ro** Danh sách bị cắt/thiếu → used sai → thanh quota + cảnh báo "Vượt giới hạn" sai; nặng hơn: nếu sau này backend phân trang, con số sẽ tụt đột ngột.

**Đề xuất** Backend bổ sung `storageUsed`/`storageTotal` vào `/api/quota` (tính `SUM(fileSize)` trong DB). Xóa 3 chỗ reduce.

## 1.5. Chấm điểm quiz

**Mức độ** P2

**File** `src/components/document-workspace/QuizzesTab.tsx:48-51`, `src/components/ui/QuizViewer.tsx:128-131`

**Code**

```ts
const score = useMemo(
  () => quizzes.reduce((s, q, i) => (answers[i] === q.answer ? s + 1 : s), 0),
  [answers, quizzes],
);
```

**Hiện tại** `QuizzesTab` là **quiz hardcode 3 câu** (dòng 14-43) không gọi API; score tính client, không lưu. `QuizViewer` đếm đúng/của quiz từ backend nhưng không submit kết quả.

**Rủi ro** Nếu score về sau dùng cho điểm/leaderboard thì phải do backend chấm; hiện tại dữ liệu không được lưu nên không có nghiệp vụ. Hardcode quiz (mọi user thấy cùng 3 câu giống nhau bất kể document) là hành vi sai sản phẩm — nên xóa tab này hoặc thay bằng quiz API thật.

**Đề xuất** Dùng `useQuizByDocument` (như `QuizTab` ở AI workspace) thay cho dữ liệu cứng; nếu cần lưu kết quả → `POST /api/quizzes/{id}/attempt` backend chấm.

## 1.6. Dựng object User giả khi /me fail

**Mức độ** P1

**File** `src/lib/auth.tsx:187-199, 221-232`

**Code**

```ts
const userObj: User = {
  id: res.userId, username: res.username, email: res.email ?? "",
  fullName: res.fullName, role: res.role,
  status: "ACTIVE", authProvider: "LOCAL",
  emailVerified: res.emailVerified ?? false,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};
setUser(userObj);
```

**Hiện tại** Login/register xong, nếu `accountApi.me()` fail (mạng, lỗi) → dựng User bằng tay với `status: "ACTIVE"` hardcode, `createdAt/updatedAt` = thời điểm hiện tại, **thiếu `plan`, `storageGb`** (User type có 2 field này).

**Rủi ro** UI hiển thị user "giả": plan/status sai → phần premium (app-shell:286, profile) hiển thị sai; storageGb undefined → quota fallback `?? 1`. Quyết định trạng thái tài khoản (ACTIVE/khóa) phải từ backend.

**Đề xuất** Không dựng User giả: nếu `/me` fail sau khi có token, giữ trạng thái "có token chưa có user" và retry `/me`; hoặc backend trả đủ `User` ngay trong response login (kèm plan/storageGb/status).

---

# 2. Authentication

## 2.1. Access + refresh token trong localStorage

**Mức độ** P0

**File** `src/lib/api.ts`

**Dòng** 6-43

**Code**

```ts
const TOKEN_KEY = "auth_token";
const REFRESH_KEY = "refresh_token";
const storage = typeof window !== "undefined" ? localStorage : null;
export const tokenStore = { get, set, clear, getRefresh, setRefresh };
```

**Hiện tại** Cả 2 token lưu `localStorage`, đọc bằng JS bất kỳ lúc nào; `api.ts:123` còn log `token.substring(0, 10)` ra console.

**Rủi ro** XSS → trộm token → chiếm phiên vĩnh viễn (refresh token dài hạn trong tay attacker).

**Đề xuất** Chuyển sang httpOnly cookie (hỗ trợ refresh cookie), bỏ `Authorization: Bearer`; `credentials: "include"` đã sẵn (api.ts:143). Xóa log token.

## 2.2. Refresh token tự động (3 nơi, có chồng lặp)

**Mức độ** P1

**File** `src/lib/api.ts:74-115, 150-210`; `src/lib/auth.tsx:136-167`; `src/features/auth/hooks/useAuth.tsx:71-97`

**Code**

```ts
// lib/auth.tsx — interval 10 phút
const interval = setInterval(async () => {
  const token = tokenStore.get();
  const refreshToken = tokenStore.getRefresh();
  if (!token || !refreshToken) { setUser(null); clearInterval(interval); return; }
  const res = await authApi.refresh();
  ...
}, 10 * 60 * 1000);
```

**Hiện tại** 3 lớp refresh: (1) api() tự refresh khi 401, (2) AuthProvider `lib/auth.tsx` interval 10 phút, (3) `features/auth/hooks/useAuth.tsx` — một AuthProvider **cũ** cũng có interval 10 phút (file này không được import ở đâu, xem Dead Code 20.1).

**Rủi ro** Interval refresh chủ động 10 phút có thể chạy đè lên refresh do 401 gây race (refresh token bị rotate 2 lần); 2 AuthProvider cùng tồn tại → cạnh tranh setUser.

**Đề xuất** Giữ 1 cơ chế: chỉ refresh khi 401 (lazy) hoặc 1 scheduler duy nhất; xóa file useAuth cũ.

## 2.3. OAuth callback — token qua URL query

**Mức độ** P0

**File** `src/routes/oauth-success.tsx`

**Dòng** 23-46

**Code**

```ts
const params = new URLSearchParams(window.location.search);
const token = params.get("access_token");
const refreshToken = params.get("refresh_token");
const userId = params.get("user_id");
...
tokenStore.set(token);
if (refreshToken) tokenStore.setRefresh(refreshToken);
if (userId) localStorage.setItem("user_id", userId);
```

**Hiện tại** Backend redirect kèm token trong query string; client đọc + lưu; `user_id` còn lưu thêm vào localStorage nhưng **không nơi nào đọc lại** (đã kiểm tra toàn repo).

**Rủi ro** Token rò qua browser history / Referer / access log / share link. `user_id` là dead data.

**Đề xuất** Dùng authorization-code exchange (backend đổi `code` lấy token, set httpOnly cookie, redirect về FE không kèm token). Xóa `localStorage.setItem("user_id")`.

## 2.4. Xác thực email — token + verify qua query

**Mức độ** P2

**File** `src/routes/verify-email.tsx:33-34`, `src/lib/realApi.ts:77-78`

**Code**

```ts
const params = new URLSearchParams(window.location.search);
const token = params.get("token");
await authApi.verifyEmail(token); // POST /api/auth/verify?token=...
```

**Hiện tại** Token verify trong URL, POST lên backend.

**Rủi ro** Token trong URL dễ bị thu thập; ngoài ra `/api/auth/verify` **không nằm** trong `PUBLIC_AUTH_PATHS` của api.ts:152-158 → nếu backend trả 401 cho verify, client sẽ tự động refresh (gọi `/refresh` với token của user khác/không có) trước khi throw. Không gây sai dữ liệu nhưng lãng phí + log lộ.

**Đề xuất** Chuyển token sang POST body; thêm path vào `PUBLIC_AUTH_PATHS`.

## 2.5. Logout chỉ xóa local, không revoke server session

**Mức độ** P2

**File** `src/lib/auth.tsx:242-258`

**Code**

```ts
const logout = async () => {
  try { await authApi.logout(); } catch { /* Ignore */ }
  tokenStore.clear();
  setUser(null);
  window.location.href = "/auth/login";
};
```

**Hiện tại** `authApi.logout()` gọi `POST /api/auth/logout` nhưng lỗi thì bỏ qua, chỉ clear token local.

**Rủi ro** Nếu backend revoke refresh token khi logout, lỗi mạng → refresh token vẫn sống → phiên chưa thực sự kết thúc phía server.

**Đề xuất** Backend revoke token server-side (blacklist refresh); FE hiển thị lỗi logout nếu API fail thay vì im lặng (hoặc chấp nhận nếu backend tự expire ngắn hạn).

## 2.6. Kiểm tra account status chỉ qua 1 lần /me khi mount

**Mức độ** P2

**File** `src/lib/auth.tsx:60-88`, `src/components/app-shell.tsx:86-91`

**Code**

```ts
// auth.tsx
const u = await accountApi.me();
setUser(u);
// app-shell.tsx — poll 60s
const interval = setInterval(() => { reloadUser().catch(() => {}); }, 60000);
```

**Hiện tại** AppShell poll `reloadUser()` mỗi 60s — đây là lớp kiểm tra trạng thái (khóa tài khoản) duy nhất, nhưng **không xử lý 403**: `reloadUser` catch bỏ qua lỗi → user bị khóa vẫn thấy app bình thường tới khi 1 request khác 403.

**Rủi ro** Tài khoản bị khóa giữa phiên → UI không phản ứng kịp; chỉ `ACCOUNT_LOCKED` từ các mutation mới alert (queries.ts:39-51).

**Đề xuất** Khi `me()` trả 403/locked → logout ngay (không nuốt lỗi); backend nên có cơ chế buộc client thoát (vd: 403 + header `X-Account-Locked`).

---

# 3. Authorization

## 3.1. Chặn route admin chỉ bằng role client

**Mức độ** P0

**File** `src/routes/admin_panel/route.tsx:15-20`, `src/routes/_authenticated/dashboard.tsx:54-57`, `src/components/app-shell.tsx:286`

**Code**

```ts
const isAdmin = user?.role === "ADMIN";
useEffect(() => {
  if (!isAuthenticated) navigate({ to: "/auth/login", replace: true });
  else if (!isAdmin) navigate({ to: "/dashboard", replace: true });
}, [isAuthenticated, isLoading, isAdmin, navigate]);
```

**Hiện tại** Toàn bộ việc "ai được vào admin panel" dựa trên `user.role` trong state client (từ localStorage token + /me). `role` từ response login, hoặc từ object User giả (2.6 mục 1.6) có thể bị sửa.

**Rủi ro** Nếu backend không chặn role ở mỗi endpoint admin → người dùng thường sửa state/localStorage vẫn gọi được admin API. Client-side guard chỉ là UX.

**Đề xuất** Backend bắt buộc `@PreAuthorize("hasRole('ADMIN')")` (hoặc tương đương) trên **mọi** `/api/admin/**`; client guard giữ nguyên như UX. Kiểm tra backend đã làm chưa.

## 3.2. Lọc document BANNED/REJECT/COMPLETED chỉ ở client

**Mức độ** P0

**File** (gộp) `src/components/ui/AIChat.tsx:207, 383` · `src/components/document-workspace/ContentPanel.tsx:171` · `src/components/document-workspace/FolderPanel.tsx:43, 59` · `src/components/shared-workspace/SharedWorkspace.tsx:151-155, 223-226` · `src/routes/_authenticated/ai.tsx:23-25` · `src/routes/_authenticated/folders.$id.tsx:26-29` · `src/routes/_authenticated/documents.tsx:181-183` · `src/components/document-actions-menu.tsx:40, 88-95`

**Code**

```ts
// AIChat.tsx
const docs = (folderDocs.data ?? []).filter((d: any) => d.status?.toUpperCase() !== 'BANNED');
// SharedWorkspace.tsx
return s !== "BANNED" && s !== "REJECT" && s !== "COMPLETED";
// ai.tsx — sau khi fetch xong mới điều hướng
if (doc.data?.status?.toUpperCase() === "BANNED") { navigate({ to: "/documents" }); }
```

**Hiện tại** FE fetch toàn bộ documents kèm `cloudinaryUrl`, sau đó tự lọc trạng thái để ẩn. Doc bị BAN vẫn nhận về đầy đủ URL nội dung.

**Rủi ro** Người dùng gọi `GET /api/documents/{id}` / list trực tiếp vẫn nhận `cloudinaryUrl` của doc BANNED (nếu backend không lọc) → xem nội dung bị cấm. Bảo mật phụ thuộc client = vỡ.

**Đề xuất** Backend filter theo role: owner chỉ nhận metadata doc BANNED (không kèm `cloudinaryUrl`); shared user không nhận BANNED/REJECT/COMPLETED ở tầng query.

## 3.3. Doc share route yêu cầu đăng nhập

**Mức độ** P2

**File** `src/routes/_authenticated/shared.$shareId.tsx` (route nằm dưới layout `_authenticated` — `route.tsx:13-45`)

**Hiện tại** `/shared/{shareId}` nằm trong nhóm `_authenticated` → chưa đăng nhập là redirect login. Cơ chế chia sẻ công khai (shareToken) có thể được thiết kế cho người không cần tài khoản.

**Rủi ro** Không phải lỗi bảo mật (share token vẫn cần auth nếu backend yêu cầu), nhưng **quyết định "share này có cần login không" đang nằm ở routing client** — nếu backend cho phép public share mà FE chặn login thì feature hỏng; ngược lại nếu backend yêu cầu auth thì OK. Cần thống nhất.

**Đề xuất** Xác định rõ chính sách: public share → route ngoài `_authenticated`; ngược lại giữ nguyên, backend chặn bằng token validity.

## 3.4. Quyền "đã xem file" để duyệt (admin) chỉ ở client

**Mức độ** P3

**File** `src/features/admin/components/AdminApprovalsPage.tsx:53, 240-253`, `AdminFilesPage.tsx:43, 192-217`

**Code**

```ts
const [reviewedIds, setReviewedIds] = useState<Set<string>>(new Set());
...
disabled={!reviewedIds.has(item.id) || action.isPending}
```

**Hiện tại** Bắt buộc admin xem preview trước khi approve/reject bằng Set state client — refresh trang là mất.

**Rủi ro** Không phải bảo mật (chỉ UX), nhưng là "rule" giả ở client; không đồng bộ giữa 2 trang admin (approvals vs files dùng 2 Set khác nhau).

**Đề xuất** Giữ nguyên nếu chỉ là UX; nếu muốn bắt buộc thật → backend ghi `previewedAt` khi gọi preview URL.

---

# 4. Payment

## 4.1. Mock payment success còn trong production code path

**Mức độ** P0

**File** `src/features/payment/components/PremiumUpgradePage.tsx`

**Dòng** 126-141

**Code**

```ts
const res = await paymentApi.createPayment(selected.id);
const url = res.checkoutUrl ?? "";
const isMockSuccess =
  url.includes("upgraded=1") ||
  (typeof window !== "undefined" &&
    url.startsWith(window.location.origin) &&
    url.includes("/premium"));
if (isMockSuccess) {
  await reloadUser();
  ...toast.success(`Đã nâng cấp lên ${selected.name}!`);
}
```

**Hiện tại** Nếu backend trả checkoutUrl chứa `upgraded=1` hoặc origin+`/premium` → FE tự tin thanh toán thành công, reload user, toast "Đã nâng cấp" — **không hề gọi xác nhận giao dịch**.

**Rủi ro** Giao dịch chưa được PayOS xác nhận nhưng UI báo thành công; kẻ tấn công tự tạo URL mock (nếu backend dev-mode trả URL này) hoặc sửa response → UI sai sự thật. Trạng thái premium chỉ nên đổi khi **backend webhook/callback** xác nhận.

**Đề xuất** Xóa nhánh mock. Chỉ hiển thị QR/link; trạng thái mới lấy từ `getTransactionStatus(orderCode)` hoặc khi webhook cập nhật subscription. Kiểm tra backend đã có PayOS webhook chưa.

## 4.2. Poll trạng thái thanh toán bằng /me mỗi 5 giây

**Mức độ** P1

**File** `src/features/payment/components/PremiumUpgradePage.tsx:205-220, 223-238`; `src/routes/_authenticated/payment.success.tsx:32-58`

**Code**

```ts
poller = setInterval(async () => {
  try {
    const u = await accountApi.me();
    if (u && u.plan && String(u.plan).toUpperCase() !== currentPlan) { ... }
  } catch (e) { ... }
}, 5000);
```

**Hiện tại** Khi QR modal mở, cứ 5s gọi `/api/account/me` (trả cả user, ghi log cả DB) chỉ để so sánh plan name.

**Rủi ro** Request rác lên server; nếu nhiều tab → nhân lên. Dùng sai endpoint cho nghiệp vụ "payment status".

**Đề xuất** Dùng `paymentApi.getTransactionStatus(orderCode)` (đã có) cho cả 2 nơi; hoặc webhook + tối thiểu là `getMySubscription()` (cache 5s) thay cho `/me`.

## 4.3. Countdown QR 180s + expiredAt fallback cứng

**Mức độ** P3

**File** `src/features/payment/components/PremiumUpgradePage.tsx:47, 144-147`

**Code**

```ts
const [countdown, setCountdown] = useState(180); // 3 phút
const remainingSec = expiredAt ? Math.max(0, Math.floor((new Date(expiredAt).getTime() - Date.now()) / 1000)) : 180;
```

**Hiện tại** Mặc định 180s khi backend không trả `expiredAt`; countdown ngược chạy interval 1s.

**Rủi ro** Chính sách hết hạn payment nằm ở client; backend đổi timeout → lệch.

**Đề xuất** Bắt buộc backend trả `expiredAt`; client chỉ đếm ngược từ giá trị đó.

---

# 5. Storage

## 5.1. Fetch file Cloudinary trực tiếp từ browser

**Mức độ** P1

**File** `src/components/document-viewer/cloudinaryUtils.ts:24-64`, `DocxViewer.tsx:53`, `TextViewer.tsx:32`

**Code**

```ts
export async function fetchCloudinaryFile(url, options?): Promise<Blob | null> {
  ...
  const response = await fetch(targetUrl, { method: 'GET', credentials: 'omit', mode: 'cors' });
  return await response.blob();
}
```

**Hiện tại** FE nhận `cloudinaryUrl` từ API và fetch trực tiếp (kèm `fl_attachment=false` để ép hiển thị) — **không qua proxy backend**.

**Rủi ro** (a) Nếu URL không signed/private → ai biết URL đều xem được file; (b) URL có signature ngắn hạn → hiển thị đứt đoạn; (c) `cloudinaryUrl` nằm trong mọi list response → càng nhiều nơi lộ URL.

**Đề xuất** Backend serve qua endpoint proxy có auth: `GET /api/documents/{id}/content` (stream file), FE viewer dùng endpoint này; chỉ trả `cloudinaryUrl` ở nơi thật sự cần (admin preview). Kiểm tra backend đã có endpoint content chưa.

## 5.2. Download URL — client mở window.open

**Mức độ** P2

**File** `src/components/document-workspace/ContentPanel.tsx:48-56`, `src/features/shares/hooks/useShareActions.tsx:19-34`

**Code**

```ts
const res = await download.mutateAsync(docId);
window.open(res.url, "_blank");
```

**Hiện tại** `GET /api/documents/{id}/download` trả URL, FE `window.open`. URL này nằm trong response (dễ log).

**Rủi ro** Nếu URL là presigned S3/Cloudinary — rò qua history; không có auth tại thời điểm tải.

**Đề xuất** Backend stream trực tiếp khi gọi download endpoint (Content-Disposition), không trả URL cho client.

## 5.3. Hardcode "11.4mb" cho size mọi item share

**Mức độ** P2

**File** `src/features/shares/services/shareApi.ts:47, 75`

**Code**

```ts
size: "11.4mb",
```

**Hiện tại** Mọi mục "Được chia sẻ với tôi" đều hiển thị size "11.4mb" — dữ liệu giả.

**Rủi ro** Sai thông tin người dùng; `items`/`fileCount` có từ backend nhưng size không.

**Đề xuất** Backend trả `size` thật (hoặc bỏ cột hiển thị).

---

# 6. AI

## 6.1. Quota AI — client tự tính "hết lượt" nhưng nguồn từ backend

**Mức độ** P2

**File** `src/features/ai/AISummary.tsx:20-23`, `src/features/ai/FlashcardTab.tsx:22-25`, `src/features/ai/QuizTab.tsx:22-25`, `src/components/ui/QuotaDisplay.tsx:65-68`

**Code**

```ts
const isExhausted = limit > 0 && remaining <= 0;
const isLow = limit > 0 && remaining <= Math.floor(limit * 0.2) && remaining > 0;
```

**Hiện tại** Các tab AI **chỉ chặn nút khi client thấy hết lượt** (dữ liệu từ `/api/quota`). Generate vẫn gọi backend.

**Rủi ro** Nếu backend không tự chặn/trừ quota khi generate (chỉ trả về "OK"), user bỏ qua UI dễ dàng (gọi thẳng API) → dùng vô hạn. Client chặn không phải enforcement.

**Đề xuất** Backend kiểm tra + trừ quota trong chính handler generate (idempotent theo yêu cầu); trả lỗi `QUOTA_EXCEEDED` — FE chỉ hiển thị. Kiểm tra backend đã làm chưa.

## 6.2. AI status ("REJECT"/"PROCESSING"...) — backend là nguồn, FE render

**Mức độ** P3

**File** `src/components/ui/AIChat.tsx:121-124, 559-658`

**Hiện tại** Trạng thái AI đến từ `/api/v1/rag/status` + `doc.aiStatus` — đã đúng (backend quyết định). Các chuỗi `"NOT_STARTED"/"PROCESSING"/"COMPLETED"/"REJECT"` so sánh cứng ở client — OK miễn backend giữ enum.

**Đề xuất** Không đổi.

## 6.3. Markdown parse + session mapping — UI thuần

**Mức độ** P3

**File** `src/components/ui/AIChat.tsx:140-148, 775-803`

**Hiện tại** Map `senderType` → role, `ReactMarkdown` render — display thuần, không có token count/citation/context chọn ở client (đã kiểm tra: không có).

**Đề xuất** Không đổi.

---

# 7. Data Filtering

## 7.1. Shares: search + sort + paginate toàn bộ client-side

**Mức độ** P1

**File** `src/features/shares/hooks/useShares.tsx`

**Dòng** 48-90

**Code**

```ts
const filteredWithMe = useMemo(() =>
  withMe.filter((x) => match(x.name) || match(x.sharedBy.name)).sort(sortFn),
  [withMe, q, sort]);
const pagedWithMe = filteredWithMe.slice((pageWithMe - 1) * PAGE_SIZE, pageWithMe * PAGE_SIZE);
totalPagesWithMe: Math.max(1, Math.ceil(filteredWithMe.length / PAGE_SIZE)),
```

**Hiện tại** `useEffect` fetch toàn bộ `getSharedWithMe()` + `getSharedByMe()` một lần; search/sort/slice tại client (`PAGE_SIZE = 4`).

**Rủi ro** Dữ liệu share tăng → payload lớn mỗi lần mount; không tận dụng index DB.

**Đề xuất** Backend hỗ trợ `GET /api/shares/shared-with-me?q=&sort=&page=&size=`; FE chỉ gọi theo tham số.

## 7.2. N+1 subjects (fetch mọi semester → từng subject)

**Mức độ** P1

**File** `src/lib/queries.ts:143-157, 174-178`

**Code**

```ts
export function useSubjects() {
  return useQuery({
    queryKey: ["subjects", "all"],
    queryFn: async () => {
      const semesters = await semesterApi.list();
      const results = await Promise.all(
        semesters.map((s) => subjectApi.listBySemester(s.id).catch(() => [] as never[]))
      );
      return results.flat();
    },
    ...
```

**Hiện tại** Mỗi lần mount (gọi từ `app-shell.tsx:73`, `documents.tsx:48`, `folders.tsx:74`, `trash.tsx:38`...) → 1 request semester + N request subject.

**Rủi ro** 9-10 request phụ mỗi trang, mỗi tab; backend nhận nhiều request lặp.

**Đề xuất** Thêm `GET /api/subjects` (trả all kèm semesterId) — bỏ vòng lặp.

## 7.3. Documents: search + sort pin client-side

**Mức độ** P2

**File** `src/routes/_authenticated/documents.tsx:57-59`, `src/routes/_authenticated/folders.tsx:112-114`

**Code**

```ts
const filtered = (data ?? [])
  .filter((d) => d.title.toLowerCase().includes(query.toLowerCase()))
  .sort((a, b) => Number(isPinned(b.id)) - Number(isPinned(a.id)));
```

**Hiện tại** Fetch toàn bộ docs/folders; tìm kiếm và sắp xếp ở client; pin/star nằm localStorage (xem 11.2) nên sort không đồng bộ giữa thiết bị.

**Rủi ro** Dữ liệu lớn → chậm; tìm kiếm không dùng index backend.

**Đề xuất** `GET /api/documents?q=&sort=`; quyết định pin/star lên backend nếu là tính năng chính thức.

## 7.4. Search header fetch toàn bộ documents

**Mức độ** P2

**File** `src/components/app-shell.tsx:79-83`

**Code**

```ts
const headerSearchResults = useMemo(() => {
  if (!headerSearch.trim()) return [];
  const q = headerSearch.toLowerCase();
  return (documents ?? []).filter((d) => d.title.toLowerCase().includes(q)).slice(0, 8);
}, [headerSearch, documents]);
```

**Hiện tại** Tìm 8 kết quả trong danh sách toàn bộ docs đã fetch.

**Rủi ro** Tốn băng thông mỗi trang chỉ để search header; hậu quả tương tự 7.3.

**Đề xuất** Endpoint search riêng (debounce + min chars).

## 7.5. Reported: filter pending/handled client

**Mức độ** P3

**File** `src/routes/_authenticated/reported.tsx:50-51`

**Code**

```ts
const pendingReports = useMemo(() => myReports.filter((r: any) => r.status === 'pending'), [myReports]);
const handledReports = useMemo(() => myReports.filter((r: any) => r.status !== 'pending'), [myReports]);
```

**Hiện tại** Backend trả `Page<ReportResponse>` (đã unwrap content) → FE tự lọc + đếm.

**Rủi ro** Nhỏ — dữ liệu report của 1 user không lớn; nhưng nên để backend lọc nếu có pagination.

**Đề xuất** `GET /api/reports/my?status=` khi cần.

## 7.6. Admin: tìm kiếm docs/users/files client-side

**Mức độ** P2

**File** `src/features/admin/components/AdminDocumentsPage.tsx:63-71`, `AdminFilesPage.tsx:47-55`, `AdminUsersPage.tsx:29-37`

**Code**

```ts
const filtered = useMemo(() =>
  documents.filter((d) =>
    d.title?.toLowerCase().includes(query.toLowerCase()) ||
    d.ownerName?.toLowerCase().includes(query.toLowerCase())),
  [documents, query]);
```

**Hiện tại** Admin fetch **toàn bộ** documents/users (API không phân trang — xem 14.2) rồi search client.

**Rủi ro** Số tài liệu/user lớn → payload nặng, chậm; không phân trang là vấn đề chính (14.2).

**Đề xuất** Backend phân trang + `q` param cho cả 3 trang admin.

## 7.7. subjects.$id — filter folders theo subjectId + thiếu subject thật

**Mức độ** P2

**File** `src/routes/_authenticated/subjects.$id.tsx:19-30`

**Code**

```ts
const allSubjects: Subject[] = useMemo(() => { ...; return []; }, [semesters]);
const folders = useMemo(
  () => allFolders.filter((f) => f.subjectId === subjectId),
  [allFolders, subjectId],
);
```

**Hiện tại** Trang "Môn học" **không có subject thật** (trả `[]`, header hiển thị ID 8 ký tự, tiêu đề cứng "Subject"); folder lọc bằng `filter` client.

**Rủi ro** Feature dở dang hiển thị tới user; mọi quyết định dữ liệu nằm client.

**Đề xuất** Backend: `GET /api/subjects/{id}` (metadata) + `GET /api/folders?subjectId=`.

---

# 8. Statistics

## 8.1. Admin dashboard trend tính client

**Mức độ** P3

**File** `src/features/admin/components/AdminDashboardPage.tsx:44-47, 116-118`

**Code**

```ts
function calculateTrend(lastWeek: number, prevWeek: number): number {
  if (prevWeek === 0) return lastWeek > 0 ? 100 : 0;
  return Math.round(((lastWeek - prevWeek) / prevWeek) * 100);
}
```

**Hiện tại** % tăng/giảm tính từ `usersLastWeek/usersPrevWeek...` do backend trả.

**Rủi ro** Không sai — nguồn từ backend. Chỉ là format. Giữ nguyên.

**Đề xuất** Không đổi (hoặc backend trả sẵn % nếu muốn thống nhất).

## 8.2. Profile: đếm docs/folders/shares bằng length danh sách không phân trang

**Mức độ** P2

**File** `src/routes/_authenticated/profile.tsx:83-102`

**Code**

```ts
const stats = [
  { label: "Tài liệu", value: docs.data?.length ?? 0, ... },
  { label: "Thư mục", value: folders.data?.length ?? 0, ... },
  { label: "Chia sẻ", value: sharedDocs.data?.length ?? 0, ... },
];
```

**Hiện tại** Fetch 3 danh sách đầy đủ chỉ để đếm.

**Rủi ro** Tốn băng thông; sai khi backend phân trang sau này.

**Đề xuất** Backend trả counts trong `/api/account/me` hoặc endpoint stats riêng.

## 8.3. Folders page: đếm lại documentCount bằng loop + console.log debug

**Mức độ** P3

**File** `src/routes/_authenticated/folders.tsx:96-110`

**Code**

```ts
const countByFolder = useMemo(() => {
  const m = new Map<string, number>();
  (docs ?? []).forEach((d) => { if (d.folderId != null) m.set(String(d.folderId), (m.get(String(d.folderId)) ?? 0) + 1); });
  console.log("DEBUG DOCUMENT COUNT MAP:", Object.fromEntries(m));
  return m;
}, [docs]);
```

**Hiện tại** Backend đã trả `f.documentCount` (dòng 107 dùng `f.documentCount ?? ...`) nhưng FE vẫn fetch toàn bộ docs để đếm lại + log debug ra console production.

**Rủi ro** Thừa request dữ liệu; log nhạy cảm.

**Đề xuất** Dùng thẳng `documentCount` backend; xóa loop + console.log.

## 8.4. Admin stats: "Total Premium Users" dùng sai con số

**Mức độ** P1

**File** `src/features/admin/components/AdminPremiumPage.tsx:282-285, 310-337`

**Code**

```ts
<StatCard label="Total Premium Users" value={String(totalPaid)} ... />
```

**Hiện tại** Gắn nhãn "Total Premium Users" nhưng giá trị là **số giao dịch PAID** trong 50 bản ghi — không phải số user premium.

**Rủi ro** Chỉ số nghiệp vụ sai ý nghĩa.

**Đề xuất** Backend trả đúng metric (đếm subscription ACTIVE phân biệt user); xem 1.2.

---

# 9. Validation

## 9.1. Upload: không check size/type trước khi gửi

**Mức độ** P1

**File** `src/routes/_authenticated/documents.tsx:358-379` (UploadDialog), `src/components/document-workspace/DocumentWorkspace.tsx:127-140`, `src/components/ui/AIChat.tsx:907-920`

**Code**

```ts
const submit = async () => {
  if (files.length === 0) return toast.error("Chọn ít nhất một file");
  if (!multiple && !title.trim()) return toast.error("Nhập tiêu đề");
  ...
  await upload.mutateAsync({ files, title, ..., folderId });
};
```

**Hiện tại** Không kiểm tra kích thước/định dạng file nào ở client; gửi thẳng multipart.

**Rủi ro** Không phải lỗi client nếu **backend đã validate** (bắt buộc phải có: max size, MIME allowlist, quota). Nếu backend thiếu → upload file khổng lồ/định dạng lạ tốn storage. UI hiện lỗi sau khi upload cả file mới biết.

**Đề xuất** Kiểm tra backend có validate size/type/quota trước khi lưu Cloudinary không; FE thêm check sơ bộ (size ≤ quota còn lại, đuôi .pdf/.docx/.txt/img) để trả lỗi sớm.

## 9.2. Plan form (admin): validate ở client, backend phải validate lại

**Mức độ** P2

**File** `src/features/admin/components/PlanFormModal.tsx:87-99`

**Code**

```ts
const validateForm = (): { valid: boolean; message?: string } => {
  if (!name.trim()) return { valid: false, message: "Tên gói không được để trống" };
  if (price < 0) ...
```

**Hiện tại** Giá/limit/tier được validate bằng form client trước khi `PUT/POST /api/admin/plans`.

**Rủi ro** Nếu backend không validate (giá âm, tier trùng...) thì admin (hoặc attacker) ghi dữ liệu hỏng → proration/giao dịch tính sai.

**Đề xuất** Backend validate nghiêm (bean validation + logic tier trùng), FE giữ UX.

## 9.3. Email regex + zod form — chỉ UX

**Mức độ** P3

**File** `src/routes/_authenticated/profile.tsx:49`, `src/routes/auth/register.tsx`, `reset-password.tsx`, `login.tsx`

**Hiện tại** Validate client trước khi gọi API (zod + regex).

**Rủi ro** Không — nếu backend validate lại. Chuẩn.

**Đề xuất** Không đổi.

---

# 10. URL Param

## 10.1. Token/OTP/email qua URL

**Mức độ** P0 (token OAuth) / P2 (OTP)

**File** (gộp) `src/routes/oauth-success.tsx:23-26` (access_token/refresh_token/user_id) · `src/routes/verify-email.tsx:33-34` (token) · `src/routes/auth/reset-password.tsx:18-21, 39` (email/otp)

**Code**

```ts
// oauth-success
const token = params.get("access_token");
// reset-password
const searchSchema = z.object({
  email: z.string().optional().default(""),
  otp: z.string().optional().default(""),
});
const { email, otp } = Route.useSearch();
```

**Hiện tại** Backend gửi token/OTP qua query string; FE đọc rồi dùng.

**Rủi ro** Rò rỉ qua browser history, Referer header, server/proxy logs. OTP + email trong URL còn bị share link vô tình.

**Đề xuất** OAuth → code exchange (2.3). Reset-password → OTP nhập tay (form) thay vì URL; verify-email → POST body.

---

# 11. Local Storage

## 11.1. Token trong localStorage

**Mức độ** P0 — gộp với 2.1.

## 11.2. Star/pin/starred-shared lưu localStorage — mất dữ liệu + sort không đồng bộ

**Mức độ** P2

**File** `src/lib/preferences.ts:8-53`

**Code**

```ts
function persist() {
  try { window.localStorage.setItem(storageKey, JSON.stringify(Array.from(ids))); } catch {}
}
const starredFoldersStore = createIdSetStore("ai-study-hub:starred-folders");
const pinnedDocumentsStore = createIdSetStore("ai-study-hub:pinned-documents");
```

**Hiện tại** Star folder / pin document / star shared-item chỉ nằm localStorage, pub-sub trong tab.

**Rủi ro** (a) Xóa cache/đổi máy → mất hết pin/star; (b) sort theo pin (documents.tsx:59, folders.tsx:114, SharedWithMeTable.tsx:37-39) không nhất quán giữa thiết bị; (c) nếu là tính năng chính thức → nên là data người dùng (backend).

**Đề xuất** Nếu pin/star là tính năng product → thêm field `pinned/starred` trên backend (Document/Folder), đồng bộ qua API. Nếu chỉ là tiện ích local → chấp nhận, ghi chú.

## 11.3. user_id localStorage thừa

**Mức độ** P3

**File** `src/routes/oauth-success.tsx:45`

**Code**

```ts
localStorage.setItem("user_id", userId);
```

**Hiện tại** Lưu nhưng không đọc lại ở bất kỳ đâu (đã grep toàn repo).

**Đề xuất** Xóa dòng này.

## 11.4. UI keys (sidebar, panel widths, theme) — OK

**Mức độ** P3 — `app-shell.tsx:53,96`, `AIChat.tsx:161-166`, `theme.tsx` — không phải nghiệp vụ. Giữ nguyên.

---

# 12. Hardcode

## 12.1. Status enum + chuỗi trạng thái rải rác

**Mức độ** P2

**File** `src/lib/document-status.ts:3` + 8 nơi so sánh `"BANNED"/"REJECT"/"COMPLETED"` (mục 3.2) + `src/routes/_authenticated/documents.tsx:196-211` (hàm `getStatusBadge` **tự dựng** badge thay vì dùng `DocumentStatusBadge`/`statusLabel` đã có)

**Hiện tại** Enum backend nhân đôi ở client (`document-status.ts`), thêm 1 bảng màu badge nữa trong `documents.tsx` → 3 nguồn trạng thái.

**Rủi ro** Backend đổi enum (thêm trạng thái) → client vỡ âm thầm (badge rỗng, filter sai).

**Đề xuất** Dùng chung `document-status.ts` (đã có `statusLabel`/`statusBadgeClasses`), xóa `getStatusBadge` cục bộ; đồng bộ enum với backend.

## 12.2. Tên gói cứng ("FREE"/"Basic"/"Premium") + ngưỡng 9999

**Mức độ** P2

**File** `src/features/admin/services/paymentApi.ts:127-129, 137-139`, `src/features/payment/components/PremiumUpgradePage.tsx:83, 87-90`, `src/components/app-shell.tsx:286`, `src/features/admin/components/AdminPremiumPage.tsx:198-210`

**Code**

```ts
const plans = await api<BackendPlan[]>("/api/payment/plans");
return plans
  .filter((p) => p.name !== "Free" && p.name !== "Basic" && p.isActive)
  ...
p.aiQuestions > 9999 ? "Không giới hạn câu hỏi AI" : ...
```

**Hiện tại** Lọc gói theo **tên** ("Free"/"Basic") thay vì `tier`; quy ước "> 9999 = không giới hạn" trong khi backend dùng `-1` cho unlimited (types.ts:72-75).

**Rủi ro** Đổi tên gói qua admin → FE lọc sai; plan limit 5000 bị hiểu là giới hạn bình thường nhưng quy ước 9999 mơ hồ; `PremiumUpgradePage` so `plan !== "FREE"` bằng tên trong khi `tier` mới là chuẩn.

**Đề xuất** Dùng `tier`/`isActive` để phân loại (đã có field `tier`); bỏ quy ước 9999, dùng `-1`.

## 12.3. "30 ngày" retention hardcode

**Mức độ** P1 — gộp với 1.3.

## 12.4. Số kỳ học hardcode `SEMESTER_COUNT = 9`

**Mức độ** P3

**File** `src/lib/config.ts:8-14`

**Hiện tại** Danh sách kỳ thật đến từ `/api/semesters`; `SEMESTERS` array chỉ là danh sách số 1..9 (không thấy nơi dùng — grep: chỉ config.ts). 

**Đề xuất** Xóa nếu không dùng.

---

# 13. TODO / FIXME / HACK / MOCK

## 13.1. `TODO(backend)` trong AdminProfilePage — form báo thành công nhưng không gọi API

**Mức độ** P1

**File** `src/features/admin/components/AdminProfilePage.tsx:32-37, 47-60`

**Code**

```ts
const save = (e: React.FormEvent) => {
  e.preventDefault();
  // TODO(backend): gọi accountApi.update(form)
  toast.success("Đã cập nhật hồ sơ");
  setEditing(false);
};
```

**Hiện tại** Admin sửa hồ sơ/đổi mật khẩu → toast "Đã cập nhật" **mà không có request nào**. Nút "Đổi mật khẩu" tương tự.

**Rủi ro** Người dùng tin là đã đổi mật khẩu nhưng không; lỗi sản phẩm nghiêm trọng (mật khẩu không đổi → vẫn đăng nhập bằng mật khẩu cũ, người khác biết mật khẩu cũ vẫn vào được).

**Đề xuất** Nối `accountApi.updateProfile` + endpoint `PUT /api/account/password` (backend); hoặc ẩn form cho tới khi có API.

## 13.2. MOCK_SESSIONS — phiên đăng nhập giả

**Mức độ** P2

**File** `src/routes/_authenticated/admin.tsx:30-52, 71, 92-100, 234-241`

**Code**

```ts
const MOCK_SESSIONS = [ { id: "1", device: "Chrome - Windows 11", ... }, ... ];
const [sessions, setSessions] = useState(MOCK_SESSIONS);
...
const revokeSession = (id: string) => { setSessions((s) => s.filter((x) => x.id !== id)); toast.success("Đã đăng xuất khỏi phiên"); };
```

**Hiện tại** Trang "Cài đặt & Bảo mật" hiển thị 3 phiên giả, nút "Đăng xuất" chỉ xóa state local; 2FA "QR Code (mock)" cũng giả (dòng 235).

**Rủi ro** Người dùng tưởng đã thu hồi phiên/đổi bảo mật — nhưng không có tác động thật; session management phải là backend (JWT jti / refresh token store).

**Đề xuất** Backend: `GET /api/account/sessions`, `DELETE /api/account/sessions/{id}`, 2FA thật — hoặc ẩn mục này tới khi có API.

## 13.3. Mock payment success

**Mức độ** P0 — gộp với 4.1.

## 13.4. ChatPanel "đang phát triển"

**Mức độ** P3

**File** `src/components/document-workspace/ChatPanel.tsx:70`

**Code**

```ts
onClick={() => toast.info(`${h.label} — đang phát triển`)}
```

**Hiện tại** 4 nút highlight chỉ toast "đang phát triển".

**Đề xuất** Ẩn nút cho tới khi implement, hoặc giữ (P3).

## 13.5. `subject.$id` chưa implement

**Mức độ** P2 — gộp với 7.7.

---

# 14. API Design

## 14.1. useDashboard — merge 4 API + đếm/sort/slice client

**Mức độ** P1

**File** `src/lib/queries.ts:163-214`

**Code**

```ts
queryFn: async () => {
  const [folders, documents, semesters] = await Promise.all([...]);
  const subjectPromises = semesters.map((sem) => subjectApi.listBySemester(sem.id).catch(...));
  const subjectArrays = await Promise.all(subjectPromises);
  const allSubjects = subjectArrays.flat();
  const docCountByFolder: Record<string, number> = {};
  documents.forEach((d) => { ... });
  const recentNotes = [...folders].map(...).sort(...).slice(0, 3);
  const recentDocuments = [...documents].sort(...).slice(0, 6);
  ...
```

**Hiện tại** Dashboard gom 3-4 API (folders + documents + semesters + N subjects), tự đếm doc/folder, sort recent, slice. Đã có comment "backward compat until phase 2 rewrite".

**Rủi ro** N+1, payload toàn bộ bảng; logic "recent" không nhất quán (sort theo updatedAt từ client).

**Đề xuất** Backend `GET /api/dashboard` trả: `recentFolders(3)`, `recentDocuments(6)`, `subjects`, `docCountByFolder` — toàn bộ tính trong 1 query.

## 14.2. Admin lists không phân trang

**Mức độ** P1

**File** `src/features/admin/services/documentApi.ts:6-13` (`GET /api/admin/documents` không có page/size), `userApi.ts:6-8`, `reportApi.ts:9-13, 55-58`

**Hiện tại** Admin docs/users/reports fetch toàn bộ (backend trả Page nhưng FE không truyền tham số, unwrap `content` và bỏ `totalElements`).

**Rủi ro** Hàng nghìn docs → payload lớn mỗi tab admin; `AdminPremiumPage` đã chứng minh hậu quả (1.2).

**Đề xuất** Thêm `?page=&size=&q=` cho 3 endpoint; FE dùng `totalElements` cho phân trang + số liệu.

## 14.3. N+1 subjects

**Mức độ** P1 — gộp với 7.2.

## 14.4. useDocument fallback shared endpoint — mờ quyền

**Mức độ** P1

**File** `src/lib/queries.ts:318-336, 299-316`

**Code**

```ts
queryFn: async () => {
  try { return await documentApi.getById(id); }
  catch (err: unknown) {
    const status = (err as { status?: number }).status;
    if (status === 401 || status === 403) {
      return documentApi.getSharedById(id);   // fallback sang endpoint shared
    }
    throw err;
  }
},
```

**Hiện tại** Khi `GET /api/documents/{id}` trả 401/403 (không phải owner), FE tự ý gọi `GET /api/documents/shared/{id}` — tự suy luận "có thể tôi là người được share".

**Rủi ro** (a) Quyết định quyền nằm client: nếu backend shared endpoint kém chặt chẽ → rò; (b) mỗi request lỗi trả 2 request; (c) che giấu lỗi 403 thật (khóa tài khoản bị nuốt thành fallback).

**Đề xuất** Bỏ fallback: 401/403 → để nguyên lỗi; backend `/api/documents/{id}` tự xử lý owner/shared trong 1 endpoint (đã có quyền trong JWT).

## 14.5. getPlanOptions — 2 API phục vụ 1 nguồn

**Mức độ** P3

**File** `src/features/admin/services/paymentApi.ts:116-122`

**Code**

```ts
getPlans: async (): Promise<AdminPlan[]> => {
  try { return await api<AdminPlan[]>("/api/plans"); }
  catch { return await api<AdminPlan[]>("/api/admin/plans"); }
},
```

**Hiện tại** User endpoint fail → rơi về admin endpoint (admin-only, sẽ 403 cho user thường; admin thì cả 2 đều chạy).

**Rủi ro** User thường khi `/api/plans` lỗi (500) sẽ gọi `/api/admin/plans` → 403 (thêm request lỗi); không phân biệt lỗi nghiệp vụ.

**Đề xuất** Chỉ dùng `/api/plans` cho user; bỏ fallback.

---

# 15. Performance

## 15.1. Polling chồng lớp

**Mức độ** P1

**File** `src/components/app-shell.tsx:86-91` (reloadUser 60s) · `src/lib/auth.tsx:136-167` (refresh 10 phút) · `src/features/payment/components/PremiumUpgradePage.tsx:205-220` (me() 5s khi QR) · `src/routes/_authenticated/payment.success.tsx:57` (status 3s) · `src/lib/queries.ts:553-558` (rag-status 3s khi PROCESSING)

**Hiện tại** 5 vòng poll độc lập; một trang premium vừa mở QR có thể có 3-4 interval chạy đồng thời (me 5s + reloadUser 60s + refresh 10p).

**Rủi ro** Request rác; `me()` 5s khi thanh toán QR để so plan name là sai endpoint (4.2).

**Đề xuất** Gộp: reloadUser 60s bỏ (quota/subscription đã tự refetch theo cache), payment dùng status endpoint, giữ rag-status (hợp lệ).

## 15.2. AppShell fetch toàn bộ 4-5 query trên mọi trang

**Mức độ** P1

**File** `src/components/app-shell.tsx:69-73, 102-103`

**Code**

```ts
const { data: documents } = useDocuments();
const { data: quota } = useQuota();
const folders = useFolders();
const semesters = useSemesters();
const subjects = useSubjects();   // N+1
...
const openDoc = useDocument(openDocId || "");  // fetch doc chỉ để hiện title header
```

**Hiện tại** Mọi trang authenticated đều mount documents + folders + semesters + subjects(N+1) + quota, dù trang không cần.

**Rủi ro** ~10-12 request mỗi trang load; mỗi tab nhân lên.

**Đề xuất** Chỉ fetch theo nhu cầu từng route (hoặc giữ cache nhưng bỏ subjects N+1 bằng 14.3); `openDoc` chỉ fetch khi cần (đã có `isFolderDetail` guard nhưng vẫn gọi `useDocument(openDocId || "")` với chuỗi rỗng bị `enabled: !!id` chặn — OK).

## 15.3. Quiz shuffle `sort(() => Math.random() - 0.5)` — thiên vị

**Mức độ** P3

**File** `src/components/ui/QuizViewer.tsx:45, 101`, `src/components/ui/FlashcardViewer.tsx:48`

**Hiện tại** Fisher-Yates thủ công bị thay bằng sort ngẫu nhiên (thiên vị, nhưng chỉ UI).

**Đề xuất** Không đổi (không phải nghiệp vụ) — ghi chú P3.

## 15.4. TransactionHistoryPage — fetch thủ công không qua React Query

**Mức độ** P3

**File** `src/features/payment/components/TransactionHistoryPage.tsx:56-64`

**Code**

```ts
useEffect(() => {
  let alive = true;
  paymentApi.getTransactions().then((d) => { if (alive) setTxs(d); });
  return () => { alive = false; };
}, []);
```

**Hiện tại** Không dùng React Query → mất cache/invalidate; khi chuyển tab quay lại là fetch lại.

**Đề xuất** Chuyển `useQuery(["my-transactions"])` như các trang khác.

---

# 16. Security

## 16.1. JWT access + refresh trong localStorage

**Mức độ** P0 — gộp với 2.1.

## 16.2. Token OAuth trong URL query

**Mức độ** P0 — gộp với 2.3 / 10.1.

## 16.3. Client tự quyết định quyền truy cập nội dung (BANNED/REJECT)

**Mức độ** P0 — gộp với 3.2.

## 16.4. Mock payment success

**Mức độ** P0 — gộp với 4.1.

## 16.5. Client tự tính tiền / quota / storage

**Mức độ** P1 — gộp với 1.1 / 1.4 / 6.1. Backend phải là nguồn quyết định khi thực thi.

## 16.6. Parse chuỗi lỗi để quyết định nghiệp vụ

**Mức độ** P1

**File** `src/routes/auth/login.tsx:58-64`, `src/routes/verify-email.tsx:53-57`

**Code**

```ts
if (msg.includes("xác thực") || msg.includes("verify") || msg.includes("Email chưa")) {
  setEmailVerifyError(true);
}
// verify-email
if (msg.includes("đã được sử dụng") || msg.includes("already been used")) ...
```

**Hiện tại** Dựa vào substring của message (tiếng Việt/tiếng Anh) để bật cờ UI.

**Rủi ro** Backend đổi message → flow vỡ; i18n thay đổi → không khớp.

**Đề xuất** Backend trả error code chuẩn (`EMAIL_NOT_VERIFIED`, `TOKEN_USED`, `TOKEN_EXPIRED`) trong body; FE switch theo code.

## 16.7. Client tin response chưa verify (mock success / fallback shared)

**Mức độ** P1 — gộp với 4.1 / 14.4.

## 16.8. `alert()` + redirect cho account locked — UX lỗi thời

**Mức độ** P3

**File** `src/lib/queries.ts:39-51`

**Code**

```ts
if (data?.accountLocked === true || data?.error === "ACCOUNT_LOCKED") {
  if (typeof window !== "undefined") {
    alert("Tài khoản của bạn đã bị khóa bởi quản trị viên.");
    window.location.href = "/login";
  }
```

**Hiện tại** Dùng `alert()` native; cũng **không clear token** trước khi redirect (api.ts đã clear khi 403 ACCOUNT_LOCKED — kiểm tra: api.ts:221-229 chỉ throw, không clear; queries.ts không clear → token chết còn nằm localStorage).

**Rủi ro** Sau khi khóa, token vẫn nằm localStorage; lần sau vào app lại gọi /me → lặp alert.

**Đề xuất** `tokenStore.clear()` trước redirect; dùng toast/dialog thay alert.

---

# 17. Date/Time Logic

## 17.1. remainingDaysUntil / remainingValue — client tính expiration

**Mức độ** P1 — gộp với 1.1. `src/features/payment/proration.ts:16-22`, `src/routes/_authenticated/profile.tsx:40` (`remainingDaysUntil(user?.planExpiresAt)`).

## 17.2. deletedAt countdown — client tính

**Mức độ** P1 — gộp với 1.3.

## 17.3. relativeTime hiển thị — OK

**Mức độ** P3 — `dashboard.tsx:36-45`, `formatTime.ts` — display thuần, giữ nguyên.

## 17.4. `new Date(expiredAt)` countdown payment — nguồn backend, OK

**Mức độ** P3 — gộp với 4.3.

---

# 18. Permission Logic

## 18.1. Không có hàm canEdit/canDelete/canShare ở client — nhưng quyết định quyền nằm ở UI rải rác

**Mức độ** P2

**File** (gộp) `src/components/document-actions-menu.tsx:88-95` (chặn share khi REJECT) · `src/components/document-workspace/ContentPanel.tsx:269-289` (nút Xóa/Download hiện cho mọi doc — **không kiểm tra ownership**) · `src/components/shared-document-actions-menu.tsx` (không có check gì) · `src/features/admin/components/AdminApprovalsPage.tsx` (reviewedIds, 3.4)

**Hiện tại** "Quyền" thể hiện bằng cách hiện/ẩn nút theo status cục bộ; không có lớp `can*` tập trung; `ContentPanel` hiện nút Xóa/Download cho doc bất kỳ trong folder (giả định toàn bộ folder là của user).

**Rủi ro** Nếu folder chứa doc share (fallback 14.4), người xem thấy nút Xóa → gọi `DELETE /api/documents/{id}` → nếu backend không kiểm tra ownership sẽ xóa nhầm doc người khác.

**Đề xuất** Backend kiểm tra ownership/membership trên **mọi** mutation (DELETE/PUT/download); FE giữ UI theo response (ẩn nút theo role/permission trả từ backend — ví dụ trả `permissions: ["view","edit"]` trong document response).

## 18.2. Role admin chỉ client — P0, gộp với 3.1.

---

# 19. State Mutation

## 19.1. Client tự sửa trạng thái nghiệp vụ (mock) — sessions/2FA

**Mức độ** P2

**File** `src/routes/_authenticated/admin.tsx:71, 86-100`

**Code**

```ts
const [sessions, setSessions] = useState(MOCK_SESSIONS);
const confirm2FA = () => { setTwoFA(true); setTwoFAOpen(false); toast.success("Đã bật xác thực 2 lớp"); };
```

**Hiện tại** "Bật 2FA", "Thu hồi phiên" chỉ đổi state local, không gọi backend — trạng thái bảo mật của tài khoản bị "sửa" ở client.

**Rủi ro** User tin rằng đã bảo mật hơn thực tế; trạng thái 2FA/session phải do backend quản lý.

**Đề xuất** Gộp với 13.2: backend API thật hoặc ẩn feature.

## 19.2. Token cleared khi locked — client không clear

**Mức độ** P3 — gộp với 16.8.

---

# 20. Dead Code

## 20.1. AuthProvider cũ — `src/features/auth/hooks/useAuth.tsx`

**Mức độ** P2

**File** `src/features/auth/hooks/useAuth.tsx` (toàn file, 230 dòng)

**Hiện tại** AuthProvider **thứ 2** tự triển khai lại login/refresh/logout, có interval refresh 10 phút riêng, dựng User giả. Grep toàn repo: **không file nào import** `features/auth/hooks/useAuth` (chỉ barrel `features/auth/index.ts` export, nhưng index không được ai import). `src/lib/auth.tsx` là provider đang dùng (import ở `__root.tsx`).

**Rủi ro** Nhầm lẫn khi sửa; nếu ai đó import nhầm barrel → 2 provider chồng.

**Đề xuất** Xóa file + barrel export tương ứng.

## 20.2. useLogin / useRegister / useLogout / useCurrentUser (queries.ts)

**Mức độ** P3

**File** `src/lib/queries.ts:57-93`

**Hiện tại** `useLogin/useRegister/useLogout/useCurrentUser` không có caller (login.tsx dùng `useAuth().login`; logout qua `useAuth().logout`).

**Đề xuất** Xóa.

## 20.3. Services/hooks chết: premiumApi + usePremium, getUserById, toggleStatus, useAdminTransactionsByUser/ByStatus, useReportedDocuments, useTransactions, usePlanOptions, getReportsByReporter, adminGetPlanById, shareApi.saveShared, useProcessFolderRag, isSharedViewable/isOwnerViewable

**Mức độ** P3

**File** (gộp) `src/features/admin/services/premiumApi.ts` + `hooks/usePremium.ts` (không route nào dùng — premium requests feature đã bị thay bằng transactions) · `userApi.ts:26-29` (getUserById), `:60-61` (toggleStatus) · `hooks/useAdminTransactions.ts:12-25` · `hooks/useAdminReportHistory.ts:26-33` (useReportedDocuments) · `hooks/usePayment.ts:6-17` (useTransactions/usePlanOptions) · `reportApi.ts:34-52` (getReportsByReporter) · `paymentApi.ts:106` (adminGetPlanById) · `services/shareApi.ts:26-30` (saveShared — chỉ `useSharedWorkspace` dùng `sharesApi.saveShared`? **kiểm tra**: `useSharedWorkspace.ts:79` gọi `sharesApi.saveShared` — **đang dùng**, bỏ khỏi danh sách) · `queries.ts:542-546` (useProcessFolderRag) · `lib/document-status.ts:12-15, 24-26` (isOwnerViewable/isSharedViewable)

**Đề xuất** Xóa từng mục đã xác nhận không caller.

## 20.4. Route /admin (Cài đặt) trùng nội dung /profile + mock

**Mức độ** P3 — gộp với 13.2 (`src/routes/_authenticated/admin.tsx`).

## 20.5. Type duplicate: auth.types.User vs lib/types.User; authApi file vs realApi

**Mức độ** P3

**File** `src/features/auth/types/auth.types.ts` (User/LoginRequest/LoginResponse — auth.types.User có `status: "ACTIVE"|"BANNED"` trong khi lib/types.User là `AccountStatus` khác) · `src/features/auth/services/authApi.ts` (26 dòng wrapper — `realApi.authApi` là bản đầy đủ; grep: chỉ realApi được dùng)

**Rủi ro** 2 định nghĩa User lệch nhau → type sai không ai biết.

**Đề xuất** Gộp về `lib/types.ts`; xóa authApi wrapper cũ.

## 20.6. `AdminTransactionsPage` state page không dùng

**Mức độ** P3

**File** `src/features/admin/components/AdminTransactionsPage.tsx:26`

**Code**

```ts
const [page] = useState(0);
```

**Đề xuất** Bỏ state thừa (cứng `useAdminTransactions(0, 50)`).

## 20.7. Query imports thừa (lint mờ)

**Mức độ** P3 — `src/components/document-workspace/DocumentWorkspace.tsx:106` (`ask.isPending` — **biến `ask` không tồn tại** → đây là bug, xem 21.1, không phải dead code).

---

# 21. Potential Bug

## 21.1. `ask.isPending` — ReferenceError: ask is not defined

**Mức độ** P1

**File** `src/components/document-workspace/DocumentWorkspace.tsx:106`

**Code**

```ts
<ChatPanel
  ...
  isPending={ask.isPending}   // 'ask' chưa từng được khai báo
  isDocSelected={Boolean(docId)}
/>
```

**Hiện tại** Biến `ask` không tồn tại trong file (chat = useRagChat() ở dòng 43). Mỗi khi `DocumentWorkspace` render → `ReferenceError` ném khi đánh giá prop → component vỡ (trang `/folders/{id}` dùng component này).

**Rủi ro** Trang folder detail bị lỗi runtime — **xác nhận: `chat.isPending` là đúng** (biến `chat` từ `useRagChat()`).

**Đề xuất** Đổi thành `chat.isPending`.

## 21.2. `useEffect` reset form login/register — stale initial

**Mức độ** P3

**File** `src/routes/_authenticated/profile.tsx:27-35, 68-71` — `initialForm` useMemo theo `user`; `form` state khởi tạo 1 lần — sau khi reloadUser user đổi, form cũ giữ giá trị cũ tới khi mở edit. Nhỏ.

**Đề xuất** Reset form khi user thay đổi (hoặc chấp nhận).

## 21.3. Preview dialog race — 2 luồng set preview

**Mức độ** P3

**File** `src/features/admin/components/AdminApprovalsPage.tsx:56-77` — `openPreview` set preview sau async fetch; nếu mở item A rồi nhanh chóng mở B, A's response có thể ghi đè B. Thêm guard `loadingPreview === item.id`.

**Đề xuất** Check `loadingPreview` trước khi set.

## 21.4. `getStatusBadge` cục bộ vs DocumentStatusBadge — hiển thị lệch

**Mức độ** P3 — gộp với 12.1.

## 21.5. Logout redirect gấp đôi (navigate + window.location)

**Mức độ** P3

**File** `src/components/app-shell.tsx:110-113` — `logout()` đã `window.location.href = "/auth/login"` (auth.tsx:256) → `navigate` thêm vô ích; không gây lỗi nhưng thừa.

**Đề xuất** Bỏ navigate trong app-shell.

## 21.6. Payment QR poll — `currentPlan` stale trong closure

**Mức độ** P2

**File** `src/features/payment/components/PremiumUpgradePage.tsx:205-220` — effect deps `[qrCodeModal, paymentInfo, currentPlan]`; sau khi reloadUser cập nhật `currentPlan`, interval cũ được cleanup và dựng lại — OK. Nhưng trong 1 vòng poll, `u.plan !== currentPlan` dùng giá trị tại thời điểm tạo interval — chấp nhận được. Ghi chú P2 (nếu payment thành công nhanh giữa 2 tick, có thể trễ 1 tick — vô hại).

## 21.7. `useRagStatus` refetchInterval 3s vô hạn nếu status lạ

**Mức độ** P3

**File** `src/lib/queries.ts:553-558` — chỉ poll khi `PROCESSING` — đúng.

---

# 22. Data Consistency

## 22.1. Merge 3 nguồn cho tên semester/subject/folder ở client

**Mức độ** P1

**File** `src/components/app-shell.tsx:74-76`, `src/routes/_authenticated/documents.tsx:49-52`, `src/routes/_authenticated/folders.tsx:76-87`, `src/routes/_authenticated/trash.tsx:41-61`

**Code**

```ts
const semesterMap = useMemo(() => new Map((semesters.data ?? []).map((s) => [s.id, s.name])), [semesters.data]);
const subjectMap = useMemo(() => new Map((subjects.data ?? []).map((s) => [s.id, s.name])), [subjects.data]);
```

**Hiện tại** 4 trang dựng Map ghép tên từ 3-4 API (trong đó subjects là N+1). Nếu 1 API fail → tên hiển thị trống/thiếu mà không ai biết; cache lệch giữa các query.

**Rủi ro** Ghép dữ liệu tại client = mỗi trang tự suy luận; sai lệch khi backend đổi cấu trúc (như comment "Document no longer has subjectId" trong queries.ts:180).

**Đề xuất** Backend trả denormalized tên (semesterName/subjectName/folderName) trong Document response — bỏ Map ghép. (Xem 14.1, 7.2.)

## 22.2. Cùng dữ liệu user qua 2 nguồn: `/me` (queries) vs AuthProvider user

**Mức độ** P2

**File** `src/lib/queries.ts:86-93` (`useCurrentUser` — không dùng) + `src/lib/auth.tsx` (user trong context)

**Hiện tại** 2 nguồn user tiềm năng; app dùng context. Nhất quán hiện tại, nhưng nếu ai đó dùng `useCurrentUser` lại là 2 cache lệch nhau.

**Đề xuất** Xóa `useCurrentUser` (20.2) — giữ 1 nguồn.

## 22.3. `useDocumentsByFolder` fallback shared — 2 nguồn dữ liệu folder

**Mức độ** P1 — gộp với 14.4 (cùng pattern owner→shared fallback).

## 22.4. Subscription state — 2 nguồn: `user.plan` (me) vs `getMySubscription()`

**Mức độ** P2

**File** `src/features/payment/components/PremiumUpgradePage.tsx:65-74`

**Code**

```ts
if (subQuery.data) {
  setCurrentPlan(subQuery.data.planName.toUpperCase());
  setExpiresAt(subQuery.data.endDate);
} else if (user?.plan) {
  setCurrentPlan(String(user.plan).toUpperCase());
  setExpiresAt(user.planExpiresAt);
}
```

**Hiện tại** Fallback giữa 2 API khi 1 cái chưa về; có thể hiển thị gói cũ nếu subscription vừa đổi mà /me chưa invalidate.

**Rủi ro** Hiển thị lệch tạm thời giữa 2 nguồn; không sai dữ liệu cuối (backend nguồn thật).

**Đề xuất** Chỉ dùng `getMySubscription()` cho trang premium; bỏ fallback user.plan.

---

# Tổng kết

- **Tổng số Business Logic**: 12 mục (1.1-1.6, 4.1-4.3, 5.1-5.3, 6.1, 8.4)
- **Tổng số Security Risk**: 10 (2.1, 2.3, 3.1, 3.2, 4.1, 10.1, 16.6, 16.8, 5.1, 9.1)
- **Tổng số Permission Logic**: 3 (3.1, 3.2, 18.1)
- **Tổng số Auth Logic**: 6 (2.1, 2.2, 2.3, 2.4, 2.5, 2.6)
- **Tổng số Payment Logic**: 4 (4.1, 4.2, 4.3, 8.4)
- **Tổng số TODO**: 6 (13.1-13.5 + 13.6 mục 12.4 SEMESTER_COUNT) — thực tế còn các comment "backward compat" (queries.ts:142, 160) nữa
- **Tổng số Dead Code**: 7 nhóm (20.1-20.7)
- **Tổng số Potential Bug**: 7 (21.1-21.7)
- **Tổng số Performance Issue**: 5 (15.1-15.4 + 14.2)
- **Tổng số API nên chuyển sang Backend**: 10 (7.1 shares, 7.2 subjects, 7.3 docs search, 7.4 header search, 7.6 admin search, 7.7 subjects.$id, 14.1 dashboard, 14.2 admin pagination, 8.2 profile counts, 5.1 file proxy)

---

# Ưu tiên xử lý

## P0
1. **Token → httpOnly cookie** (2.1/16.1) — xóa token khỏi localStorage; xóa log token (api.ts:123).
2. **OAuth code exchange** (2.3/10.1) — không trả token qua URL; xóa `user_id` localStorage.
3. **Xóa mock payment success** (4.1/16.4) — trạng thái premium chỉ đổi sau webhook/xác nhận backend.
4. **Backend lọc document theo trạng thái khi serve** (3.2/16.3) — BANNED/REJECT/COMPLETED không trả `cloudinaryUrl` cho người không có quyền; chặn role admin ở mọi `/api/admin/**` (3.1).
5. **Doanh thu admin tính từ backend** (1.2/8.4) — `GET /api/admin/transactions/summary`.

## P1
6. **`ask.isPending` → `chat.isPending`** (21.1) — trang folder detail đang vỡ runtime.
7. **Quota trả `storageUsed`** (1.4) — bỏ reduce fileSize ở 3 chỗ; kèm check quota upload (9.1).
8. **Proration/expiration do backend quyết định** (1.1/17.1) — quote trong `createPayment`.
9. **Bỏ fallback owner→shared** (14.4) + backend ownership check trên mutation (18.1).
10. **`GET /api/subjects` bỏ N+1** (7.2/14.3); dashboard backend hóa (14.1).
11. **Admin lists phân trang + `q`** (14.2); shares search/sort/page server-side (7.1).
12. **Payment poll dùng `getTransactionStatus`** (4.2); gộp polling (15.1).
13. **AdminProfilePage nối API thật** (13.1) — không toast giả; error code chuẩn cho login/verify (16.6).
14. **Denormalized tên trong document response** (22.1) — bỏ Map ghép 3 API.

## P2
15. **Mock sessions/2FA → API thật hoặc ẩn** (13.2/19.1).
16. **Xóa AuthProvider cũ** (20.1) + duplicate types (20.5).
17. **Hardcode tên gói → dùng `tier`** (12.2); bỏ quy ước 9999.
18. **Pin/star lên backend hoặc chấp nhận local** (11.2).
19. **Xóa quiz hardcode trong QuizzesTab** (1.5); subjects.$id có API thật (7.7).
20. **Backend validate plan form** (9.2); `QUOTA_EXCEEDED` chuẩn (6.1).

## P3
21. Dọn dead code (20.2-20.7), hardcode "11.4mb" (5.3), `SEMESTER_COUNT` (12.4), `getStatusBadge` trùng (12.1), `console.log` debug (8.3, api.ts), `alert()` → dialog (16.8), reset form (21.2), preview race (21.3), TransactionHistoryPage → useQuery (15.4).
