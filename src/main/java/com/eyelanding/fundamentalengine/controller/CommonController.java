package com.eyelanding.fundamentalengine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping
public class CommonController {

    @GetMapping("/health")
    public ResponseEntity<Object> checkHealth() {
        return ResponseEntity.ok(null);
    }

    /** Redirect root path to FA Engine dashboard */
    @GetMapping("/")
    public void rootRedirect(HttpServletResponse response) throws java.io.IOException {
        response.sendRedirect("/index.html");
    }
}
