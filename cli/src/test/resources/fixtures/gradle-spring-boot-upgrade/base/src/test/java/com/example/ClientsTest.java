package com.example;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

class ClientsTest {
    @Test
    void buildsARestTemplate() {
        assertNotNull(new Clients().restTemplate(new RestTemplateBuilder()));
    }
}
