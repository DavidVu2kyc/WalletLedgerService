package com.example.wallet.interfaces.rest;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    String code, String message, Instant timestamp, Map<String, Object> details) {}
