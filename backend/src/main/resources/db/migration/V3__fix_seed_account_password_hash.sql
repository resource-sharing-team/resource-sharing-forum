-- Fix development seed accounts to use a BCrypt hash accepted by Spring Security.
-- Raw password for both demo_user and admin: password

UPDATE user_account
SET password_hash = '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se',
    password_changed_time = COALESCE(password_changed_time, NOW(3))
WHERE username IN ('demo_user', 'admin');
