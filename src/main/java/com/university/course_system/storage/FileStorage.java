package com.university.course_system.storage;

import com.university.course_system.api.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${course.storage.dir:storage}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create storage directory " + root, exception);
        }
    }

    public String store(int courseId, MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String original = (originalName == null || originalName.isBlank())
                ? "file"
                : Path.of(originalName).getFileName().toString();
        String cleaned = original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isBlank()) {
            cleaned = "file";
        }
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(cleaned.length() - 80);
        }

        String fileKey = courseId + "-" + UUID.randomUUID() + "-" + cleaned;
        Path target = resolve(fileKey);
        try {
            file.transferTo(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store the file", exception);
        }
        return fileKey;
    }

    public Path resolve(String fileKey) {
        Path target = root.resolve(fileKey).normalize();
        if (!target.startsWith(root)) {
            throw new BadRequestException("Invalid file key");
        }
        return target;
    }

    public void delete(String fileKey) {
        try {
            Files.deleteIfExists(resolve(fileKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete the file", exception);
        }
    }

}
