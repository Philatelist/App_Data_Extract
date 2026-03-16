package com.clmextract.endpoint;

import java.util.List;

public class EndpointDefinition {

    private String name;
    private String method;
    private String path;
    private boolean absolutePath;
    private AuthConfig auth;
    private List<HeaderDef> requestHeaders;
    private BodyConfig requestBody;
    private ResponseConfig response;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(boolean absolutePath) {
        this.absolutePath = absolutePath;
    }

    public AuthConfig getAuth() {
        return auth;
    }

    public void setAuth(AuthConfig auth) {
        this.auth = auth;
    }

    public List<HeaderDef> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(List<HeaderDef> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public BodyConfig getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(BodyConfig requestBody) {
        this.requestBody = requestBody;
    }

    public ResponseConfig getResponse() {
        return response;
    }

    public void setResponse(ResponseConfig response) {
        this.response = response;
    }
}
