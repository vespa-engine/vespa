// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.jersey;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.JDiscTest;
import com.yahoo.application.container.jersey.resources.TestResource;
import com.yahoo.application.container.jersey.resources.nestedpackage1.NestedTestResource1;
import com.yahoo.application.container.jersey.resources.nestedpackage2.NestedTestResource2;
import com.yahoo.container.test.jars.jersey.resources.TestResourceBase;
import com.yahoo.osgi.maven.ProjectBundleClassPaths;
import com.yahoo.osgi.maven.ProjectBundleClassPaths.BundleClasspathMapping;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import javax.ws.rs.core.UriBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author tonytv
 * @author Olli Virtanen
 */
public class JerseyTest {
    private final Path testJar = Paths.get("target/test-jars/jersey-resources.jar");
    private final String testClassesDirectory = "target/test-classes";
    private final String bundleSymbolicName = "myBundle";

    private static final Set<Class<? extends TestResourceBase>> classPathResources;
    private static final Set<Class<? extends TestResourceBase>> jarFileResources;

    static {
        classPathResources = new HashSet<>();
        classPathResources.add(TestResource.class);
        classPathResources.add(NestedTestResource1.class);
        classPathResources.add(NestedTestResource2.class);

        jarFileResources = new HashSet<>();
        jarFileResources.add(com.yahoo.container.test.jars.jersey.resources.TestResource.class);
        jarFileResources.add(com.yahoo.container.test.jars.jersey.resources.nestedpackage1.NestedTestResource1.class);
        jarFileResources.add(com.yahoo.container.test.jars.jersey.resources.nestedpackage2.NestedTestResource2.class);
    }

    @Test
    public void jersey_resources_on_classpath_can_be_invoked_from_application() throws Exception {
        saveMainBundleClassPathMappings(testClassesDirectory);

        with_jersey_resources(emptyList(), httpGetter -> assertResourcesResponds(classPathResources, httpGetter));
    }

    @Test
    public void jersey_resources_in_provided_dependencies_can_be_invoked_from_application() throws Exception {
        BundleClasspathMapping providedDependency = new BundleClasspathMapping(bundleSymbolicName,
                Arrays.asList(testClassesDirectory));

        save(new ProjectBundleClassPaths(new BundleClasspathMapping("main", emptyList()),
                Arrays.asList(providedDependency)));
        with_jersey_resources(emptyList(), httpGetter -> assertResourcesResponds(classPathResources, httpGetter));
    }

    @Test
    public void jersey_resource_on_classpath_can_be_filtered_using_packages() throws Exception {
        saveMainBundleClassPathMappings(testClassesDirectory);

        with_jersey_resources(Arrays.asList("com.yahoo.application.container.jersey.resources",
                "com.yahoo.application.container.jersey.resources.nestedpackage1"), httpGetter -> {
            Class<NestedTestResource2> nestedResource2 = NestedTestResource2.class;
            assertDoesNotRespond(nestedResource2, httpGetter);
            assertResourcesResponds(copySetExcept(classPathResources, nestedResource2), httpGetter);
        });
    }

    @Test
    public void jersey_resource_in_jar_can_be_invoked_from_application() throws Exception {
        saveMainBundleJarClassPathMappings(testJar);

        with_jersey_resources(emptyList(), httpGetter -> assertResourcesResponds(jarFileResources, httpGetter));
    }

    @Test
    public void jersey_resource_in_jar_can_be_filtered_using_packages() throws Exception {
        saveMainBundleJarClassPathMappings(testJar);

        with_jersey_resources(Arrays.asList("com.yahoo.container.test.jars.jersey.resources",
                "com.yahoo.container.test.jars.jersey.resources.nestedpackage1"), httpGetter -> {
            Class<com.yahoo.container.test.jars.jersey.resources.nestedpackage2.NestedTestResource2> nestedResource2 = com.yahoo.container.test.jars.jersey.resources.nestedpackage2.NestedTestResource2.class;

            assertDoesNotRespond(nestedResource2, httpGetter);
            assertResourcesResponds(copySetExcept(jarFileResources, nestedResource2), httpGetter);
        });
    }

