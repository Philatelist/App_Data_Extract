package com.clmextract.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PropertyDto {

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Value")
    private String value;

    @JsonProperty("InternalValue")
    private String internalValue;

    @JsonProperty("DataType")
    private String dataType;

    @JsonProperty("InstancePath")
    private String instancePath;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getInternalValue() { return internalValue; }
    public void setInternalValue(String internalValue) { this.internalValue = internalValue; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getInstancePath() { return instancePath; }
    public void setInstancePath(String instancePath) { this.instancePath = instancePath; }
}
