package com.fitback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;

@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
@ComponentScan(basePackages = {
        "com.fitback.core",
        "com.fitback.global"
})
@Profile("core-local")
public class CoreLocalApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreLocalApplication.class, args);
    }
}
