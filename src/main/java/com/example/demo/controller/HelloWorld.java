package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloWorld {
 
    // a random comment
    @GetMapping("/hello")
    public String hello() {
        return "Hola Clase IS 2025-2026, estamos en el laboratorio 3.1.10";
    }
}
