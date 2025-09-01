package com.yahoo.config.model.application.provider;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class ApplicationInheritanceTest {

    private static final String resourcesDir = "src/test/resources/";
    private static final String inheritableAppDir = resourcesDir + "inheritable-apps/";

    @Test
    public void testInheritance() throws IOException {
        var tester = new InheritanceTester();
        var app1 = tester.app("inheriting-app1");
        assertEquals(IOUtils.readFile(new File(inheritableAppDir + "internal/text-search/services.xml")),
                     IOUtils.readAll(app1.getServices()));
        assertEquals(1, app1.getSchemas().size());
        assertEquals(IOUtils.readFile(new File(inheritableAppDir + "internal/text-search/schemas/doc.sd")),
                     IOUtils.readAll(app1.getSchemas().iterator().next().getReader()));
    }

    @Test
    public void testInheritanceWithOverride() throws IOException { // TODO: Allow and enforce inheritance of parent doc schema
        var tester = new InheritanceTester();
        var app2 = tester.app("inheriting-app2");
        assertEquals(IOUtils.readFile(new File(inheritableAppDir + "internal/text-search/services.xml")),
                     IOUtils.readAll(app2.getServices()));
        assertEquals(1, app2.getSchemas().size());
        assertEquals(IOUtils.readFile(new File(resourcesDir + "inheriting-app2/schemas/doc.sd")),
                     IOUtils.readAll(app2.getSchemas().iterator().next().getReader()));
    }

    @Test
    public void testMultipleInheritance() throws IOException {
        var tester = new InheritanceTester();
        var app3 = tester.app("inheriting-app3");
        assertEquals(IOUtils.readFile(new File(inheritableAppDir + "internal/text-search/services.xml")),
                     IOUtils.readAll(app3.getServices()));
        assertEquals(3, app3.getSchemas().size());
        Iterator<NamedReader> schemas = app3.getSchemas().iterator();
        assertEquals(IOUtils.readFile(new File(resourcesDir + "inheriting-app3/schemas/additional.sd")),
                     IOUtils.readAll(schemas.next().getReader()));
        assertEquals(IOUtils.readFile(new File(inheritableAppDir + "internal/text-search/schemas/doc.sd")),
                     IOUtils.readAll(schemas.next().getReader()));
        assertEquals(IOUtils.readFile(new File(inheritableAppDir + "internal/recommendation/schemas/product.sd")),
                     IOUtils.readAll(schemas.next().getReader()));
    }

    @Test
    public void testNonExistingInheritance() {
        try {
            var textSearchApp = FilesApplicationPackage.fromDir(new File(inheritableAppDir + "internal/text-search"),
                                                                Map.of());
            Map<String, FilesApplicationPackage> inheritableApps = Map.of("internal.not-text-search", textSearchApp);
            var tester = new InheritanceTester(inheritableApps);
            tester.app("inheriting-app1");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Inherited application 'internal.text-search' does not exist. Available applications: [internal.not-text-search]",
                         Exceptions.toMessageString(e));
        }
    }

    private class InheritanceTester {

        private final Map<String, FilesApplicationPackage> inheritableApps;

        public InheritanceTester() {
            this(createAllInheritableApps());
        }

        public InheritanceTester(Map<String, FilesApplicationPackage> inheritableApps) {
            this.inheritableApps = inheritableApps;
        }

        private FilesApplicationPackage app(String appDir) {
            return FilesApplicationPackage.fromDir(new File(resourcesDir + appDir), inheritableApps);
        }

        private static Map<String, FilesApplicationPackage> createAllInheritableApps() {
            Map<String, FilesApplicationPackage> apps = new HashMap<>();
            var textSearchApp = FilesApplicationPackage.fromDir(new File(inheritableAppDir + "internal/text-search"),
                                                                Map.of());
            var recommendationApp = FilesApplicationPackage.fromDir(new File(inheritableAppDir + "internal/recommendation"),
                                                                    Map.of());
            apps.put("internal.recommendation", recommendationApp);
            apps.put("internal.text-search", textSearchApp);
            return apps;
        }

    }
}
