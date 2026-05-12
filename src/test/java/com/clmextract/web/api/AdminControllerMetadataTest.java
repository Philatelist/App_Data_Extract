package com.clmextract.web.api;

import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AdminController.buildMetadataResponse().
 *
 * The static helper is tested directly without a Javalin Context.
 */
class AdminControllerMetadataTest {

    // -----------------------------------------------------------------------
    // Stub builders
    // -----------------------------------------------------------------------

    private static FieldMetadata stubField(String internalName, String displayName,
                                           String dataType, String instancePath) {
        FieldMetadata f = new FieldMetadata();
        f.setInternalName(internalName);
        f.setDisplayName(displayName);
        f.setDataType(dataType);
        f.setInstancePath(instancePath);
        return f;
    }

    private static ComponentMetadata stubComponent(String internalName, String displayName,
                                                   String cardinality, FieldMetadata... fields) {
        ComponentMetadata c = new ComponentMetadata();
        c.setInternalName(internalName);
        c.setDisplayName(displayName);
        c.setCardinality(cardinality);
        c.setFields(fields.length > 0 ? List.of(fields) : null);
        return c;
    }

    private static BoMetadata stubBoMetadata(String boName, String boDisplayName,
                                             ComponentMetadata... components) {
        BoMetadata m = new BoMetadata();
        m.setBoName(boName);
        m.setBoDisplayName(boDisplayName);
        m.setComponents(components.length > 0 ? List.of(components) : null);
        return m;
    }

    // -----------------------------------------------------------------------
    // Top-level BO fields
    // -----------------------------------------------------------------------

    @Test
    void response_containsBoNameAndDisplayName() {
        BoMetadata metadata = stubBoMetadata("ContractBO", "Contract Business Object");

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        assertEquals("ContractBO", response.get("boName"));
        assertEquals("Contract Business Object", response.get("boDisplayName"));
    }

    @Test
    void response_componentsListIsPresent() {
        BoMetadata metadata = stubBoMetadata("SomeBo", "Some BO");

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        assertTrue(response.containsKey("components"));
    }

    // -----------------------------------------------------------------------
    // Component structure
    // -----------------------------------------------------------------------

    @Test
    void response_oneComponent_hasCorrectedFields() {
        ComponentMetadata comp = stubComponent("ReqInfo", "Request Information", "single");
        BoMetadata metadata = stubBoMetadata("MyBo", "My BO", comp);

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        assertEquals(1, components.size());

        Map<String, Object> compMap = components.get(0);
        assertEquals("ReqInfo", compMap.get("internalName"));
        assertEquals("Request Information", compMap.get("displayName"));
        assertEquals("single", compMap.get("cardinality"));
        assertTrue(compMap.containsKey("fields"));
    }

    @Test
    void response_twoComponents_preservesOrder() {
        ComponentMetadata c1 = stubComponent("CompA", "Component A", "single");
        ComponentMetadata c2 = stubComponent("CompB", "Component B", "multiple");
        BoMetadata metadata = stubBoMetadata("OrderBo", "Order BO", c1, c2);

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        assertEquals(2, components.size());
        assertEquals("CompA", components.get(0).get("internalName"));
        assertEquals("CompB", components.get(1).get("internalName"));
    }

    // -----------------------------------------------------------------------
    // Field structure and instancePath stripping
    // -----------------------------------------------------------------------

    @Test
    void response_fieldHasExpectedKeys() {
        FieldMetadata field = stubField("agreementType", "Agreement Type", "String",
                "MCPDef:/GPAMEA_Data/ReqGPAMEAInfo/agreementType");
        ComponentMetadata comp = stubComponent("ReqInfo", "Request Info", "single", field);
        BoMetadata metadata = stubBoMetadata("FieldBo", "Field BO", comp);

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) components.get(0).get("fields");
        assertEquals(1, fields.size());

