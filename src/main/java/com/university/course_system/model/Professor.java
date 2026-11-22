package com.university.course_system.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "name"})
public class Professor {

    public int id;
    public String name;

    public Professor(int id, String name) {
        this.id = id;
        this.name = name;
    }

}
