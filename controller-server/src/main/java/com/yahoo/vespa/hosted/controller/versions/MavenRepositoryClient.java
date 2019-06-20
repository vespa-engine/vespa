package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.vespa.hosted.controller.api.integration.maven.ArtifactId;
import com.yahoo.vespa.hosted.controller.api.integration.maven.Metadata;
import com.yahoo.vespa.hosted.controller.api.integration.maven.MavenRepository;
import com.yahoo.vespa.hosted.controller.maven.repository.config.MavenRepositoryConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MavenRepositoryClient implements MavenRepository {

    private static final String artifactoryApi = "https://edge.artifactory.ouroath.com:4443/artifactory/vespa-maven-libs-release-local/";

    private final HttpClient client;
    private final URI apiUrl;

    public MavenRepositoryClient(MavenRepositoryConfig config) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.apiUrl = URI.create(config.apiUrl() + "/").normalize();
    }

    @Override
    public Metadata getMetadata(ArtifactId id) {
        try {
            HttpRequest request = HttpRequest.newBuilder(withArtifactPath(apiUrl, id)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
            if (response.statusCode() != 200)
                throw new RuntimeException("Status code '" + response.statusCode() + "' and body\n'''\n" +
                                           response.body() + "\n'''\nfor request " + request);

            return Metadata.fromXml(response.body());
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static URI withArtifactPath(URI baseUrl, ArtifactId id) {
        List<String> parts = new ArrayList<>(List.of(id.groupId().split("\\.")));
        parts.add(id.artifactId());
        parts.add("maven-metadata.xml");
        return baseUrl.resolve(String.join("/", parts));
    }

}
