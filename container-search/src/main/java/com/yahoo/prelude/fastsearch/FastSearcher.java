// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import java.util.Optional;

import com.yahoo.compress.CompressionType;
import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.ChannelTimeoutException;
import com.yahoo.fs4.GetDocSumsPacket;
import com.yahoo.fs4.Packet;
import com.yahoo.fs4.PingPacket;
import com.yahoo.fs4.PongPacket;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.fs4.QueryResultPacket;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.fs4.mplex.InvalidChannelException;
import com.yahoo.net.HostName;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.querytransform.QueryRewrite;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.SearchCluster;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;

import static com.yahoo.container.util.Util.quote;

/**
 * The searcher which forwards queries to fdispatch nodes, using the fnet/fs4
 * network layer.
 *
 * @author  bratseth
 */
// TODO: Clean up all the duplication in the various search methods by
// switching to doing all the error handling using exceptions below doSearch2.
// Right now half is done by exceptions handled in doSearch2 and half by setting
// errors on results and returning them. It could be handy to create a QueryHandlingErrorException
// or similar which could wrap an error message, and then just always throw that and
// catch and unwrap into a results with an error in high level methods.  -Jon
public class FastSearcher extends VespaBackEndSearcher {

    /** If this is turned on this will make search queries directly to the local search node when possible */
    private final static CompoundName dispatchDirect = new CompoundName("dispatch.direct");

    /** The compression method which will be used with rpc dispatch. "lz4" (default) and "none" is supported. */
    private final static CompoundName dispatchCompression = new CompoundName("dispatch.compression");

    /** Used to dispatch directly to search nodes over RPC, replacing the old fnet communication path */
    private final Dispatcher dispatcher;

    private final Backend dispatchBackend;
    
    private final FS4ResourcePool fs4ResourcePool;
    
    private final String selfHostname;
    
    /**
     * Creates a Fastsearcher.
     *
     * @param dispatchBackend       The backend object containing the connection to the dispatch node this should talk to
     *                      over the fs4 protocol
     * @param fs4ResourcePool the resource pool used to create direct connections to the local search nodes when
     *                        bypassing the dispatch node
     * @param dispatcher the dispatcher used (when enabled) to send summary requests over the rpc protocol.
     *                   Eventually we will move everything to this protocol and never use dispatch nodes.
     *                   At that point we won't need a cluster searcher above this to select and pass the right
     *                   backend.
     * @param docSumParams  document summary parameters
     * @param clusterParams the cluster number, and other cluster backend parameters
     * @param cacheParams   the size, lifetime, and controller of our cache
     * @param documentdbInfoConfig document database parameters
     */
    public FastSearcher(Backend dispatchBackend, FS4ResourcePool fs4ResourcePool,
                        Dispatcher dispatcher, SummaryParameters docSumParams, ClusterParams clusterParams,
                        CacheParams cacheParams, DocumentdbInfoConfig documentdbInfoConfig) {
        init(docSumParams, clusterParams, cacheParams, documentdbInfoConfig);
        this.dispatchBackend = dispatchBackend;
        this.fs4ResourcePool = fs4ResourcePool;
        this.dispatcher = dispatcher;
        this.selfHostname = HostName.getLocalhost();
    }

    private int countFastHits(Result result) {
        int count = 0;
        for (Iterator<Hit> i = hitIterator(result); i.hasNext();) {
            if (i.next() instanceof FastHit)
                count++;
        }
        return count;
    }

    /**
     * Pings the backend. Does not propagate to other searchers.
     */
    @Override
    public Pong ping(Ping ping, Execution execution) {
        return ping(ping, dispatchBackend, getName());
    }
    
