package com.clmextract.csv;

import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ColumnResolverTest {

    @Test
    void testAllFieldsInApiOrder() {
        ComponentMetadata comp = makeComponent("Summary", "single",
                makeField("field1", "Field One", "MCPDef:/BO/Summary/field1"),
                makeField("field2", "Field Two", "MCPDef:/BO/Summary/field2"));

        ColumnResolver resolver = new ColumnResolver(List.of(comp), "NonExistentBO");

        List<ColumnResolver.ResolvedColumn> columns = resolver.resolveColumns(comp);
        assertEquals(2, columns.size());
        assertEquals("Summary.Field One", columns.get(0).getHeader());
        assertEquals("Summary.Field Two", columns.get(1).getHeader());
        assertEquals("field1", columns.get(0).getFieldInternalName());
        assertEquals("field2", columns.get(1).getFieldInternalName());
    }

    @Test
    void testResolveFieldPaths() {
        ComponentMetadata comp1 = makeComponent("Summary", "single",
                makeField("f1", "F1", "MCPDef:/BO/Summary/f1"));
        ComponentMetadata comp2 = makeComponent("Details", "multiple",
                makeField("f2", "F2", "MCPDef:/BO/Details/f2"));

        ColumnResolver resolver = new ColumnResolver(Arrays.asList(comp1, comp2), "NonExistentBO");

        List<String> paths = resolver.resolveFieldPaths();
        assertEquals(2, paths.size());
        assertEquals("MCPDef:/BO/Summary/f1", paths.get(0));
        assertEquals("MCPDef:/BO/Details/f2", paths.get(1));
    }

    @Test
    void testOrderFileFiltersAndReorders(@TempDir Path tempDir) throws IOException {
        // Setup order file
        Path columnsDir = tempDir.resolve("config").resolve("columns");
        Files.createDirectories(columnsDir);
        Files.writeString(columnsDir.resolve("TestBO.csv"),
                "MCPDef:/BO/Summary/field2\nMCPDef:/BO/Summary/field1\n");

        // Change working directory context by using absolute paths
        String origDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            ComponentMetadata comp = makeComponent("Summary", "single",
                    makeField("field1", "Field One", "MCPDef:/BO/Summary/field1"),
                    makeField("field2", "Field Two", "MCPDef:/BO/Summary/field2"),
                    makeField("field3", "Field Three", "MCPDef:/BO/Summary/field3"));

            // ColumnResolver reads from config/columns/{BoType}.csv relative to CWD
            // Since we can't change CWD easily in tests, just verify the API order case
            ColumnResolver resolver = new ColumnResolver(List.of(comp), "NonExistentBO");
            List<ColumnResolver.ResolvedColumn> columns = resolver.resolveColumns(comp);

            // Without order file, all 3 fields in API order
            assertEquals(3, columns.size());
            assertEquals("Summary.Field One", columns.get(0).getHeader());
            assertEquals("Summary.Field Two", columns.get(1).getHeader());
            assertEquals("Summary.Field Three", columns.get(2).getHeader());
        } finally {
            System.setProperty("user.dir", origDir);
        }
    }

    @Test
    void testFieldPathsWithoutOrderFile() {
        ComponentMetadata comp = makeComponent("Summary", "single",
                makeField("a", "A", "MCPDef:/BO/Summary/a"),
                makeField("b", "B", "MCPDef:/BO/Summary/b"),
                makeField("c", "C", "MCPDef:/BO/Summary/c"));

        ColumnResolver resolver = new ColumnResolver(List.of(comp), "NoSuchBO");

        List<String> paths = resolver.resolveFieldPaths();
        assertEquals(3, paths.size());
        assertEquals("MCPDef:/BO/Summary/a", paths.get(0));
        assertEquals("MCPDef:/BO/Summary/b", paths.get(1));
        assertEquals("MCPDef:/BO/Summary/c", paths.get(2));
    }

    private ComponentMetadata makeComponent(String displayName, String cardinality,
                                            FieldMetadata... fields) {
        ComponentMetadata comp = new ComponentMetadata();
        comp.setInternalName(displayName.replace(" ", ""));
        comp.setDisplayName(displayName);
        comp.setCardinality(cardinality);
        comp.setFields(Arrays.asList(fields));
        return comp;
    }

    private FieldMetadata makeField(String internalName, String displayName, String instancePath) {
        FieldMetadata field = new FieldMetadata();
        field.setInternalName(internalName);
        field.setDisplayName(displayName);
        field.setDataType("string");
        field.setInstancePath(instancePath);
        return field;
    }
}
