package com.sage.inventory_service.filter;

import com.sage.inventory_service.kafka.MetricsPublisher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestTimingFilter extends OncePerRequestFilter {

    private final MetricsPublisher metricsPublisher;

    public RequestTimingFilter(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String status = "OK";
        try {
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 500) {
                status = "ERROR";
            }
        } catch (Exception e) {
            status = "ERROR";
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            metricsPublisher.publish(duration, status);
        }
    }
}