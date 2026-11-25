// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Triton Server container for testing.
 *
 * @author bjorncs
 */
public class TritonServerContainer extends GenericContainer<TritonServerContainer> {

    private static final int GRPC_PORT = 8001;
    private static final int HTTP_PORT = 8000;
    private static final int METRICS_PORT = 8002;
    private static final int OPENAI_PORT = 9000;
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("nvcr.io/nvidia/tritonserver:26.08-py3");

    // Overrides the image, e.g. to test against Vespa's Triton images: -DVESPA_TRITON_IMAGE=localhost/tritonserver:<tag>
    private static final String IMAGE_NAME_PROPERTY = "VESPA_TRITON_IMAGE";

    private final Path modelRepositoryPath;

    public TritonServerContainer() throws IOException {
        this(configuredImageName());
    }

    private static DockerImageName configuredImageName() {
        var imageName = System.getProperty(IMAGE_NAME_PROPERTY);
        return imageName == null ? DEFAULT_IMAGE_NAME : DockerImageName.parse(imageName).asCompatibleSubstituteFor(DEFAULT_IMAGE_NAME);
    }

    public TritonServerContainer(DockerImageName imageName) throws IOException {
        super(imageName);
        this.modelRepositoryPath = Files.createTempDirectory("triton-model-repository");
        setLogConsumers(List.of(new Slf4jLogConsumer(LoggerFactory.getILoggerFactory().getLogger(TritonOnnxClient.class.getName()))));
        addEnv("ORT_LOGGING_LEVEL_FATAL", "4");
        addExposedPorts(GRPC_PORT, HTTP_PORT, METRICS_PORT);
        addFileSystemBind(modelRepositoryPath.toString(), "/models", BindMode.READ_ONLY, SelinuxContext.NONE);
        setCommandParts(new String[]{"tritonserver", "--model-repository=/models", "--model-control-mode=explicit"});
    }

    /**
     * Returns a container of the configured image that runs Triton's OpenAI frontend with its KServe frontends,
     * as llama.cpp models need. Requires a Vespa Triton image with llama.cpp, e.g. cpu-onnx-llamacpp.
     */
    public static TritonServerContainer withLlamacpp() throws IOException {
        var container = new TritonServerContainer();
        container.addExposedPort(OPENAI_PORT);
        // OpenAI requests reach llama.cpp through Triton's Python backend, whose shared memory in /dev/shm
        // must hold the largest bodies of every instance; the container default of 64 MiB is too small.
        container.withSharedMemorySize(1024L * 1024 * 1024);
        // The images with llama.cpp start the same by default; given here so older images work too.
        container.setCommandParts(new String[]{"python3", "/opt/tritonserver/python/openai/openai_frontend/main.py",
                                               "--model-repository=/models", "--model-control-mode=explicit",
                                               "--enable-kserve-frontends"});
        return container;
    }

    // Only Vespa's Triton images, given with the image override, support ONNX session sharing and pooling.
    public boolean isVespaImage() {
        return System.getProperty(IMAGE_NAME_PROPERTY) != null;
    }

    // Vespa's Triton images with llama.cpp are tagged with it, e.g. <version>-cpu-onnx-llamacpp.
    public static boolean isLlamacppImageConfigured() {
        var imageName = System.getProperty(IMAGE_NAME_PROPERTY);
        return imageName != null && imageName.contains("llamacpp");
    }

    public String getLlamacppEndpoint() {
        return "http://" + getHost() + ":" + getMappedPort(OPENAI_PORT);
    }

    public String getGrpcEndpoint() {
        return getHost() + ":" + getMappedPort(GRPC_PORT);
    }

    public String getHttpEndpoint() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT);
    }

    public Path getModelRepositoryPath() { return modelRepositoryPath; }

    public void addModel(String modelName, Path modelFile) throws IOException {
        Path modelDir = modelRepositoryPath.resolve(modelName + "/1");
        Files.createDirectories(modelDir);
        Path targetModelPath = modelDir.resolve("model.onnx");
        Files.copy(modelFile, targetModelPath, StandardCopyOption.REPLACE_EXISTING);
    }
}
