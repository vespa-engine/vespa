// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.yolean.Exceptions;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;

/**
 * @author mpolden
 * @author jonmv
 */
public class LoadBalancersV1ApiHandler extends ThreadedHttpRequestHandler {

    private final NodeRepository nodeRepository;

    @Inject
    public LoadBalancersV1ApiHandler(ThreadedHttpRequestHandler.Context parentCtx, NodeRepository nodeRepository) {
        super(parentCtx);
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            return switch (request.getMethod()) {
                case GET -> get(request);
                case PUT -> put(request);
                default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            };
        } catch (NotFoundException e) {
            return ErrorResponse.notFoundError(Exceptions.toMessageString(e));
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/loadbalancers/v1")) return new LoadBalancersResponse(request, nodeRepository);
        throw new NotFoundException("Nothing at " + path);
    }

    private HttpResponse put(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/loadbalancers/v1/state/{state}/{id}")) return setState(path.get("state"), path.get("id"));
        throw new NotFoundException("Nothing at " + path);
    }

    private HttpResponse setState(String state, String id) {
        LoadBalancer.State toState = stateFrom(state);
        LoadBalancerId loadBalancerId = LoadBalancerId.fromSerializedForm(id);
        try (var lock = nodeRepository.database().lock(loadBalancerId.application(), Duration.ofSeconds(1))) {
            LoadBalancer loadBalancer = nodeRepository.database().readLoadBalancer(loadBalancerId)
                                                      .orElseThrow(() -> new NotFoundException(loadBalancerId + " does not exist"));
            nodeRepository.database().writeLoadBalancer(loadBalancer.with(toState, nodeRepository.clock().instant()),
                                                        loadBalancer.state());
        }
        return new MessageResponse("Moved " + loadBalancerId + " to " + toState);
    }

    private LoadBalancer.State stateFrom(String state) {
        return switch (state) {
            case "reserved" -> LoadBalancer.State.reserved;
            case "inactive" -> LoadBalancer.State.inactive;
            case "active" -> LoadBalancer.State.active;
            case "removable" -> LoadBalancer.State.removable;
            default -> throw new IllegalArgumentException("Invalid state '" + state + "'");
        };
    }

}
