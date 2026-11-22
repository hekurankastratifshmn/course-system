package com.university.course_system.api;

import com.university.course_system.db.GeneratedIds;
import com.university.course_system.model.UserAccount;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@RestController
public class UserController {

    private static final RowMapper<UserAccount> USER_MAPPER = (rs, rowNum) -> {
        UserAccount user = new UserAccount();
        user.id = rs.getInt("id");
        user.name = rs.getString("name");
        user.email = rs.getString("email");
        user.role = rs.getString("role");
        return user;
    };

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/users")
    public ResponseEntity<UserAccount> create(@RequestBody CreateUserRequest request) {
        String name = required(request.name, "Name is required");
        String email = required(request.email, "Email is required");
        String password = required(request.password, "Password is required");
        if (!email.contains("@")) {
            throw new BadRequestException("Email is not valid");
        }
        String role = request.role == null ? "" : request.role.trim().toUpperCase();
        if (!role.equals("STUDENT") && !role.equals("PROFESSOR") && !role.equals("ADMIN")) {
            throw new BadRequestException("Role must be STUDENT, PROFESSOR, or ADMIN");
        }

        try {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users (name, email, role, password_hash) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, name);
                statement.setString(2, email);
                statement.setString(3, role);
                statement.setString(4, passwordEncoder.encode(password));
                return statement;
            }, keys);
            return ResponseEntity.status(HttpStatus.CREATED).body(userById(GeneratedIds.from(keys)));
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("A user with email " + email + " already exists");
        }
    }

    @GetMapping("/users/{id}")
    public UserAccount get(@PathVariable int id) {
        return userById(id);
    }

    private UserAccount userById(int id) {
        List<UserAccount> rows = jdbcTemplate.query(
                "SELECT id, name, email, role FROM users WHERE id = ?",
                USER_MAPPER,
                id);
        if (rows.isEmpty()) {
            throw new NotFoundException("User " + id + " was not found");
        }
        return rows.getFirst();
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    public static class CreateUserRequest {
        public String name;
        public String email;
        public String role;
        public String password;
    }

}