    private static <T> Set<T> copySetExcept(Set<T> in, T except) {
        Set<T> ret = new HashSet<>(in);
        ret.remove(except);
        return ret;
    }

    private interface ThrowingConsumer<T> {
        public void accept(T arg) throws Exception;
    }

    private interface HttpGetter {
        public HttpResponse get(String path) throws Exception;
    }

    @SuppressWarnings("try") // jdisc unused inside try-with-resources
    private void with_jersey_resources(List<String> packagesToScan, ThrowingConsumer<HttpGetter> f) throws Exception {
        StringBuffer packageElements = new StringBuffer();
        for (String p : packagesToScan) {
            packageElements.append("<package>");
            packageElements.append(p);
            packageElements.append("</package>");
        }

        try (JDisc jdisc = JDisc.fromServicesXml(
                "<services>" + //
                        "<jdisc version=\"1.0\" id=\"default\" jetty=\"true\">" + //
                        "<rest-api path=\"rest-api\" jersey2=\"true\">" + //
                        "<components bundle=\"" + bundleSymbolicName + "\">" + //
                        packageElements + //
                        "</components>" + //
                        "</rest-api>" + //
                        "<http>" + //
                        "<server id=\"mainServer\" port=\"0\" />" + //
                        "</http>" + //
                        "</jdisc>" + //
                        "</services>", //
                Networking.enable)) {
            final int port = JDiscTest.getListenPort();
            f.accept(path -> {
                String p = path.startsWith("/") ? path.substring(1) : path;
                CloseableHttpClient client = HttpClientBuilder.create().build();
                return client.execute(new HttpGet("http://localhost:" + port + "/rest-api/" + p));
            });
        }
    }

    public void assertResourcesResponds(Collection<Class<? extends TestResourceBase>> resourceClasses,
                                        HttpGetter httpGetter) throws Exception {
        for (Class<? extends TestResourceBase> resource : resourceClasses) {
            HttpResponse response = httpGetter.get(path(resource));
            assertThat("Failed sending response to " + resource, response.getStatusLine().getStatusCode(), is(200));

            String content = IOUtils.toString(response.getEntity().getContent());
            assertThat(content, is(TestResourceBase.content(resource)));
        }
    }

    public void assertDoesNotRespond(Class<? extends TestResourceBase> resourceClass, HttpGetter httpGetter)
            throws Exception {
        HttpResponse response = httpGetter.get(path(resourceClass));
        assertThat(response.getStatusLine().getStatusCode(), is(404));
        EntityUtils.consume(response.getEntity());
    }

    public void saveMainBundleJarClassPathMappings(Path jarFile) throws Exception {
        assertTrue("Couldn't find file " + jarFile + ", please remember to run mvn process-test-resources first.",
                Files.isRegularFile(jarFile));
        saveMainBundleClassPathMappings(jarFile.toAbsolutePath().toString());
    }

    public void saveMainBundleClassPathMappings(String classPathElement) throws Exception {
        BundleClasspathMapping mainBundleClassPathMappings = new BundleClasspathMapping(bundleSymbolicName,
                Arrays.asList(classPathElement));
        save(new ProjectBundleClassPaths(mainBundleClassPathMappings, emptyList()));
    }

    public void save(ProjectBundleClassPaths projectBundleClassPaths) throws Exception {
        Path path = Paths.get(testClassesDirectory).resolve(ProjectBundleClassPaths.CLASSPATH_MAPPINGS_FILENAME);
        ProjectBundleClassPaths.save(path, projectBundleClassPaths);
    }

    public String path(Class<?> resourceClass) {
        return UriBuilder.fromResource(resourceClass).build().toString();
    }
}
