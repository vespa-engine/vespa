package com.yahoo.container.plugin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static com.yahoo.container.plugin.BundleTest.findBundleJar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for a USER bundle that imports non-PublicApi packages.
 *
 * @author gjoranv
 */
public class NonPublicApiDetectionTest {

    private static Attributes mainAttributes;

    @BeforeAll
    public static void setup() {
        try {
            File componentJar = findBundleJar("non-public-api-usage");
            mainAttributes = new JarFile(componentJar).getManifest().getMainAttributes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void usage_of_non_publicApi_packages_is_detected() {
        var nonPublicApiAttribute = mainAttributes.getValue("X-JDisc-Non-PublicApi-Import-Package");
        assertNotNull(nonPublicApiAttribute);
        var usedNonPublicApi = Arrays.stream(nonPublicApiAttribute.split(",")).collect(Collectors.toSet());

        assertEquals(2, usedNonPublicApi.size());
        assertTrue(usedNonPublicApi.contains("ai.vespa.lib.non_public"));
        assertTrue(usedNonPublicApi.contains("com.yahoo.lib.non_public"));
    }

    @Test
    void vespa_version_is_added_to_manifest() {
        var vespaVersionAttribute = mainAttributes.getValue("X-JDisc-Vespa-Build-Version");
        assertNotNull(vespaVersionAttribute);
        assertNotEquals("", vespaVersionAttribute);
    }
}
