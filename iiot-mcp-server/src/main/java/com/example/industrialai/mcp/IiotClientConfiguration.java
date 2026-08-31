package com.example.industrialai.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class IiotClientConfiguration {

    @Bean
    RestClient iiotRestClient(@Value("${iiot.api.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Client-Name", "iiot-mcp-server")
                .build();
    }
}

