// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Level;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hmusum
 */
public class HnswValidatorTest {

    private static final String message = "Cluster 'mycluster' has searchable copies > 1 and fields with hnsw index. This will use a lot of resources, consider using searchable-copies=1";

    @Test
    void warns_when_2_searchable_copies_and_flat_setup() {
        var logger = new TestLogger();
        createModelAndValidate(logger, flat(), 2);
        assertEquals(message + " and going to a grouped setup, see https://docs.vespa.ai/en/elasticity.html#grouped-distribution",
                     logger.message.toString());
    }

    @Test
    void does_not_warn_when_1_searchable_copy_and_flat_setup() {
        var logger = new TestLogger();
        createModelAndValidate(logger, flat(), 1);
        assertEquals("", logger.message.toString());
    }

    @Test
    void warns_when_2_searchable_copies_and_2_groups() {
        var logger = new TestLogger();
        createModelAndValidate(logger, twoGroups(), 4);
        assertEquals("Cluster 'mycluster' has searchable copies > 1 and fields with hnsw index. This will use a lot of resources, consider using searchable-copies=1",
                     logger.message.toString());
    }

    @Test
    void does_not_warn_when_1_searchable_copy_and_2_groups() {
        var logger = new TestLogger();
        createModelAndValidate(logger, flat(), 1);
        assertEquals("", logger.message.toString());
    }

    private static String schema() {
        return """
                schema test {
                  document test {
                    field f1 type tensor(x[2]) {
                      indexing: attribute | index
                      index { hnsw }
                    }
                  }
                }
                """;
    }

    private String flat() {
        return """
               <group>
                 <node distribution-key='0' hostalias='mockhost'/>
                 <node distribution-key='1' hostalias='mockhost'/>
               </group>""";
    }

    private String twoGroups() {
        return """
               <group>
                 <distribution partitions='2|*'/>
                 <group distribution-key='0' name='group0'>
                   <node distribution-key='0' hostalias='mockhost'/>
                   <node distribution-key='1' hostalias='mockhost'/>
                 </group>
                 <group distribution-key='1' name='group1'>
                   <node distribution-key='2' hostalias='mockhost'/>
                   <node distribution-key='3' hostalias='mockhost'/>
                 </group>
               </group>""";
    }

    private static class TestLogger implements DeployLogger {
        public StringBuilder message = new StringBuilder();
        @Override
        public void log(Level level, String message) {
            this.message.append(message);
        }
    }

    private static void createModelAndValidate(DeployLogger logger, String groupXml, int redundancyAndSearchableCopies) {
        var builder = new ContentClusterBuilder()
                .docTypes("test")
                .redundancy(redundancyAndSearchableCopies)
                .searchableCopies(redundancyAndSearchableCopies)
                .groupXml(groupXml);
        DeployState deployState = createDeployState(servicesXml(builder), schema(), logger);
        VespaModel model;
        try {
            model = new VespaModel(new NullConfigModelRegistry(), deployState);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ValidationTester.validate(new HnswValidator(), model, deployState);
    }

    private static DeployState createDeployState(String servicesXml, String schema, DeployLogger logger) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withSchemas(List.of(schema))
                .build();
        var builder = new DeployState.Builder()
                .applicationPackage(app)
                .properties(new TestProperties().setHostedVespa(false));
        if (logger != null) {
            builder.deployLogger(logger);
        }
        return builder.build();
    }

    private static String servicesXml(ContentClusterBuilder builder) {
        return joinLines("<services version='1.0'>",
                builder.getXml(),
                "</services>");
    }

}
