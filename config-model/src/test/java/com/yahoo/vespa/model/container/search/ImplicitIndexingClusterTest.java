// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ImplicitIndexingClusterTest {

    @Test
    void existing_jdisc_is_used_as_indexing_cluster_when_multitenant() {
        final String servicesXml = "<services version=\"1.0\">\n" + //
                "  <container version=\"1.0\" id=\"jdisc\">\n" + //
                "    <search />\n" + //
                "    <nodes count=\"1\" />\n" + //
                "    <http>\n" + //
                "      <server id=\"bar\" port=\"" + Defaults.getDefaults().vespaWebServicePort() + "\" />\n" + //
                "    </http>\n" + //
                "  </container>\n" + //
                "  <content id=\"music\" version=\"1.0\">\n" + //
                "    <redundancy>1</redundancy>\n" + //
                "    <documents>\n" + //
                "      <document type=\"music\" mode=\"index\" />\n" + //
                "    </documents>\n" + //
                "    <nodes count=\"1\" />\n" + //
                "  </content>\n" + //
                "</services>\n";


        VespaModel vespaModel = buildMultiTenantVespaModel(servicesXml);
        ContainerCluster<ApplicationContainer> jdisc = vespaModel.getContainerClusters().get("jdisc");
        assertNotNull(jdisc.getDocproc(), "Docproc not added to jdisc");
        assertNotNull(jdisc.getDocprocChains().allChains().getComponent("indexing"), "Indexing chain not added to jdisc");
    }

    @Test
    void warning_is_logged_when_no_explicit_document_processing_cluster() {
        final String servicesXml = "<services version=\"1.0\">\n" +
                "  <container version=\"1.0\" id=\"jdisc\">\n" +
                "    <search />\n" +
                "    <nodes count=\"1\" />\n" +
                "    <http>\n" +
                "      <server id=\"bar\" port=\"" + Defaults.getDefaults().vespaWebServicePort() + "\" />\n" +
                "    </http>\n" +
                "  </container>\n" +
                "  <container version=\"1.0\" id=\"jdisc2\">\n" +
                "    <search />\n" +
                "    <nodes count=\"1\" />\n" +
                "  </container>\n" +
                "  <content id=\"music\" version=\"1.0\">\n" +
                "    <redundancy>1</redundancy>\n" +
                "    <documents>\n" +
                "      <document type=\"music\" mode=\"index\" />\n" +
                "    </documents>\n" +
                "    <nodes count=\"1\" />\n" +
                "  </content>\n" +
                "</services>\n";

        var warnings = new ArrayList<String>();
        ModelContext.Properties properties = new TestProperties().setMultitenant(true).setHostedVespa(true);
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                .properties(properties)
                .endpoints(Set.of(new ContainerEndpoint("jdisc", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com")),
                                  new ContainerEndpoint("jdisc2", ApplicationClusterEndpoint.Scope.zone, List.of("default2.example.com"))))
                .modelHostProvisioner(new InMemoryProvisioner(6, false))
                .deployLogger((level, message) -> { if (level == Level.WARNING) warnings.add(message); });

        new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                .withServices("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + servicesXml)
                .withSchema(MockApplicationPackage.MUSIC_SCHEMA)
                .build())
                .create(deployStateBuilder);

        assertTrue(warnings.stream().anyMatch(w -> w.contains("music") && w.contains("document-processing")),
                   "Expected warning about missing document-processing cluster, got: " + warnings);
    }

    @Test
    void no_warning_is_logged_when_no_explicit_document_processing_cluster_and_one_container_cluster() {
        final String servicesXml = """
                <services version="1.0">
                  <container version="1.0" id="jdisc">
                    <document-processing />
                    <nodes count="1" />
                  </container>
                  <content id="music" version="1.0">
                    <redundancy>1</redundancy>
                    <documents>
                      <document type="music" mode="index" />
                    </documents>
                    <nodes count="1" />
                  </content>
                </services>
                """;

        var warnings = new ArrayList<String>();
        ModelContext.Properties properties = new TestProperties().setMultitenant(true).setHostedVespa(true);
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                .properties(properties)
                .endpoints(Set.of(new ContainerEndpoint("jdisc", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))))
                .modelHostProvisioner(new InMemoryProvisioner(6, false))
                .deployLogger((level, message) -> { if (level == Level.WARNING) warnings.add(message); });

        new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                                                 .withServices("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + servicesXml)
                                                 .withSchema(MockApplicationPackage.MUSIC_SCHEMA)
                                                 .build())
                .create(deployStateBuilder);

        assertTrue(warnings.stream().noneMatch(w -> w.contains("document-processing")),
                   "Expected no warning about document-processing cluster, got: " + warnings);
    }

    @Test
    void no_warning_when_explicit_document_processing_cluster_is_configured() {
        final String servicesXml = "<services version=\"1.0\">\n" +
                "  <container version=\"1.0\" id=\"jdisc\">\n" +
                "    <document-processing />\n" +
                "    <nodes count=\"1\" />\n" +
                "    <http>\n" +
                "      <server id=\"bar\" port=\"" + Defaults.getDefaults().vespaWebServicePort() + "\" />\n" +
                "    </http>\n" +
                "  </container>\n" +
                "  <content id=\"music\" version=\"1.0\">\n" +
                "    <redundancy>1</redundancy>\n" +
                "    <documents>\n" +
                "      <document type=\"music\" mode=\"index\" />\n" +
                "      <document-processing cluster=\"jdisc\" />\n" +
                "    </documents>\n" +
                "    <nodes count=\"1\" />\n" +
                "  </content>\n" +
                "</services>\n";

        var warnings = new ArrayList<String>();
        ModelContext.Properties properties = new TestProperties().setMultitenant(true).setHostedVespa(true);
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                .properties(properties)
                .endpoints(Set.of(new ContainerEndpoint("jdisc", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))))
                .modelHostProvisioner(new InMemoryProvisioner(6, false))
                .deployLogger((level, message) -> { if (level == Level.WARNING) warnings.add(message); });

        new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                .withServices("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + servicesXml)
                .withSchema(MockApplicationPackage.MUSIC_SCHEMA)
                .build())
                .create(deployStateBuilder);

        assertTrue(warnings.stream().noneMatch(w -> w.contains("document-processing")),
                   "Expected no warning about document-processing cluster, got: " + warnings);
    }

    private static VespaModel buildMultiTenantVespaModel(String servicesXml) {
        ModelContext.Properties properties = new TestProperties().setMultitenant(true).setHostedVespa(true);
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                .properties(properties)
                .endpoints(Set.of(new ContainerEndpoint("jdisc", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))))
                .modelHostProvisioner(new InMemoryProvisioner(6, false));

        return new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                .withServices("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + servicesXml)
                .withSchema(MockApplicationPackage.MUSIC_SCHEMA)
                .build())
                .create(deployStateBuilder);
    }
}
