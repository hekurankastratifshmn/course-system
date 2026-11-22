package com.university.course_system.api;

import com.university.course_system.db.GeneratedIds;
import com.university.course_system.model.CourseMaterial;
import com.university.course_system.storage.FileStorage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@RestController
public class MaterialController {

    private static final RowMapper<CourseMaterial> MATERIAL_MAPPER = (rs, rowNum) -> {
        CourseMaterial material = new CourseMaterial();
        material.id = rs.getInt("id");
        material.courseId = rs.getInt("course_id");
        material.title = rs.getString("title");
        material.fileKey = rs.getString("file_key");
        material.contentType = rs.getString("content_type");
        material.uploadedAt = rs.getTimestamp("uploaded_at").toInstant();
        return material;
    };

    private final JdbcTemplate jdbcTemplate;
    private final FileStorage storage;

    public MaterialController(JdbcTemplate jdbcTemplate, FileStorage storage) {
        this.jdbcTemplate = jdbcTemplate;
        this.storage = storage;
    }

    @PostMapping("/courses/{id}/materials")
    public ResponseEntity<CourseMaterial> upload(
            @PathVariable int id,
            @RequestParam String title,
            @RequestParam("file") MultipartFile file) {
        requireCourse(id);
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Title is required");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A file is required");
        }

        String fileKey = storage.store(id, file);
        String contentType = file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType();
        try {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO course_materials (course_id, title, file_key, content_type) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setInt(1, id);
                statement.setString(2, title.trim());
                statement.setString(3, fileKey);
                statement.setString(4, contentType);
                return statement;
            }, keys);
            return ResponseEntity.status(HttpStatus.CREATED).body(materialById(GeneratedIds.from(keys)));
        } catch (DataAccessException exception) {
            storage.delete(fileKey);
            throw exception;
        }
    }

    @GetMapping("/courses/{id}/materials")
    public List<CourseMaterial> list(@PathVariable int id) {
        requireCourse(id);
        return jdbcTemplate.query(
                "SELECT id, course_id, title, file_key, content_type, uploaded_at FROM course_materials WHERE course_id = ? ORDER BY id",
                MATERIAL_MAPPER,
                id);
    }

    @GetMapping("/materials/{id}")
    public ResponseEntity<Resource> download(@PathVariable int id) {
        CourseMaterial material = materialById(id);
        Path path = storage.resolve(material.fileKey);
        if (!Files.exists(path)) {
            throw new NotFoundException("File for material " + id + " is not in storage");
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(material.contentType);
        } catch (InvalidMediaTypeException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/materials/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        CourseMaterial material = materialById(id);
        jdbcTemplate.update("DELETE FROM course_materials WHERE id = ?", id);
        storage.delete(material.fileKey);
        return ResponseEntity.noContent().build();
    }

    private CourseMaterial materialById(int id) {
        List<CourseMaterial> rows = jdbcTemplate.query(
                "SELECT id, course_id, title, file_key, content_type, uploaded_at FROM course_materials WHERE id = ?",
                MATERIAL_MAPPER,
                id);
        if (rows.isEmpty()) {
            throw new NotFoundException("Material " + id + " was not found");
        }
        return rows.getFirst();
    }

    private void requireCourse(int courseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courses WHERE id = ?", Integer.class, courseId);
        if (count == null || count == 0) {
            throw new NotFoundException("Course " + courseId + " was not found");
        }
    }

}
