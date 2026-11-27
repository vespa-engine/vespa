// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import com.yahoo.config.ModelReference;
import com.yahoo.config.model.NullConfigModelRegistry;
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
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author ingridhabrekke
 * @author glebashnik
 */
class TritonLlamacppOptionsValidatorTest {

    private static final SidecarSpec TRITON_SIDECAR = SidecarSpec.builder()
            .id(0)
            .name(SidecarProvider.TRITON_SIDECAR_NAME)
            .image(DockerImage.fromString("example.invalid/test/triton:1.0"))
            .minCpu(1)
            .build();

    @Test
    void default_options_are_supported() {
        assertEquals(List.of(), unsupportedOptions(config()));
    }

    @Test
    void pooling_type_none_is_unsupported() {
        assertEquals(List.of("poolingType NONE: Triton returns only pooled embeddings. " +
                             "Set a pooling type, or leave it unspecified for a model that has one"),
                     unsupportedOptions(config().poolingType(GgufEmbedderConfig.PoolingType.Enum.NONE)));
    }

    @Test
    void each_parallel_sequence_must_hold_the_micro_batch() {
        assertEquals(List.of("contextSize 2048 gives 512 per parallel sequence (parallel 4), which is below " +
                             "physicalMaxBatchSize 1024: each sequence must hold a whole input"),
                     unsupportedOptions(config().contextSize(2048).parallel(4).physicalMaxBatchSize(1024)));
        assertEquals(List.of("contextSize 1024 gives 256 per parallel sequence (parallel 4), which is below " +
                             "llama.cpp's default physicalMaxBatchSize 512: each sequence must hold a whole input"),
                     unsupportedOptions(config().contextSize(1024).parallel(4)));
        assertEquals(List.of(), unsupportedOptions(config().contextSize(4096).parallel(4).physicalMaxBatchSize(1024)));
    }

    @Test
    void parallel_must_divide_the_micro_batch() {
        assertEquals(List.of("parallel 3 must divide physicalMaxBatchSize 1024"),
                     unsupportedOptions(config().parallel(3).physicalMaxBatchSize(1024)));
        assertEquals(List.of("parallel 3 must divide llama.cpp's default physicalMaxBatchSize 512"),
                     unsupportedOptions(config().parallel(3)));
    }

    @Test
    void parallel_below_1_is_unsupported() {
        assertEquals(List.of("parallel 0 is below 1"), unsupportedOptions(config().parallel(0)));
    }

    @Test
    void rejects_unsupported_options_of_gguf_embedder_type_in_triton() {
        var services = services("""
                <component id="gguf" type="gguf-embedder">
                  <transformer-model url="https://example.invalid/model.gguf"/>
                  <pooling-strategy>none</pooling-strategy>
                </component>
                """);
        var exception = assertThrows(IllegalArgumentException.class, () -> validate(services, true));
        assertEquals("GGUF embedder 'gguf' in cluster 'container' runs in Triton, which does not support its options: " +
                     "poolingType NONE: Triton returns only pooled embeddings. " +
                     "Set a pooling type, or leave it unspecified for a model that has one",
                     exception.getMessage());
    }

    @Test
    void rejects_unsupported_options_of_gguf_embedder_class_in_triton() {
        var services = services("""
                <component id="gguf" class="ai.vespa.embedding.GgufEmbedder" bundle="model-integration">
                  <config name="ai.vespa.embedding.config.gguf-embedder">
                    <embeddingModel url="https://example.invalid/model.gguf"/>
                    <parallel>3</parallel>
                  </config>
                </component>
                """);
        var exception = assertThrows(IllegalArgumentException.class, () -> validate(services, true));
        assertEquals("GGUF embedder 'gguf' in cluster 'container' runs in Triton, which does not support its options: " +
                     "parallel 3 must divide llama.cpp's default physicalMaxBatchSize 512",
                     exception.getMessage());
    }

    @Test
    void accepts_the_same_options_without_triton() {
        var services = services("""
                <component id="gguf" type="gguf-embedder">
                  <transformer-model url="https://example.invalid/model.gguf"/>
                  <pooling-strategy>none</pooling-strategy>
                  <parallel>3</parallel>
                </component>
                """);
        validate(services, false);
    }

    private static List<String> unsupportedOptions(GgufEmbedderConfig.Builder config) {
        return TritonLlamacppOptionsValidator.unsupportedOptions(config.build());
    }

    private static GgufEmbedderConfig.Builder config() {
        return new GgufEmbedderConfig.Builder().embeddingModel(ModelReference.valueOf("models/embedder.gguf"));
    }

    private static String services(String components) {
        return """
               <services version="1.0">
                 <container id="container" version="1.0">
                   %s
                   <nodes count="2">
                     <resources vcpu="4" memory="16Gb" disk="125Gb"/>
                   </nodes>
                 </container>
               </services>
               """.formatted(components);
    }

    private static void validate(String services, boolean useTriton) {
        var state = new DeployState.Builder()
                .applicationPackage(new MockApplicationPackage.Builder().withServices(services).build())
                .zone(new Zone(SystemName.PublicCd, Environment.prod, RegionName.from("us-east-1")))
                .properties(new TestProperties().setHostedVespa(true).setMultitenant(true).setUseTriton(useTriton))
                .modelHostProvisioner(new InMemoryProvisioner(8, new NodeResources(4, 16, 125, 1), false))
                .endpoints(Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("container.example.invalid"))))
                .sidecarProvider((clusterId, resources, needTriton) -> needTriton ? List.of(TRITON_SIDECAR) : List.of())
                .build();
        try {
            var execution = (Validation.Execution) Validation.createContext(new VespaModel(new NullConfigModelRegistry(), state), state);
            new TritonLlamacppOptionsValidator().validate(execution);
            execution.throwIfFailed();
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

}
