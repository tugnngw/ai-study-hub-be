package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.config.EmailProperties;
import com.tugnw.aistudy.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailProperties emailProperties;

    @Override
    @Async("mailExecutor")
    public void sendEmail(String to, String subject, String text) {
        if (!emailProperties.isEnabled()) {
            log.info("[MAIL DISABLED] Would send plain to={} subject={}", to, subject);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailProperties.getFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        sendWithRetry(() -> mailSender.send(message), to, subject);
    }

    @Override
    @Async("mailExecutor")
    public void sendHtmlEmail(String to, String subject, String template, Map<String, Object> context) {
        if (!emailProperties.isEnabled()) {
            log.info("[MAIL DISABLED] Would send HTML to={} subject={} template={}", to, subject, template);
            return;
        }

        String html;
        try {
            Context thymeleafContext = new Context();
            if (context != null) {
                thymeleafContext.setVariables(context);
            }
            html = templateEngine.process(template, thymeleafContext);
        } catch (Exception e) {
            log.error("[MAIL] Template rendering failed template={} to={} subject={}", template, to, subject, e);
            return;
        }
        sendMimeMessage(to, subject, html);
    }

    @Override
    @Async("mailExecutor")
    public void sendMimeMessage(String to, String subject, String htmlContent) {
        if (!emailProperties.isEnabled()) {
            log.info("[MAIL DISABLED] Would send MIME to={} subject={}", to, subject);
            return;
        }

        sendWithRetry(() -> {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(emailProperties.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(mime);
        }, to, subject);
    }

    /**
     * Execute a mail-send action with configured retries.
     * Handles both {@link MailException} and {@link MessagingException}.
     */
    private void sendWithRetry(ThrowingRunnable sendAction, String to, String subject) {
        int maxAttempts = emailProperties.getRetryMaxAttempts();
        long delayMs = emailProperties.getRetryDelayMs();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                sendAction.run();
                log.info("[MAIL] Sent to={} subject={} attempt={}/{}", to, subject, attempt, maxAttempts);
                return;
            } catch (MailException | MessagingException e) {
                log.warn("[MAIL] Failed attempt={}/{} to={} subject={} error={}",
                        attempt, maxAttempts, to, subject, e.getMessage());
                if (attempt == maxAttempts) {
                    log.error("[MAIL] EXHAUSTED all {} retries to={} subject={}", maxAttempts, to, subject, e);
                    return;
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[MAIL] Interrupted during retry delay to={}", to);
                    return;
                }
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws MailException, MessagingException;
    }
}
