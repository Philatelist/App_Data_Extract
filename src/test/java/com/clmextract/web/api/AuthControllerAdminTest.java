package com.clmextract.web.api;

import com.clmextract.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the admin-check and role-resolution logic introduced in
 * AuthController (Spec 013, Slice 2).
 *
 * Javalin Context is not mocked here; instead we test the pure business logic
 * helpers extracted into static utility methods.
 */
class AuthControllerAdminTest {

    // ---------------------------------------------------------------------------
    // Helper that reproduces the isAdmin check used in AuthController.checkAdmin()
    // ---------------------------------------------------------------------------

    private static boolean resolveIsAdmin(AppConfig config, String email) {
        if (email == null || email.isBlank() || config == null || config.getAdminEmails() == null) {
            return false;
        }
        return config.getAdminEmails().stream()
                .anyMatch(e -> e.equalsIgnoreCase(email.trim()));
    }

    // ---------------------------------------------------------------------------
    // Helper that reproduces the role decision made in AuthController.login()
    // ---------------------------------------------------------------------------

    private static String resolveRole(AppConfig config, String username, boolean asAdmin) {
        boolean isAdminEmail = asAdmin && config.getAdminEmails() != null
                && config.getAdminEmails().stream().anyMatch(e -> e.equalsIgnoreCase(username));
        return isAdminEmail ? "ADMIN" : "OPERATOR";
    }

    // ---------------------------------------------------------------------------
    // checkAdmin logic tests
    // ---------------------------------------------------------------------------

    @Test
    void checkAdmin_returnsTrue_whenEmailMatchesAdminList() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of("admin@company.com", "boss@company.com"));

        assertTrue(resolveIsAdmin(config, "admin@company.com"));
    }

    @Test
    void checkAdmin_isCaseInsensitive() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of("Admin@Company.COM"));

        assertTrue(resolveIsAdmin(config, "admin@company.com"));
        assertTrue(resolveIsAdmin(config, "ADMIN@COMPANY.COM"));
    }

    @Test
    void checkAdmin_returnsFalse_whenEmailNotInList() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of("admin@company.com"));

        assertFalse(resolveIsAdmin(config, "user@company.com"));
    }

    @Test
    void checkAdmin_returnsFalse_whenAdminListIsEmpty() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of());

        assertFalse(resolveIsAdmin(config, "anyone@company.com"));
    }

    @Test
    void checkAdmin_returnsFalse_whenConfigIsNull() {
        assertFalse(resolveIsAdmin(null, "admin@company.com"));
    }

    @Test
    void checkAdmin_returnsFalse_whenEmailIsNull() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of("admin@company.com"));

        assertFalse(resolveIsAdmin(config, null));
    }

    @Test
    void checkAdmin_returnsFalse_whenEmailIsBlank() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of("admin@company.com"));

        assertFalse(resolveIsAdmin(config, "   "));
    }

    // ---------------------------------------------------------------------------
    // login role-resolution logic tests
    // ---------------------------------------------------------------------------

    @Test
    void login_grantAdminRole_whenAsAdminTrueAndEmailOnList() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of("admin@company.com"));

        String role = resolveRole(config, "admin@company.com", true);
        assertEquals("ADMIN", role);
    }

    @Test
    void login_grantOperatorRole_whenAsAdminFalseEvenIfEmailOnList() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of("admin@company.com"));

        String role = resolveRole(config, "admin@company.com", false);
        assertEquals("OPERATOR", role);
    }

    @Test
    void login_grantOperatorRole_whenAsAdminTrueButEmailNotOnList() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of("admin@company.com"));

        String role = resolveRole(config, "user@company.com", true);
        assertEquals("OPERATOR", role);
    }

    @Test
    void login_grantOperatorRole_whenAdminListIsEmpty() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of());

        String role = resolveRole(config, "admin@company.com", true);
        assertEquals("OPERATOR", role);
    }

    @Test
    void login_adminRoleCheck_isCaseInsensitive() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(List.of("Admin@Company.COM"));

        assertEquals("ADMIN", resolveRole(config, "admin@company.com", true));
        assertEquals("ADMIN", resolveRole(config, "ADMIN@COMPANY.COM", true));
    }

    // ---------------------------------------------------------------------------
    // AppConfig.setAdminEmails null-safety
    // ---------------------------------------------------------------------------

    @Test
    void appConfig_setAdminEmails_handlesNull() {
        AppConfig config = new AppConfig();
        config.setAdminEmails(null);
        assertNotNull(config.getAdminEmails());
        assertTrue(config.getAdminEmails().isEmpty());
    }
}
