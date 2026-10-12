package com.sage.order_service.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// @Configuration = "this class builds helper objects that the rest of the app can use"
@Configuration
public class HttpClientConfig {

    // @Bean = "Spring, please create this object once and hand it out wherever it is needed"
    // @Value reads timeout-ms from application.yml (the 2000 we wrote).
    @Bean
    public RestClient restClient(@Value("${sage.downstream.timeout-ms}") long timeoutMs) {

        // connectTimeout = how long to wait for the other service to "pick up the phone"
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);

        // readTimeout = after they pick up, how long to wait for the answer.
        // WITHOUT this, a slow service would make Order wait forever. We want Order to give up after 2 seconds.
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        // RestClient = Spring's tool for "call another service over HTTP"
        return RestClient.builder().requestFactory(factory).build();
    }
}