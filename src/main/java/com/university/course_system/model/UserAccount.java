package com.university.course_system.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "name", "email", "role"})
public class UserAccount {

    public int id;
    public String name;
    public String email;
    public String role;

}
