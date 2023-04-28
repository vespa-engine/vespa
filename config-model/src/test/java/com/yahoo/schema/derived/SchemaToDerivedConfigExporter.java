// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.config.DocumenttypesConfig;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Utility class for deriving backend configs for a given schema and exporting them to a set of files.
 *
 * This can be used by e.g C++ unit tests to generate configs that are checked in as part of the test.
 * Place the schema file in a directory $HOME/mydir and run the following in $HOME/git/vespa:
 *   mvn test -Dtest=SchemaToDerivedConfigExporter -Dschema.exporter.path=$HOME/mydir -pl config-model
 *
 * Note that the directory path must be absolute.
 *
 * @author geirst
 */
public class SchemaToDerivedConfigExporter {

    private void exportConfig(String dirPath, DerivedConfiguration config, ApplicationBuilder builder) throws IOException {
        config.export(dirPath);
        DerivedConfiguration.exportDocuments(new DocumentTypes().produce(builder.getModel(), new DocumenttypesConfig.Builder()), dirPath);
    }

    @Test
    public void deriveAndExport() throws IOException, ParseException {
        var dirPath = System.getProperty("schema.exporter.path");
        // The test is marked as skipped if this is not true.
        assumeTrue(dirPath != null);

        var props = new TestProperties();
        var logger = new TestableDeployLogger();
        var builder = ApplicationBuilder.createFromDirectory(dirPath, new MockFileRegistry(), logger, props);
        var derived = new DerivedConfiguration(builder.getSchema(null),
                new DeployState.Builder().properties(props)
                        .deployLogger(logger)
                        .rankProfileRegistry(builder.getRankProfileRegistry())
                        .queryProfiles(builder.getQueryProfileRegistry())
                        .build());
        exportConfig(dirPath, derived, builder);
    }
}
