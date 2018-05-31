// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.google.inject.Module;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.Container;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertThat;

/**
 * @author Einar M R Rosenvinge
 */
public class StandaloneContainerActivatorTest {

    private static String getJdiscXml(String contents) throws ParserConfigurationException, IOException, SAXException {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<services>\n" +
                "  <jdisc version=\"1.0\" jetty=\"true\">\n" +
                contents +
                "  </jdisc>\n" +
                "</services>";
    }

    private static void writeApplicationPackage(String servicesXml, Path tmpDir) throws IOException {
        FileWriter fw = new FileWriter(tmpDir.resolve("services.xml").toFile(), false);
        fw.write(servicesXml);
        fw.close();
    }

    @Test
    public void requireThatPortsCanBeFoundBasic() throws IOException, ParserConfigurationException, SAXException {
        final Path applicationDir = Files.createTempDirectory("application");
        try {
            writeApplicationPackage(getJdiscXml(""), applicationDir);
            StandaloneContainerActivator activator = new StandaloneContainerActivator();
            Container container = StandaloneContainerActivator.getContainer(newAppDirBinding(applicationDir));
            List<Integer> ports = getPorts(activator, container);
            assertThat(ports, is(singletonList(Defaults.getDefaults().vespaWebServicePort())));
        } finally {
            IOUtils.recursiveDeleteDir(applicationDir.toFile());
        }
    }

    private static List<Integer> getPorts(StandaloneContainerActivator activator, Container container) {
        return StandaloneContainerActivator.getConnectorConfigs(container).stream().
                map(ConnectorConfig::listenPort).
                collect(toList());
    }

    @Test
    public void requireThatPortsCanBeFoundNoHttp() throws IOException, ParserConfigurationException, SAXException {
        final Path applicationDir = Files.createTempDirectory("application");
        try {
            writeApplicationPackage(getJdiscXml("<http/>"), applicationDir);
            StandaloneContainerActivator activator = new StandaloneContainerActivator();
            Container container = StandaloneContainerActivator.getContainer(newAppDirBinding(applicationDir));
            List<Integer> ports = getPorts(activator, container);
            assertThat(ports, empty());
        } finally {
            IOUtils.recursiveDeleteDir(applicationDir.toFile());
        }
    }

    @Test
    public void requireThatPortsCanBeFoundHttpThreeServers() throws IOException, ParserConfigurationException, SAXException {
        final Path applicationDir = Files.createTempDirectory("application");
        try {
            final String contents =
                    "<http>\n" +
                    "  <server id=\"a\" port=\"123\"/>\n" +
                    "  <server id=\"b\" port=\"456\"/>\n" +
                    "  <server id=\"c\" port=\"789\"/>\n" +
                    "</http>\n";
            writeApplicationPackage(getJdiscXml(contents), applicationDir);
            StandaloneContainerActivator activator = new StandaloneContainerActivator();
            Container container = StandaloneContainerActivator.getContainer(newAppDirBinding(applicationDir));
            List<Integer> ports = getPorts(activator, container);
            assertThat(ports, is(asList(123, 456, 789)));
        } finally {
            IOUtils.recursiveDeleteDir(applicationDir.toFile());
        }
    }

    private static Module newAppDirBinding(final Path applicationDir) {
        return binder -> binder.bind(Path.class)
                .annotatedWith(StandaloneContainerApplication.APPLICATION_PATH_NAME)
                .toInstance(applicationDir);
    }

}
