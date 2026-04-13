package com.aiarchitect.terraquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TerraQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TerraQueryApplication.class, args);
    }
}
