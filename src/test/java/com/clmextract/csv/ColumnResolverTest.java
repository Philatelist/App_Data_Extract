package com.clmextract.csv;

import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ColumnResolverTest {

    // Track files created during tests so they can be cleaned up in @AfterEach
    private final List<Path> filesToCleanup = new ArrayList<>();

    @AfterEach
    void cleanup() throws IOException {
        for (Path file : filesToCleanup) {
            Files.deleteIfExists(file);
        }
        filesToCleanup.clear();
    }

    // -----------------------------------------------------------------------
    // 1. No order file: all fields from metadata in metadata order
    // -----------------------------------------------------------------------
    @Test
    void testAllFieldsInMetadataOrderWithoutOrderFile() {
        ComponentMetadata comp = makeComponent("CompA", "Component A", "single",
                makeField("alpha", "Alpha Display", "MCPDef:/Mod/CompA/alpha"),
                makeField("beta", "Beta Display", "MCPDef:/Mod/CompA/beta"),
                makeField("gamma", "Gamma Display", "MCPDef:/Mod/CompA/gamma"));

        ColumnResolver resolver = new ColumnResolver(List.of(comp), "NoOrderFileBO_1");

        // resolveFieldPaths returns all fields with MCPDef:/ stripped
        List<String> paths = resolver.resolveFieldPaths();
        assertEquals(3, paths.size());
        assertEquals("Mod/CompA/alpha", paths.get(0));
        assertEquals("Mod/CompA/beta", paths.get(1));
        assertEquals("Mod/CompA/gamma", paths.get(2));

        // resolveColumns returns all fields in metadata order
        List<ColumnResolver.ResolvedColumn> columns = resolver.resolveColumns(comp);
        assertEquals(3, columns.size());
        assertEquals("Component A.Alpha Display", columns.get(0).getHeader());
        assertEquals("alpha", columns.get(0).getFieldInternalName());
        assertEquals("Component A.Beta Display", columns.get(1).getHeader());
        assertEquals("beta", columns.get(1).getFieldInternalName());
        assertEquals("Component A.Gamma Display", columns.get(2).getHeader());
        assertEquals("gamma", columns.get(2).getFieldInternalName());
    }

    // -----------------------------------------------------------------------
    // 2. Multiple components, no order file: resolveFieldPaths returns all
    //    fields from all components in metadata order, MCPDef:/ stripped
    // -----------------------------------------------------------------------
    @Test
    void testResolveFieldPathsWithoutOrderFile() {
        ComponentMetadata comp1 = makeComponent("Summary", "Summary", "single",
                makeField("f1", "F1", "MCPDef:/BO/Summary/f1"),
                makeField("f2", "F2", "MCPDef:/BO/Summary/f2"));
        ComponentMetadata comp2 = makeComponent("Details", "Details", "multiple",
                makeField("f3", "F3", "MCPDef:/BO/Details/f3"));
        ComponentMetadata comp3 = makeComponent("Notes", "Notes", "single",
                makeField("f4", "F4", "MCPDef:/BO/Notes/f4"),
                makeField("f5", "F5", "MCPDef:/BO/Notes/f5"));

        ColumnResolver resolver = new ColumnResolver(Arrays.asList(comp1, comp2, comp3), "NoOrderFileBO_2");

        List<String> paths = resolver.resolveFieldPaths();
        assertEquals(5, paths.size());
        assertEquals("BO/Summary/f1", paths.get(0));
        assertEquals("BO/Summary/f2", paths.get(1));
        assertEquals("BO/Details/f3", paths.get(2));
        assertEquals("BO/Notes/f4", paths.get(3));
        assertEquals("BO/Notes/f5", paths.get(4));
    }

    // -----------------------------------------------------------------------
    // 3. Order file present: resolveFieldPaths returns ALL paths from the
    //    file, including ones not present in metadata
    // -----------------------------------------------------------------------
    @Test
    void testOrderFileReturnsAllPathsDirectly() throws IOException {
        String boName = "TestOrderBO";
        Path orderFile = writeOrderFile(boName,
                "Module/CompA/field1",
                "Module/CompA/field2",
                "Module/CompB/fieldX",
                "Module/CompC/notInMetadata");

        try {
            // Only CompA exists in metadata, but all paths should be returned
            ComponentMetadata comp = makeComponent("CompA", "Component A", "single",
                    makeField("field1", "Field One", "MCPDef:/Module/CompA/field1"));

            ColumnResolver resolver = new ColumnResolver(List.of(comp), boName);

            List<String> paths = resolver.resolveFieldPaths();
            assertEquals(4, paths.size());
            assertEquals("Module/CompA/field1", paths.get(0));
            assertEquals("Module/CompA/field2", paths.get(1));
            assertEquals("Module/CompB/fieldX", paths.get(2));
            assertEquals("Module/CompC/notInMetadata", paths.get(3));
        } finally {
            Files.deleteIfExists(orderFile);
            filesToCleanup.remove(orderFile);
        }
    }

    // -----------------------------------------------------------------------
    // 4. Order file present: resolveColumns matches by component internal
    //    name and parameter name, returning columns in file order
    // -----------------------------------------------------------------------
    @Test
    void testResolveColumnsMatchesByComponentAndParameterName() throws IOException {
        String boName = "TestColumnsBO";
        // Order file lists field2 before field1 to verify file-order is respected
        Path orderFile = writeOrderFile(boName,
                "Module/CompA/field2",
                "Module/CompA/field1",
                "Module/CompB/fieldX");

        try {
            ComponentMetadata comp = makeComponent("CompA", "Component A", "single",
                    makeField("field1", "Field One", "MCPDef:/Module/CompA/field1"),
                    makeField("field2", "Field Two", "MCPDef:/Module/CompA/field2"),
                    makeField("field3", "Field Three", "MCPDef:/Module/CompA/field3"));

            ColumnResolver resolver = new ColumnResolver(List.of(comp), boName);

            List<ColumnResolver.ResolvedColumn> columns = resolver.resolveColumns(comp);

            // Only field2 and field1 match CompA; field3 is not in the order file;
            // CompB/fieldX does not match CompA. File order: field2 then field1.
            assertEquals(2, columns.size());
            assertEquals("Component A.Field Two", columns.get(0).getHeader());
            assertEquals("field2", columns.get(0).getFieldInternalName());
            assertEquals("Component A.Field One", columns.get(1).getHeader());
            assertEquals("field1", columns.get(1).getFieldInternalName());
        } finally {
            Files.deleteIfExists(orderFile);
            filesToCleanup.remove(orderFile);
        }
    }

    // -----------------------------------------------------------------------
    // 5. Order file references a parameter NOT in metadata: a ResolvedColumn
    //    is created using the parameter name as both internal name and
    //    fallback display name
    // -----------------------------------------------------------------------
    @Test
    void testResolveColumnsRetainsPathsNotInMetadata() throws IOException {
        String boName = "TestRetainBO";
        Path orderFile = writeOrderFile(boName,
                "Module/CompA/knownField",
                "Module/CompA/unknownField");

        try {
            ComponentMetadata comp = makeComponent("CompA", "Component A", "single",
                    makeField("knownField", "Known Display", "MCPDef:/Module/CompA/knownField"));

            ColumnResolver resolver = new ColumnResolver(List.of(comp), boName);

            List<ColumnResolver.ResolvedColumn> columns = resolver.resolveColumns(comp);
            assertEquals(2, columns.size());

            // First column matched metadata
            assertEquals("Component A.Known Display", columns.get(0).getHeader());
            assertEquals("knownField", columns.get(0).getFieldInternalName());

            // Second column was NOT in metadata: parameter name used as fallback display name
            assertEquals("Component A.unknownField", columns.get(1).getHeader());
            assertEquals("unknownField", columns.get(1).getFieldInternalName());
            assertEquals("Module/CompA/unknownField", columns.get(1).getInstancePath());
        } finally {
            Files.deleteIfExists(orderFile);
            filesToCleanup.remove(orderFile);
        }
    }

    // -----------------------------------------------------------------------
    // 6. Order file lists paths for one component but not another:
    //    resolveColumns returns empty for the unlisted component
    // -----------------------------------------------------------------------
    @Test
    void testComponentExclusion() throws IOException {
        String boName = "TestExcludeBO";
        Path orderFile = writeOrderFile(boName,
                "Module/CompA/field1",
                "Module/CompA/field2");

        try {
            ComponentMetadata compA = makeComponent("CompA", "Component A", "single",
                    makeField("field1", "F1", "MCPDef:/Module/CompA/field1"),
                    makeField("field2", "F2", "MCPDef:/Module/CompA/field2"));

            ComponentMetadata compB = makeComponent("CompB", "Component B", "multiple",
                    makeField("fieldX", "FX", "MCPDef:/Module/CompB/fieldX"));

            ColumnResolver resolver = new ColumnResolver(Arrays.asList(compA, compB), boName);

            // CompA should get its columns
            List<ColumnResolver.ResolvedColumn> columnsA = resolver.resolveColumns(compA);
            assertEquals(2, columnsA.size());

            // CompB is not mentioned in the order file: empty result
            List<ColumnResolver.ResolvedColumn> columnsB = resolver.resolveColumns(compB);
            assertTrue(columnsB.isEmpty(), "Expected empty columns for component not in order file");
        } finally {
            Files.deleteIfExists(orderFile);
            filesToCleanup.remove(orderFile);
        }
    }

    // -----------------------------------------------------------------------
    // 7. parameter-displaynames.csv override file: display name override is applied
    //    in the column header
    // -----------------------------------------------------------------------
    @Test
    void testAddParametersOverrideApplied() throws IOException {
        String boName = "TestOverrideBO";
        Path orderFile = writeOrderFile(boName,
                "Module/CompA/field1",
                "Module/CompA/extraField");

        Path overridesFile = writeOverridesFile(
                "Component;Parameter;DisplayName;",
                "CompA;field1;Overridden Field One;",
                "CompA;extraField;Extra Display Name;");

        try {
            ComponentMetadata comp = makeComponent("CompA", "Component A", "single",
                    makeField("field1", "Original Field One", "MCPDef:/Module/CompA/field1"));

            ColumnResolver resolver = new ColumnResolver(List.of(comp), boName);

            List<ColumnResolver.ResolvedColumn> columns = resolver.resolveColumns(comp);
            assertEquals(2, columns.size());

            // field1 is in metadata but override should win
            assertEquals("Component A.Overridden Field One", columns.get(0).getHeader());
            assertEquals("field1", columns.get(0).getFieldInternalName());

            // extraField is NOT in metadata; override provides the display name
            assertEquals("Component A.Extra Display Name", columns.get(1).getHeader());
            assertEquals("extraField", columns.get(1).getFieldInternalName());
        } finally {
            Files.deleteIfExists(orderFile);
            filesToCleanup.remove(orderFile);
            Files.deleteIfExists(overridesFile);
            filesToCleanup.remove(overridesFile);
        }
    }

    // -----------------------------------------------------------------------
    // 8. Both order file and parameter-displaynames.csv present: override display name
    //    wins over metadata display name
    // -----------------------------------------------------------------------
    @Test
    void testDisplayNamePrecedence() throws IOException {
        String boName = "TestPrecedenceBO";
        Path orderFile = writeOrderFile(boName,
                "Module/CompA/field1",
                "Module/CompA/field2");

        Path overridesFile = writeOverridesFile(
                "Component;Parameter;DisplayName;",
                "CompA;field1;Override Wins;");

        try {
            ComponentMetadata comp = makeComponent("CompA", "Component A", "single",
                    makeField("field1", "Metadata Display", "MCPDef:/Module/CompA/field1"),
                    makeField("field2", "Field Two Display", "MCPDef:/Module/CompA/field2"));

            ColumnResolver resolver = new ColumnResolver(List.of(comp), boName);

            List<ColumnResolver.ResolvedColumn> columns = resolver.resolveColumns(comp);
            assertEquals(2, columns.size());

            // field1: override should win over metadata display name
            assertEquals("Component A.Override Wins", columns.get(0).getHeader());

            // field2: no override, metadata display name should be used
            assertEquals("Component A.Field Two Display", columns.get(1).getHeader());
        } finally {
            Files.deleteIfExists(orderFile);
            filesToCleanup.remove(orderFile);
            Files.deleteIfExists(overridesFile);
            filesToCleanup.remove(overridesFile);
        }
    }

    // -----------------------------------------------------------------------
    // 9. No override file: metadata display name is used in column headers
    // -----------------------------------------------------------------------
    @Test
    void testNoOverrideFileUsesMetadataDisplayName() {
        ComponentMetadata comp = makeComponent("CompA", "Component A", "single",
                makeField("field1", "First Field", "MCPDef:/Mod/CompA/field1"),
                makeField("field2", "Second Field", "MCPDef:/Mod/CompA/field2"));

        // Use a BO name that has no matching order file, and no overrides file exists
        // for this test since parameter-displaynames.csv is a global file. We rely on the real
        // parameter-displaynames.csv either not existing or not containing entries for "CompA".
        // Using a unique component name avoids collisions with any real override data.
        ComponentMetadata compUnique = makeComponent("UniqueComp_NoOverride", "Unique Component", "single",
                makeField("fieldA", "Display A", "MCPDef:/Mod/UniqueComp_NoOverride/fieldA"),
                makeField("fieldB", "Display B", "MCPDef:/Mod/UniqueComp_NoOverride/fieldB"));

        ColumnResolver resolver = new ColumnResolver(List.of(compUnique), "NoOverrideBO_9");

        List<ColumnResolver.ResolvedColumn> columns = resolver.resolveColumns(compUnique);
        assertEquals(2, columns.size());
        assertEquals("Unique Component.Display A", columns.get(0).getHeader());
        assertEquals("fieldA", columns.get(0).getFieldInternalName());
        assertEquals("MCPDef:/Mod/UniqueComp_NoOverride/fieldA", columns.get(0).getInstancePath());
        assertEquals("Unique Component.Display B", columns.get(1).getHeader());
        assertEquals("fieldB", columns.get(1).getFieldInternalName());
        assertEquals("MCPDef:/Mod/UniqueComp_NoOverride/fieldB", columns.get(1).getInstancePath());
    }

    // ============================= Helpers =================================

    /**
     * Write a column order file at config/columns/{boName}.csv relative to CWD.
     * Each line is one path entry.
     */
    private Path writeOrderFile(String boName, String... lines) throws IOException {
        Path dir = Path.of("config", "columns");
        Files.createDirectories(dir);
        Path file = dir.resolve(boName + ".csv");
        Files.writeString(file, String.join("\n", lines) + "\n");
        filesToCleanup.add(file);
        return file;
    }

    /**
     * Write the overrides file at config/overrides/parameter-displaynames.csv relative to CWD.
     * First line should be the header row; subsequent lines are data rows.
     */
    private Path writeOverridesFile(String... lines) throws IOException {
        Path dir = Path.of("config", "overrides");
        Files.createDirectories(dir);
        Path file = dir.resolve("parameter-displaynames.csv");
        Files.writeString(file, String.join("\n", lines) + "\n");
        filesToCleanup.add(file);
        return file;
    }

    private ComponentMetadata makeComponent(String internalName, String displayName,
                                            String cardinality, FieldMetadata... fields) {
        ComponentMetadata comp = new ComponentMetadata();
        comp.setInternalName(internalName);
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
