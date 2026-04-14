package com.clmextract.config;

public class AdditionalColumnConfig {

    private String header;
    private int position = 1;

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
