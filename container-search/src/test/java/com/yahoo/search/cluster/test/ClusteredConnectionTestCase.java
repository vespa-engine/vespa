// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster.test;

import com.yahoo.component.ComponentId;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.ClusterSearcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author bratseth
 */
public class ClusteredConnectionTestCase {

    @Test
    public void testClustering() {
        Connection connection0=new Connection("0");
        Connection connection1=new Connection("1");
        Connection connection2=new Connection("2");
        List<Connection> connections=new ArrayList<>();
        connections.add(connection0);
        connections.add(connection1);
        connections.add(connection2);
        MyBackend myBackend=new MyBackend(new ComponentId("test"),connections);

        Result r;
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(1));
        assertEquals("from:2",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(2));
        assertEquals("from:1",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(3));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());

        connection2.setInService(false);
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(1));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(2));
        assertEquals("from:1",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(3));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());

        connection1.setInService(false);
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(1));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(2));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(3));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());

        connection0.setInService(false);
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertEquals("Failed calling connection '2' in searcher 'test' for query 'NULL': Connection failed",
                     r.hits().getError().getDetailedMessage());

        connection0.setInService(true);
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(1));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(2));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(3));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());

        connection1.setInService(true);
        connection2.setInService(true);
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(1));
        assertEquals("from:2",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(2));
        assertEquals("from:1",r.hits().get(0).getId().stringValue());
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(3));
        assertEquals("from:0",r.hits().get(0).getId().stringValue());
    }

    @Test
    public void testClusteringWithPing() {
        Connection connection0=new Connection("0");
        Connection connection1=new Connection("1");
        Connection connection2=new Connection("2");
        List<Connection> connections=new ArrayList<>();
        connections.add(connection0);
        connections.add(connection1);
        connections.add(connection2);
        MyBackend myBackend=new MyBackend(new ComponentId("test"),connections);

        Result r;

        // Note that we cannot make any successful queries here or we have to wait 10 seconds for
        // the traffic monitor to agree that these nodes are really not responding

        connection2.setInService(false);
        connection1.setInService(false);
        connection0.setInService(false);
        forcePing(myBackend);
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertEquals("No backends in service. Try later",r.hits().getError().getMessage());

        connection2.setInService(true);
        connection1.setInService(true);
        connection0.setInService(true);
        forcePing(myBackend);
        r=new Execution(myBackend, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertNull(r.hits().getError());
    }

    private void forcePing(MyBackend myBackend) {
        myBackend.getMonitor().ping(Executors.newCachedThreadPool(new DaemonThreadFactory()));
        Thread.yield();
    }

    /** Represents a connection, e.g over http, in this test */
    private static class Connection {

        private String id;

        private boolean inService=true;

        public Connection(String id) {
            this.id=id;
        }

        /** This is used for both fill, pings and queries */
        public String getResponse() {
            if (!inService) throw new RuntimeException("Connection failed");
            return id;
        }

        public void setInService(boolean inservice) {
            this.inService=inservice;
        }

        public String toString() {
            return "connection '" + id + "'";
        }

    }

    /**
     * This is the kind of searcher which will be implemented by those who wish to create a searcher which is a
     * client to a clustered service.
     * The goal is to make writing this correctly as simple as possible.
     */
    private static class MyBackend extends ClusterSearcher<Connection> {

        public MyBackend(ComponentId componentId, List<Connection> connections) {
            super(componentId,connections,false);
        }

        @Override
        public Result search(Query query,Execution execution,Connection connection) {
            Result result=new Result(query);
            result.hits().add(new Hit("from:" + connection.getResponse()));
            return result;
        }

        @Override
        public void fill(Result result,String summary,Execution execution,Connection connection) {
            result.hits().get(0).fields().put("filled",connection.getResponse());
        }

        @Override
        public Pong ping(Ping ping,Connection connection) {
            Pong pong=new Pong();
            if (connection.getResponse()==null)
                pong.addError(ErrorMessage.createBackendCommunicationError("No ping response from '" + connection + "'"));
            return pong;
        }

    }

    /** A query with a predictable hash function */
    private static class SimpleQuery extends Query {

        int hashValue;

        public SimpleQuery(int hashValue) {
            this.hashValue = hashValue;
        }

        @Override
        public int hashCode() {
            return hashValue;
        }

    }

}
