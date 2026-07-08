package ai.vespa.testcontainers;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Testcontainers implementation for Vespa.
 * <p>
 * Supported image: {@code vespaengine/vespa}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>Container (query/document API): 8080</li>
 *     <li>Config server: 19071</li>
 * </ul>
 * @author edvardwd
 */
public class VespaContainer extends GenericContainer<VespaContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("vespaengine/vespa");
    public static final int CONTAINER_PORT = 8080;
    public static final int CONFIG_SERVER_PORT = 19071;
    private static final Duration STARTUP_WAIT_TIME = Duration.ofMinutes(1);
    private static final Duration DEFAULT_DEPLOY_WAIT_TIME = Duration.ofMinutes(1);
    private static final String CONTAINER_APP_PACKAGE_PATH = "/tmp/app";
    private static final String APPLICATION_STATUS_ENDPOINT = "/ApplicationStatus";
    private MountableFile applicationPackage = null;
    private Duration deployWaitTime = DEFAULT_DEPLOY_WAIT_TIME;

    /** Creates a container from the given image name, e.g. {@code "vespaengine/vespa:8.0.0"}. */
    public VespaContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /** Creates a container using the default {@code vespaengine/vespa} image. */
    public VespaContainer() {
        this(DEFAULT_IMAGE_NAME);
    }

    /** Creates a container from the given {@link DockerImageName}. */
    public VespaContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        addExposedPorts(CONTAINER_PORT, CONFIG_SERVER_PORT);

        waitingFor(
            Wait
                .forHttp(APPLICATION_STATUS_ENDPOINT)
                .forPort(CONFIG_SERVER_PORT)
                .withStartupTimeout(STARTUP_WAIT_TIME)
        );
    }

    /**
     * Configures an application package to be deployed automatically on container start.
     * Must be called before {@link #start()}.
     *
     * @param classpathResource classpath path to the application package directory
     */
    public VespaContainer withApplicationPackage(String classpathResource) {
        return withApplicationPackage(MountableFile.forClasspathResource(classpathResource));
    }

    /**
     * Configures an application package to be deployed automatically on container start.
     * Must be called before {@link #start()}.
     *
     * @param hostPath path to the application package directory on the host
     */
    public VespaContainer withApplicationPackage(Path hostPath) {
        return withApplicationPackage(MountableFile.forHostPath(hostPath));
    }

    /**
     * Configures an application package to be deployed automatically on container start.
     * Must be called before {@link #start()}.
     *
     * @param applicationPackage the application package to deploy
     */
    public VespaContainer withApplicationPackage(MountableFile applicationPackage) {
        this.applicationPackage = applicationPackage;
        this.withCopyFileToContainer(applicationPackage, CONTAINER_APP_PACKAGE_PATH);
        return this;
    }

    /**
     * Overrides the default deploy wait time (1 minute).
     * Increase this for large application packages that take longer to deploy.
     *
     * @param deployWaitTime maximum time to wait for deployment to complete
     */
    public VespaContainer withDeployWaitTime(Duration deployWaitTime) {
        this.deployWaitTime = deployWaitTime;
        return this;
    }

    /**
     * Deploys the application package configured via {@link #withApplicationPackage}.
     * Blocks until the container endpoint is ready to serve requests.
     * Useful for redeploying after the container has already started.
     */
    public void deployApplicationPackage() {
        if (this.applicationPackage == null) throw new IllegalStateException("Application package not added. Use `withApplicationPackage()` first.");
        if (!this.isRunning()) throw new IllegalStateException("Container is not running. Use `start()` before deploying.");
        try {
            Container.ExecResult result = this.execInContainer(
                "vespa", "deploy", CONTAINER_APP_PACKAGE_PATH, "--wait", String.valueOf(deployWaitTime.getSeconds())
            );
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("vespa deploy failed:\n" + result.getStderr());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to deploy application package", e);
        }
        Wait
            .forHttp(APPLICATION_STATUS_ENDPOINT)
            .forPort(CONTAINER_PORT)
            .withStartupTimeout(deployWaitTime)
            .waitUntilReady(this);
    }

    /**
     * Copies and deploys the given application package on a running container.
     * Blocks until the container endpoint is ready to serve requests.
     *
     * @param classpathResource classpath path to the application package directory
     */
    public void deployApplicationPackage(String classpathResource) {
        deployApplicationPackage(MountableFile.forClasspathResource(classpathResource));
    }

    /**
     * Copies and deploys the given application package on a running container.
     * Blocks until the container endpoint is ready to serve requests.
     *
     * @param hostPath path to the application package directory on the host
     */
    public void deployApplicationPackage(Path hostPath) {
        deployApplicationPackage(MountableFile.forHostPath(hostPath));
    }

    /**
     * Copies and deploys the given application package on a running container.
     * Blocks until the container endpoint is ready to serve requests.
     *
     * @param applicationPackage the application package to deploy
     */
    public void deployApplicationPackage(MountableFile applicationPackage) {
        if (!this.isRunning()) throw new IllegalStateException("Container is not running. Use `start()` before deploying.");
        this.applicationPackage = applicationPackage;
        this.copyFileToContainer(applicationPackage, CONTAINER_APP_PACKAGE_PATH);
        deployApplicationPackage();
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (applicationPackage == null) {
            return;
        }
        deployApplicationPackage();
    }

    /** Returns the base URL of the query and document API, e.g. {@code http://localhost:8080}. */
    public String getEndpoint() {
        return "http://" + this.getHost() + ":" + this.getMappedContainerPort();
    }

    /** Returns the base URL of the config server, e.g. {@code http://localhost:19071}. */
    public String getConfigEndpoint() {
        return "http://" + this.getHost() + ":" + this.getConfigServerPort();
    }

    /** Returns the mapped host port for the container endpoint (8080). */
    public int getMappedContainerPort() {
        return getMappedPort(CONTAINER_PORT);
    }

    /** Returns the mapped host port for the config server (19071). */
    public int getConfigServerPort() {
        return getMappedPort(CONFIG_SERVER_PORT);
    }
}
