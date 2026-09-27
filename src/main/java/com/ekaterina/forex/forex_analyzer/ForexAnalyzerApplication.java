package com.ekaterina.forex.forex_analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ForexAnalyzerApplication {

	public static void main(String[] args) {
        SpringApplication.run(ForexAnalyzerApplication.class, args);
	}

}
