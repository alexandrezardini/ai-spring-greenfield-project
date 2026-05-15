package com.streamtube.backend.common.web;

public record ApiErrorResponse(int statusCode, String error, String message) {}
