// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.zone.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.EnvironmentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;
import com.yahoo.vespa.hosted.controller.api.identifiers.RegionId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Aka the controller proxy service.
 *
 * Proxies calls to correct config server with the additional feature of
 * retry and fail detection (ping).
 */
@Path(ZoneApiV2.API_VERSION)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ZoneApiV2 {

    String API_VERSION = "v2";

    @GET
    @Path("/")
    ZoneReferences listZones();

    @GET
    @Path("/{environment}/{region}/{proxy_request: .+}")
    Response proxyGet(
            @PathParam("environment") String env,
            @PathParam("region") String region,
            @PathParam("proxy_request") String proxyRequest);

    @POST
    @Path("/{environment}/{region}/{proxy_request: .+}")
    Response proxyPost(
            @PathParam("environment") String env,
            @PathParam("region") String region,
            @PathParam("proxy_request") String proxyRequest);

    @PUT
    @Path("/{environment}/{region}/{proxy_request: .+}")
    Response proxyPut(
            @PathParam("environment") String env,
            @PathParam("region") String region,
            @PathParam("proxy_request") String proxyRequest);

    @DELETE
    @Path("/{environment}/{region}/{proxy_request: .+}")
    Response proxyDelete(
            @PathParam("environment") String env,
            @PathParam("region") String region,
            @PathParam("proxy_request") String proxyRequest);

    // Explicit mappings of some proxy requests (to enable creation of proxy clients with javax.ws.rs)

    @GET
    @Path("/{environmentId}/{regionId}/application/v2/tenant/{tenantId}/application/{applicationId}/environment/{environmentId}/region/{regionId}/instance/{instanceId}/serviceconverge")
    Response waitForConfigConvergeV2(@PathParam("tenantId") TenantId tenantId,
                                          @PathParam("applicationId") ApplicationId applicationId,
                                          @PathParam("environmentId") EnvironmentId environmentId,
                                          @PathParam("regionId") RegionId regionId,
                                          @PathParam("instanceId") InstanceId instanceId,
                                          @QueryParam("timeout") long timeoutInSeconds);
    @GET
    @Path("/{environmentId}/{regionId}/application/v2/tenant/{tenantId}/application/{applicationId}/environment/{environmentId}/region/{regionId}/instance/{instanceId}/serviceconverge/{host}")
    Response waitForConfigConvergeV2(@PathParam("tenantId") TenantId tenantId,
                                            @PathParam("applicationId") ApplicationId applicationId,
                                            @PathParam("environmentId") EnvironmentId environmentId,
                                            @PathParam("regionId") RegionId regionId,
                                            @PathParam("instanceId") InstanceId instanceId,
                                            @PathParam("host") String host,
                                            @QueryParam("timeout") long timeoutInSeconds);

    @GET
    @Path("/{environmentId}/{regionId}/config/v2/tenant/{tenantId}/application/{applicationId}/prelude.fastsearch.documentdb-info/{clusterid}/search/cluster.{clusterid}")
    JsonNode getConfigWithDocumentTypes(@PathParam("tenantId") TenantId tenantId,
                                        @PathParam("applicationId") ApplicationId applicationId,
                                        @PathParam("environmentId") EnvironmentId environmentId,
                                        @PathParam("regionId") RegionId regionId,
                                        @PathParam("clusterid") String clusterid,
                                        @QueryParam("timeout") long timeoutInSeconds);

    @GET
    @Path("/{environmentId}/{regionId}/config/v2/tenant/{tenantId}/application/{applicationId}/cloud.config.cluster-list")
    JsonNode getVespaConfigClusterList(@PathParam("tenantId") TenantId tenantId,
                                       @PathParam("applicationId") ApplicationId applicationId,
                                       @PathParam("environmentId") EnvironmentId environmentId,
                                       @PathParam("regionId") RegionId regionId,
                                       @QueryParam("timeout") long timeoutInSeconds);
}
