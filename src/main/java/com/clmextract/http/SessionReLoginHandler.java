package com.clmextract.http;

public interface SessionReLoginHandler {

    void onSessionExpired();

    String getNewSessionId();
}
