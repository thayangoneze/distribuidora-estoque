package br.com.distribuidora.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class IaConfig {
    @Bean
    RestClient iaRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(45000);
        return RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta")
            .requestFactory(factory).build();
    }
}
