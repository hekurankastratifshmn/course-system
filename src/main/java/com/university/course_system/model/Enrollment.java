package com.university.course_system.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({"enrollmentId", "studentId", "studentName", "courseId", "status", "enrolledAt"})
public class Enrollment {

    public int enrollmentId;
    public int studentId;
    public String studentName;
    public int courseId;
    public String status;
    public Instant enrolledAt;

}
