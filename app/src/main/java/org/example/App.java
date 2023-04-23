package org.example;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/")
@SpringBootApplication(exclude = {TaskExecutionAutoConfiguration.class})
public class App {


    @RequestMapping
    public String hello() {
        return "hello";
    }


    public static void main(String[] args) {
        SpringApplication.run(App.class);
    }

}
