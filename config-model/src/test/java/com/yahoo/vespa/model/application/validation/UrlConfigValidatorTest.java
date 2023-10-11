// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yahoo.config.provision.Environment.prod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UrlConfigValidatorTest {

    @Test
    void failsWhenContainerNodesNotExclusive() throws IOException, SAXException {
        runValidatorOnApp(true, SystemName.Public);  // Exclusive nodes in public => success

        assertEquals("Found s3:// urls in config for container cluster default. This is only supported in public systems",
                     assertThrows(IllegalArgumentException.class,
                                  () -> runValidatorOnApp(false, SystemName.main))
                             .getMessage());

        assertEquals("Found s3:// urls in config for container cluster default. This is only supported in public systems",
                     assertThrows(IllegalArgumentException.class,
                                  () -> runValidatorOnApp(true, SystemName.main))
                             .getMessage());

        assertEquals("Found s3:// urls in config for container cluster default. Nodes in the cluster need to be 'exclusive',"
                     + " see https://cloud.vespa.ai/en/reference/services#nodes",
                     assertThrows(IllegalArgumentException.class,
                                  () -> runValidatorOnApp(false, SystemName.Public))
                             .getMessage());
    }

    private static String containerXml(boolean isExclusive) {
        return """
                           <container version='1.0' id='default'>
                               <component id='transformer' class='ai.vespa.embedding.BertBaseEmbedder' bundle='model-integration'>
                                   <config name='embedding.bert-base-embedder'>
                                     <transformerModel url='s3://models/minilm-l6-v2/sentence_all_MiniLM_L6_v2.onnx' path='foo'/>
                                     <tokenizerVocab url='s3://models/bert-base-uncased.txt'/>
                                   </config>
                               </component>
                               <search/>
                               <document-api/>
                               <nodes count='2' exclusive='%s' />
                           </container>
                """.formatted(Boolean.toString(isExclusive));
    }

    private static void runValidatorOnApp(boolean isExclusive, SystemName systemName) throws IOException, SAXException {
        String container = containerXml(isExclusive);
        String servicesXml = """
                        <services version='1.0'>
                          %s
                        </services>
                """.formatted(container);
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .build();
        DeployState deployState = createDeployState(app, systemName);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        new UrlConfigValidator().validate(model, deployState);
    }

    private static DeployState createDeployState(ApplicationPackage app, SystemName systemName) {
        boolean isHosted = true;
        var builder = new DeployState.Builder()
                .applicationPackage(app)
                .zone(new Zone(systemName, prod, RegionName.from("us-east-3")))
                .endpoints(Set.of(new ContainerEndpoint("default", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))))
                .properties(new TestProperties().setHostedVespa(isHosted))
                .fileRegistry(new MockFileRegistry());

        Map<ConfigDefinitionKey, ConfigDefinition> defs = new HashMap<>();
        defs.put(new ConfigDefinitionKey(BertBaseEmbedderConfig.CONFIG_DEF_NAME, BertBaseEmbedderConfig.CONFIG_DEF_NAMESPACE),
                 new ConfigDefinition(BertBaseEmbedderConfig.CONFIG_DEF_NAME, BertBaseEmbedderConfig.CONFIG_DEF_SCHEMA));
        builder.configDefinitionRepo(new ConfigDefinitionRepo() {
            @Override
            public Map<ConfigDefinitionKey, com.yahoo.vespa.config.buildergen.ConfigDefinition> getConfigDefinitions() {
                return defs;
            }

            @Override
            public com.yahoo.vespa.config.buildergen.ConfigDefinition get(ConfigDefinitionKey key) {
                return defs.get(key);
            }
        });
        return builder.build();
    }

}