        Map<String, Object> fieldMap = fields.get(0);
        assertEquals("agreementType", fieldMap.get("internalName"));
        assertEquals("Agreement Type", fieldMap.get("displayName"));
        assertEquals("String", fieldMap.get("dataType"));
        assertTrue(fieldMap.containsKey("instancePath"));
    }

    @Test
    void response_instancePath_stripsMCPDefPrefix() {
        FieldMetadata field = stubField("agrType", "Agr Type", "String",
                "MCPDef:/GPAMEA_Data/ReqGPAMEAInfo/agreementType");
        ComponentMetadata comp = stubComponent("ReqInfo", "Request Info", "single", field);
        BoMetadata metadata = stubBoMetadata("StripBo", "Strip BO", comp);

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) components.get(0).get("fields");

        assertEquals("GPAMEA_Data/ReqGPAMEAInfo/agreementType", fields.get(0).get("instancePath"));
    }

    @Test
    void response_instancePath_isEmptyString_whenNull() {
        FieldMetadata field = stubField("nullPathField", "Null Path Field", "Date", null);
        ComponentMetadata comp = stubComponent("CompNull", "Comp Null", "single", field);
        BoMetadata metadata = stubBoMetadata("NullBo", "Null BO", comp);

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) components.get(0).get("fields");

        assertEquals("", fields.get(0).get("instancePath"));
    }

    @Test
    void response_instancePath_notStripped_whenNoPrefixPresent() {
        FieldMetadata field = stubField("plainField", "Plain Field", "String",
                "SomePath/Without/Prefix");
        ComponentMetadata comp = stubComponent("PlainComp", "Plain Comp", "single", field);
        BoMetadata metadata = stubBoMetadata("PlainBo", "Plain BO", comp);

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) components.get(0).get("fields");

        assertEquals("SomePath/Without/Prefix", fields.get(0).get("instancePath"));
    }

    @Test
    void response_twoFields_preservesOrder() {
        FieldMetadata f1 = stubField("field1", "Field One", "String",
                "MCPDef:/Data/Comp/field1");
        FieldMetadata f2 = stubField("field2", "Field Two", "Date",
                "MCPDef:/Data/Comp/field2");
        ComponentMetadata comp = stubComponent("Comp", "Component", "single", f1, f2);
        BoMetadata metadata = stubBoMetadata("OrderFieldBo", "Order Field BO", comp);

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) components.get(0).get("fields");

        assertEquals(2, fields.size());
        assertEquals("field1", fields.get(0).get("internalName"));
        assertEquals("Data/Comp/field1", fields.get(0).get("instancePath"));
        assertEquals("field2", fields.get(1).get("internalName"));
        assertEquals("Data/Comp/field2", fields.get(1).get("instancePath"));
    }

    // -----------------------------------------------------------------------
    // Null / empty component lists
    // -----------------------------------------------------------------------

    @Test
    void response_componentsIsEmptyList_whenNoComponentsSet() {
        BoMetadata metadata = stubBoMetadata("EmptyBo", "Empty BO");
        // components not set — null internally

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        assertNotNull(components);
        assertTrue(components.isEmpty());
    }

    @Test
    void response_fieldsIsEmptyList_whenComponentHasNoFields() {
        ComponentMetadata comp = stubComponent("NoFields", "No Fields Component", "single");
        // fields left as null
        BoMetadata metadata = stubBoMetadata("NoFieldBo", "No Field BO", comp);

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) components.get(0).get("fields");

        assertNotNull(fields);
        assertTrue(fields.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Full integration-style check
    // -----------------------------------------------------------------------

    @Test
    void response_fullStructure_oneComponentTwoFields() {
        FieldMetadata f1 = stubField("agreementType", "Agreement Type", "String",
                "MCPDef:/GPAMEA_Data/ReqGPAMEAInfo/agreementType");
        FieldMetadata f2 = stubField("startDate", "Start Date", "Date", null);
        ComponentMetadata comp = stubComponent("ReqGPAMEAInfo", "GPAMEA Info", "single", f1, f2);
        BoMetadata metadata = stubBoMetadata("GPAMEA", "GPAMEA Contract", comp);

        Map<String, Object> response = AdminController.buildMetadataResponse(metadata);

        assertEquals("GPAMEA", response.get("boName"));
        assertEquals("GPAMEA Contract", response.get("boDisplayName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) response.get("components");
        assertEquals(1, components.size());
        assertEquals("ReqGPAMEAInfo", components.get(0).get("internalName"));
        assertEquals("single", components.get(0).get("cardinality"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) components.get(0).get("fields");
        assertEquals(2, fields.size());

        assertEquals("agreementType", fields.get(0).get("internalName"));
        assertEquals("GPAMEA_Data/ReqGPAMEAInfo/agreementType", fields.get(0).get("instancePath"));
        assertEquals("String", fields.get(0).get("dataType"));

        assertEquals("startDate", fields.get(1).get("internalName"));
        assertEquals("", fields.get(1).get("instancePath"));
        assertEquals("Date", fields.get(1).get("dataType"));
    }
}
