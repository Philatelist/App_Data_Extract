package com.clmextract.export;

public record ConversionResult(boolean success, String reason) {
    public static ConversionResult ok() { return new ConversionResult(true, ""); }
    public static ConversionResult fail(String reason) { return new ConversionResult(false, reason); }
}
