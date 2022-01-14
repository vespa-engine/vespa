// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import javax.inject.Inject;

/**
 * @author mpolden
 * @author jonmv
 */
public class LoadBalancersV1ApiHandler extends RestApiRequestHandler<LoadBalancersV1ApiHandler> {

    private final NodeRepository nodeRepository;

    @Inject
    public LoadBalancersV1ApiHandler(ThreadedHttpRequestHandler.Context parentCtx, NodeRepository nodeRepository) {
        super(parentCtx, LoadBalancersV1ApiHandler::createRestApiDefinition);
        this.nodeRepository = nodeRepository;
    }

    private static RestApi createRestApiDefinition(LoadBalancersV1ApiHandler self) {
        return RestApi.builder()
                      .addRoute(RestApi.route("/loadbalancers/v1")
                                       .get(self::getLoadBalancers))
                      .build();
    }

    private HttpResponse getLoadBalancers(RestApi.RequestContext context) {
        return new LoadBalancersResponse(context.request(), nodeRepository);
    }

}
