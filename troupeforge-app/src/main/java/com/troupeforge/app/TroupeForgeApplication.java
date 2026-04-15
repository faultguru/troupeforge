package com.troupeforge.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.troupeforge")
public class TroupeForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TroupeForgeApplication.class, args);
    }
}
