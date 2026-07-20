package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    /** Null when MAIL_ENABLED=false — Spring Boot skips the bean. */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@aistudyhub.com}")
    private String fromEmail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Override
    @Async
    public void sendVerificationEmail(String to, String username, String token) {
        if (!mailEnabled || mailSender == null) {
            log.info("[MAIL DISABLED] Would send verification email to={}", to);
            return;
        }

        String verifyUrl = frontendUrl + "/verify-email?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "AI Study Hub");
            helper.setTo(to);
            helper.setSubject("Xác thực email của bạn - AI Study Hub");

            String html = buildVerificationHtml(username, verifyUrl);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("Verification email queued for delivery to: {}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send verification email to: {}", to, e);
        }
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String to, String username, String otp) {
        if (!mailEnabled || mailSender == null) {
            log.info("[MAIL DISABLED] Would send password-reset email to={}", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "AI Study Hub");
            helper.setTo(to);
            helper.setSubject("Đặt lại mật khẩu - AI Study Hub");

            String html = buildPasswordResetHtml(username, otp);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("Password-reset email queued for delivery to: {}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send password-reset email to: {}", to, e);
        }
    }

    private String buildVerificationHtml(String name, String verifyUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
                <title>Xác thực email</title></head>
                <body style="margin:0;padding:0;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                <table width="100%%" cellspacing="0" cellpadding="0" style="padding:40px 16px;">
                <tr><td align="center">
                <table width="100%%" style="max-width:480px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);" cellspacing="0" cellpadding="0">
                <tr><td style="background:linear-gradient(135deg,#6366f1,#4f46e5);padding:32px;text-align:center;">
                <h1 style="margin:0;color:#fff;font-size:24px;font-weight:700;">AI Study Hub</h1>
                <p style="margin:8px 0 0;color:#c7d2fe;font-size:14px;">Xác thực địa chỉ email</p>
                </td></tr>
                <tr><td style="padding:32px;">
                <h2 style="margin:0 0 8px;font-size:20px;color:#1e293b;">Chào %s,</h2>
                <p style="margin:0 0 24px;font-size:15px;color:#64748b;line-height:1.6;">
                Cảm ơn bạn đã đăng ký tài khoản AI Study Hub. Vui lòng nhấn nút bên dưới để xác thực email của bạn.
                </p>
                <table width="100%%" cellspacing="0" cellpadding="0" style="margin:32px 0;">
                <tr><td align="center">
                <a href="%s" style="display:inline-block;padding:14px 40px;background:linear-gradient(135deg,#6366f1,#4f46e5);color:#fff;text-decoration:none;border-radius:8px;font-size:16px;font-weight:600;">Xác thực email</a>
                </td></tr>
                </table>
                <div style="background:#fef3c7;border:1px solid #fde68a;border-radius:8px;padding:12px 16px;">
                <p style="margin:0;font-size:13px;color:#92400e;">Liên kết này sẽ hết hạn sau 24 giờ.</p>
                </div>
                <p style="margin-top:24px;font-size:13px;color:#94a3b8;">Nếu bạn không tạo tài khoản này, vui lòng bỏ qua email này.</p>
                </td></tr>
                <tr><td style="padding:24px;background:#f8fafc;text-align:center;border-top:1px solid #e2e8f0;">
                <p style="margin:0;font-size:12px;color:#94a3b8;">&copy; 2026 AI Study Hub. All rights reserved.</p>
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(name, verifyUrl);
    }

    private String buildPasswordResetHtml(String name, String otp) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
                <title>Đặt lại mật khẩu</title></head>
                <body style="margin:0;padding:0;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                <table width="100%%" cellspacing="0" cellpadding="0" style="padding:40px 16px;">
                <tr><td align="center">
                <table width="100%%" style="max-width:480px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);" cellspacing="0" cellpadding="0">
                <tr><td style="background:linear-gradient(135deg,#6366f1,#4f46e5);padding:32px;text-align:center;">
                <h1 style="margin:0;color:#fff;font-size:24px;font-weight:700;">AI Study Hub</h1>
                <p style="margin:8px 0 0;color:#c7d2fe;font-size:14px;">Đặt lại mật khẩu</p>
                </td></tr>
                <tr><td style="padding:32px;">
                <h2 style="margin:0 0 8px;font-size:20px;color:#1e293b;">Chào %s,</h2>
                <p style="margin:0 0 24px;font-size:15px;color:#64748b;line-height:1.6;">
                Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản AI Study Hub của bạn.
                Mã xác nhận của bạn là:
                </p>
                <div style="background:#eef2ff;border:2px dashed #6366f1;border-radius:12px;padding:24px;text-align:center;margin:24px 0;">
                <span style="font-size:36px;font-weight:700;color:#4f46e5;letter-spacing:8px;">%s</span>
                </div>
                <p style="font-size:14px;color:#64748b;">Mã này có hiệu lực trong 10 phút.</p>
                <p style="margin-top:24px;font-size:13px;color:#94a3b8;">Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>
                </td></tr>
                <tr><td style="padding:24px;background:#f8fafc;text-align:center;border-top:1px solid #e2e8f0;">
                <p style="margin:0;font-size:12px;color:#94a3b8;">&copy; 2026 AI Study Hub. All rights reserved.</p>
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(name, otp);
    }
}
