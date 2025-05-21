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
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("nvcr.io/nvidia/tritonserver:25.03-py3");

    private final Path modelRepositoryPath;

    public TritonServerContainer() throws IOException {
        this(DEFAULT_IMAGE_NAME);
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

    public String getGrpcEndpoint() {
        return getHost() + ":" + getMappedPort(GRPC_PORT);
    }

    public Path getModelRepositoryPath() { return modelRepositoryPath; }

    public void addModel(String modelName, Path modelFile) throws IOException {
        Path modelDir = modelRepositoryPath.resolve(modelName + "/1");
        Files.createDirectories(modelDir);
        Path targetModelPath = modelDir.resolve("model.onnx");
        Files.copy(modelFile, targetModelPath, StandardCopyOption.REPLACE_EXISTING);
    }
}
