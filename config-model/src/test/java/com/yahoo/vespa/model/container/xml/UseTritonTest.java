// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.rankingexpression.importer.onnx.OnnxImporter;
import com.yahoo.component.ComponentId;
import com.yahoo.component.Version;
import com.yahoo.config.model.ApplicationPackageTester;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.SidecarProvider;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SidecarSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModelEvaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.model.api.SidecarProvider.TRITON_LLAMACPP_CAPABILITY;
import static com.yahoo.config.model.api.SidecarProvider.TRITON_ONNX_CAPABILITY;
import static com.yahoo.config.model.api.SidecarProvider.TRITON_SIDECAR_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests which container clusters run their ONNX models in a Triton sidecar.
 *
 * @author glebashnik
 */
public class UseTritonTest {

    private static final Path TEST_APP = Path.of("src/test/cfg/application/onnx");
    private static final NodeResources RESOURCES = new NodeResources(4, 16, 125, 1);

    private static final String NODES = """
            <nodes count="2">
              <resources vcpu="4" memory="16Gb" disk="125Gb"/>
            </nodes>
            """;

    private static final String EMBEDDER_SERVICES = """
            <services version="1.0">
              <container id="container" version="1.0">
                <component id="embedder" type="hugging-face-embedder">
                  <transformer-model url="https://example.invalid/model.onnx"/>
                  <tokenizer-model url="https://example.invalid/tokenizer.json"/>
                </component>
                %s
              </container>
            </services>
            """.formatted(NODES);

    private static final String GGUF_EMBEDDER_SERVICES = """
            <services version="1.0">
              <container id="container" version="1.0">
                <component id="embedder" class="ai.vespa.embedding.GgufEmbedder" bundle="model-integration">
                  <config name="ai.vespa.embedding.config.gguf-embedder">
                    <embeddingModel url="https://example.invalid/model.gguf"/>
                  </config>
                </component>
                %s
              </container>
            </services>
            """.formatted(NODES);

    private static final String GGUF_EMBEDDER_TYPE_SERVICES = """
            <services version="1.0">
              <container id="container" version="1.0">
                <component id="embedder" type="gguf-embedder">
                  <transformer-model url="https://example.invalid/model.gguf"/>
                </component>
                %s
              </container>
            </services>
            """.formatted(NODES);

    private static final String GGUF_AND_ONNX_EMBEDDER_SERVICES = """
            <services version="1.0">
              <container id="container" version="1.0">
                <component id="embedder" type="hugging-face-embedder">
                  <transformer-model url="https://example.invalid/model.onnx"/>
                  <tokenizer-model url="https://example.invalid/tokenizer.json"/>
                </component>
                <component id="gguf-embedder" class="ai.vespa.embedding.GgufEmbedder" bundle="model-integration">
                  <config name="ai.vespa.embedding.config.gguf-embedder">
                    <embeddingModel url="https://example.invalid/model.gguf"/>
                  </config>
                </component>
                %s
              </container>
            </services>
            """.formatted(NODES);

    private static final String MODEL_EVALUATION_SERVICES = """
            <services version="1.0">
              <container id="container" version="1.0">
                <model-evaluation/>
                %s
              </container>
            </services>
            """.formatted(NODES);

    private static final String GLOBAL_PHASE_SERVICES = """
            <services version="1.0">
              <container id="container" version="1.0">
                <search/>
                %s
              </container>
              <content id="test" version="1.0">
                <redundancy>1</redundancy>
                <documents>
                  <document mode="index" type="test"/>
                </documents>
                %s
              </content>
            </services>
            """.formatted(NODES, NODES);

    private static final String GLOBAL_PHASE_SCHEMA = """
            schema test {
                document test {
                    field document_value type tensor<float>(d0[1]) {
                        indexing: attribute
                    }
                }
                onnx-model my_add {
                    file: files/add.onnx
                    input input1: query(q1)
                    input input2: query(q2)
                    output output: out
                }
                rank-profile global_ranker {
                    inputs {
                        query(q1) tensor<float>(d0[1])
                        query(q2) tensor<float>(d0[1])
                    }
                    first-phase {
                        expression: 1
                    }
                    global-phase {
                        expression: sum(onnx(my_add).out)
                    }
                }
            }
            """;

    private static final String INHERITED_GLOBAL_PHASE_SCHEMA = """
            schema test {
                document test {
                    field document_value type tensor<float>(d0[1]) {
                        indexing: attribute
                    }
                }
                rank-profile with_model {
                    onnx-model my_add {
                        file: files/add.onnx
                        input input1: query(q1)
                        input input2: query(q2)
                        output output: out
                    }
                    inputs {
                        query(q1) tensor<float>(d0[1])
                        query(q2) tensor<float>(d0[1])
                    }
                }
                rank-profile global_ranker inherits with_model {
                    first-phase {
                        expression: 1
                    }
                    global-phase {
                        expression: sum(onnx(my_add).out)
                    }
                }
            }
            """;

