// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTaskScheduler;
import com.yahoo.vespa.clustercontroller.core.restapiv2.requests.ClusterListRequest;
import com.yahoo.vespa.clustercontroller.core.restapiv2.requests.ClusterStateRequest;
import com.yahoo.vespa.clustercontroller.core.restapiv2.requests.NodeStateRequest;
import com.yahoo.vespa.clustercontroller.core.restapiv2.requests.ServiceStateRequest;
import com.yahoo.vespa.clustercontroller.core.restapiv2.requests.SetNodeStateRequest;
import com.yahoo.vespa.clustercontroller.core.restapiv2.requests.SetNodeStatesForClusterRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.StateRestAPI;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InternalFailure;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.OtherMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.UnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.SetResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class ClusterControllerStateRestAPI implements StateRestAPI {

    private static final Logger log = Logger.getLogger(ClusterControllerStateRestAPI.class.getName());

    public interface FleetControllerResolver {
        Map<String, RemoteClusterControllerTaskScheduler> getFleetControllers();
    }

    public static class Socket {
        public final String hostname;
        public final int port;

        public Socket(String hostname, int port) {
            this.hostname = hostname;
            this.port = port;
        }

        @Override
        public String toString() {
            return hostname + ":" + port;
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, port);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Socket)) return false;
            Socket socket = (Socket) object;
            return Objects.equals(hostname, socket.hostname) && port == socket.port;
        }
    }

    private final FleetControllerResolver fleetControllerResolver;
    private final Map<Integer, Socket> clusterControllerSockets;

    public ClusterControllerStateRestAPI(FleetControllerResolver resolver,
                                         Map<Integer, Socket> clusterControllerSockets)
    {
        fleetControllerResolver = resolver;
        this.clusterControllerSockets = clusterControllerSockets;
    }

    @Override
    public UnitResponse getState(final UnitStateRequest request) throws StateRestApiException {
        log.finest("Got getState() request");
        UnitPathResolver<UnitResponse> resolver = new UnitPathResolver<>(fleetControllerResolver.getFleetControllers());
        Request<? extends UnitResponse> req = resolver.visit(
                request.getUnitPath(), new UnitPathResolver.Visitor<>()
        {
            @Override
            public Request<? extends UnitResponse> visitGlobal() {
                return new ClusterListRequest(request.getRecursiveLevels(), fleetControllerResolver);
            }
            @Override
            public Request<? extends UnitResponse> visitCluster(Id.Cluster id)  {
                return new ClusterStateRequest(id, request.getRecursiveLevels());
            }
            @Override
            public Request<? extends UnitResponse> visitService(Id.Service id)  {
                return new ServiceStateRequest(id, request.getRecursiveLevels());
            }
            @Override
            public Request<? extends UnitResponse> visitNode(Id.Node id)  {
                return new NodeStateRequest(id);
            }
        });
        if (req instanceof ClusterListRequest) {
            log.fine("Got cluster list request");
            req.doRemoteFleetControllerTask(null);
            req.notifyCompleted();
            log.finest("Completed processing cluster list request");
        } else {
            log.fine("Scheduling state request: " + req.getClass().toString());
            resolver.resolveFleetController(request.getUnitPath()).schedule(req);
            log.finest("Scheduled state request: " + req.getClass().toString());
            req.waitForCompletion();
            log.finest("Completed processing state request: " + req.getClass().toString());
        }
        try {
            return req.getResult();
        } catch (OtherMasterIndexException e) {
            createAndThrowOtherMasterException(e.getMasterIndex());
            throw new RuntimeException("Should not get here");
        }
    }

    @Override
    public SetResponse setUnitState(final SetUnitStateRequest request) throws StateRestApiException {
        UnitPathResolver<SetResponse> resolver = new UnitPathResolver<>(fleetControllerResolver.getFleetControllers());
        Request<? extends SetResponse> req = resolver.visit(request.getUnitPath(),
                new UnitPathResolver.AbstractVisitor<>(request.getUnitPath(),
                                                       "State can only be set at cluster or node level")
        {
            @Override
            public Request<? extends SetResponse> visitCluster(Id.Cluster id) {
                return new SetNodeStatesForClusterRequest(id, request);
            }
            @Override
            public Request<? extends SetResponse> visitNode(Id.Node id) {
                return new SetNodeStateRequest(id, request);
            }
        });
        resolver.resolveFleetController(request.getUnitPath()).schedule(req);
        req.waitForCompletion();
        try{
            return req.getResult();
        } catch (OtherMasterIndexException e) {
            createAndThrowOtherMasterException(e.getMasterIndex());
            throw new RuntimeException("Should not get here");
        }
    }

    // Will always throw an exception.
    private void createAndThrowOtherMasterException(int master) throws StateRestApiException {
        Socket s = clusterControllerSockets.get(master);
        // TODO: Consider changing return status code of this call to 503.
        if (s == null) throw new InternalFailure(
                "Cannot create redirect response to master at index " + master
              + ", as we failed to get correct config to detect running cluster controllers.");
        throw new OtherMasterException(s.hostname, s.port);
    }
}
