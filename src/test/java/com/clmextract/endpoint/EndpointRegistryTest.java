package com.clmextract.endpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EndpointRegistryTest {

    @TempDir
    Path tempDir;

    private String writeYaml(String content) throws IOException {
        Path file = tempDir.resolve("endpoints.yml");
        Files.writeString(file, content);
        return file.toString();
    }

    @Test
    void testParsesRealEndpointsFile() {
        EndpointRegistry registry = new EndpointRegistry("inputs/endpoints.yml");
        registry.load();

        assertEquals("/services/rest/methods", registry.getBasePath());
        assertTrue(registry.getAllEndpoints().size() > 5);

        EndpointDefinition login = registry.getEndpoint(EndpointRegistry.LOGIN);
        assertEquals("login", login.getName());
        assertEquals("GET", login.getMethod());
        assertEquals("/login", login.getPath());
        assertEquals("basicHeaders", login.getAuth().getType());

        EndpointDefinition logout = registry.getEndpoint(EndpointRegistry.LOGOUT);
        assertEquals("logout", logout.getName());
        assertEquals("POST", logout.getMethod());
        assertEquals("sessionHeader", logout.getAuth().getType());
        assertEquals("session_id", logout.getAuth().getHeaderName());

        EndpointDefinition metadata = registry.getEndpoint(EndpointRegistry.GET_BO_METADATA);
        assertEquals("getBoMetaData", metadata.getName());
        assertNotNull(metadata.getRequestHeaders());
        assertEquals(1, metadata.getRequestHeaders().size());
        assertEquals("boType", metadata.getRequestHeaders().get(0).getName());
        assertEquals("boUsageType", metadata.getRequestHeaders().get(0).getValueFrom());

        EndpointDefinition tracking = registry.getEndpoint(EndpointRegistry.GET_TRACKING_NUMBERS);
        assertEquals("getTrackingNumbers", tracking.getName());

        EndpointDefinition bundles = registry.getEndpoint(EndpointRegistry.BUNDLES);
        assertEquals("bundles", bundles.getName());
        assertEquals("POST", bundles.getMethod());
        assertNotNull(bundles.getRequestBody());
        assertEquals("json", bundles.getRequestBody().getType());

        EndpointDefinition boTypes = registry.getEndpoint(EndpointRegistry.GET_BO_TYPES);
        assertEquals("getBoTypes", boTypes.getName());
    }

    @Test
    void testAllRequiredOperationsResolved() {
        EndpointRegistry registry = new EndpointRegistry("inputs/endpoints.yml");
        registry.load();

        assertNotNull(registry.getEndpoint(EndpointRegistry.LOGIN));
        assertNotNull(registry.getEndpoint(EndpointRegistry.LOGOUT));
        assertNotNull(registry.getEndpoint(EndpointRegistry.GET_BO_METADATA));
        assertNotNull(registry.getEndpoint(EndpointRegistry.GET_TRACKING_NUMBERS));
        assertNotNull(registry.getEndpoint(EndpointRegistry.BUNDLES));
    }

    @Test
    void testMissingRequiredEndpointThrows() throws IOException {
        String yaml = "basePath: /api\n" +
                "endpoints:\n" +
                "- name: login\n" +
                "  method: GET\n" +
                "  path: /login\n" +
                "- name: logout\n" +
                "  method: POST\n" +
                "  path: /logout\n";

        String path = writeYaml(yaml);
        EndpointRegistry registry = new EndpointRegistry(path);

        EndpointResolutionException ex = assertThrows(
                EndpointResolutionException.class, registry::load);
        assertTrue(ex.getMessage().contains("Required endpoint not found"));
    }

    @Test
    void testMissingLoginThrows() throws IOException {
        String yaml = "basePath: /api\n" +
                "endpoints:\n" +
                "- name: logout\n" +
                "  method: POST\n" +
                "  path: /logout\n" +
                "- name: getBoMetaData\n" +
                "  method: GET\n" +
                "  path: /boMetaData\n" +
                "- name: getTrackingNumbers\n" +
                "  method: GET\n" +
                "  path: /trackingNumbers\n" +
                "- name: bundles\n" +
                "  method: POST\n" +
                "  path: /bundles\n";

        String path = writeYaml(yaml);
        EndpointRegistry registry = new EndpointRegistry(path);

        EndpointResolutionException ex = assertThrows(
                EndpointResolutionException.class, registry::load);
        assertTrue(ex.getMessage().contains("login"));
    }

    @Test
    void testNonExistentFileThrows() {
        EndpointRegistry registry = new EndpointRegistry("/nonexistent/endpoints.yml");

        EndpointResolutionException ex = assertThrows(
                EndpointResolutionException.class, registry::load);
        assertTrue(ex.getMessage().contains("Cannot read endpoints file"));
    }

    @Test
    void testResponseConfigParsed() {
        EndpointRegistry registry = new EndpointRegistry("inputs/endpoints.yml");
        registry.load();

        EndpointDefinition login = registry.getEndpoint(EndpointRegistry.LOGIN);
        assertNotNull(login.getResponse());
        assertEquals("text", login.getResponse().getType());
        assertEquals("sessionId", login.getResponse().getSaveAs());

        EndpointDefinition metadata = registry.getEndpoint(EndpointRegistry.GET_BO_METADATA);
        assertNotNull(metadata.getResponse());
        assertEquals("json", metadata.getResponse().getType());
        assertNull(metadata.getResponse().getSaveAs());
    }

    @Test
    void testMinimalValidEndpoints() throws IOException {
        String yaml = "basePath: /api\n" +
                "endpoints:\n" +
                "- name: login\n" +
                "  method: GET\n" +
                "  path: /login\n" +
                "- name: logout\n" +
                "  method: POST\n" +
                "  path: /logout\n" +
                "- name: getBoMetaData\n" +
                "  method: GET\n" +
                "  path: /boMetaData\n" +
                "- name: getTrackingNumbers\n" +
                "  method: GET\n" +
                "  path: /trackingNumbers\n" +
                "- name: bundles\n" +
                "  method: POST\n" +
                "  path: /bundles\n";

        String path = writeYaml(yaml);
        EndpointRegistry registry = new EndpointRegistry(path);
        registry.load();

        assertEquals(5, registry.getAllEndpoints().size());
        assertEquals("/api", registry.getBasePath());
        assertNotNull(registry.getEndpoint(EndpointRegistry.LOGIN));
        assertNotNull(registry.getEndpoint(EndpointRegistry.BUNDLES));
    }

    @Test
    void testGetBoTypesOptional() throws IOException {
        String yaml = "basePath: /api\n" +
                "endpoints:\n" +
                "- name: login\n" +
                "  method: GET\n" +
                "  path: /login\n" +
                "- name: logout\n" +
                "  method: POST\n" +
                "  path: /logout\n" +
                "- name: getBoMetaData\n" +
                "  method: GET\n" +
                "  path: /boMetaData\n" +
                "- name: getTrackingNumbers\n" +
                "  method: GET\n" +
                "  path: /trackingNumbers\n" +
                "- name: bundles\n" +
                "  method: POST\n" +
                "  path: /bundles\n";

        String path = writeYaml(yaml);
        EndpointRegistry registry = new EndpointRegistry(path);
        registry.load();

        // GET_BO_TYPES is optional, should throw when accessed but not fail on load
        assertThrows(EndpointResolutionException.class,
                () -> registry.getEndpoint(EndpointRegistry.GET_BO_TYPES));
    }
}
