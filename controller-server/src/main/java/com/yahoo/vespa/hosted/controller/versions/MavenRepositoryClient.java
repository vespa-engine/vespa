// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.vespa.hosted.controller.api.integration.maven.ArtifactId;
import com.yahoo.vespa.hosted.controller.api.integration.maven.MavenRepository;
import com.yahoo.vespa.hosted.controller.api.integration.maven.Metadata;
import com.yahoo.vespa.hosted.controller.maven.repository.config.MavenRepositoryConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Http client implementation of a {@link MavenRepository}, which uses a configured repository and artifact ID.
 *
 * @author jonmv
 */
public class MavenRepositoryClient implements MavenRepository {

    private final HttpClient client;
    private final URI apiUrl;
    private final ArtifactId id;

    public MavenRepositoryClient(MavenRepositoryConfig config) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.apiUrl = URI.create(config.apiUrl() + "/").normalize();
        this.id = new ArtifactId(config.groupId(), config.artifactId());
    }

    @Override
    public Metadata metadata() {
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

    @Override
    public ArtifactId artifactId() {
        return id;
    }

    static URI withArtifactPath(URI baseUrl, ArtifactId id) {
        List<String> parts = new ArrayList<>(List.of(id.groupId().split("\\.")));
        parts.add(id.artifactId());
        parts.add("maven-metadata.xml");
        return baseUrl.resolve(String.join("/", parts));
    }

}