    public static Pong ping(Ping ping, Backend backend, String name) {
        FS4Channel channel = backend.openPingChannel();

        // If you want to change this code, you need to understand
        // com.yahoo.prelude.cluster.ClusterSearcher.ping(Searcher) and
        // com.yahoo.prelude.cluster.TrafficNodeMonitor.failed(ErrorMessage)
        try {
            PingPacket pingPacket = new PingPacket();
            try {
                boolean couldSend = channel.sendPacket(pingPacket);
                if ( ! couldSend) {
                    return new Pong(ErrorMessage.createBackendCommunicationError("Could not ping " + name));
                }
            } catch (InvalidChannelException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Invalid channel " + name));
            } catch (IllegalStateException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Illegal state in FS4: " + e.getMessage()));
            } catch (IOException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("IO error while sending ping: " + e.getMessage()));
            }

            // We should only get a single packet
            BasicPacket[] packets;

            try {
                packets = channel.receivePackets(ping.getTimeout(), 1);
            } catch (ChannelTimeoutException e) {
                return new Pong(ErrorMessage.createNoAnswerWhenPingingNode("timeout while waiting for fdispatch for " + name));
            } catch (InvalidChannelException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Invalid channel for " + name));
            }

            if (packets.length == 0) {
                return new Pong(ErrorMessage.createBackendCommunicationError(name + " got no packets back"));
            }

            try {
                ensureInstanceOf(PongPacket.class, packets[0], name);
            } catch (TimeoutException e) {
                return new Pong(ErrorMessage.createTimeout(e.getMessage()));
            } catch (IOException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Unexpected packet class returned after ping: " + e.getMessage()));
            }
            return new Pong((PongPacket)packets[0]);
        } finally {
            if (channel != null) {
                channel.close();
            }
        }
    }

    protected void transformQuery(Query query) {
        QueryRewrite.rewriteSddocname(query);
    }

    @Override
    public Result doSearch2(Query query, QueryPacket queryPacket, CacheKey cacheKey, Execution execution) {
        FS4Channel channel = null;
        try {
            if (dispatcher.searchCluster().groupSize() == 1)
                forceSinglePassGrouping(query);
            channel = chooseBackend(query).openChannel();
            channel.setQuery(query);

            Result result = searchTwoPhase(channel, query, queryPacket, cacheKey);

            if (query.properties().getBoolean(Ranking.RANKFEATURES, false)) {
                // There is currently no correct choice for which
                // summary class we want to fetch at this point. If we
                // fetch the one selected by the user it may not
                // contain the data we need. If we fetch the default
                // one we end up fetching docsums twice unless the
                // user also requested the default one.
                fill(result, query.getPresentation().getSummary(), execution); // ARGH
            }
            return result;
        } catch (TimeoutException e) {
            return new Result(query,ErrorMessage.createTimeout(e.getMessage()));
        } catch (IOException e) {
            Result result = new Result(query);
            if (query.getTraceLevel() >= 1)
                query.trace(getName() + " error response: " + result, false, 1);
            result.hits().addError(ErrorMessage.createBackendCommunicationError(getName() + " failed: "+ e.getMessage()));
            return result;
        } finally {
            if (channel != null)
                channel.close();
        }
    }
    
    /** When we only search a single node, doing all grouping in one pass is more efficient */
    private void forceSinglePassGrouping(Query query) {
        for (GroupingRequest groupingRequest : GroupingRequest.getRequests(query))
            forceSinglePassGrouping(groupingRequest.getRootOperation());
    }
    
    private void forceSinglePassGrouping(GroupingOperation operation) {
        operation.setForceSinglePass(true);
        for (GroupingOperation childOperation : operation.getChildren())
            forceSinglePassGrouping(childOperation);
    }

    /**
     * Returns the backend object to issue a search request over.
     * Normally this is the backend field of this instance, which connects to the dispatch node this talk to
     * (which is why this instance was chosen by the cluster controller). However, when certain conditions obtain
     * (see below), we will instead return a backend instance which connects directly to the local search node
     * for efficiency.
     */
    private Backend chooseBackend(Query query) {
        // TODO 2016-08-16: Turn this on by default (by changing the 'false' below to 'true')
        if ( ! query.properties().getBoolean(dispatchDirect, false)) return dispatchBackend;

        // Don't use direct dispatch if the upstream ClusterSearcher did not chose the local dispatch
        // as that probably means that we are in a failover situation
        if ( ! dispatchBackend.getHost().equals(selfHostname)) return dispatchBackend;

        Optional<SearchCluster.Node> directDispatchRecipient = dispatcher.searchCluster().directDispatchTarget();
        if ( ! directDispatchRecipient.isPresent()) return dispatchBackend;

        query.trace(false, 2, "Dispatching directly to ", directDispatchRecipient.get());
        return fs4ResourcePool.getBackend(directDispatchRecipient.get().hostname(), 
                                          directDispatchRecipient.get().fs4port());
    }

    /**
     * Only used to fill the sddocname field when using direct dispatching as that is normally done in VespaBackEndSearcher.decodeSummary
     * @param result The result
     */
    private void fillSDDocName(Result result) {
        DocumentDatabase db = getDocumentDatabase(result.getQuery());
        for (Iterator<Hit> i = hitIterator(result); i.hasNext();) {
            Hit hit = i.next();
            if (hit instanceof FastHit) {
                hit.setField(Hit.SDDOCNAME_FIELD, db.getName());
            }
        }
    }
    /**
     * Perform a partial docsum fill for a temporary result
     * representing a partition of the complete fill request.
     *
     * @param result result containing a partition of the unfilled hits
     * @param summaryClass the summary class we want to fill with
     **/
    protected void doPartialFill(Result result, String summaryClass) {
        if (result.isFilled(summaryClass)) return;

        Query query = result.getQuery();
        traceQuery(getName(), "fill", query, query.getOffset(), query.getHits(), 2, quotedSummaryClass(summaryClass));

        if (wantsRPCSummaryFill(query)) {
            CompressionType compression =
                CompressionType.valueOf(query.properties().getString(dispatchCompression, "LZ4").toUpperCase());
            fillSDDocName(result);
            dispatcher.fill(result, summaryClass, compression);
            return;
        }

        CacheKey cacheKey = null;
        PacketWrapper packetWrapper = null;
        if (getCacheControl().useCache(query)) {
            cacheKey = fetchCacheKeyFromHits(result.hits(), summaryClass);
            if (cacheKey == null) {
                QueryPacket queryPacket = QueryPacket.create(query);
                cacheKey = new CacheKey(queryPacket);
            }
            packetWrapper = cacheLookupTwoPhase(cacheKey, result,summaryClass);
        }

        FS4Channel channel = chooseBackend(query).openChannel();
        channel.setQuery(query);
        Packet[] receivedPackets;
        try {
            DocsumPacketKey[] packetKeys;

            if (countFastHits(result) > 0) {
                packetKeys = getPacketKeys(result, summaryClass, false);
                if (packetKeys.length == 0) {
                    receivedPackets = new Packet[0];
                } else {
                    try {
                        receivedPackets = fetchSummaries(channel, result, summaryClass);
                    } catch (InvalidChannelException e) {
                        result.hits().addError(ErrorMessage.createBackendCommunicationError("Invalid channel " + getName() + " (summary fetch)"));
                        return;
                    } catch (ChannelTimeoutException e) {
                        result.hits().addError(ErrorMessage.createTimeout("timeout waiting for summaries from " + getName()));
                        return;
                    } catch (IOException e) {
                        result.hits().addError(ErrorMessage.createBackendCommunicationError(
                                "IO error while talking on channel " + getName() + " (summary fetch): " + e.getMessage()));
                        return;
                    }
                    if (receivedPackets.length == 0) {
                        result.hits().addError(ErrorMessage.createBackendCommunicationError(getName() + " got no packets back (summary fetch)"));
                        return;
                    }
                }
            } else {
                packetKeys = new DocsumPacketKey[0];
                receivedPackets = new Packet[0];
            }

            int skippedHits;
            try {
                FillHitsResult fillHitsResult = fillHits(result, 0, receivedPackets, summaryClass);
                skippedHits = fillHitsResult.skippedHits;
                if (fillHitsResult.error != null) {
                    result.hits().addError(ErrorMessage.createTimeout(fillHitsResult.error));
                    return;
                }
            } catch (TimeoutException e) {
                result.hits().addError(ErrorMessage.createTimeout(e.getMessage()));
                return;
            } catch (IOException e) {
                result.hits().addError(ErrorMessage.createBackendCommunicationError("Error filling hits with summary fields, source: " + getName()));
                return;
            }
            if (skippedHits == 0 && packetWrapper != null) {
                cacheControl.updateCacheEntry(cacheKey, query, packetKeys, receivedPackets);
            }

            if ( skippedHits > 0 )
                result.hits().addError(com.yahoo.search.result.ErrorMessage.createEmptyDocsums("Missing hit data for summary '" + summaryClass + "' for " + skippedHits + " hits"));
            result.analyzeHits();

            if (query.getTraceLevel() >= 3) {
                int hitNumber = 0;
                for (Iterator<com.yahoo.search.result.Hit> i = hitIterator(result); i.hasNext();) {
                    com.yahoo.search.result.Hit hit = i.next();
                    if ( ! (hit instanceof FastHit)) continue;
                    FastHit fastHit = (FastHit) hit;

                    String traceMsg = "Hit: " + (hitNumber++) + " from " + (fastHit.isCached() ? "cache" : "backend" );
                    if ( ! fastHit.isFilled(summaryClass))
                        traceMsg += ". Error, hit, not filled";
                    query.trace(traceMsg, false, 3);
                }
            }
        } finally {
            channel.close();
        }
    }

    private static @NonNull Optional<String> quotedSummaryClass(String summaryClass) {
        return Optional.of(summaryClass == null ? "[null]" : quote(summaryClass));
    }

    private CacheKey fetchCacheKeyFromHits(HitGroup hits, String summaryClass) {
        for (Iterator<Hit> i = hits.unorderedDeepIterator(); i.hasNext();) {
            Hit h = i.next();
            if (h instanceof FastHit) {
                FastHit hit = (FastHit) h;
                if (hit.isFilled(summaryClass)) {
                    continue;
                }
                if (hit.getCacheKey() != null) {
                    return hit.getCacheKey();
                }
            }
        }
        return null;
    }

    private Result searchTwoPhase(FS4Channel channel, Query query, QueryPacket queryPacket, CacheKey cacheKey) throws IOException {

        if (isLoggingFine())
            getLogger().finest("sending query packet");

        try {
            boolean couldSend = channel.sendPacket(queryPacket);
            if ( ! couldSend)
                return new Result(query,ErrorMessage.createBackendCommunicationError("Could not reach '" + getName() + "'"));
        } catch (InvalidChannelException e) {
            return new Result(query,ErrorMessage.createBackendCommunicationError("Invalid channel " + getName()));
        } catch (IllegalStateException e) {
            return new Result(query, ErrorMessage.createBackendCommunicationError("Illegal state in FS4: " + e.getMessage()));
        }

        BasicPacket[] basicPackets;

        try {
            basicPackets = channel.receivePackets(query.getTimeLeft(), 1);
        } catch (ChannelTimeoutException e) {
            return new Result(query,ErrorMessage.createTimeout("Timeout while waiting for " + getName()));
        } catch (InvalidChannelException e) {
            return new Result(query,ErrorMessage.createBackendCommunicationError("Invalid channel for " + getName()));
        }

        if (basicPackets.length == 0) {
            return new Result(query,ErrorMessage.createBackendCommunicationError(getName() + " got no packets back"));
        }

        if (isLoggingFine())
            getLogger().finest("got packets " + basicPackets.length + " packets");

        ensureInstanceOf(QueryResultPacket.class, basicPackets[0], getName());
        QueryResultPacket resultPacket = (QueryResultPacket) basicPackets[0];

        if (isLoggingFine())
            getLogger().finest("got query packet. " + "docsumClass=" + query.getPresentation().getSummary());

        if (query.getPresentation().getSummary() == null)
            query.getPresentation().setSummary(getDefaultDocsumClass());

        Result result = new Result(query);

        addMetaInfo(query, queryPacket.getQueryPacketData(), resultPacket, result, false);

        addUnfilledHits(result, resultPacket.getDocuments(), false, queryPacket.getQueryPacketData(), cacheKey);
        Packet[] packets;
        PacketWrapper packetWrapper = cacheControl.lookup(cacheKey, query);

        if (packetWrapper != null) {
            cacheControl.updateCacheEntry(cacheKey, query, resultPacket);
        }
        else {
            if (resultPacket.getCoverageFeature() && ! resultPacket.getCoverageFull()) {
                // Don't add error here, it was done in first phase
                // No check if packetWrapper already exists, since incomplete
                // first phase data won't be cached anyway.
            } else {
                packets = new Packet[1];
                packets[0] = resultPacket;
                cacheControl.cache(cacheKey, query, new DocsumPacketKey[0], packets);
            }
        }
        return result;
    }

    private Packet[] convertBasicPackets(BasicPacket[] basicPackets) throws ClassCastException {
        // trying to cast a BasicPacket[] to Packet[] will compile,
        // but lead to a runtime error. At least that's what I got
        // from testing and reading the specification. I'm just happy
        // if someone tells me what's the proper Java way of doing
        // this. -SK
        Packet[] packets = new Packet[basicPackets.length];

        for (int i = 0; i < basicPackets.length; i++) {
            packets[i] = (Packet) basicPackets[i];
        }
        return packets;
    }

    private Packet[] fetchSummaries(FS4Channel channel, Result result, String summaryClass)
            throws InvalidChannelException, ChannelTimeoutException, ClassCastException, IOException {

        BasicPacket[] receivedPackets;
        boolean summaryNeedsQuery = summaryNeedsQuery(result.getQuery());
        if (result.getQuery().getTraceLevel() >=3)
            result.getQuery().trace((summaryNeedsQuery ? "Resending " : "Not resending ") + "query during document summary fetching", 3);

        GetDocSumsPacket docsumsPacket = GetDocSumsPacket.create(result, summaryClass, summaryNeedsQuery);
        int compressionLimit = result.getQuery().properties().getInteger(PACKET_COMPRESSION_LIMIT, 0);
        docsumsPacket.setCompressionLimit(compressionLimit);
        if (compressionLimit != 0) {
            docsumsPacket.setCompressionType(result.getQuery().properties().getString(PACKET_COMPRESSION_TYPE, "lz4"));
        }

        boolean couldSend = channel.sendPacket(docsumsPacket);
        if (isLoggingFine())
            getLogger().finest("Sent " + docsumsPacket + " on " + channel);
        if ( ! couldSend) throw new IOException("Could not successfully send GetDocSumsPacket.");
        receivedPackets = channel.receivePackets(result.getQuery().getTimeLeft(), docsumsPacket.getNumDocsums() + 1);

        if (isLoggingFine())
            getLogger().finest("got " + receivedPackets.length + "docsumPackets");

        return convertBasicPackets(receivedPackets);
    }

    public String toString() {
        return "fast searcher (" + getName() + ") " + dispatchBackend;
    }

    /**
     * Returns an array of the hits contained in this result
     *
     * @param filled true to return all hits, false to return only unfilled hits
     * @return array of docids, empty array if no hits
     */
    private DocsumPacketKey[] getPacketKeys(Result result, String summaryClass, boolean filled) {
        DocsumPacketKey[] packetKeys = new DocsumPacketKey[result.getHitCount()];
        int x = 0;

        for (Iterator<com.yahoo.search.result.Hit> i = hitIterator(result); i.hasNext();) {
            com.yahoo.search.result.Hit hit = i.next();
            if (hit instanceof FastHit) {
                FastHit fastHit = (FastHit) hit;
                if(filled || !fastHit.isFilled(summaryClass)) {
                    packetKeys[x] = new DocsumPacketKey(fastHit.getGlobalId(), fastHit.getPartId(), summaryClass);
                    x++;
                }
            }
        }
        if (x < packetKeys.length) {
            DocsumPacketKey[] tmp = new DocsumPacketKey[x];

            System.arraycopy(packetKeys, 0, tmp, 0, x);
            return tmp;
        } else {
            return packetKeys;
        }
    }

    protected boolean isLoggingFine() {
        return getLogger().isLoggable(Level.FINE);
    }

}
