// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.local;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.SubscriptionParameters;
import com.yahoo.documentapi.SubscriptionSession;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.VisitorDestinationParameters;
import com.yahoo.documentapi.VisitorDestinationSession;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The main class of the local implementation of the document api.
 * To easily obtain an instance of this, with the documents using the schemas (.sd-files) in a given directoy,
 * use the {@code com.yahoo.vespa.application} test module and {@code DocumentAccesses.ofSchemas(schemaDirectory)}
 *
 * @author bratseth
 * @author jonmv
 */
public class LocalDocumentAccess extends DocumentAccess {

    final Map<DocumentId, Document> documents = new ConcurrentHashMap<>();
    final AtomicReference<Phaser> phaser = new AtomicReference<>();

    public LocalDocumentAccess(DocumentAccessParams params) {
        super(params);
    }

    @Override
    public LocalSyncSession createSyncSession(SyncParameters parameters) {
        return new LocalSyncSession(this);
    }

    @Override
    public LocalAsyncSession createAsyncSession(AsyncParameters parameters) {
        return new LocalAsyncSession(parameters, this);
    }

    @Override
    public LocalVisitorSession createVisitorSession(VisitorParameters parameters) throws ParseException {
        return new LocalVisitorSession(this, parameters);
    }

    @Override
    public VisitorDestinationSession createVisitorDestinationSession(VisitorDestinationParameters parameters) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public SubscriptionSession createSubscription(SubscriptionParameters parameters) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public SubscriptionSession openSubscription(SubscriptionParameters parameters) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    /**
     * Sets a {@link Phaser} for synchronization of otherwise async operations in sessions backed by this.
     *
     * {@link AsyncSession} and {@link VisitorSession} are by nature async. The {@link LocalAsyncSession} is, by default,
     * synchronous, i.e., responses are sent by the thread that sends the document operations. {@link LocalVisitorSession},
     * on the other hand, is asynchronous by default, i.e., all documents are sent by a dedicated sender thread.
     * To enable more advanced testing using the {@link LocalDocumentAccess}, this method lets the user specify a
     * {@link Phaser} used to synchronize the sending of documents from the visitor, and the responses for the
     * document operations — which are then also done by a dedicated thread pool, instead of the caller thread.
     *
     * When this is set, a party is registered with the phaser for the sender thread (visit) or for each document
     * operation (async-session). The thread that sends a document (visit) or response (async-session) then arrives
     * and awaits advance before sending each response, so the user can trigger these documents and responses.
     * After the document or response is delivered, the thread arrives and awaits advance, so the user
     * can wait until the document or response has been delivered. This also ensures memory visibility.
     * The visit sender thread deregisters when the whole visit is done; the async session threads after each operation.
     * Example usage:
     *
     * <pre> {@code
     * void testOperations(LocalDocumentAccess access) {
     *   List<Response> responses = new ArrayList<>();
     *   Phaser phaser = new Phaser(1);    // "1" to register self
     *   access.setPhaser(phaser);
     *   AsyncSession session = access.createAsyncSession(new AsyncParameters().setReponseHandler(responses::add));
     *   session.put(documentPut);
     *   session.get(documentId);
     *                                     // Operations wait for this thread to arrive at "phaser"
     *   phaser.arriveAndAwaitAdvance();   // Let operations send their responses
     *                                     // "responses" may or may not hold the responses now
     *   phaser.arriveAndAwaitAdvance();   // Wait for operations to complete sending responses, memory visibility, etc.
     *                                     // "responses" now has responses from all previous operations
     *   phaser.arriveAndDeregister();     // Deregister so further operations flow freely
     * }}</pre>
     */
    public void setPhaser(Phaser phaser) {
        this.phaser.set(phaser);
    }

}
