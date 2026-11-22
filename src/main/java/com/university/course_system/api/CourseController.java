package com.university.course_system.api;

import com.university.course_system.db.GeneratedIds;
import com.university.course_system.model.Course;
import com.university.course_system.model.Professor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

@RestController
public class CourseController {

    private static final String SELECT_COURSE = """
            SELECT c.id, c.code, c.name, c.credits, c.semester, c.status,
                   p.id AS professor_id, p.name AS professor_name
            FROM courses c
            LEFT JOIN users p ON p.id = c.professor_id
            """;

    private static final RowMapper<Course> COURSE_MAPPER = (rs, rowNum) -> {
        Course course = new Course();
        course.id = rs.getInt("id");
        course.code = rs.getString("code");
        course.name = rs.getString("name");
        course.credits = rs.getInt("credits");
        course.semester = rs.getString("semester");
        course.status = rs.getString("status");
        int professorId = rs.getInt("professor_id");
        if (!rs.wasNull()) {
            course.professor = new Professor(professorId, rs.getString("professor_name"));
        }
        return course;
    };

    private final JdbcTemplate jdbcTemplate;

    public CourseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/courses")
    public List<Course> list(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String status) {
        StringBuilder sql = new StringBuilder(SELECT_COURSE);
        sql.append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();

        if (semester != null && !semester.isBlank()) {
            sql.append(" AND c.semester = ?");
            args.add(semester.trim());
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            if (!normalized.equals("OPEN") && !normalized.equals("CLOSED")) {
                throw new BadRequestException("Status must be OPEN or CLOSED");
            }
            sql.append(" AND c.status = ?");
            args.add(normalized);
        }
        sql.append(" ORDER BY c.id");
        return jdbcTemplate.query(sql.toString(), COURSE_MAPPER, args.toArray());
    }

    @GetMapping("/courses/{id}")
    public Course get(@PathVariable int id) {
        return courseById(id);
    }

    @PostMapping("/courses")
    public ResponseEntity<Course> create(@RequestBody CreateCourseRequest request) {
        String code = required(request.code, "Code is required");
        String name = required(request.name, "Name is required");
        String semester = required(request.semester, "Semester is required");
        if (request.credits == null || request.credits < 1) {
            throw new BadRequestException("Credits must be at least 1");
        }
        if (request.professorId != null) {
            requireProfessor(request.professorId);
        }

        try {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO courses (code, name, credits, semester, status, professor_id) VALUES (?, ?, ?, ?, 'OPEN', ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, code);
                statement.setString(2, name);
                statement.setInt(3, request.credits);
                statement.setString(4, semester);
                if (request.professorId == null) {
                    statement.setNull(5, Types.INTEGER);
                } else {
                    statement.setInt(5, request.professorId);
                }
                return statement;
            }, keys);
            return ResponseEntity.status(HttpStatus.CREATED).body(courseById(GeneratedIds.from(keys)));
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("A course with code " + code + " already exists");
        }
    }

    @PutMapping("/courses/{id}")
    public Course update(@PathVariable int id, @RequestBody UpdateCourseRequest request) {
        Course current = courseById(id);
        String code = request.code == null ? current.code : required(request.code, "Code is required");
        String name = request.name == null ? current.name : required(request.name, "Name is required");
        int credits = request.credits == null ? current.credits : request.credits;
        if (credits < 1) {
            throw new BadRequestException("Credits must be at least 1");
        }
        String semester = request.semester == null ? current.semester : required(request.semester, "Semester is required");
        String status = request.status == null ? current.status : request.status.trim().toUpperCase();
        if (!status.equals("OPEN") && !status.equals("CLOSED")) {
            throw new BadRequestException("Status must be OPEN or CLOSED");
        }
        Integer professorId = request.professorId == null
                ? (current.professor == null ? null : current.professor.id)
                : request.professorId;
        if (professorId != null) {
            requireProfessor(professorId);
        }

        try {
            jdbcTemplate.update(
                    "UPDATE courses SET code = ?, name = ?, credits = ?, semester = ?, status = ?, professor_id = ? WHERE id = ?",
                    code, name, credits, semester, status, professorId, id);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("A course with code " + code + " already exists");
        }
        return courseById(id);
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        courseById(id);
        Integer enrollments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE course_id = ?", Integer.class, id);
        if (enrollments != null && enrollments > 0) {
            throw new ConflictException("Course " + id + " cannot be removed because students are enrolled in it");
        }
        Integer materials = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_materials WHERE course_id = ?", Integer.class, id);
        if (materials != null && materials > 0) {
            throw new ConflictException("Course " + id + " cannot be removed because it still has files");
        }
        jdbcTemplate.update("DELETE FROM courses WHERE id = ?", id);
        return ResponseEntity.noContent().build();
    }

    private Course courseById(int id) {
        List<Course> rows = jdbcTemplate.query(SELECT_COURSE + " WHERE c.id = ?", COURSE_MAPPER, id);
        if (rows.isEmpty()) {
            throw new NotFoundException("Course " + id + " was not found");
        }
        return rows.getFirst();
    }

    private void requireProfessor(int professorId) {
        List<String> roles = jdbcTemplate.query(
                "SELECT role FROM users WHERE id = ?",
                (rs, rowNum) -> rs.getString("role"),
                professorId);
        if (roles.isEmpty()) {
            throw new NotFoundException("Professor " + professorId + " was not found");
        }
        if (!roles.getFirst().equals("PROFESSOR")) {
            throw new BadRequestException("User " + professorId + " is not a professor");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    public static class CreateCourseRequest {
        public String code;
        public String name;
        public Integer credits;
        public String semester;
        public Integer professorId;
    }

    public static class UpdateCourseRequest {
        public String code;
        public String name;
        public Integer credits;
        public String semester;
        public Integer professorId;
        public String status;
    }

}
