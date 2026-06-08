package com.resourcesharing.forum.service.identity;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class EmailCodeService {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TxSupport txSupport;
    private final ValueSupport values;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailUsername;
    private final String mailPassword;
    private final String mailFrom;
    private final int expiresMinutes;

    public EmailCodeService(
            TxSupport txSupport,
            ValueSupport values,
            PasswordEncoder passwordEncoder,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${forum.mail.from:}") String mailFrom,
            @Value("${forum.mail.code-expires-minutes:10}") int expiresMinutes
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.passwordEncoder = passwordEncoder;
        this.mailSenderProvider = mailSenderProvider;
        this.mailUsername = mailUsername == null ? "" : mailUsername.trim();
        this.mailPassword = mailPassword == null ? "" : mailPassword.trim();
        this.mailFrom = mailFrom == null ? "" : mailFrom.trim();
        this.expiresMinutes = Math.max(1, Math.min(60, expiresMinutes));
    }

    public Map<String, Object> requestRegisterCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc != null) {
            ensureEmailUnused(jdbc, normalizedEmail);
        }
        String code = generateCode();
        saveCode(null, normalizedEmail, "REGISTER", code);
        sendCode(normalizedEmail, "资源分享论坛注册验证码", code);
        return values.map("ok", true, "email", normalizedEmail, "expiresInMinutes", expiresMinutes);
    }

    public Map<String, Object> requestResetPasswordCode(Long accountId, String email) {
        String normalizedEmail = normalizeEmail(email);
        String code = generateCode();
        saveCode(accountId, normalizedEmail, "RESET_PASSWORD", code);
        sendCode(normalizedEmail, "资源分享论坛重置密码验证码", code);
        return values.map("ok", true, "email", normalizedEmail, "expiresInMinutes", expiresMinutes);
    }

    public void verifyRegisterCode(String email, String code) {
        verifyCode(null, normalizeEmail(email), "REGISTER", code);
    }

    public void verifyResetPasswordCode(Long accountId, String email, String code) {
        verifyCode(accountId, normalizeEmail(email), "RESET_PASSWORD", code);
    }

    private void saveCode(Long accountId, String email, String scene, String code) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        txSupport.required(() -> {
            jdbc.update("""
                    UPDATE email_verification_code
                    SET status = 'EXPIRED'
                    WHERE email = ? AND scene = ? AND status = 'UNUSED'
                    """, email, scene);
            jdbc.update("""
                    INSERT INTO email_verification_code(account_id, email, scene, code_hash, status, expire_time)
                    VALUES (?, ?, ?, ?, 'UNUSED', DATE_ADD(NOW(3), INTERVAL ? MINUTE))
                    """, accountId, email, scene, passwordEncoder.encode(code), expiresMinutes);
            return null;
        });
    }

    private void verifyCode(Long accountId, String email, String scene, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "verification code must be 6 digits");
        }
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        List<Map<String, Object>> codes = jdbc.query("""
                SELECT id, code_hash
                FROM email_verification_code
                WHERE email = ? AND scene = ? AND status = 'UNUSED' AND expire_time > NOW(3)
                  AND (? IS NULL OR account_id = ?)
                ORDER BY id DESC
                LIMIT 1
                """, (rs, rowNum) -> values.map("id", rs.getLong("id"), "hash", rs.getString("code_hash")), email, scene, accountId, accountId);
        if (codes.isEmpty() || !passwordEncoder.matches(code, String.valueOf(codes.get(0).get("hash")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "verification code is invalid or expired");
        }
        jdbc.update("UPDATE email_verification_code SET status = 'USED', used_time = NOW(3) WHERE id = ?", codes.get(0).get("id"));
    }

    private void ensureEmailUnused(JdbcTemplate jdbc, String email) {
        try {
            Integer exists = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM user_account
                    WHERE email = ? AND deleted_at IS NULL
                    """, Integer.class, email);
            if (exists != null && exists > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "email is already used");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException ignored) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to check email availability");
        }
    }

    private void sendCode(String email, String subject, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        String from = values.firstNonBlank(mailFrom, mailUsername);
        if (mailSender == null || mailUsername.isBlank() || mailPassword.isBlank() || from.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "mail service is not configured. Please set MAIL_USERNAME, MAIL_PASSWORD and MAIL_FROM");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText("""
                您好：

                您正在使用资源分享论坛邮箱验证码，本次验证码为：%s

                验证码 %d 分钟内有效，请勿泄露给他人。
                如果不是您本人操作，请忽略本邮件。
                """.formatted(code, expiresMinutes));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to send verification email");
        }
    }

    private static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank() || !EMAIL.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "email format is invalid");
        }
        return normalized;
    }

    private static String generateCode() {
        return String.valueOf(RANDOM.nextInt(900000) + 100000);
    }
}
