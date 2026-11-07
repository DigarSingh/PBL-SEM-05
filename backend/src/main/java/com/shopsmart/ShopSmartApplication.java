package com.shopsmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopSmartApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopSmartApplication.class, args);
    }
}
