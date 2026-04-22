package com.clmextract.config;

import java.util.List;

public class DateFormatConfig {

    private List<String> inputFormats;
    private String outputFormat;
    private List<String> inputDateTimeFormats;
    private String outputDateTimeFormat;

    public List<String> getInputFormats() {
        return inputFormats;
    }

    public void setInputFormats(List<String> inputFormats) {
        this.inputFormats = inputFormats;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public List<String> getInputDateTimeFormats() {
        return inputDateTimeFormats;
    }

    public void setInputDateTimeFormats(List<String> inputDateTimeFormats) {
        this.inputDateTimeFormats = inputDateTimeFormats;
    }

    public String getOutputDateTimeFormat() {
        return outputDateTimeFormat;
    }

    public void setOutputDateTimeFormat(String outputDateTimeFormat) {
        this.outputDateTimeFormat = outputDateTimeFormat;
    }
}
