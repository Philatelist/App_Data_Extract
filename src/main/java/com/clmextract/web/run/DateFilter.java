package com.clmextract.web.run;

public class DateFilter {
    private String dateField;
    private String dateFrom;
    private boolean modifiedWithinPeriod;
    private int daysBeforeToday;

    public DateFilter(String dateField, String dateFrom, boolean modifiedWithinPeriod, int daysBeforeToday) {
        this.dateField = dateField;
        this.dateFrom = dateFrom;
        this.modifiedWithinPeriod = modifiedWithinPeriod;
        this.daysBeforeToday = daysBeforeToday;
    }

    public String getDateField() { return dateField; }
    public String getDateFrom() { return dateFrom; }
    public boolean isModifiedWithinPeriod() { return modifiedWithinPeriod; }
    public int getDaysBeforeToday() { return daysBeforeToday; }
}
