package com.yahoo.documentapi.local;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.document.fieldset.FieldSetRepo;
import com.yahoo.document.select.DocumentSelector;
import com.yahoo.document.select.Result;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AckToken;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorDataHandler;
import com.yahoo.documentapi.VisitorDataQueue;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorResponse;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.messagebus.Trace;
import com.yahoo.yolean.Exceptions;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local visitor session that copies and iterates through all items in the local document access.
 * Each document must be ack'ed for the session to be done visiting, unless the destination is remote.
 * Only document puts are sent by this session, and this is done from a separate thread.
 *
 * @author jonmv
 */
public class LocalVisitorSession implements VisitorSession {

    private enum State { RUNNING, FAILURE, ABORTED, SUCCESS }

    private final VisitorDataHandler data;
    private final VisitorControlHandler control;
    private final Map<DocumentId, Document> outstanding;
    private final DocumentSelector selector;
    private final FieldSet fieldSet;
    private final AtomicReference<State> state;
    private final AtomicReference<Phaser> phaser;
    private final ProgressToken token;

    public LocalVisitorSession(LocalDocumentAccess access, VisitorParameters parameters) throws ParseException {
        this.selector = new DocumentSelector(parameters.getDocumentSelection());
        this.fieldSet = new FieldSetRepo().parse(access.getDocumentTypeManager(), parameters.fieldSet());
        this.token = parameters.getResumeToken();

        if (parameters.getRemoteDataHandler() == null) {
            this.data = parameters.getLocalDataHandler() == null ? new VisitorDataQueue() : parameters.getLocalDataHandler();
            this.data.reset();
            this.data.setSession(this);
        }
        else {
            if (parameters.getLocalDataHandler() != null)
                throw new IllegalArgumentException("Cannot have both a remote and a local data handler");
            this.data = null;
        }

        this.control = parameters.getControlHandler() == null ? new VisitorControlHandler() : parameters.getControlHandler();
        this.control.reset();
        this.control.setSession(this);

        this.outstanding = new ConcurrentSkipListMap<>(Comparator.comparing(DocumentId::toString));
        this.outstanding.putAll(access.documents);
        this.state = new AtomicReference<>(State.RUNNING);
        this.phaser = access.phaser;

        start();
    }

    void start() {
        Phaser synchronizer = phaser.get();
        if (synchronizer != null)
            synchronizer.register();

        new Thread(() -> {
            try {
                // Iterate through all documents and pass on to data handler
                outstanding.forEach((id, document) -> {
                    if (state.get() != State.RUNNING)
                        return;

                    try {
                        if (selector.accepts(new DocumentPut(document)) != Result.TRUE)
                            return;
                    }
                    catch (RuntimeException e) {
                        return;
                    }

                    Document copy = new Document(document.getDataType(), document.getId());
                    new FieldSetRepo().copyFields(document, copy, fieldSet);


                    if (synchronizer != null)
                        synchronizer.arriveAndAwaitAdvance();

                    if (data != null)
                        data.onMessage(new PutDocumentMessage(new DocumentPut(copy)),
                                       new AckToken(id));
                    else
                        outstanding.remove(id);

                    if (synchronizer != null)
                        synchronizer.arriveAndAwaitAdvance();
                });
                // Transition to a terminal state when done
                state.updateAndGet(current -> {
                    switch (current) {
                        case RUNNING:
                            control.onDone(VisitorControlHandler.CompletionCode.SUCCESS, "Success");
                            return State.SUCCESS;
                        case ABORTED:
                            control.onDone(VisitorControlHandler.CompletionCode.ABORTED, "Aborted by user");
                            return State.ABORTED;
                        default:
                            control.onDone(VisitorControlHandler.CompletionCode.FAILURE, "Unexpected state '" + current + "'");;
                            return State.FAILURE;
                    }
                });
            }
            // Transition to failure terminal state on error
            catch (Exception e) {
                state.set(State.FAILURE);
                outstanding.clear();
                control.onDone(VisitorControlHandler.CompletionCode.FAILURE, Exceptions.toMessageString(e));
            }
            finally {
                if (synchronizer != null)
                    synchronizer.arriveAndDeregister();
                if (data != null)
                    data.onDone();
            }
        }).start();
    }

    @Override
    public boolean isDone() {
        return    outstanding.isEmpty() // All documents ack'ed
               && control.isDone();     // Control handler has been notified
    }

    /** Returns the token set in the parameters used to create this. */
    @Override
    public ProgressToken getProgress() {
        return token;
    }

    @Override
    public Trace getTrace() {
        throw new UnsupportedOperationException("Traces are not supported");
    }

    @Override
    public boolean waitUntilDone(long timeoutMs) throws InterruptedException {
        return control.waitUntilDone(timeoutMs);
    }

    @Override
    public void ack(AckToken token) {
        outstanding.remove((DocumentId) token.ackObject);
    }

    @Override
    public void abort() {
        state.updateAndGet(current -> current == State.RUNNING ? State.ABORTED : current);
        outstanding.clear();
    }

    @Override
    public VisitorResponse getNext() {
        return data.getNext();
    }

    @Override
    public VisitorResponse getNext(int timeoutMilliseconds) throws InterruptedException {
        return data.getNext(timeoutMilliseconds);
    }

    @Override
    public void destroy() {
        abort();
        try {
            control.waitUntilDone(0);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
