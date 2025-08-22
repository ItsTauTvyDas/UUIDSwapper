package me.itstautvydas.uuidswapper.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.net.http.HttpResponse;

@AllArgsConstructor
@Getter
public class ResponseData {
    private HttpResponse<String> response;
    private Throwable exception;
}
