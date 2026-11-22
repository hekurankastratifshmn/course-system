-- Loaded once, when the Docker volume is first created.
-- Login with the email and the password. The password_hash column is the hash, not the password.
--
--   admin@uni-pr.edu / admin
--
-- Professors, students, and courses are created from Postman after login.

USE course_system;

INSERT INTO users (id, name, email, role, password_hash) VALUES
    (1, 'System Administrator', 'admin@uni-pr.edu', 'ADMIN', '$2a$10$jD4PN5yb8Xh.0MhOGWl.IuN7SGmwwWe1zUjJieFpgEy1Cp2YBlkIC');
