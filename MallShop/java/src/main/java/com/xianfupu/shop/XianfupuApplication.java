package com.xianfupu.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class XianfupuApplication {
    public static void main(String[] args) {
        SpringApplication.run(XianfupuApplication.class, args);
    }
}
