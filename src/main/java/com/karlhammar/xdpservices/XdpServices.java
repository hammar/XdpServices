package com.karlhammar.xdpservices;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.datatype.guava.GuavaModule;

@ComponentScan
@EnableAutoConfiguration
public class XdpServices {

    public static void main(String[] args) {
        SpringApplication.run(XdpServices.class, args);
    }
    
    @Bean
    public Jackson2ObjectMapperBuilder objectMapperBuilder() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        builder = builder.modulesToInstall(new GuavaModule());
        return builder;
    }
}