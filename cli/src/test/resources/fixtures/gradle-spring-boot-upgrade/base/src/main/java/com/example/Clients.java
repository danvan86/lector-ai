package com.example;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

public class Clients {
    // Spring Boot 3.4 deprecates setConnectTimeout. UpgradeSpringBoot_3_4 renames it by type to connectTimeout.
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.setConnectTimeout(Duration.ofSeconds(5)).build();
    }
}
