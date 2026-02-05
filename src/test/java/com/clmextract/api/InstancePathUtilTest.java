package com.clmextract.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstancePathUtilTest {

    @Test
    void testMetadataFieldPath() {
        var parsed = InstancePathUtil.parse("MCPDef:/NAFData/ReqNAFInfo/trackingNumber");
        assertEquals("NAFData", parsed.module());
        assertEquals("ReqNAFInfo", parsed.component());
        assertEquals("trackingNumber", parsed.parameter());
        assertNull(parsed.componentInstanceId());
    }

    @Test
    void testMetadataComponentPath() {
        var parsed = InstancePathUtil.parse("MCPDef:/NAFData/ReqNAFInfo/");
        assertEquals("NAFData", parsed.module());
        assertEquals("ReqNAFInfo", parsed.component());
        assertNull(parsed.parameter());
        assertNull(parsed.componentInstanceId());
    }

    @Test
    void testBundleDefPath() {
        var parsed = InstancePathUtil.parse("BundleDef:/NAFBO/");
        assertEquals("NAFBO", parsed.module());
        assertNull(parsed.component());
        assertNull(parsed.parameter());
    }

    @Test
    void testBundlesFieldPathWithInstanceIds() {
        var parsed = InstancePathUtil.parse("MCP:/NAFData|16016628/ReqNAFInfo|16016628/trackingNumber");
        assertEquals("NAFData", parsed.module());
        assertEquals("ReqNAFInfo", parsed.component());
        assertEquals("trackingNumber", parsed.parameter());
        assertEquals("16016628", parsed.componentInstanceId());
    }

    @Test
    void testBundlesMultiCardinalityWithDifferentInstanceIds() {
        var parsed1 = InstancePathUtil.parse("MCP:/NAFData|16016628/ReqAttachment|1001/serverFileName");
        var parsed2 = InstancePathUtil.parse("MCP:/NAFData|16016628/ReqAttachment|1002/serverFileName");

        assertEquals("ReqAttachment", parsed1.component());
        assertEquals("ReqAttachment", parsed2.component());
        assertEquals("serverFileName", parsed1.parameter());
        assertEquals("serverFileName", parsed2.parameter());
        assertEquals("1001", parsed1.componentInstanceId());
        assertEquals("1002", parsed2.componentInstanceId());
    }

    @Test
    void testNullPath() {
        var parsed = InstancePathUtil.parse(null);
        assertTrue(parsed.isEmpty());
    }

    @Test
    void testEmptyPath() {
        var parsed = InstancePathUtil.parse("");
        assertTrue(parsed.isEmpty());
    }

    @Test
    void testBlankPath() {
        var parsed = InstancePathUtil.parse("   ");
        assertTrue(parsed.isEmpty());
    }

    @Test
    void testModuleOnlyPath() {
        var parsed = InstancePathUtil.parse("MCPDef:/NAFData/");
        assertEquals("NAFData", parsed.module());
        assertNull(parsed.component());
        assertNull(parsed.parameter());
        assertFalse(parsed.isEmpty());
    }
}
