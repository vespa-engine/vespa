package com.yahoo.osgi.maven;

import com.yahoo.osgi.maven.ProjectBundleClassPaths.BundleClasspathMapping;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class ProjectBundleClassPathsTest {

    @Test
    public void bundle_classpaths_serializes_correctly_to_json() throws IOException {
        ProjectBundleClassPaths projectBundleClassPaths =
                new ProjectBundleClassPaths(
                        new BundleClasspathMapping("main-bundle-name", asList("classpath-elem-0-1", "classpath-elem-0-2")),
                        asList(
                                new BundleClasspathMapping(
                                        "main-bundle-dep1",
                                        asList("classpath-elem-1-1", "classpath-elem-1-2")),
                                new BundleClasspathMapping(
                                        "main-bundle-dep2",
                                        asList("classpath-elem-2-1", "classpath-elem-2-2"))));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProjectBundleClassPaths.save(out, projectBundleClassPaths);
        ProjectBundleClassPaths deserialized = ProjectBundleClassPaths.load(out.toByteArray());
        assertEquals(projectBundleClassPaths, deserialized);
    }

}
