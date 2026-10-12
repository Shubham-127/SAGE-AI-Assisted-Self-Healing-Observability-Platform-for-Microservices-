package com.sage.payment_service.filter;

import com.sage.payment_service.kafka.MetricsPublisher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// A filter sees EVERY request on its way in and out, like a guard at the shop door.
// OncePerRequestFilter = "run exactly one time for each request"
@Component
public class RequestTimingFilter extends OncePerRequestFilter {

    private final MetricsPublisher metricsPublisher;

    public RequestTimingFilter(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    // Skip health checks and fault switches, so they don't pollute our real traffic data
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();   // start the stopwatch
        String status = "OK";
        try {
            filterChain.doFilter(request, response);       // let the request do its normal work
            if (response.getStatus() >= 500) {             // 500+ means the server failed
                status = "ERROR";
            }
        } catch (Exception e) {
            status = "ERROR";                              // it crashed
            throw e;                                       // pass the crash on, don't hide it
        } finally {
            // "finally" always runs, success or crash: stop the stopwatch and report
            long duration = System.currentTimeMillis() - start;
            metricsPublisher.publish(duration, status);
        }
    }
}