package com.bachelorthesis.supervised_problem_solving.controller;


import com.bachelorthesis.supervised_problem_solving.exchangeAPI.pojo.chartData.ChartDataPojo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/test")
    public String test() {
        return "hello world";
    }

    @GetMapping("/api/hello")
    public String hello() {
        return "hello world";
    }

    @GetMapping("/api/json")
    public String json() throws JsonProcessingException {
        final ChartDataPojo chartDataPojo = new ChartDataPojo.Builder()
                .close("2.0")
                .date(2L)
                .high("2.0")
                .low("1.0")
                .open("0.0")
                .quoteVolume("2.0")
                .volume("2.0")
                .weightedAverage("2.0")
                .build();
        return objectMapper.writeValueAsString(chartDataPojo);
    }


    @GetMapping(value = "/student/{studentId}")
    public @ResponseBody
    String getTestData(@PathVariable Integer studentId) {


        return "student";
    }

/*    @GetMapping("/logout")
    public String logout() {

        Runnable runnable = () -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            SpringApplication.exit(applicationContext);
        };
        runnable.run();

        return "logout";
    }*/
}
