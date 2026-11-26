// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.SidecarProvider;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SidecarSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.EmbedExpressionValidator;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests which input a GGUF embedder with a multimodal projector accepts, by where its cluster runs GGUF embedders.
 *
 * @author glebashnik
 */
public class GgufMultimodalEmbedderTest {

    private static final String SERVICES = """
            <services version="1.0">
              <container id="container" version="1.0">
                <component id="gguf" type="gguf-embedder">
                  <transformer-model url="https://example.invalid/model.gguf"/>
                  <mmproj-model url="https://example.invalid/mmproj.gguf"/>
                </component>
                <document-processing/>
                <nodes count="2">
                  <resources vcpu="4" memory="16Gb" disk="125Gb"/>
                </nodes>
              </container>
              <content id="test" version="1.0">
                <redundancy>1</redundancy>
                <documents>
                  <document mode="index" type="product"/>
                  <document-processing cluster="container"/>
                </documents>
                <nodes count="2">
                  <resources vcpu="4" memory="16Gb" disk="125Gb"/>
                </nodes>
              </content>
            </services>
            """;

    private static final String SCHEMA = """
            schema product {
              document product {
                field title type string {
                  indexing: summary | index
                }
                field image type raw {}
              }
              field product_embedding type tensor<float>(x[768]) {
                indexing: input title, input image | embed gguf | attribute
              }
            }
            """;

    private static final SidecarSpec TRITON_SIDECAR = SidecarSpec.builder()
            .id(0)
            .name(SidecarProvider.TRITON_SIDECAR_NAME)
            .image(DockerImage.fromString("example.invalid/test/triton:1.0"))
            .minCpu(1)
            .build();

    @Test
    void accepts_images_when_gguf_embedders_run_in_triton() throws Exception {
        var model = validate(true);
        var config = model.getConfig(GgufEmbedderConfig.class, "container/component/gguf");
        assertEquals(1, config.mmproj().size());
    }

    @Test
    void accepts_text_only_when_gguf_embedders_run_in_the_container() {
        var e = assertThrows(IllegalArgumentException.class, () -> validate(false));
        assertTrue(e.getMessage().contains("uses the embedder with id 'gguf', which accepts text, but is given text and image"),
                   e.getMessage());
    }

    private static VespaModel validate(boolean useTriton) throws Exception {
        var deployState = new DeployState.Builder()
                .applicationPackage(new MockApplicationPackage.Builder().withServices(SERVICES).withSchemas(List.of(SCHEMA)).build())
                .zone(new Zone(SystemName.PublicCd, Environment.prod, RegionName.from("us-east-1")))
                .properties(new TestProperties().setHostedVespa(true).setMultitenant(true).setUseTriton(useTriton))
                .modelHostProvisioner(new InMemoryProvisioner(8, new NodeResources(4, 16, 125, 1), false))
                .endpoints(Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("container.example.invalid"))))
                .sidecarProvider((clusterId, resources, needTriton) -> needTriton ? List.of(TRITON_SIDECAR) : List.of())
                .build();
        var model = new VespaModel(deployState);
        ValidationTester.validate(new EmbedExpressionValidator(), model, deployState);
        return model;
    }

}