    private static final String GLOBAL_PHASE_SCHEMA_WITHOUT_ONNX = """
            schema test {
                document test {
                    field document_value type tensor<float>(d0[1]) {
                        indexing: attribute
                    }
                }
                rank-profile global_ranker {
                    first-phase {
                        expression: 1
                    }
                    global-phase {
                        expression: sum(attribute(document_value))
                    }
                }
            }
            """;

    private static final String NO_ONNX_SERVICES = """
            <services version="1.0">
              <container id="container" version="1.0">
                %s
              </container>
            </services>
            """.formatted(NODES);

    private static final SidecarSpec TRITON_SIDECAR = SidecarSpec.builder()
            .id(0)
            .name(SidecarProvider.TRITON_SIDECAR_NAME)
            .image(DockerImage.fromString("example.invalid/test/triton:1.0"))
            .minCpu(1)
            .build();

    @Test
    void cluster_with_onnx_embedder_uses_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), EMBEDDER_SERVICES);
        assertUsesTriton(containerCluster(appDir));
    }

    @Test
    void cluster_with_model_evaluation_onnx_model_uses_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), MODEL_EVALUATION_SERVICES);
        copy(TEST_APP.resolve("models/mul.onnx"), appDir.resolve("models/mul.onnx"));
        assertUsesTriton(containerCluster(appDir));
    }

    @Test
    void cluster_with_global_phase_onnx_model_uses_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), GLOBAL_PHASE_SERVICES);
        write(appDir.resolve("schemas/test.sd"), GLOBAL_PHASE_SCHEMA);
        copy(TEST_APP.resolve("files/add.onnx"), appDir.resolve("files/add.onnx"));
        assertUsesTriton(containerCluster(appDir));
    }

    @Test
    void cluster_with_global_phase_onnx_model_from_inherited_profile_uses_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), GLOBAL_PHASE_SERVICES);
        write(appDir.resolve("schemas/test.sd"), INHERITED_GLOBAL_PHASE_SCHEMA);
        copy(TEST_APP.resolve("files/add.onnx"), appDir.resolve("files/add.onnx"));
        assertUsesTriton(containerCluster(appDir));
    }

    @Test
    void cluster_without_onnx_models_does_not_use_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), NO_ONNX_SERVICES);
        assertDoesNotUseTriton(containerCluster(appDir));
    }

    @Test
    void cluster_with_model_evaluation_without_onnx_models_does_not_use_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), MODEL_EVALUATION_SERVICES);
        assertDoesNotUseTriton(containerCluster(appDir));
    }

    @Test
    void cluster_with_global_phase_without_onnx_models_does_not_use_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), GLOBAL_PHASE_SERVICES);
        write(appDir.resolve("schemas/test.sd"), GLOBAL_PHASE_SCHEMA_WITHOUT_ONNX);
        assertDoesNotUseTriton(containerCluster(appDir));
    }

    @Test
    void cluster_with_gguf_embedder_runs_it_in_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), GGUF_EMBEDDER_SERVICES);
        var cluster = containerCluster(appDir);
        // The sidecar, but the ONNX runtime stays in the container, as there are no ONNX models
        assertEquals(List.of(TRITON_SIDECAR), cluster.getSpec().sidecars());
        assertFalse(hasTritonRuntime(cluster));
        assertTrue(cluster.usesTritonForLlamacpp());
        assertEquals(Set.of(ContainerModelEvaluation.TRITON_LLAMACPP_RUNTIME_CLASS), llamacppRuntimes(cluster));
        assertEquals(Map.of(TRITON_SIDECAR_NAME, Set.of(TRITON_LLAMACPP_CAPABILITY)), sidecarProvider.sidecarNamesWithCapabilities);
    }

    @Test
    void cluster_with_gguf_embedder_type_runs_it_in_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), GGUF_EMBEDDER_TYPE_SERVICES);
        var cluster = containerCluster(appDir);
        assertEquals(List.of(TRITON_SIDECAR), cluster.getSpec().sidecars());
        assertTrue(cluster.usesTritonForLlamacpp());
        assertEquals(Set.of(ContainerModelEvaluation.TRITON_LLAMACPP_RUNTIME_CLASS), llamacppRuntimes(cluster));
        assertEquals(Map.of(TRITON_SIDECAR_NAME, Set.of(TRITON_LLAMACPP_CAPABILITY)), sidecarProvider.sidecarNamesWithCapabilities);
    }

    @Test
    void cluster_with_gguf_and_onnx_embedders_runs_both_in_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), GGUF_AND_ONNX_EMBEDDER_SERVICES);
        var cluster = containerCluster(appDir);
        assertUsesTriton(cluster);
        assertTrue(cluster.usesTritonForLlamacpp());
        assertEquals(Set.of(ContainerModelEvaluation.TRITON_LLAMACPP_RUNTIME_CLASS), llamacppRuntimes(cluster));
        assertEquals(Map.of(TRITON_SIDECAR_NAME, Set.of(TRITON_ONNX_CAPABILITY, TRITON_LLAMACPP_CAPABILITY)), sidecarProvider.sidecarNamesWithCapabilities);
    }

    @Test
    void cluster_with_gguf_embedder_runs_it_in_the_container_without_triton(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), GGUF_EMBEDDER_SERVICES);
        var cluster = containerCluster(appDir, false);
        assertDoesNotUseTriton(cluster);
        assertFalse(cluster.usesTritonForLlamacpp());
        assertEquals(Set.of(ContainerModelEvaluation.EMBEDDED_LLAMACPP_RUNTIME_CLASS), llamacppRuntimes(cluster));
    }

    @Test
    void cluster_with_onnx_embedder_runs_llamacpp_in_the_container(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), EMBEDDER_SERVICES);
        var cluster = containerCluster(appDir);
        assertUsesTriton(cluster);
        assertFalse(cluster.usesTritonForLlamacpp());
        assertEquals(Set.of(ContainerModelEvaluation.EMBEDDED_LLAMACPP_RUNTIME_CLASS), llamacppRuntimes(cluster));
        assertEquals(Map.of(TRITON_SIDECAR_NAME, Set.of(TRITON_ONNX_CAPABILITY)), sidecarProvider.sidecarNamesWithCapabilities);
    }

    @Test
    void cluster_without_models_has_the_embedded_llamacpp_runtime(@TempDir Path appDir) {
        write(appDir.resolve("services.xml"), NO_ONNX_SERVICES);
        var cluster = containerCluster(appDir);
        assertEquals(Set.of(ContainerModelEvaluation.EMBEDDED_LLAMACPP_RUNTIME_CLASS), llamacppRuntimes(cluster));
    }

    // The llama.cpp runtime components of the cluster, by class name
    private static Set<String> llamacppRuntimes(ApplicationContainerCluster cluster) {
        var runtimeClasses = Set.of(ContainerModelEvaluation.EMBEDDED_LLAMACPP_RUNTIME_CLASS,
                                    ContainerModelEvaluation.TRITON_LLAMACPP_RUNTIME_CLASS);
        return cluster.getAllComponents().stream()
                      .map(component -> component.getClassId().getName())
                      .filter(runtimeClasses::contains)
                      .collect(Collectors.toSet());
    }

    private static void assertUsesTriton(ApplicationContainerCluster cluster) {
        assertEquals(List.of(TRITON_SIDECAR), cluster.getSpec().sidecars());
        assertTrue(hasTritonRuntime(cluster));
    }

    private static void assertDoesNotUseTriton(ApplicationContainerCluster cluster) {
        assertEquals(List.of(), cluster.getSpec().sidecars());
        assertFalse(hasTritonRuntime(cluster));
    }

    private static boolean hasTritonRuntime(ApplicationContainerCluster cluster) {
        return cluster.getComponentsMap().containsKey(ComponentId.fromString(ContainerModelEvaluation.TRITON_ONNX_RUNTIME_CLASS));
    }

    private final TritonSidecarProvider sidecarProvider = new TritonSidecarProvider();

    private ApplicationContainerCluster containerCluster(Path appDir) {
        return containerCluster(appDir, true);
    }

    private ApplicationContainerCluster containerCluster(Path appDir, boolean useTriton) {
        var deployState = new DeployState.Builder()
                .applicationPackage(ApplicationPackageTester.create(appDir.toString()).app())
                .zone(new Zone(SystemName.PublicCd, Environment.prod, RegionName.from("us-east-1")))
                .properties(new TestProperties().setHostedVespa(true).setMultitenant(true).setUseTriton(useTriton))
                .modelHostProvisioner(new InMemoryProvisioner(8, RESOURCES, false))
                .endpoints(Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("container.example.invalid"))))
                .modelImporters(List.of(new OnnxImporter()))
                .sidecarProvider(sidecarProvider)
                .build();
        try {
            return new VespaModel(deployState).getContainerClusters().get("container");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copy(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class TritonSidecarProvider implements SidecarProvider {

        @Override
        public List<SidecarSpec> getSidecars(ClusterSpec.Id clusterId, NodeResources minNodeResources, boolean needTriton) {
            throw new AssertionError("The config model should invoke the application-aware method");
        }

        /** The sidecars and capabilities requested last. */
        Map<String, Set<String>> sidecarNamesWithCapabilities;

        @Override
        public List<SidecarSpec> getSidecars(ApplicationId application, Version vespaVersion, ClusterSpec.Id clusterId,
                                             NodeResources minNodeResources, Set<String> sidecarNames) {
            throw new AssertionError("The config model should invoke the capability-aware method");
        }

        @Override
        public List<SidecarSpec> getSidecars(ApplicationId application, Version vespaVersion, ClusterSpec.Id clusterId,
                                             NodeResources minNodeResources, Map<String, Set<String>> sidecarNamesWithCapabilities) {
            this.sidecarNamesWithCapabilities = sidecarNamesWithCapabilities;
            return sidecarNamesWithCapabilities.containsKey(TRITON_SIDECAR_NAME) ? List.of(TRITON_SIDECAR) : List.of();
        }
    }
}
