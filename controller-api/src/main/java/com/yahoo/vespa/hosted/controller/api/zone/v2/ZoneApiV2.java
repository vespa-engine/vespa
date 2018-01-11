// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.zone.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.EnvironmentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;
import com.yahoo.vespa.hosted.controller.api.identifiers.RegionId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.MaintenanceJobList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.Set;

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


    @POST
    @Path("/{environmentId}/{regionId}/nodes/v2/node")
    String addNodes(@PathParam("environmentId") EnvironmentId environmentId,
                    @PathParam("regionId") RegionId regionId,
                    Collection<NodeRepositoryNode> node);

    @DELETE
    @Path("/{environmentId}/{regionId}/nodes/v2/node/{hostname}")
    String deleteNode(@PathParam("environmentId") EnvironmentId environmentId,
                      @PathParam("regionId") RegionId regionId,
                      @PathParam("hostname") String hostname);

    @GET
    @Path("/{environmentId}/{regionId}/nodes/v2/node/{hostname}")
    NodeRepositoryNode getNode(@PathParam("environmentId") EnvironmentId environmentId,
                               @PathParam("regionId") RegionId regionId,
                               @PathParam("hostname") String hostname);

    @POST
    @Path("/{environmentId}/{regionId}/nodes/v2/node/{hostname}")
    String patchNode(@PathParam("environmentId") EnvironmentId environmentId,
                     @PathParam("regionId") RegionId regionId,
                     @PathParam("hostname") String hostname,
                     NodeRepositoryNode patchValues,
                     @HeaderParam("X-HTTP-Method-Override") String patchOverride);

    @GET
    @Path("/{environmentId}/{regionId}/nodes/v2/node/")
    NodeList listNodes(@PathParam("environmentId") EnvironmentId environmentId,
                       @PathParam("regionId") RegionId regionId,
                       @QueryParam("recursive") boolean recursive);

    @GET
    @Path("/{environmentId}/{regionId}/nodes/v2/node/")
    NodeList listNodes(@PathParam("environmentId") EnvironmentId environmentId,
                       @PathParam("regionId") RegionId regionId,
                       @QueryParam("application") String applicationString,
                       @QueryParam("recursive") boolean recursive);

    @PUT
    @Path("/{environmentId}/{regionId}/nodes/v2/state/{state}/{hostname}")
    String setNodeState(@PathParam("environmentId") EnvironmentId environmentId,
                        @PathParam("regionId") RegionId regionId,
                        @PathParam("state") NodeState state,
                        @PathParam("hostname") String hostname);

    @POST
    @Path("/{environmentId}/{regionId}/nodes/v2/command/reboot")
    String rebootNode(@PathParam("environmentId") EnvironmentId environmentId,
                      @PathParam("regionId") RegionId regionId,
                      @QueryParam("hostname") String hostname);

    @POST
    @Path("/{environmentId}/{regionId}/nodes/v2/command/restart")
    String restartNode(@PathParam("environmentId") EnvironmentId environmentId,
                       @PathParam("regionId") RegionId regionId,
                       @QueryParam("hostname") String hostname);

    @GET
    @Path("/{environmentId}/{regionId}/nodes/v2/maintenance/")
    MaintenanceJobList listMaintenanceJobs(@PathParam("environmentId") EnvironmentId environmentId,
                                           @PathParam("regionId") RegionId regionId);

    @POST
    @Path("/{environmentId}/{regionId}/nodes/v2/maintenance/inactive/{jobname}")
    String disableMaintenanceJob(@PathParam("environmentId") EnvironmentId environmentId,
                                 @PathParam("regionId") RegionId regionId,
                                 @PathParam("jobname") String jobname);

    @DELETE
    @Path("/{environmentId}/{regionId}/nodes/v2/maintenance/inactive/{jobname}")
    String enableMaintenanceJob(@PathParam("environmentId") EnvironmentId environmentId,
                                @PathParam("regionId") RegionId regionId,
                                @PathParam("jobname") String jobname);


    @GET
    @Path("/{environmentId}/{regionId}/orchestrator/v1/suspensions/applications")
    Set<String> getApplications(@PathParam("environmentId") EnvironmentId environmentId,
                                @PathParam("regionId") RegionId regionId);

    @GET
    @Path("/{environmentId}/{regionId}/orchestrator/v1/suspensions/applications/{application}")
    void getApplication(@PathParam("environmentId") EnvironmentId environmentId,
                        @PathParam("regionId") RegionId regionId,
                        @PathParam("application") String applicationIdString);

    @POST
    @Path("/{environmentId}/{regionId}/orchestrator/v1/suspensions/applications")
    void suspendApplication(@PathParam("environmentId") EnvironmentId environmentId,
                            @PathParam("regionId") RegionId regionId,
                            String applicationIdString);

    @DELETE
    @Path("/{environmentId}/{regionId}/orchestrator/v1/suspensions/applications/{application}")
    void resumeApplication(@PathParam("environmentId") EnvironmentId environmentId,
                           @PathParam("regionId") RegionId regionId,
                           @PathParam("application") String applicationIdString);
}
