package ru.kirsachik.uas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UasSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(UasSimulatorApplication.class, args);
    }
}
