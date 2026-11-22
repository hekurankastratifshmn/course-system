package com.university.course_system.api;

import com.university.course_system.db.GeneratedIds;
import com.university.course_system.model.Course;
import com.university.course_system.model.Enrollment;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@RestController
public class EnrollmentController {

    private static final String SELECT_ENROLLMENT = """
            SELECT e.id, e.student_id, u.name AS student_name, e.course_id, e.status, e.enrolled_at
            FROM enrollments e
            JOIN users u ON u.id = e.student_id
            """;

    private static final RowMapper<Enrollment> ENROLLMENT_MAPPER = (rs, rowNum) -> {
        Enrollment enrollment = new Enrollment();
        enrollment.enrollmentId = rs.getInt("id");
        enrollment.studentId = rs.getInt("student_id");
        enrollment.studentName = rs.getString("student_name");
        enrollment.courseId = rs.getInt("course_id");
        enrollment.status = rs.getString("status");
        enrollment.enrolledAt = rs.getTimestamp("enrolled_at").toInstant();
        return enrollment;
    };

    private static final String SELECT_COURSE = """
            SELECT c.id, c.code, c.name, c.credits, c.semester, c.status,
                   p.id AS professor_id, p.name AS professor_name
            FROM courses c
            LEFT JOIN users p ON p.id = c.professor_id
            JOIN enrollments e ON e.course_id = c.id
            WHERE e.student_id = ? AND e.status = 'ENROLLED'
            ORDER BY c.id
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

    public EnrollmentController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/courses/{id}/enrollments")
    public ResponseEntity<Enrollment> enroll(@PathVariable int id, @RequestBody EnrollRequest request) {
        if (request.studentId == null) {
            throw new BadRequestException("studentId is required");
        }
        String status = courseStatus(id);
        if (!"OPEN".equals(status)) {
            throw new ConflictException("Course " + id + " is not open for enrollment");
        }
        requireStudent(request.studentId);

        try {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO enrollments (student_id, course_id, status) VALUES (?, ?, 'ENROLLED')",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setInt(1, request.studentId);
                statement.setInt(2, id);
                return statement;
            }, keys);
            return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentById(GeneratedIds.from(keys)));
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Student " + request.studentId + " already has an enrollment for course " + id);
        }
    }

    @GetMapping("/courses/{id}/enrollments")
    public List<Enrollment> roster(@PathVariable int id) {
        courseStatus(id);
        return jdbcTemplate.query(
                SELECT_ENROLLMENT + " WHERE e.course_id = ? AND e.status = 'ENROLLED' ORDER BY e.id",
                ENROLLMENT_MAPPER,
                id);
    }

    @GetMapping("/students/{id}/courses")
    public List<Course> myCourses(@PathVariable int id) {
        requireStudent(id);
        return jdbcTemplate.query(SELECT_COURSE, COURSE_MAPPER, id);
    }

    @DeleteMapping("/enrollments/{id}")
    public Enrollment cancel(@PathVariable int id) {
        Enrollment enrollment = enrollmentById(id);
        if ("CANCELLED".equals(enrollment.status)) {
            throw new ConflictException("Enrollment " + id + " is already cancelled");
        }
        jdbcTemplate.update("UPDATE enrollments SET status = 'CANCELLED' WHERE id = ?", id);
        return enrollmentById(id);
    }

    private Enrollment enrollmentById(int id) {
        List<Enrollment> rows = jdbcTemplate.query(SELECT_ENROLLMENT + " WHERE e.id = ?", ENROLLMENT_MAPPER, id);
        if (rows.isEmpty()) {
            throw new NotFoundException("Enrollment " + id + " was not found");
        }
        return rows.getFirst();
    }

    private String courseStatus(int courseId) {
        List<String> statuses = jdbcTemplate.query(
                "SELECT status FROM courses WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"),
                courseId);
        if (statuses.isEmpty()) {
            throw new NotFoundException("Course " + courseId + " was not found");
        }
        return statuses.getFirst();
    }

    private void requireStudent(int studentId) {
        List<String> roles = jdbcTemplate.query(
                "SELECT role FROM users WHERE id = ?",
                (rs, rowNum) -> rs.getString("role"),
                studentId);
        if (roles.isEmpty()) {
            throw new NotFoundException("Student " + studentId + " was not found");
        }
        if (!roles.getFirst().equals("STUDENT")) {
            throw new BadRequestException("User " + studentId + " is not a student");
        }
    }

    public static class EnrollRequest {
        public Integer studentId;
    }

}
