// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.model.api.ApplicationRoles;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.slime.ArrayInserter;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Injector;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectInserter;
import com.yahoo.slime.ObjectSymbolInserter;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeInserter;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointSerializer;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataSerializer;
import org.junit.Test;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class PrepareParamsTest {

    private static final String vespaVersion = "6.37.49";
    private static final String baseRequest = "http://foo:19071/application/v2/tenant/foo/application/bar";
    private static final String request = baseRequest + "?" +
                                          PrepareParams.DRY_RUN_PARAM_NAME + "=true&" +
                                          PrepareParams.VERBOSE_PARAM_NAME+ "=true&" +
                                          PrepareParams.IGNORE_VALIDATION_PARAM_NAME + "=false&" +
                                          PrepareParams.APPLICATION_NAME_PARAM_NAME + "=baz&" +
                                          PrepareParams.VESPA_VERSION_PARAM_NAME + "=" + vespaVersion;

    private static final String json = "{\n" +
                                       "\"" + PrepareParams.DRY_RUN_PARAM_NAME + "\": true,\n" +
                                       "\"" + PrepareParams.VERBOSE_PARAM_NAME+ "\": true,\n" +
                                       "\"" + PrepareParams.IGNORE_VALIDATION_PARAM_NAME + "\": false,\n" +
                                       "\"" + PrepareParams.APPLICATION_NAME_PARAM_NAME + "\":\"baz\",\n" +
                                       "\"" + PrepareParams.VESPA_VERSION_PARAM_NAME + "\":\"" + vespaVersion + "\"\n" +
                                       "}";

    @Test
    public void testCorrectParsing() {
        PrepareParams prepareParams = createParams("http://foo:19071/application/v2/", TenantName.defaultName());

        assertThat(prepareParams.getApplicationId(), is(ApplicationId.defaultId()));
        assertFalse(prepareParams.isDryRun());
        assertFalse(prepareParams.isVerbose());
        assertFalse(prepareParams.ignoreValidationErrors());
        assertThat(prepareParams.vespaVersion(), is(Optional.<String>empty()));
        assertTrue(prepareParams.getTimeoutBudget().hasTimeLeft());
        assertThat(prepareParams.containerEndpoints().size(), is(0));
    }

    @Test
    public void testCorrectParsingWithContainerEndpoints() throws IOException {
        var endpoints = List.of(new ContainerEndpoint("qrs1",
                                                      List.of("c1.example.com",
                                                              "c2.example.com")),
                                new ContainerEndpoint("qrs2",
                                                      List.of("c3.example.com",
                                                              "c4.example.com")));
        var param = "[\n" +
                    "  {\n" +
                    "    \"clusterId\": \"qrs1\",\n" +
                    "    \"names\": [\"c1.example.com\", \"c2.example.com\"]\n" +
                    "  },\n" +
                    "  {\n" +
                    "    \"clusterId\": \"qrs2\",\n" +
                    "    \"names\": [\"c3.example.com\", \"c4.example.com\"]\n" +
                    "  }\n" +
                    "]";

        var encoded = URLEncoder.encode(param, StandardCharsets.UTF_8);
        var prepareParams = createParams(request + "&" + PrepareParams.CONTAINER_ENDPOINTS_PARAM_NAME +
                                                   "=" + encoded, TenantName.from("foo"));
        assertEquals(endpoints, prepareParams.containerEndpoints());

        // Verify using json object
        var slime = SlimeUtils.jsonToSlime(json);
        new Injector().inject(ContainerEndpointSerializer.endpointListToSlime(endpoints).get(), new ObjectInserter(slime.get(), PrepareParams.CONTAINER_ENDPOINTS_PARAM_NAME));
        PrepareParams prepareParamsJson = PrepareParams.fromJson(SlimeUtils.toJsonBytes(slime), TenantName.from("foo"), Duration.ofSeconds(60));
        assertPrepareParamsEqual(prepareParams, prepareParamsJson);
    }

    @Test
    public void testCorrectParsingWithApplicationRoles() throws IOException {
        String req = request + "&" +
                     PrepareParams.APPLICATION_HOST_ROLE + "=hostRole&" +
                     PrepareParams.APPLICATION_CONTAINER_ROLE + "=containerRole";
        var prepareParams = createParams(req, TenantName.from("foo"));

        Optional<ApplicationRoles> applicationRoles = prepareParams.applicationRoles();
        assertTrue(applicationRoles.isPresent());
        assertEquals("hostRole", applicationRoles.get().applicationHostRole());
        assertEquals("containerRole", applicationRoles.get().applicationContainerRole());

        // Verify using json object
        var slime = SlimeUtils.jsonToSlime(json);
        var cursor = slime.get();
        cursor.setString(PrepareParams.APPLICATION_HOST_ROLE, "hostRole");
        cursor.setString(PrepareParams.APPLICATION_CONTAINER_ROLE, "containerRole");

        PrepareParams prepareParamsJson = PrepareParams.fromJson(SlimeUtils.toJsonBytes(slime), TenantName.from("foo"), Duration.ofSeconds(60));
        assertPrepareParamsEqual(prepareParams, prepareParamsJson);
    }

    @Test
    public void testQuotaParsing() throws IOException {
        var quotaParam = "{\"clusterSize\": 23, \"budget\": 23232323}";
        var quotaEncoded = URLEncoder.encode(quotaParam, StandardCharsets.UTF_8);
        var prepareParams = createParams(request + "&" + PrepareParams.QUOTA_PARAM_NAME + "=" + quotaEncoded, TenantName.from("foo"));
        assertEquals(23, (int) prepareParams.quota().get().maxClusterSize().get());
        assertEquals(23232323, (int) prepareParams.quota().get().budget().get());

        // Verify using json object
        var slime = SlimeUtils.jsonToSlime(json);
        new Injector().inject(SlimeUtils.jsonToSlime(quotaParam).get(), new ObjectInserter(slime.get(), PrepareParams.QUOTA_PARAM_NAME));
        PrepareParams prepareParamsJson = PrepareParams.fromJson(SlimeUtils.toJsonBytes(slime), TenantName.from("foo"), Duration.ofSeconds(60));
        assertPrepareParamsEqual(prepareParams, prepareParamsJson);
    }

    @Test
    public void testEndpointCertificateParsing() throws IOException {
        var certMeta = new EndpointCertificateMetadata("key", "cert", 3);
        var slime = new Slime();
        EndpointCertificateMetadataSerializer.toSlime(certMeta, slime.setObject());
        String encoded = URLEncoder.encode(new String(SlimeUtils.toJsonBytes(slime), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        var prepareParams = createParams(request + "&" + PrepareParams.ENDPOINT_CERTIFICATE_METADATA_PARAM_NAME + "=" + encoded, TenantName.from("foo"));
        assertTrue(prepareParams.endpointCertificateMetadata().isPresent());
        assertEquals("key", prepareParams.endpointCertificateMetadata().get().keyName());
        assertEquals("cert", prepareParams.endpointCertificateMetadata().get().certName());
        assertEquals(3, prepareParams.endpointCertificateMetadata().get().version());

        // Verify using json object
        var root = SlimeUtils.jsonToSlime(json);
        new Injector().inject(slime.get(), new ObjectInserter(root.get(), PrepareParams.ENDPOINT_CERTIFICATE_METADATA_PARAM_NAME));
        PrepareParams prepareParamsJson = PrepareParams.fromJson(SlimeUtils.toJsonBytes(root), TenantName.from("foo"), Duration.ofSeconds(60));
        assertPrepareParamsEqual(prepareParams, prepareParamsJson);
    }

    @Test
    public void compareEmptyUrlparamsVsJson() {
        TenantName tenantName = TenantName.from("foo");
        Duration barrierTimeout = Duration.ofSeconds(60);
        HttpRequest httpRequest = HttpRequest.createTestRequest(baseRequest, com.yahoo.jdisc.http.HttpRequest.Method.POST);
        PrepareParams urlPrepareParams = PrepareParams.fromHttpRequest(httpRequest, tenantName, barrierTimeout);
        PrepareParams jsonPrepareParams = PrepareParams.fromJson("{}".getBytes(StandardCharsets.UTF_8), tenantName, barrierTimeout);

        assertPrepareParamsEqual(urlPrepareParams, jsonPrepareParams);
    }

    @Test
    public void compareStandardUrlparamsVsJson() {
        TenantName tenantName = TenantName.from("foo");
        Duration barrierTimeout = Duration.ofSeconds(60);
        HttpRequest httpRequest = HttpRequest.createTestRequest(request, com.yahoo.jdisc.http.HttpRequest.Method.POST);
        PrepareParams urlPrepareParams = PrepareParams.fromHttpRequest(httpRequest, tenantName, barrierTimeout);
        PrepareParams jsonPrepareParams = PrepareParams.fromJson(json.getBytes(StandardCharsets.UTF_8), tenantName, barrierTimeout);
        assertPrepareParamsEqual(urlPrepareParams, jsonPrepareParams);
    }

    private void assertPrepareParamsEqual(PrepareParams urlParams, PrepareParams jsonParams) {
        assertEquals(urlParams.ignoreValidationErrors(), jsonParams.ignoreValidationErrors());
        assertEquals(urlParams.isDryRun(), jsonParams.isDryRun());
        assertEquals(urlParams.isVerbose(), jsonParams.isVerbose());
        assertEquals(urlParams.isBootstrap(), jsonParams.isBootstrap());
        assertEquals(urlParams.force(), jsonParams.force());
        assertEquals(urlParams.waitForResourcesInPrepare(), jsonParams.waitForResourcesInPrepare());
        assertEquals(urlParams.getApplicationId(), jsonParams.getApplicationId());
        assertEquals(urlParams.getTimeoutBudget().timeout(), jsonParams.getTimeoutBudget().timeout());
        assertEquals(urlParams.vespaVersion(), jsonParams.vespaVersion());
        assertEquals(urlParams.containerEndpoints(), jsonParams.containerEndpoints());
        assertEquals(urlParams.endpointCertificateMetadata(), jsonParams.endpointCertificateMetadata());
        assertEquals(urlParams.dockerImageRepository(), jsonParams.dockerImageRepository());
        assertEquals(urlParams.athenzDomain(), jsonParams.athenzDomain());
        assertEquals(urlParams.applicationRoles(), jsonParams.applicationRoles());
        assertEquals(urlParams.quota(), jsonParams.quota());
        assertEquals(urlParams.tenantSecretStores(), jsonParams.tenantSecretStores());
    }

    // Create PrepareParams from a request (based on uri and tenant name)
    private static PrepareParams createParams(String uri, TenantName tenantName) {
        return PrepareParams.fromHttpRequest(
                HttpRequest.createTestRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.PUT),
                tenantName,
                Duration.ofSeconds(60));
    }

}
