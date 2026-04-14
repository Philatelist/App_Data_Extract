package com.clmextract.config;

public class DateFormatConfig {

    private String inputFormat;
    private String outputFormat;
    private String inputDateTimeFormat;
    private String outputDateTimeFormat;

    public String getInputFormat() {
        return inputFormat;
    }

    public void setInputFormat(String inputFormat) {
        this.inputFormat = inputFormat;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public String getInputDateTimeFormat() {
        return inputDateTimeFormat;
    }

    public void setInputDateTimeFormat(String inputDateTimeFormat) {
        this.inputDateTimeFormat = inputDateTimeFormat;
    }

    public String getOutputDateTimeFormat() {
        return outputDateTimeFormat;
    }

    public void setOutputDateTimeFormat(String outputDateTimeFormat) {
        this.outputDateTimeFormat = outputDateTimeFormat;
    }
}
