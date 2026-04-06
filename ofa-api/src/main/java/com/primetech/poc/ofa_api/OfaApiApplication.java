package com.primetech.poc.ofa_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"com.primetech.poc.ofa_api"})
@EnableJpaRepositories(basePackages = {"com.primetech.poc.ofa_api"})
public class OfaApiApplication {

  static void main(String[] args) {
    SpringApplication.run(OfaApiApplication.class, args);
  }

}
