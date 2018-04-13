// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import com.yahoo.search.result.Hit;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

/**
 * Runs a query thru the configured search chain from a real http channel
 * to a mock fdispatch channel. The setup is rather complicated, as the point is
 * to span as much of the processing from query to result as possible.
 *
 * @author bratseth
 */
public class IntegrationTestCase {

    public static class SecondSearcher extends Searcher {
        public Result search(com.yahoo.search.Query query, Execution execution) {
            Result result = execution.search(query);
            result.hits().add(new Hit("searcher:2",1000));
            return result;
        }
    }
    public static class ThirdSearcher extends Searcher {
        public Result search(com.yahoo.search.Query query, Execution execution) {
            Result result = execution.search(query);
            result.hits().add(new Hit("searcher:3",1000));
            return result;
        }
    }

    @Test
    public void testQuery() throws java.io.IOException {
    /*
       TODO: (JSB) This blocks forever on Linux (not Windows) because
             the ServerSocketChannel.accept method in Server
         seems to starve the test running thread,
         causing it to get stuck in waitForServerInitialization.
         This must be caused by starvation because
         replacing the test with Thread.sleep(n)
         gives the same result if n is large enough (2000
         is large enough, 1000 is not.
         Resolve this in some way, perhaps by switching to
         non-blocking io (and then remember to remove the next line).
     */
    }

    /*
        if (1==1) return;
        ServerThread serverThread=new ServerThread();
        try {
            serverThread.start();
            waitForServerInitialization();
            insertMockFs4Channel();
            ByteBuffer buffer=ByteBuffer.allocate(4096);
            buffer.put(getBytes("GET /?query=hans HTTP/1.1\n\n"));
            SocketChannel socket=
                SocketChannel.open(new InetSocketAddress(Server.get().getHost(),
                                                         Server.get().getPort()));
            buffer.flip();
            socket.write(buffer);

            buffer.clear();
            socket.read(buffer);
            // TODO: Validate return too

        }
        finally {
            serverThread.interrupt();
        }
    }

    private static void assertCorrectQueryData(QueryPacket packet) {
        assertEquals("Query x packet " +
                     "[query: query 'RANK hans bcatpat.bidxpatlvl1:hans' [path='/']]",
                     packet.toString());
    }

    private void insertMockFs4Channel() {
        Searcher current=SearchChain.get();
        while (current.getChained().getChained()!=null)
            current=current.getChained();
        assertTrue(current.getChained() instanceof FastSearcher);
        FastSearcher mockFastSearcher=
            new FastSearcher(new MockFSChannel(),
                            "file:etc/qr-summary.cf",
                             "testhittype");
        current.setChained(mockFastSearcher);
    }

    private void waitForServerInitialization() {
        int sleptMs=0;
        while (Server.get().getHost()==null) {
            try { Thread.sleep(10); } catch (Exception e) {}
            sleptMs+=10;
        }
    }

    private class ServerThread extends Thread {

        public void run() {
            try {
                Server.get().start(8081,new SearchRequestHandler());
            }
            catch (java.io.IOException e) {
                throw new RuntimeException("Failed",e);
            }
        }
    }

    private byte[] getBytes(String string) {
        try {
            return string.getBytes("utf-8");
        }
        catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Won't happen",e);
        }
    }
    */
    /** A channel which returns hardcoded packets of the same type as fdispatch */
    /*
    private static class MockFSChannel extends Channel {

        public MockFSChannel() {}

        public void sendPacket(Packet packet) {
            if (packet instanceof QueryPacket) {
                assertCorrectQueryData((QueryPacket)packet);
            }
            else {
                throw new RuntimeException("Mock channel don't know what to reply to " +
                                           packet);
            }
        }

        public Packet[] receivePackets() {
            List packets=new java.util.ArrayList();
            QueryResultPacket result=QueryResultPacket.create();
            result.addDocument(new DocumentInfo(123,2003,234,1000,1));
            result.addDocument(new DocumentInfo(456,1855,234,1001,1));
            packets.add(result);
            addDocsums(packets);
            return (Packet[])packets.toArray(new Packet[packets.size()]);
        }

        private void addDocsums(List packets) {
            ByteBuffer buffer=createDocsumPacketData(DocsumDefinitionTestCase.docsum4);
            buffer.position(0);
            packets.add(PacketDecoder.decode(buffer));

            buffer=createDocsumPacketData(DocsumDefinitionTestCase.docsum4);
            buffer.position(0);
            packets.add(PacketDecoder.decode(buffer));

            packets.add(EolPacket.create());
        }

        private ByteBuffer createDocsumPacketData(byte[] docsumData) {
            ByteBuffer buffer=ByteBuffer.allocate(docsumData.length+12+4);
            buffer.limit(buffer.capacity());
            buffer.position(0);
            buffer.putInt(docsumData.length+8+4);
            buffer.putInt(205); // Docsum packet code
            buffer.putInt(0);
            buffer.putInt(0);
            buffer.put(docsumData);
            return buffer;
        }

    }
    */
}
