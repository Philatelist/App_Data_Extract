package com.clmextract.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataNodeDto {

    @JsonProperty("listType")
    private String listType;

    @JsonProperty("id")
    private String id;

    @JsonProperty("parentId")
    private String parentId;

    @JsonProperty("Property")
    private List<PropertyDto> properties;

    public String getListType() { return listType; }
    public void setListType(String listType) { this.listType = listType; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public List<PropertyDto> getProperties() { return properties; }
    public void setProperties(List<PropertyDto> properties) { this.properties = properties; }
}
