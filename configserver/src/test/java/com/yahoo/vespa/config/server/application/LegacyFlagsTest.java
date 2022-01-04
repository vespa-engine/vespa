// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.vespa.config.server.modelfactory.LegacyFlags;
import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.component.Version;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.ModelStub;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.document.config.DocumentmanagerConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author arnej
 */
public class LegacyFlagsTest {

    @Test
    public void testThatLegacyOverridesWork() throws Exception {
        File testApp = new File("src/test/apps/legacy-flag");
        var appPkg = FilesApplicationPackage.fromFile(testApp);
        var flag = Flags.USE_V8_GEO_POSITIONS.bindTo(LegacyFlags.from(appPkg, new InMemoryFlagSource()));
        assertTrue(flag.value());
        /* rest here tests that having a "legacy" XML tag doesn't break other things, but without actually using it: */
        VespaModel model = new VespaModel(appPkg);
        ApplicationId applicationId = new ApplicationId.Builder().tenant("foo").applicationName("foo").build();
        ServerCache cache = new ServerCache();
        Application app = new Application(model, cache, 1L, new Version(1, 2, 3),
                                  new MetricUpdater(Metrics.createTestMetrics(), Metrics.createDimensions(applicationId)), applicationId);
        assertNotNull(app.getModel());
        /*
        // Note: no feature flags active with this code path
        ConfigDefinitionKey cdk = new ConfigDefinitionKey(DocumentmanagerConfig.CONFIG_DEF_NAME, DocumentmanagerConfig.CONFIG_DEF_NAMESPACE);
        ConfigDefinition cdd = new ConfigDefinition(cdk.getName(), DocumentmanagerConfig.CONFIG_DEF_SCHEMA);
        var cfgBuilder = app.getModel().getConfigInstance(new ConfigKey<>(cdk.getName(), "client", cdk.getNamespace()), cdd);
        assertTrue(cfgBuilder instanceof DocumentmanagerConfig.Builder);
        var cfg = ((DocumentmanagerConfig.Builder)cfgBuilder).build();
        // no effect from legacy override seen here:
        System.out.println("CFG: "+cfg);
        */
    }

}
