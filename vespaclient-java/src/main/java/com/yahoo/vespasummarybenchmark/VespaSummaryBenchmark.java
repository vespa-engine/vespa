// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasummarybenchmark;

import com.yahoo.compress.CompressionType;
import com.yahoo.document.GlobalId;
import com.yahoo.document.idstring.IdString;
import com.yahoo.document.serialization.DeserializationException;
import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int8Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.Values;
import com.yahoo.log.LogSetup;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * This is used for testing and benchmarking rpc docsum interface.
 * time vespa-summary-benchmark file-containing-docids connectionspec summary-class repetitions threads
 * fx ' time vespa-summary-benchmark feed.xml tcp/localhost:19115 keyvaluesummary 10000 32'
 *
 * @author baldersheim
 */
public class VespaSummaryBenchmark {

    private final Supervisor supervisor = new Supervisor(new Transport("client"));
    private static final LZ4Factory lz4Factory = LZ4Factory.fastestInstance();

    private VespaSummaryBenchmark() { }

    private static List<String> getDocIds(String fileName) {
        try {
            FileInputStream fstream = new FileInputStream(fileName);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;

            List<String> docIds = new ArrayList<>();
            while ((strLine = br.readLine()) != null) {
                docIds.add(strLine);
            }
            in.close();
            return docIds;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Target> getTargets(String connectionSpec, int numTargets) {
        List<Target> targets = new ArrayList<>(numTargets);
        for ( int i=0; i < numTargets; i++) {
            targets.add(supervisor.connect(new Spec(connectionSpec)));
        }
        return targets;
    }

    private static Slime createDocsumRequest(String summaryClass, List<GlobalId> gids) {
        Slime docsumRequest = new Slime();
        Cursor root = docsumRequest.setObject();
        root.setString("class", summaryClass);
        Cursor gidCursor = root.setArray("gids");
        for (GlobalId gid : gids) {
            gidCursor.addData(gid.getRawId());
        }
        return docsumRequest;
    }

    private static class Waiter implements RequestWaiter {

        int waitingFor;
        boolean dump;

        Waiter(int expect, boolean dump) {
            waitingFor = expect;
            this.dump = dump;
        }

        private void print(Request request) {
            Values ret = request.returnValues();
            CompressionType type = CompressionType.valueOf(ret.get(0).asInt8());
            int uncompressedSize = ret.get(1).asInt32();
            byte [] blob = ret.get(2).asData();
            if (type == CompressionType.LZ4) {
                LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();
                byte [] uncompressed = new byte [uncompressedSize];
                int compressedLength = decompressor.decompress(blob, 0, uncompressed, 0, uncompressedSize);
                if (compressedLength != blob.length) {
                    throw new DeserializationException("LZ4 decompression failed. compressed size does not match. Expected " + blob.length + ". Got " + compressedLength);
                }
                blob = uncompressed;
            }
            Slime slime = BinaryFormat.decode(blob);
            try {
                new JsonFormat(true).encode(System.out, slime);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void handleRequestDone(Request request) {
            synchronized (this) {
                if (dump) {
                    print(request);
                    dump = false;
                }
                waitingFor--;
                if (waitingFor == 0) {
                    this.notifyAll();
                }
            }
        }
        void waitForReplies() throws InterruptedException {
            synchronized (this) {
                while (waitingFor > 0) {
                    this.wait();
                }
            }
        }
    }

    private static void fetchDocIds(String summaryClass, List<Target> targets, List<GlobalId> gids, boolean dump) {
        Slime docsumRequest = createDocsumRequest(summaryClass, gids);
        byte [] blob = BinaryFormat.encode(docsumRequest);
        Waiter waiter = new Waiter(targets.size(), dump);
        for (Target target : targets) {
            Request r = new Request("proton.getDocsums");
            r.parameters().add(new Int8Value(CompressionType.NONE.getCode()));
            r.parameters().add(new Int32Value(blob.length));
            r.parameters().add(new DataValue(blob));
            target.invokeAsync(r, Duration.ofSeconds(100), waiter);
        }
        try {
            waiter.waitForReplies();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) {
        LogSetup.initVespaLogging("vespasummarybenchmark");
        String docidFileName = args[0];
        String connectionSpec = args[1];
        String summaryClass = args[2];
        int numRuns = Integer.parseInt(args[3]);
        int numTargets = Integer.parseInt(args[4]);
        VespaSummaryBenchmark benchmark = new VespaSummaryBenchmark();
        List<String> docIds = getDocIds(docidFileName);
        List<GlobalId> gids = new ArrayList<>(docIds.size());
        for (String docid : docIds) {
            GlobalId gid = new GlobalId(IdString.createIdString(docid));
            gids.add(gid);
        }
        List<Target> targets = benchmark.getTargets(connectionSpec, numTargets);
        for (int i = 0; i < numRuns; i++) {
            fetchDocIds(summaryClass, targets, gids, i==0);
        }
    }
}
