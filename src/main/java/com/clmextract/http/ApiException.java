package com.clmextract.http;

public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public ApiException(int statusCode, String responseBody) {
        super("API error " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
