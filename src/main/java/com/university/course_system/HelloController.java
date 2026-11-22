package com.university.course_system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    public HelloController() {

    }

    @GetMapping("hello")
    public String Hello(){
        return "We are learning the course Cloud Computing!";
    }

}
