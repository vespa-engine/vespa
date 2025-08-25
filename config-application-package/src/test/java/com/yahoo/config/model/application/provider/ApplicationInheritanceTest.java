package com.yahoo.config.model.application.provider;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class ApplicationInheritanceTest {

    private final String resourcesDir = "src/test/resources/";
    private final String inheritableAppDir = resourcesDir + "inheritable-apps/";

    @Test
    public void testInheritance() throws IOException {
        var textSearchApp = FilesApplicationPackage.fromDir(new File("src/test/resources/inheritable-apps/internal/text-search"),
                                                            Map.of());

        Map<String, FilesApplicationPackage> inheritableApps = new HashMap<>();
        inheritableApps.put("internal.text-search", textSearchApp);
        var app1 = FilesApplicationPackage.fromDir(new File("src/test/resources/inheriting-app1"),
                                                             inheritableApps);
        assertEquals(IOUtils.readFile(new File(inheritableAppDir + "internal/text-search/services.xml")),
                     IOUtils.readAll(app1.getServices()));
        assertEquals(1, app1.getSchemas().size());
        assertEquals(IOUtils.readFile(new File(inheritableAppDir + "internal/text-search/schemas/doc.sd")),
                     IOUtils.readAll(app1.getSchemas().iterator().next().getReader()));
    }

    @Test
    public void testNonExistingInheritance() {
        try {
            var textSearchApp = FilesApplicationPackage.fromDir(new File("src/test/resources/inheritable-apps/internal/text-search"),
                                                                Map.of());
            Map<String, FilesApplicationPackage> inheritableApps = new HashMap<>();
            inheritableApps.put("internal.not-text-search", textSearchApp);
            FilesApplicationPackage.fromDir(new File("src/test/resources/inheriting-app1"), inheritableApps);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Inherited application 'internal.text-search' does not exist. Available applications: [internal.not-text-search]",
                         Exceptions.toMessageString(e));
        }
    }
}
