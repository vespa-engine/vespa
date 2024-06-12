// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Path;
import ai.vespa.http.HttpURL.Scheme;
import ai.vespa.util.http.hc5.VespaAsyncHttpClientBuilder;
import com.yahoo.config.model.api.ApplicationClusterEndpoint.AuthMethod;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.config.server.modelfactory.ModelResult;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;

import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.QRSERVER;
import static com.yahoo.slime.SlimeUtils.entriesStream;
import static com.yahoo.slime.SlimeUtils.jsonToSlime;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * @author jonmv
 */
public class ActiveTokenFingerprintsClient implements ActiveTokenFingerprints, AutoCloseable {

    private final CloseableHttpAsyncClient httpClient = createHttpClient();

    public ActiveTokenFingerprintsClient() {
        httpClient.start();
    }

    @Override
    public Map<String, List<Token>> get(ModelResult application) {
        Set<String> containersWithTokenFilter = application.getModel().applicationClusterInfo().stream()
                                                           .flatMap(cluster -> cluster.endpoints().stream())
                                                           .filter(endpoint -> endpoint.authMethod() == AuthMethod.token)
                                                           .flatMap(endpoint -> endpoint.hostNames().stream())
                                                           .collect(toSet());
        return getFingerprints(application.getModel().getHosts().stream()
                                          .filter(host -> containersWithTokenFilter.contains(host.getHostname()))
                                          .flatMap(host -> host.getServices().stream())
                                          .filter(service ->    service.getServiceType().equals(CONTAINER.serviceName)
                                                             || service.getServiceType().equals(QRSERVER.serviceName))
                                          .toList());
    }

    private Map<String, List<Token>> getFingerprints(List<ServiceInfo> services) {
        Map<String, List<Token>> tokens = new ConcurrentHashMap<>();
        Phaser phaser = new Phaser(services.size() + 1);
        for (ServiceInfo service : services) getFingerprints(tokens, service, phaser);
        phaser.arriveAndAwaitAdvance();
        return tokens;
    }

    // A container may be unable to provide its fingerprints for a number of reasons, which may be OK, so
    // we only track those containers which return an OK response, but we do require at least one such response.
    private void getFingerprints(Map<String, List<Token>> hostTokens, ServiceInfo service, Phaser phaser) {
        URI uri = HttpURL.create(Scheme.http,
                                 DomainName.of(service.getHostName()),
                                 service.getPorts().stream().filter(port -> port.getTags().stream().anyMatch("http"::equals)).findAny().get().getPort(),
                                 Path.parse("/data-plane-tokens/v1"))
                         .asURI();
        httpClient.execute(SimpleRequestBuilder.get(uri).build(), new FutureCallback<>() {
            @Override public void completed(SimpleHttpResponse result) {
                if (result.getCode() == 200) hostTokens.put(service.getHostName(), parseTokens(result));
                phaser.arrive();
            }
            @Override public void failed(Exception ex) { phaser.arrive(); }
            @Override public void cancelled() { phaser.arrive(); }
        });
    }

    private static List<Token> parseTokens(SimpleHttpResponse response) {
        return entriesStream(jsonToSlime(response.getBodyBytes()).get().field("tokens"))
                .map(entry -> new Token(entry.field("id").asString(),
                                        entriesStream(entry.field("fingerprints")).map(Inspector::asString).toList()))
                .toList();
    }

    private static CloseableHttpAsyncClient createHttpClient() {
        return VespaAsyncHttpClientBuilder
                .create(tlsStrategy -> PoolingAsyncClientConnectionManagerBuilder.create()
                                                                                 .setTlsStrategy(tlsStrategy)
                                                                                 .setDefaultConnectionConfig(ConnectionConfig.custom()
                                                                                                                             .setConnectTimeout(Timeout.ofSeconds(2))
                                                                                                                             .build())
                                                                                 .build())
                .setIOReactorConfig(IOReactorConfig.custom()
                                                   .setSoTimeout(Timeout.ofSeconds(2))
                                                   .build())
                .setDefaultRequestConfig(
                        RequestConfig.custom()
                                     .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                                     .setResponseTimeout(Timeout.ofSeconds(2))
                                     .build())
                .setUserAgent("data-plane-token-client")
                .build();
    }

    @Override
    public void close() throws Exception {
        httpClient.close(CloseMode.GRACEFUL);
        httpClient.awaitShutdown(TimeValue.ofSeconds(10));
    }

}
