package com.tugnw.aistudy.service;

import jakarta.mail.MessagingException;

import java.util.Map;

public interface EmailService {

    /**
     * Send a plain-text email.
     *
     * @param to      recipient address
     * @param subject email subject
     * @param text    plain-text body
     */
    void sendEmail(String to, String subject, String text);

    /**
     * Send an HTML email rendered from a Thymeleaf template.
     *
     * @param to       recipient address
     * @param subject  email subject
     * @param template name of the Thymeleaf template (without .html suffix)
     * @param context  variables to inject into the template
     */
    void sendHtmlEmail(String to, String subject, String template, Map<String, Object> context);

    /**
     * Send an HTML email with pre-built MimeMessage content.
     * Callers are responsible for <pre>MimeMessageHelper</pre> setup.
     */
    void sendMimeMessage(String to, String subject, String htmlContent);
}
