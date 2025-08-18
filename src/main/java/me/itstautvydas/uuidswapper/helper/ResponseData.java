package me.itstautvydas.uuidswapper.helper;

import java.net.http.HttpResponse;

public record ResponseData(HttpResponse<String> response, Throwable exception) {
    public Throwable getException() {
        return exception;
    }

    public HttpResponse<String> getResponse() {
        return response;
    }
}
