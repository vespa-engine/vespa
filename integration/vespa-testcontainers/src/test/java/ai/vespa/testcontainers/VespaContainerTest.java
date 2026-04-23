package ai.vespa.testcontainers;

import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.JsonFeeder;
import ai.vespa.feed.client.Result;
import ai.vespa.feed.client.FeedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.Container;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VespaContainerTest {
    private static final DockerImageName LATEST_VESPA_IMAGE = DockerImageName.parse("vespaengine/vespa:latest");
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String APP_PACKAGE_PATH = "/app";
    private static final String DOCUMENTS_RESOURCE = "/documents.jsonl";
    private static final String APPLICATION_STATUS_ENDPOINT = "/ApplicationStatus";
    private static String[] getVespaTestVersions() { return new String[] {"vespaengine/vespa:8.640.27", "vespaengine/vespa:8.635.112"}; }

    @Test
    void containerStartsWithLatestImage() {
        try (VespaContainer container = new VespaContainer(LATEST_VESPA_IMAGE)) {
            container.start();
            assertThat(container.isRunning()).withFailMessage("Container failed to start").isTrue();
        }
    }

    @ParameterizedTest
    @MethodSource("getVespaTestVersions")
    void containerStartsWithVersion(String imageName) {
        try (VespaContainer container = new VespaContainer(imageName)) {
            container.start();
            assertThat(container.isRunning()).withFailMessage("Failed to start container for image " + imageName).isTrue();
        }
    }

    @Test
    void automaticDeploymentWithApplicationPackage() throws Exception {
        try (VespaContainer container = new VespaContainer(LATEST_VESPA_IMAGE).withApplicationPackage(APP_PACKAGE_PATH)) {
            container.start();
            assertThat(container.isRunning()).withFailMessage("Container failed to start").isTrue();
            assertThat(httpGet(container.getEndpoint() + APPLICATION_STATUS_ENDPOINT)).isEqualTo(200);
        }
    }

    @Test
    void appDeploysAfterApplicationPackageIsAdded() throws Exception {
        try (VespaContainer container = new VespaContainer(LATEST_VESPA_IMAGE)) {
            container.start();
            assertThat(container.isRunning()).withFailMessage("Container failed to start").isTrue();

            // Should throw exception when trying to deploy non-existent app package
            assertThatThrownBy(container::deployApplicationPackage)
                    .as("Expected deployment to fail when no app package is added")
                    .isInstanceOf(IllegalStateException.class);

            // Should succeed when deploying with an explicit package
            container.deployApplicationPackage(APP_PACKAGE_PATH);
            assertThat(httpGet(container.getEndpoint() + APPLICATION_STATUS_ENDPOINT)).isEqualTo(200);

        }
    }

    @Test
    void documentsAreFedSuccessfully() throws Exception {
        try(VespaContainer container = new VespaContainer(LATEST_VESPA_IMAGE).withApplicationPackage(APP_PACKAGE_PATH)) {
            container.start();

            final String documentsContainerPath = "/tmp/documents.jsonl";
            container.copyFileToContainer(MountableFile.forClasspathResource("documents.jsonl"), documentsContainerPath);
            Container.ExecResult feedResult = container.execInContainer("vespa", "feed", documentsContainerPath);
            assertThat(feedResult.getExitCode())
                    .withFailMessage("vespa feed failed:\nstdout: " + feedResult.getStdout() + "\nstderr: " + feedResult.getStderr())
                    .isEqualTo(0);

            JsonNode counts = mapper.readTree(feedResult.getStdout()).get("http.response.code.counts");
            assertThat(counts.size())
                    .as("Expected only 200 responses, got: %s", counts)
                    .isEqualTo(1);
            assertThat(counts.has("200"))
                    .as("Expected 200 key to be present, got: %s", counts)
                    .isTrue();
        }
    }

    @Test
    void feedAndQueryWithClients() throws Exception {
        try (VespaContainer container = new VespaContainer(LATEST_VESPA_IMAGE).withApplicationPackage(APP_PACKAGE_PATH)) {
            container.start();

            // Feed documents using vespa-feed-client
            try (FeedClient client = FeedClientBuilder.create(URI.create(container.getEndpoint())).build();
                 JsonFeeder feeder = JsonFeeder.builder(client).withTimeout(Duration.ofSeconds(30)).build();
                 InputStream jsonStream = VespaContainerTest.class.getResourceAsStream(DOCUMENTS_RESOURCE)) {
                feeder.feedMany(jsonStream, new JsonFeeder.ResultCallback() {
                    @Override
                    public void onNextResult(Result result, FeedException error) {
                        if (error != null) throw new RuntimeException("Feed error: " + error);
                    }
                    @Override
                    public void onError(FeedException error) {
                        throw new RuntimeException("Fatal feed error: " + error.getMessage());
                    }
                }).join();
            }

            // Query using Java HTTP client
            HttpClient httpClient = HttpClient.newHttpClient();
            String yql = URLEncoder.encode("select * from person where true", StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(container.getEndpoint() + "/search/?yql=" + yql))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode result = mapper.readTree(response.body());
            assertThat(result.get("root").get("fields").get("totalCount").asInt())
                    .as("Expected all 5 documents to be returned")
                    .isEqualTo(5);
        }
    }

    private static int httpGet(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
