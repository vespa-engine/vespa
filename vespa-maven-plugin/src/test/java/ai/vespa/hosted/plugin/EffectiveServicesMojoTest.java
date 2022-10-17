// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.zone.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static ai.vespa.hosted.plugin.EffectiveServicesMojo.effectiveServices;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 * @author olaa
 */
@DisplayName("Effective services are correctly generated")
class EffectiveServicesMojoTest {

    private final File servicesFile = new File("src/test/resources/effective-services/services.xml");

    @Test
    @DisplayName("when zone matches environment-only directive")
    void devServices() throws Exception {
        assertEquals(Files.readString(Paths.get("src/test/resources/effective-services/dev.xml")),
                     effectiveServices(servicesFile, ZoneId.from("dev", "us-east-3"), InstanceName.defaultName(), Tags.empty()));
    }

    @Test
    @DisplayName("when zone matches region-and-environment directive")
    void prodUsEast3() throws Exception {
        assertEquals(Files.readString(Paths.get("src/test/resources/effective-services/prod_us-east-3.xml")),
                     effectiveServices(servicesFile, ZoneId.from("prod", "us-east-3"), InstanceName.defaultName(), Tags.empty()));
    }

    @Test
    @DisplayName("when zone doesn't match any directives")
    void prodUsWest1Services() throws Exception {
        assertEquals(Files.readString(Paths.get("src/test/resources/effective-services/prod_us-west-1.xml")),
                     effectiveServices(servicesFile, ZoneId.from("prod", "us-west-1"), InstanceName.defaultName(), Tags.empty()));
    }

}
