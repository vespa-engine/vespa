// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.api.ValidationParameters.CheckRouting;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class PagedAttributesRemoteStorageTest {

    @Test
    void logs_warning_when_using_paged_attributes_and_remote_storage() throws IOException, SAXException {
        var logger = new MyLogger();
        createModelAndValidate("""
                    search test {
                      document test {
                        field year type int {
                         indexing: summary | attribute
                         attribute: paged
                        }
                        field name type string {
                         indexing: summary | attribute
                         attribute: paged
                        }
                        field rating type int {
                         indexing: summary | attribute
                        }
                      }
                    }
                    """,
                logger);
        assertTrue(logger.message.toString().contains(
                "Cluster 'mycluster' has nodes with remote storage and fields with paged attributes." +
                        " This might lead to performance issues when doing I/O." +
                        " Consider using storage-type='local' or removing 'paged' setting for these fields: 'year', 'name'"));
    }

    private static class MyLogger implements DeployLogger {
        public StringBuilder message = new StringBuilder();
        @Override
        public void log(Level level, String message) {
            this.message.append(message);
        }
    }

    private static void createModelAndValidate(String schema, DeployLogger logger) throws IOException, SAXException {
        var deployState = createDeployState(servicesXml(), schema, logger);
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        var validationParameters = new ValidationParameters(CheckRouting.FALSE);
        new Validation().validate(model, validationParameters, deployState);
    }

    private static DeployState createDeployState(String servicesXml, String schema, DeployLogger logger) {
        var app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withSchema(schema)
                .build();
        var builder = new DeployState.Builder()
                .applicationPackage(app)
                .endpoints(Set.of(new ContainerEndpoint("mycluster.indexing", ApplicationClusterEndpoint.Scope.zone, List.of("c1.example.com", "c1-alias.example.com"))))
                .properties(new TestProperties().setHostedVespa(true).setMultitenant(true))
                .modelHostProvisioner(new InMemoryProvisioner(8, new NodeResources(2, 8, 100, 10, fast, remote), true))
                .deployLogger(logger);
        return builder.build();
    }

    private static String servicesXml() {
        return joinLines("<services version='1.0'>",
                new ContentClusterBuilder().groupXml("""
              <nodes count="2">
                <resources vcpu="2" memory="8Gb" disk="99Gb" storage-type="remote"/>
              </nodes>
              """).getXml(),
              "</services>");
    }

}
