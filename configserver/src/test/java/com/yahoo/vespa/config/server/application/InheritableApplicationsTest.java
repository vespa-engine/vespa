package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.yahoo.test.JunitCompat.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class InheritableApplicationsTest {

    @Test
    void testInternalInheritableApps() {
        var inheritable = new InheritableApplications.DirectoryImporter().importFrom("src/main/resources/inheritable-apps");
        Map<String, FilesApplicationPackage> map = inheritable.toMap();
        assertEquals(1, map.size());
        assertTrue(map.containsKey("internal.text-search"));
        assertNotNull(map.get("internal.text-search"));
        // No need to test the imported content here
    }

}
