// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import com.yahoo.config.application.XmlPreProcessor;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.zone.ZoneId;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Computes the effective services.xml for the indicated zone.
 *
 * @author jonmv
 * @author olaa
 */
@Mojo(name = "effectiveServices")
public class EffectiveServicesMojo extends AbstractVespaDeploymentMojo {

    @Parameter(property = "servicesFile", defaultValue = "src/main/application/services.xml")
    private String servicesFile;

    @Parameter(property = "outputDirectory", defaultValue = "target")
    private String outputDirectory;

    @Override
    protected void doExecute() throws Exception {
        File services = new File(servicesFile);
        if ( ! services.isFile())
            throw new IllegalArgumentException(servicesFile + " does not exist. Set correct path with -DservicesFile=<path to services.xml>");

        ZoneId zone = zoneOf(environment, region);
        Path output = Paths.get(outputDirectory).resolve("services-" + zone.environment().value() + "-" + zone.region().value() + ".xml");
        Files.write(output, effectiveServices(services, zone, InstanceName.from(instance), Tags.fromString(tags)).getBytes(StandardCharsets.UTF_8));
        getLog().info("Effective services for " + zone + " written to " + output);
    }

    static String effectiveServices(File servicesFile, ZoneId zone, InstanceName instance, Tags tags) throws Exception {
        Document processedServicesXml = new XmlPreProcessor(servicesFile.getParentFile(),
                                                            servicesFile,
                                                            instance,
                                                            zone.environment(),
                                                            zone.region(),
                                                            tags)
                .run();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        Writer writer = new StringWriter();
        transformer.transform(new DOMSource(processedServicesXml), new StreamResult(writer));
        return writer.toString().replaceAll("\n(\\s*\n)+","\n");
    }

}

