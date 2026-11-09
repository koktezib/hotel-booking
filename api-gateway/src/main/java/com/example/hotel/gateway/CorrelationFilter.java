package com.example.hotel.gateway;

import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
public class CorrelationFilter implements WebFilter, Ordered {
  private static final Logger log = LoggerFactory.getLogger(CorrelationFilter.class);

  @Override
  public int getOrder() {
    return -200;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String incoming = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
    String trace =
        incoming != null && incoming.matches("[a-zA-Z0-9-]{1,64}")
            ? incoming
            : UUID.randomUUID().toString();
    exchange.getAttributes().put("traceId", trace);
    exchange.getResponse().getHeaders().set("X-Trace-Id", trace);
    var next =
        exchange
            .mutate()
            .request(
                exchange.getRequest().mutate().headers(h -> h.set("X-Trace-Id", trace)).build())
            .build();
    return chain
        .filter(next)
        .doFinally(
            signal ->
                log.atInfo()
                    .addKeyValue("traceId", trace)
                    .addKeyValue("path", exchange.getRequest().getPath().value())
                    .addKeyValue("status", exchange.getResponse().getStatusCode())
                    .log("gateway_request"));
  }
}
