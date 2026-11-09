package com.example.hotel.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class TraceFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String trace = request.getHeader("X-Trace-Id");
    if (trace == null || !trace.matches("[a-zA-Z0-9-]{1,64}")) trace = UUID.randomUUID().toString();
    MDC.put("traceId", trace);
    request.setAttribute("traceId", trace);
    response.setHeader("X-Trace-Id", trace);
    try {
      chain.doFilter(request, response);
    } finally {
      log.info(
          "event=http_request method={} path={} status={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus());
      MDC.remove("traceId");
    }
  }
}
