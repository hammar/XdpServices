package com.karlhammar.xdpservices;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
@EnableAutoConfiguration
public class XdpServices {

    public static void main(String[] args) {
        SpringApplication.run(XdpServices.class, args);
    }
}