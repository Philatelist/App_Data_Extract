package com.clmextract.http;

public class SessionExpiredException extends ApiException {

    public SessionExpiredException(int statusCode, String responseBody) {
        super(statusCode, responseBody);
    }
}
