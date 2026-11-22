package com.university.course_system.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({"id", "courseId", "title", "fileKey", "contentType", "uploadedAt"})
public class CourseMaterial {

    public int id;
    public int courseId;
    public String title;
    public String fileKey;
    public String contentType;
    public Instant uploadedAt;

}
