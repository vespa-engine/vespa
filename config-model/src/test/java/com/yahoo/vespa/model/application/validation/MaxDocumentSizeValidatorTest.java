// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class MaxDocumentSizeValidatorTest {

    @Test
    void warns_when_document_api_exceeds_smallest_content_cluster_max() {
        var logger = new CapturingLogger();
        buildModel(logger, "200Mb", "100Mb");
        assertTrue(logger.warnings.stream().anyMatch(m -> m.contains("is 200 MiB") && m.contains("100 MiB")),
                () -> "Expected mismatch warning, got: " + logger.warnings);
    }

    @Test
    void no_warning_when_document_api_at_or_below_content_max() {
        var logger = new CapturingLogger();
        buildModel(logger, "100Mb", "100Mb");
        assertFalse(logger.warnings.stream().anyMatch(m -> m.contains("max-document-size in <document-api>")),
                () -> "Expected no mismatch warning, got: " + logger.warnings);
    }

    @Test
    void no_warning_when_content_cluster_has_no_explicit_max() {
        var logger = new CapturingLogger();
        buildModel(logger, "200Mb", null);
        assertFalse(logger.warnings.stream().anyMatch(m -> m.contains("max-document-size in <document-api>")),
                () -> "Expected no mismatch warning when content cluster has no explicit max, got: " + logger.warnings);
    }

    private static VespaModel buildModel(DeployLogger logger, String containerMax, String contentMax) {
        String tuning = (contentMax == null) ? "" :
                "      <tuning><max-document-size>" + contentMax + "</max-document-size></tuning>";
        String services =
                "<?xml version='1.0' encoding='UTF-8' ?>" +
                "<services version='1.0'>" +
                "  <admin version='2.0'><adminserver hostalias='node1'/></admin>" +
                "  <container id='default' version='1.0'>" +
                "    <document-api>" +
                "      <max-document-size>" + containerMax + "</max-document-size>" +
                "    </document-api>" +
                "    <nodes><node hostalias='node1'/></nodes>" +
                "  </container>" +
                "  <content id='storage' version='1.0'>" +
                "    <redundancy>1</redundancy>" +
                "    <documents><document mode='index' type='type1'/></documents>" +
                "    <group><node distribution-key='0' hostalias='node1'/></group>" +
                tuning +
                "  </content>" +
                "</services>";
        var sds = ApplicationPackageUtils.generateSchemas("type1");
        var pkg = new MockApplicationPackage.Builder().withServices(services).withSchemas(sds).build();
        var deployStateBuilder = new DeployState.Builder()
                .applicationPackage(pkg)
                .properties(new TestProperties())
                .deployLogger(logger);
        return new VespaModelCreatorWithMockPkg(pkg).create(deployStateBuilder);
    }

    private static class CapturingLogger implements DeployLogger {
        final List<String> warnings = new ArrayList<>();
        @Override public void log(Level level, String message) {
            if (level.intValue() >= Level.WARNING.intValue()) warnings.add(message);
        }
    }
}
