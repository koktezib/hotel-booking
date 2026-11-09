package com.example.hotel.common;

import java.time.Instant;

public record ErrorDto(
    Instant timestamp, int status, String error, String message, String path, String traceId) {}
