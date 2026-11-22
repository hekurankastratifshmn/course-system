package com.university.course_system.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/auth/login")
    public LoginResult login(@RequestBody LoginRequest request) {
        if (request.email == null || request.email.isBlank() || request.password == null) {
            throw new BadRequestException("Email and password are required");
        }

        List<LoginRow> rows = jdbcTemplate.query(
                "SELECT id, name, email, role, password_hash FROM users WHERE email = ?",
                (rs, rowNum) -> new LoginRow(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getString("password_hash")),
                request.email.trim());

        if (rows.isEmpty() || !passwordMatches(request.password, rows.getFirst().passwordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        LoginRow user = rows.getFirst();
        String token = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO tokens (token, user_id, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR))",
                token,
                user.id());

        LoginResult result = new LoginResult();
        result.tokenType = "Bearer";
        result.token = token;
        result.userId = user.id();
        result.name = user.name();
        result.role = user.role();
        return result;
    }

    private boolean passwordMatches(String password, String passwordHash) {
        try {
            return passwordEncoder.matches(password, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record LoginRow(int id, String name, String email, String role, String passwordHash) {
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class LoginResult {
        public String tokenType;
        public String token;
        public int userId;
        public String name;
        public String role;
    }

}
