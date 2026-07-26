-- Stage 2 incremental script
-- For databases ALREADY initialized in stage 1 (docker init only runs on first boot).
-- Adds the admin seed account: admin / admin123
-- Run:
--   docker exec -i ainsight-mysql mysql -uroot -painsight123 ainsight < docs/stage2-admin-seed.sql

INSERT INTO sys_user (username, password, nickname, role, status) VALUES
('admin', '$2b$10$CWYHqIN0axesk.tH8VaiqOsEuTLj8gUS.lW4kCSdZQLAX/AFwc0i6', '管理员', 'ADMIN', 1);
