package com.clmextract.api.mapper;

import com.clmextract.api.dto.MetadataNodeDto;
import com.clmextract.api.dto.PropertyDto;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MetadataMapperTest {

    @Test
    void testMapExtractsBoNameAndUsageType() {
        List<MetadataNodeDto> nodes = buildMinimalNodes();
        MetadataMapper mapper = new MetadataMapper();
        BoMetadata result = mapper.map(nodes);

        assertEquals("NAFBO", result.getBoName());
        assertEquals("Contract", result.getBoUsageType());
    }

    @Test
    void testMapBuildsComponents() {
        List<MetadataNodeDto> nodes = buildMinimalNodes();
        MetadataMapper mapper = new MetadataMapper();
        BoMetadata result = mapper.map(nodes);

        assertEquals(2, result.getComponents().size());

        ComponentMetadata summary = result.getComponents().get(0);
        assertEquals("ReqNAFInfo", summary.getInternalName());
        assertEquals("Summary", summary.getDisplayName());
        assertEquals("single", summary.getCardinality());
        assertTrue(summary.isSingleCardinality());

        ComponentMetadata attachments = result.getComponents().get(1);
        assertEquals("ReqAttachment", attachments.getInternalName());
        assertEquals("Attachments", attachments.getDisplayName());
        assertEquals("multiple", attachments.getCardinality());
        assertTrue(attachments.isMultipleCardinality());
    }

    @Test
    void testMapAssignsFieldsToComponents() {
        List<MetadataNodeDto> nodes = buildMinimalNodes();
        MetadataMapper mapper = new MetadataMapper();
        BoMetadata result = mapper.map(nodes);

        ComponentMetadata summary = result.getComponents().get(0);
        assertEquals(2, summary.getFields().size());

        FieldMetadata trackingField = summary.getFields().get(0);
        assertEquals("trackingNumber", trackingField.getInternalName());
        assertEquals("Tracking #", trackingField.getDisplayName());
        assertEquals("int", trackingField.getDataType());
        assertEquals("MCPDef:/NAFData/ReqNAFInfo/trackingNumber", trackingField.getInstancePath());

        FieldMetadata nameField = summary.getFields().get(1);
        assertEquals("name", nameField.getInternalName());
        assertEquals("Contract Number", nameField.getDisplayName());

        ComponentMetadata attachments = result.getComponents().get(1);
        assertEquals(1, attachments.getFields().size());
        assertEquals("serverFileName", attachments.getFields().get(0).getInternalName());
    }

    @Test
    void testMapWithNoParameterNodes() {
        List<MetadataNodeDto> nodes = List.of(
                makeNode("BundleProperties", "NAFBO/", null,
                        makeProp("name", "TestBO"), makeProp("usageType", "Amendment")),
                makeNode("ModuleProperties", "TestData/", "NAFBO/",
                        makeProp("name", "TestData")),
                makeNode("ComponentProperties", "TestData/ReqInfo/", "TestData/",
                        makePropWithPath("name", "ReqInfo", "MCPDef:/TestData/ReqInfo/"),
                        makePropWithPath("displayName", "Info", "MCPDef:/TestData/ReqInfo/"),
                        makePropWithPath("cardinality", "single", "MCPDef:/TestData/ReqInfo/"))
        );

        MetadataMapper mapper = new MetadataMapper();
        BoMetadata result = mapper.map(nodes);

        assertEquals("TestBO", result.getBoName());
        assertEquals(1, result.getComponents().size());
        assertTrue(result.getComponents().get(0).getFields().isEmpty());
    }

    // --- Helper methods ---

    private List<MetadataNodeDto> buildMinimalNodes() {
        return Arrays.asList(
                // BundleProperties
                makeNode("BundleProperties", "NAFBO/", null,
                        makeProp("name", "NAFBO"),
                        makeProp("usageType", "Contract")),
                // ModuleProperties
                makeNode("ModuleProperties", "NAFData/", "NAFBO/",
                        makeProp("name", "NAFData")),
                // ComponentProperties - single
                makeNode("ComponentProperties", "NAFData/ReqNAFInfo/", "NAFData/",
                        makePropWithPath("name", "ReqNAFInfo", "MCPDef:/NAFData/ReqNAFInfo/"),
                        makePropWithPath("displayName", "Summary", "MCPDef:/NAFData/ReqNAFInfo/"),
                        makePropWithPath("cardinality", "single", "MCPDef:/NAFData/ReqNAFInfo/")),
                // ParameterProperties for ReqNAFInfo
                makeNode("ParameterProperties", "NAFData/ReqNAFInfo/trackingNumber", "NAFData/ReqNAFInfo/",
                        makePropWithPath("name", "trackingNumber", "MCPDef:/NAFData/ReqNAFInfo/trackingNumber"),
                        makePropWithPath("displayName", "Tracking #", "MCPDef:/NAFData/ReqNAFInfo/trackingNumber"),
                        makePropWithPath("dataType", "int", "MCPDef:/NAFData/ReqNAFInfo/trackingNumber")),
                makeNode("ParameterProperties", "NAFData/ReqNAFInfo/name", "NAFData/ReqNAFInfo/",
                        makePropWithPath("name", "name", "MCPDef:/NAFData/ReqNAFInfo/name"),
                        makePropWithPath("displayName", "Contract Number", "MCPDef:/NAFData/ReqNAFInfo/name"),
                        makePropWithPath("dataType", "string", "MCPDef:/NAFData/ReqNAFInfo/name")),
                // ComponentProperties - multiple
                makeNode("ComponentProperties", "NAFData/ReqAttachment/", "NAFData/",
                        makePropWithPath("name", "ReqAttachment", "MCPDef:/NAFData/ReqAttachment/"),
                        makePropWithPath("displayName", "Attachments", "MCPDef:/NAFData/ReqAttachment/"),
                        makePropWithPath("cardinality", "multiple", "MCPDef:/NAFData/ReqAttachment/")),
                // ParameterProperties for ReqAttachment
                makeNode("ParameterProperties", "NAFData/ReqAttachment/serverFileName", "NAFData/ReqAttachment/",
                        makePropWithPath("name", "serverFileName", "MCPDef:/NAFData/ReqAttachment/serverFileName"),
                        makePropWithPath("displayName", "File Path", "MCPDef:/NAFData/ReqAttachment/serverFileName"),
                        makePropWithPath("dataType", "string", "MCPDef:/NAFData/ReqAttachment/serverFileName"))
        );
    }

    private MetadataNodeDto makeNode(String listType, String id, String parentId, PropertyDto... props) {
        MetadataNodeDto node = new MetadataNodeDto();
        node.setListType(listType);
        node.setId(id);
        node.setParentId(parentId);
        node.setProperties(Arrays.asList(props));
        return node;
    }

    private PropertyDto makeProp(String name, String value) {
        PropertyDto prop = new PropertyDto();
        prop.setName(name);
        prop.setValue(value);
        return prop;
    }

    private PropertyDto makePropWithPath(String name, String value, String instancePath) {
        PropertyDto prop = new PropertyDto();
        prop.setName(name);
        prop.setValue(value);
        prop.setInstancePath(instancePath);
        return prop;
    }
}
