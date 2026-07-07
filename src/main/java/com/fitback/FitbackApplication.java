package com.fitback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class FitbackApplication {

	public static void main(String[] args) {
		SpringApplication.run(FitbackApplication.class, args);
	}

}
