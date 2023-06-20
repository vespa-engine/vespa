// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTaskScheduler;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.MissingUnitException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.OperationNotSupportedForUnitException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnitPathResolver<T> {

    public interface Visitor<T> {

        Request<? extends T> visitGlobal() throws StateRestApiException;
        Request<? extends T> visitCluster(Id.Cluster id) throws StateRestApiException;
        Request<? extends T> visitService(Id.Service id) throws StateRestApiException;
        Request<? extends T> visitNode(Id.Node id) throws StateRestApiException;

    }

    public static abstract class AbstractVisitor<T> implements Visitor<T> {

        private final List<String> path;
        private final String failureMessage;

        public AbstractVisitor(List<String> path, String failureMessage) {
            this.path = path;
            this.failureMessage = failureMessage;
        }
        private Request<T> fail() throws StateRestApiException {
            throw new OperationNotSupportedForUnitException(path, failureMessage);
        }

        public Request<? extends T> visitGlobal() throws StateRestApiException { return fail(); }
        public Request<? extends T> visitCluster(Id.Cluster id) throws StateRestApiException { return fail(); }
        public Request<? extends T> visitService(Id.Service id) throws StateRestApiException { return fail(); }
        public Request<? extends T> visitNode(Id.Node id) throws StateRestApiException { return fail(); }

    }

    private final Map<String, RemoteClusterControllerTaskScheduler> fleetControllers;

    public UnitPathResolver(Map<String, RemoteClusterControllerTaskScheduler> fleetControllers) {
        this.fleetControllers = new HashMap<>(fleetControllers);
    }

    public RemoteClusterControllerTaskScheduler resolveFleetController(List<String> path) throws StateRestApiException {
        if (path.size() == 0) return null;
        RemoteClusterControllerTaskScheduler fc = fleetControllers.get(path.get(0));
        if (fc == null) {
            throw new MissingUnitException(path, 0);
        }
        return fc;
    }

    public Request<? extends T> visit(List<String> path, Visitor<T> visitor) throws StateRestApiException {
        if (path.size() == 0) {
            return visitor.visitGlobal();
        }
        RemoteClusterControllerTaskScheduler fc = fleetControllers.get(path.get(0));
        if (fc == null) throw new MissingUnitException(path, 0);
        Id.Cluster cluster = new Id.Cluster(path.get(0));
        if (path.size() == 1) {
            return visitor.visitCluster(cluster);
        }
        Id.Service service;
        try{
            service = new Id.Service(cluster, NodeType.get(path.get(1)));
        } catch (IllegalArgumentException e) {
            throw new MissingUnitException(path, 1);
        }
        if (path.size() == 2) {
            return visitor.visitService(service);
        }
        Id.Node node;
        try{
            node = new Id.Node(service, Integer.parseInt(path.get(2)));
        } catch (NumberFormatException e) {
            throw new MissingUnitException(path, 2);
        }
        if (path.size() == 3) {
            return visitor.visitNode(node);
        }
        throw new MissingUnitException(path, 4);
    }

}
