package com.prathamesh.jbsolar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JbsolarApplication {

	public static void main(String[] args) {
		SpringApplication.run(JbsolarApplication.class, args);
	}

}
