package com.university.course_system.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "code", "name", "credits", "semester", "status", "professor"})
public class Course {

    public int id;
    public String code;
    public String name;
    public int credits;
    public String semester;
    public String status;
    public Professor professor;

}
