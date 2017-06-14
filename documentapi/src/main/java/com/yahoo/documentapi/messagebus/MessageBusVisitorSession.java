// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.*;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.routing.RoutingTable;
import com.yahoo.vdslib.VisitorStatistics;
import com.yahoo.vdslib.state.ClusterState;

import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * <p>
 * A visitor session for tracking progress for and potentially receiving data from
 * a visitor using a MessageBus source and destination session. The source session
 * is used to initiate visiting by sending create visitor messages to storage and
 * the destination session is used for receiving progress. If the visitor is not
 * set up to send data to a remote destination, data will also be received through
 * the destination session.
 * </p>
 * <p>
 * Create the visitor session by calling the
 * <code>DocumentAccess.createVisitorSession</code> method.
 * </p>
 */
public class MessageBusVisitorSession implements VisitorSession {
    /**
     * Abstract away notion of source session into a generic Sender
     * interface to allow easy mocking.
     */
    public interface Sender {
        Result send(Message msg);
        int getPendingCount();
        void destroy();
    }

    public interface SenderFactory {
        Sender createSender(ReplyHandler replyHandler, VisitorParameters visitorParameters);
    }

    /**
     * Abstract away notion of destination session into a generic Receiver
     * interface to allow easy mocking.
     * The implementation must be thread safe since reply() can be invoked
     * from an arbitrary thread.
     */
    public interface Receiver {
        void reply(Reply reply);
        void destroy();
        /**
         * Get connection spec that can be used by other clients to send
         * messages to this Receiver.
         * @return connection spec
         */
        String getConnectionSpec();
    }

    public interface ReceiverFactory {
        Receiver createReceiver(MessageHandler messageHandler, String sessionName);
    }

    public interface AsyncTaskExecutor {
        void submitTask(Runnable event);
        void scheduleTask(Runnable event, long delay, TimeUnit unit);
    }

    public interface Clock {
        long monotonicNanoTime();
    }

    public static class VisitingProgress {
        private final VisitorIterator iterator;
        private final ProgressToken token;

        public VisitingProgress(VisitorIterator iterator, ProgressToken token) {
            this.iterator = iterator;
            this.token = token;
        }

        public VisitorIterator getIterator() {
            return iterator;
        }

        public ProgressToken getToken() {
            return token;
        }
    }

    public enum State {
        NOT_STARTED(false),
        WORKING(false),
        COMPLETED(false),
        ABORTED(true),
        FAILED(true),
        TIMED_OUT(true);

        private final boolean failure;
        State(boolean failure) {
            this.failure = failure;
        }

        public boolean isFailure() {
            return failure;
        }
    }

    public class StateDescription {
        private final State state;
        private final String description;

        public StateDescription(State state, String description) {
            this.state = state;
            this.description = description;
        }

        public StateDescription(State state) {
            this.state = state;
            this.description = "";
        }

        public State getState() {
            return state;
        }

        public String getDescription() {
            return description;
        }

        VisitorControlHandler.CompletionCode toCompletionCode() {
            switch (state) {
                case COMPLETED: return VisitorControlHandler.CompletionCode.SUCCESS;
                case ABORTED:   return VisitorControlHandler.CompletionCode.ABORTED;
                case FAILED:    return VisitorControlHandler.CompletionCode.FAILURE;
                case TIMED_OUT: return VisitorControlHandler.CompletionCode.TIMEOUT;
                default:
                    throw new IllegalStateException("Current state did not have a valid value: " + state);
            }
        }

        public boolean failed() {
            return state.isFailure();
        }

        public String toString() {
            return state + ": " + description;
        }
    }

    /**
     * Message bus implementations of interfaces
     */

    public static class MessageBusSender implements Sender {
        private final SourceSession sourceSession;

        public MessageBusSender(SourceSession sourceSession) {
            this.sourceSession = sourceSession;
        }

        @Override
        public Result send(Message msg) {
            return sourceSession.send(msg);
        }

        @Override
        public int getPendingCount() {
            return sourceSession.getPendingCount();
        }

        @Override
        public void destroy() {
            sourceSession.destroy();
        }
    }

    public static class MessageBusSenderFactory implements SenderFactory {
        private final MessageBus messageBus;

        public MessageBusSenderFactory(MessageBus messageBus) {
            this.messageBus = messageBus;
        }

        private SourceSessionParams createSourceSessionParams(VisitorParameters visitorParameters) {
            SourceSessionParams sourceParams = new SourceSessionParams();

            if (visitorParameters.getThrottlePolicy() != null) {
                sourceParams.setThrottlePolicy(visitorParameters.getThrottlePolicy());
            } else {
                sourceParams.setThrottlePolicy(new DynamicThrottlePolicy());
            }

            return sourceParams;
        }

        @Override
        public Sender createSender(ReplyHandler replyHandler, VisitorParameters visitorParameters) {
            messageBus.setMaxPendingCount(0);
            SourceSessionParams sessionParams = createSourceSessionParams(visitorParameters);
            return new MessageBusSender(messageBus.createSourceSession(replyHandler, sessionParams));
        }
    }

    public static class MessageBusReceiver implements Receiver {
        private final DestinationSession destinationSession;

        public MessageBusReceiver(DestinationSession destinationSession) {
            this.destinationSession = destinationSession;
        }

        @Override
        public void reply(Reply reply) {
            destinationSession.reply(reply);
        }

        @Override
        public void destroy() {
            destinationSession.destroy();
        }

        @Override
        public String getConnectionSpec() {
            return destinationSession.getConnectionSpec();
        }
    }

    public static class MessageBusReceiverFactory implements ReceiverFactory {
        private final MessageBus messageBus;

        public MessageBusReceiverFactory(MessageBus messageBus) {
            this.messageBus = messageBus;
        }

        private DestinationSessionParams createDestinationParams(MessageHandler messageHandler, String visitorName) {
            DestinationSessionParams destparams = new DestinationSessionParams();
            destparams.setName(visitorName);
            destparams.setBroadcastName(false);
            destparams.setMessageHandler(messageHandler);
            return destparams;
        }

        @Override
        public Receiver createReceiver(MessageHandler messageHandler, String sessionName) {
            DestinationSessionParams destinationParams = createDestinationParams(messageHandler, sessionName);
            return new MessageBusReceiver(messageBus.createDestinationSession(destinationParams));
        }
    }

    public static class ThreadAsyncTaskExecutor implements AsyncTaskExecutor {
        private final ScheduledExecutorService executor;

        public ThreadAsyncTaskExecutor(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void submitTask(Runnable task) {
            executor.submit(task);
        }

        @Override
        public void scheduleTask(Runnable task, long delay, TimeUnit unit) {
            executor.schedule(task, delay, unit);
        }
    }

    public static class RealClock implements Clock {
        @Override
        public long monotonicNanoTime() {
            return System.nanoTime();
        }
    }

    private static final Logger log = Logger.getLogger(MessageBusVisitorSession.class.getName());

    private static AtomicLong sessionCounter = new AtomicLong(0);
    private static long getNextSessionId() {
        return sessionCounter.incrementAndGet();
    }
    private static String createSessionName() {
        StringBuilder sb = new StringBuilder();
        sb.append("visitor-").append(getNextSessionId()).append('-').append(System.currentTimeMillis());
        return sb.toString();
    }

    private final VisitorParameters params;
    private final Sender sender;
    private final Receiver receiver;
    private final AsyncTaskExecutor taskExecutor;
    private final VisitingProgress progress;
    private final VisitorStatistics statistics;
    private final String sessionName = createSessionName();
    private final String dataDestination;
    private final Clock clock;
    private StateDescription state;
    private long visitorCounter = 0;
    private long startTimeNanos = 0;
    private boolean scheduledSendCreateVisitors = false;
    private boolean done = false;
    private boolean destroying = false; // For testing and sanity checking
    private final Object completionMonitor = new Object();
    private Trace trace;
    /**
     * We keep our own track of pending messages since the sender's pending
     * count cannot be relied on in an async task execution context. This
     * because it is decremented before the message is actually processed.
     */
    private int pendingMessageCount = 0;

    public MessageBusVisitorSession(VisitorParameters visitorParameters,
                                    AsyncTaskExecutor taskExecutor,
                                    SenderFactory senderFactory,
                                    ReceiverFactory receiverFactory,
                                    RoutingTable routingTable)
            throws ParseException
    {
        this(visitorParameters, taskExecutor, senderFactory,
             receiverFactory, routingTable, new RealClock());
    }

    public MessageBusVisitorSession(VisitorParameters visitorParameters,
                                    AsyncTaskExecutor taskExecutor,
                                    SenderFactory senderFactory,
                                    ReceiverFactory receiverFactory,
                                    RoutingTable routingTable,
                                    Clock clock)
            throws ParseException
    {
        this.params = visitorParameters; // TODO(vekterli): make copy? legacy impl does not copy
        initializeRoute(routingTable);
        this.sender = senderFactory.createSender(createReplyHandler(), this.params);
        this.receiver = receiverFactory.createReceiver(createMessageHandler(), sessionName);
        this.taskExecutor = taskExecutor;
        this.progress = createVisitingProgress(params);
        this.statistics = new VisitorStatistics();
        this.state = new StateDescription(State.NOT_STARTED);
        this.clock = clock;
        initializeHandlers();
        trace = new Trace(visitorParameters.getTraceLevel());
        dataDestination = (params.getLocalDataHandler() == null
                ? params.getRemoteDataHandler()
                : receiver.getConnectionSpec());

        validateSessionParameters();

        // If we're already done, no need to do anything at all!
        if (progress.getIterator().isDone()) {
            markSessionCompleted();
        }
    }

    public static MessageBusVisitorSession createForMessageBus(final MessageBus mbus,
                                                               final ScheduledExecutorService scheduledExecutorService,
                                                               final VisitorParameters params) throws ParseException {
        final AsyncTaskExecutor executor = new ThreadAsyncTaskExecutor(scheduledExecutorService);
        final MessageBusSenderFactory senderFactory = new MessageBusSenderFactory(mbus);
        final MessageBusReceiverFactory receiverFactory = new MessageBusReceiverFactory(mbus);
        final RoutingTable table = mbus.getRoutingTable(DocumentProtocol.NAME);

        return new MessageBusVisitorSession(params, executor, senderFactory, receiverFactory, table);
    }

    private void validateSessionParameters() {
        if (dataDestination == null) {
            throw new IllegalStateException("No data destination specified");
        }
    }

    public void start() {
        synchronized (progress.getToken()) {
            this.startTimeNanos = clock.monotonicNanoTime();
            if (progress.getIterator().isDone()) {
                log.log(LogLevel.DEBUG, sessionName + ": progress token indicates " +
                        "session is done before it could even start; no-op");
                return;
            }
            transitionTo(new StateDescription(State.WORKING));
            taskExecutor.submitTask(new SendCreateVisitorsTask(computeBoundedMessageTimeoutMillis(0)));
        }
    }

    private void updateStateUnlessAlreadyFailed(StateDescription newState) {
        if (!state.failed()) {
            state = newState;
        } // else: don't override existing failure state
    }

    /**
     * Attempt to transition to a new state. Depending on the current state,
     * some transitions may be disallowed, such as transitioning from ABORTED
     * to COMPLETED, since failures take precedence. Transitioning multiple
     * times to the same state is a no-op in order to conserve the textual
     * description given by the first transition to said state (which most
     * likely is the most useful one for the end-user).
     *
     * @param newState State to attempt to transition to.
     * @return State which is current after the transition. If transition was
     *   successful, will be equal to newState.
     */
    private StateDescription transitionTo(StateDescription newState) {
        log.log(LogLevel.DEBUG, sessionName + ": attempting transition to state " + newState);
        switch (newState.getState()) {
            case WORKING:
                assert(state.getState() == State.NOT_STARTED);
                state = newState;
                break;
            case ABORTED:
                state = newState;
                break;
            case COMPLETED:
            case FAILED:
            case TIMED_OUT:
                updateStateUnlessAlreadyFailed(newState);
                break;
            default:
                com.yahoo.protect.Process.logAndDie("Invalid target transition state: " + newState);
        }
        log.log(LogLevel.DEBUG, "Session '" + sessionName + "' is now in state " +  state);
        return state;
    }

    private ReplyHandler createReplyHandler() {
        return (reply) -> {
            // Generally, handleReply will run in the context of the
            // underlying transport layer's processing thread(s), so we
            // schedule our own reply handling task to avoid blocking it.
            try {
                taskExecutor.submitTask(new HandleReplyTask(reply));
            } catch (RejectedExecutionException e) {
                 // We cannot reliably handle reply tasks failing to be submitted, since
                 // the reply task performs all our internal state handling logic. As such,
                 // we just immediately go into a failure destruction mode as soon as this
                 // happens, in which we do not wait for any active messages to be replied
                 // to.
                log.log(LogLevel.WARNING, "Visitor session '" + sessionName +
                        "': failed to submit reply task to executor service! " +
                        "Session cannot reliably continue; terminating it early.", e);

                synchronized (progress.getToken()) {
                    transitionTo(new StateDescription(State.FAILED, "Failed to submit reply task to executor service: " + e.getMessage()));
                    if (!done) {
                        markSessionCompleted();
                    }
                }
            }
        };
    }

    private MessageHandler createMessageHandler() {
        return (message) -> {
            try {
                taskExecutor.submitTask(new HandleMessageTask(message));
            } catch (RejectedExecutionException e) {
                Reply reply = ((DocumentMessage)message).createReply();
                message.swapState(reply);
                reply.addError(new Error(
                        DocumentProtocol.ERROR_ABORTED,
                        "Visitor session has been aborted"));
                receiver.reply(reply);
            }
        };
    }

    private void initializeRoute(RoutingTable routingTable) {
        // If no cluster route has been set by user arguments, attempt to retrieve it from mbus config.
        if (params.getRoute() == null || !params.getRoute().hasHops()) {
            params.setRoute(getClusterRoute(routingTable));
            log.log(LogLevel.DEBUG, "No route specified; resolved implicit " +
                    "storage cluster: " + params.getRoute().toString());
        }
    }

    private String getClusterRoute(RoutingTable routingTable) throws IllegalArgumentException{
        String route = null;
        for (RoutingTable.RouteIterator it = routingTable.getRouteIterator();
             it.isValid(); it.next())
        {
            String str = it.getName();
            if (str.startsWith("storage/cluster.")) {
                if (route != null) {
                    throw new IllegalArgumentException(
                            "There are multiple storage clusters in your application, " +
                                    "please specify which one to visit.");
                }
                route = str;
            }
        }
        if (route == null) {
            throw new IllegalArgumentException("No storage cluster found in your application.");
        }
        return route;
    }

    /**
     * Called from the constructor to ensure control and data handlers
     * are set and initialized.
     */
    private void initializeHandlers() {
        if (this.params.getLocalDataHandler() != null) {
            this.params.getLocalDataHandler().reset();
            this.params.getLocalDataHandler().setSession(this);
        } else if (this.params.getRemoteDataHandler() == null) {
            this.params.setLocalDataHandler(new VisitorDataQueue());
            this.params.getLocalDataHandler().setSession(this);
        }

        if (params.getControlHandler() != null) {
            params.getControlHandler().reset();
        } else {
            params.setControlHandler(new VisitorControlHandler());
        }
        params.getControlHandler().setSession(this);
    }

    private VisitingProgress createVisitingProgress(VisitorParameters params)
            throws ParseException
    {
        ProgressToken progressToken;
        if (params.getResumeToken() != null) {
            progressToken = params.getResumeToken();
        } else {
            progressToken = new ProgressToken();
        }
        VisitorIterator visitorIterator;

        if (params.getBucketsToVisit() == null
            || params.getBucketsToVisit().isEmpty())
        {
            // Use 1 distribution bit as a starting point. This will almost certainly
            // trigger a ERROR_WRONG_DISTRIBUTION reply immediately, meaning that we'll
            // get a fresh system state from the start. Since no buckets should ever
            // return with a OK result in such a case, we recognize this as a special
            // case in the iterator and simply reset its entire internal state using
            // the new db count rather than doing any splitting.
            BucketIdFactory bucketIdFactory = new BucketIdFactory();
            visitorIterator = VisitorIterator.createFromDocumentSelection(
                    params.getDocumentSelection(),
                    bucketIdFactory,
                    1,
                    progressToken);
        } else {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "parameters specify explicit bucket set " +
                        "to visit; using it rather than document selection (" +
                        params.getBucketsToVisit().size() + " buckets given)");
            }
            // Allow override of target buckets iff an explicit set of buckets
            // to visit is given by the visitor parameters. This was primarily
            // used for the defunct synchronization functionality, but since it's
            // so easy to support, don't deprecate it just yet.
            visitorIterator = VisitorIterator.createFromExplicitBucketSet(
                    params.getBucketsToVisit(),
                    1,
                    progressToken);
        }
        return new VisitingProgress(visitorIterator, progressToken);
    }

    private class SendCreateVisitorsTask implements Runnable {
        // All private methods in this task must be protected by a lock around
        // the progress token!

        private final long messageTimeoutMs;

        public SendCreateVisitorsTask(long messageTimeoutMs) {
            this.messageTimeoutMs = messageTimeoutMs;
        }

        private String getNextVisitorId() {
            StringBuilder sb = new StringBuilder();
            ++visitorCounter;
            sb.append(sessionName).append('-').append(visitorCounter);
            return sb.toString();
        }

        private CreateVisitorMessage createMessage(VisitorIterator.BucketProgress bucket) {
            CreateVisitorMessage msg = new CreateVisitorMessage(
                    params.getVisitorLibrary(),
                    getNextVisitorId(),
                    receiver.getConnectionSpec(),
                    dataDestination);

            msg.getTrace().setLevel(params.getTraceLevel());
            msg.setTimeRemaining(messageTimeoutMs);
            msg.setBuckets(Arrays.asList(bucket.getSuperbucket(), bucket.getProgress()));
            msg.setDocumentSelection(params.getDocumentSelection());
            msg.setFromTimestamp(params.getFromTimestamp());
            msg.setToTimestamp(params.getToTimestamp());
            msg.setMaxPendingReplyCount(params.getMaxPending());
            msg.setFieldSet(params.fieldSet());
            msg.setVisitInconsistentBuckets(params.visitInconsistentBuckets());
            msg.setVisitRemoves(params.visitRemoves());
            msg.setParameters(params.getLibraryParameters());
            msg.setRoute(params.getRoute());
            msg.setVisitorOrdering(params.getVisitorOrdering());
            msg.setMaxBucketsPerVisitor(params.getMaxBucketsPerVisitor());
            msg.setLoadType(params.getLoadType());
            msg.setPriority(params.getPriority());

            msg.setRetryEnabled(false);

            return msg;
        }

        public void run() {
            // Must sync around token as legacy API exposes it to handlers
            // and they expect to be able to sync around it.
            synchronized (progress.getToken()) {
                try {
                    scheduledSendCreateVisitors = false;
                    while (progress.getIterator().hasNext()) {
                        VisitorIterator.BucketProgress bucket = progress.getIterator().getNext();
                        Result result = sender.send(createMessage(bucket));
                        if (result.isAccepted()) {
                            log.log(LogLevel.DEBUG, sessionName + ": sent CreateVisitor for bucket " +
                                    bucket.getSuperbucket() + " with progress " + bucket.getProgress());
                            ++pendingMessageCount;
                        } else {
                            // Must reinsert bucket without progress into iterator since
                            // we failed to send visitor.
                            progress.getIterator().update(bucket.getSuperbucket(), bucket.getProgress());
                            break;
                        }
                    }
                } catch (Exception e) {
                    String msg = "Got exception of type " + e.getClass().getName() +
                            " with message '" + e.getMessage() +
                            "' while attempting to send visitors";
                    log.log(LogLevel.WARNING, msg);
                    transitionTo(new StateDescription(State.FAILED, msg));
                    // It's likely that the exception caused a failure to send a
                    // visitor message, meaning we won't get a reply task in the
                    // future from which we can execute logic to complete the
                    // session. Thusly, we have to do this here and now.
                    continueVisiting();
                } catch (Throwable t) {
                    // We can't reliably handle this; take a nosedive
                    com.yahoo.protect.Process.logAndDie("Caught unhandled error when trying to send visitors", t);
                }
            }
        }
    }

    private void continueVisiting() {
        if (visitingCompleted()) {
            markSessionCompleted();
        } else {
            scheduleSendCreateVisitorsIfApplicable();
        }
    }

    private void markSessionCompleted() {
        // 'done' is only ever written when token mutex is held, so safe to check
        // outside of completionMonitor lock.
        assert(!done) : "Session was marked as completed more than once";
        log.log(LogLevel.DEBUG, "Visitor session '" + sessionName + "' has completed");
        if (params.getLocalDataHandler() != null) {
            params.getLocalDataHandler().onDone();
        }
        // If skipFatalErrors is set and a fatal error did occur, fail
        // the session now with the first encountered error message.
        if (progress.getToken().containsFailedBuckets()) {
            transitionTo(new StateDescription(State.FAILED, progress.getToken().getFirstErrorMsg()));
        }
        // NOTE: transitioning to COMPLETED will not override a failure
        // state, so it's safe to always do this.
        transitionTo(new StateDescription(State.COMPLETED));
        params.getControlHandler().onDone(state.toCompletionCode(), state.getDescription());
        synchronized (completionMonitor) {
            done = true;
            completionMonitor.notifyAll();
        }
    }

    private class HandleReplyTask implements Runnable {
        private Reply reply;
        public HandleReplyTask(Reply reply) {
            this.reply = reply;
        }

        @Override
        public void run() {
            synchronized (progress.getToken()) {
                try {
                    assert(pendingMessageCount > 0);
                    --pendingMessageCount;
                    if (reply.hasErrors()) {
                        handleErrorReply(reply);
                    } else if (reply instanceof CreateVisitorReply) {
                        handleCreateVisitorReply((CreateVisitorReply)reply);
                    } else {
                        String msg = "Received reply we do not know how to handle: " +
                                reply.getClass().getName();
                        log.log(LogLevel.ERROR, msg);
                        transitionTo(new StateDescription(State.FAILED, msg));
                    }
                } catch (Exception e) {
                    String msg = "Got exception of type " + e.getClass().getName() +
                            " with message '" + e.getMessage() +
                            "' while processing reply in visitor session";
                    e.printStackTrace();
                    transitionTo(new StateDescription(State.FAILED, msg));
                } catch (Throwable t) {
                    // We can't reliably handle this; take a nosedive
                    com.yahoo.protect.Process.logAndDie("Caught unhandled error when running reply task", t);
                } finally {
                    continueVisiting();
                }
            }
        }
    }

    private class HandleMessageTask implements Runnable {
        private Message message;

        private HandleMessageTask(Message message) {
            this.message = message;
        }

        @Override
        public void run() {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Visitor session " + sessionName + ": Received message " + message);
            }
            try {
                if (message instanceof VisitorInfoMessage) {
                    handleVisitorInfoMessage((VisitorInfoMessage)message); // always replies
                } else {
                    handleDocumentMessage((DocumentMessage)message); // always replies on error
                }
            } catch (Throwable t) {
                com.yahoo.protect.Process.logAndDie("Caught unhandled error when processing message", t);
            }
        }
    }

    private void handleMessageProcessingException(Reply reply, Exception e, String what) {
        final String errorDesc = formatProcessingException(e, what);
        final String fullMsg = formatIdentifyingVisitorErrorString(errorDesc);
        log.log(LogLevel.ERROR, fullMsg, e);
        int errorCode;
        synchronized (progress.getToken()) {
            if (!params.skipBucketsOnFatalErrors()) {
                errorCode = ErrorCode.APP_FATAL_ERROR;
                transitionTo(new StateDescription(State.FAILED, errorDesc));
            } else {
                errorCode = DocumentProtocol.ERROR_UNPARSEABLE;
            }
        }
        reply.addError(new Error(errorCode, errorDesc));
    }

    private String formatProcessingException(Exception e, String whileProcessing) {
        return String.format(
                    "Got exception of type %s with message '%s' while processing %s",
                    e.getClass().getName(),
                    e.getMessage(),
                    whileProcessing);
    }

    private String formatIdentifyingVisitorErrorString(String details) {
        return String.format(
                    "Visitor %s (selection '%s'): %s",
                    sessionName,
                    params.getDocumentSelection(),
                    details);
    }

    /**
     * NOTE: not called from within lock, function must take lock itself
     */
    private void handleVisitorInfoMessage(VisitorInfoMessage msg) {

        Reply reply = msg.createReply();
        msg.swapState(reply);

        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Visitor session " + sessionName +
                    ": Received VisitorInfo with " +
                    msg.getFinishedBuckets().size() + " finished buckets");
        }

        try {
            if (msg.getErrorMessage().length() > 0) {
                params.getControlHandler().onVisitorError(msg.getErrorMessage());
            }
            synchronized (progress.getToken()) {
                // NOTE: control handlers shall sync on token themselves if
                // they want to access it, but recursive locking is OK and by
                // always locking we make screwing it up harder.
                if (!isDone()) {
                    params.getControlHandler().onProgress(progress.getToken());
                } else {
                    reply.addError(new Error(ErrorCode.APP_FATAL_ERROR, "Visitor has been shut down"));
                }
            }
        } catch (Exception e) {
            handleMessageProcessingException(reply, e, "VisitorInfoMessage");
        }  finally {
            receiver.reply(reply);
        }
    }

    private void handleDocumentMessage(DocumentMessage msg) {
        Reply reply = msg.createReply();
        msg.swapState(reply);

        if (params.getLocalDataHandler() == null) {
            log.log(LogLevel.ERROR, sessionName + ": Got visitor data back to client with no local data destination.");
            reply.addError(new Error(ErrorCode.APP_FATAL_ERROR, "Visitor data with no local data destination"));
            receiver.reply(reply);
            return;
        }
        try {
            params.getLocalDataHandler().onMessage(msg, new AckToken(reply));
        } catch (Exception e) {
            handleMessageProcessingException(reply, e, "DocumentMessage");
            // Immediately reply since we cannot count on AckToken being registered
            receiver.reply(reply);
        }
    }

    private boolean isFatalError(Reply reply) {
        Error error = reply.getError(0);
        switch (error.getCode()) {
            case ErrorCode.TIMEOUT:
            case DocumentProtocol.ERROR_BUCKET_NOT_FOUND:
            case DocumentProtocol.ERROR_WRONG_DISTRIBUTION:
                return false;
        }
        return error.isFatal();
    }

    /**
     * Return whether a (transient) error shall be exempt from visitor
     * error reporting. This to prevent spamming handlers and output with
     * errors for things that are happening naturally in the system.
     * @return true if the error should be reported
     */
    private boolean shouldReportError(Reply reply) {
        Error error = reply.getError(0);
        switch (error.getCode()) {
            case DocumentProtocol.ERROR_BUCKET_NOT_FOUND:
            case DocumentProtocol.ERROR_BUCKET_DELETED:
                return false;
        }
        return true;
    }

    private static String getErrorMessage(Error r) {
        return DocumentProtocol.getErrorName(r.getCode()) + ": " + r.getMessage();
    }

    private static boolean isErrorOfType(Reply reply, int errorCode) {
        return reply.getError(0).getCode() == errorCode;
    }

    private void reportVisitorError(String message) {
        params.getControlHandler().onVisitorError(message);
    }

    private void handleErrorReply(Reply reply) {
        CreateVisitorMessage msg = (CreateVisitorMessage)reply.getMessage();
        // Must reset bucket progress back to what it was before sending.
        BucketId bucket = msg.getBuckets().get(0);
        BucketId subProgress = msg.getBuckets().get(1);
        progress.getIterator().update(bucket, subProgress);

        String message = getErrorMessage(reply.getError(0));
        log.log(LogLevel.DEBUG, sessionName + ": received error reply for bucket " +
                bucket + " with message '" + message + "'");

        if (isFatalError(reply)) {
            if (params.skipBucketsOnFatalErrors()) {
                markBucketProgressAsFailed(bucket, subProgress, message);
            } else {
                reportVisitorError(message);
                transitionTo(new StateDescription(State.FAILED, message));
                return; // no additional visitors will be scheduled post-failure
            }
        }
        if (isErrorOfType(reply, DocumentProtocol.ERROR_WRONG_DISTRIBUTION)) {
            handleWrongDistributionReply((WrongDistributionReply)reply);
        } else {
            if (shouldReportError(reply)) {
                reportVisitorError(message);
            }
            // Wait 100ms before new visitor task is executed. Will prevent
            // visitors from being scheduled from caller.
            scheduleSendCreateVisitorsIfApplicable(100, TimeUnit.MILLISECONDS);
        }
    }

    private void markBucketProgressAsFailed(BucketId bucket, BucketId subProgress, String message) {
        progress.getToken().addFailedBucket(bucket, subProgress, message);
        progress.getIterator().update(bucket, ProgressToken.FINISHED_BUCKET);
    }

    private boolean enoughHitsReceived() {
        if (params.getMaxFirstPassHits() != -1
                && statistics.getDocumentsReturned() >= params.getMaxFirstPassHits())
        {
            return true;
        }
        if (params.getMaxTotalHits() != -1
                && ((statistics.getDocumentsReturned()
                     + statistics.getSecondPassDocumentsReturned())
                    >= params.getMaxTotalHits()))
        {
            return true;
        }
        return false;
    }

    /**
     * A session is considered completed if one or more of the following holds true:
     *   - All buckets have been visited (i.e. no active or pending visitors).
     *   - Visiting has failed fatally (or has been aborted) AND there are no
     *     active visitors remaining. 'Active' here means that we're waiting
     *     for a reply.
     *   - We have received sufficient number of documents (set via visitor
     *     parameters) from the buckets already visited AND there are no
     *     active visitors remaining.
     * @return true if visiting has completed, false otherwise
     */
    private boolean visitingCompleted() {
        return (pendingMessageCount == 0)
                && (progress.getIterator().isDone()
                    || state.failed()
                    || enoughHitsReceived());
    }

    private long messageTimeoutMillis() {
        return !isInfiniteTimeout(params.getTimeoutMs()) ? params.getTimeoutMs() : 5 * 60 * 1000;
    }

    private long sessionTimeoutMillis() {
        return params.getSessionTimeoutMs();
    }

    private long elapsedTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(clock.monotonicNanoTime() - startTimeNanos);
    }

    private static boolean isInfiniteTimeout(long timeoutMillis) {
        return timeoutMillis < 0;
    }

    private long computeBoundedMessageTimeoutMillis(long elapsedMs) {
        final long messageTimeoutMillis = messageTimeoutMillis();
        return !isInfiniteTimeout(sessionTimeoutMillis())
                ? Math.min(sessionTimeoutMillis() - elapsedMs, messageTimeoutMillis)
                : messageTimeoutMillis;
    }

    /**
     * Schedule a new SendCreateVisitors task iff there are still buckets to
     * visit, the visiting has not failed fatally and we haven't already
     * scheduled such a task.
     */
    private void scheduleSendCreateVisitorsIfApplicable(long delay, TimeUnit unit) {
        final long elapsedMillis = elapsedTimeMillis();
        if (!isInfiniteTimeout(sessionTimeoutMillis()) && (elapsedMillis >= sessionTimeoutMillis())) {
            transitionTo(new StateDescription(State.TIMED_OUT, String.format("Session timeout of %d ms expired", sessionTimeoutMillis())));
            if (visitingCompleted()) {
                markSessionCompleted();
            }
            return;
        }
        if (!mayScheduleCreateVisitorsTask()) {
            return;
        }
        final long messageTimeoutMillis = computeBoundedMessageTimeoutMillis(elapsedMillis);
        taskExecutor.scheduleTask(new SendCreateVisitorsTask(messageTimeoutMillis), delay, unit);
        scheduledSendCreateVisitors = true;
    }

    private boolean mayScheduleCreateVisitorsTask() {
        return ! (scheduledSendCreateVisitors
                  || !progress.getIterator().hasNext()
                  || state.failed()
                  || enoughHitsReceived());
    }

    private void scheduleSendCreateVisitorsIfApplicable() {
        scheduleSendCreateVisitorsIfApplicable(0, TimeUnit.MILLISECONDS);
    }

    private void handleCreateVisitorReply(CreateVisitorReply reply) {
        CreateVisitorMessage msg = (CreateVisitorMessage)reply.getMessage();

        BucketId superbucket = msg.getBuckets().get(0);
        BucketId subBucketProgress = reply.getLastBucket();

        log.log(LogLevel.DEBUG, sessionName + ": received CreateVisitorReply for bucket " +
                superbucket + " with progress " + subBucketProgress);

        progress.getIterator().update(superbucket, subBucketProgress);
        params.getControlHandler().onProgress(progress.getToken());
        statistics.add(reply.getVisitorStatistics());
        params.getControlHandler().onVisitorStatistics(statistics);
        trace.getRoot().addChild(reply.getTrace().getRoot());

        if (params.getDynamicallyIncreaseMaxBucketsPerVisitor()
                && (reply.getVisitorStatistics().getDocumentsReturned()
                    < params.getMaxFirstPassHits() / 2.0))
        {
            // Attempt to increase parallelism to reduce latency of visiting
            // Ensure new count is within [1, 128]
            int newMaxBuckets = Math.max(Math.min((int)(params.getMaxBucketsPerVisitor()
                    * params.getDynamicMaxBucketsIncreaseFactor()), 128), 1);
            params.setMaxBucketsPerVisitor(newMaxBuckets);
            log.log(LogLevel.DEBUG, sessionName + ": increasing max buckets per visitor to "
                    + params.getMaxBucketsPerVisitor());
        }
    }

    private void handleWrongDistributionReply(WrongDistributionReply reply) {
        try {
            ClusterState newState = new ClusterState(reply.getSystemState());
            int stateBits = newState.getDistributionBitCount();
            if (stateBits != progress.getIterator().getDistributionBitCount()) {
                log.log(LogLevel.DEBUG, "System state changed; now at " +
                        stateBits + " distribution bits");
                // Update the internal state of the visitor iterator. If we're increasing
                // the number of distribution bits, this may lead to splitting of pending
                // buckets. If we're decreasing, it may lead to merging of pending buckets
                // and potential loss of sub-bucket progress. In either way, the iterator
                // will not let any new buckets out before all active buckets have been
                // updated.
                progress.getIterator().setDistributionBitCount(stateBits);
            }
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "Failed to parse new system state string: "
                    + reply.getSystemState());
            transitionTo(new StateDescription(State.FAILED, "Failed to parse cluster state '"
                    + reply.getSystemState() + "'"));
        }
    }

    public String getSessionName() {
        return sessionName;
    }

    @Override
    public boolean isDone() {
        synchronized (progress.getToken()) {
            return done;
        }
    }

    @Override
    public ProgressToken getProgress() {
        return progress.getToken();
    }

    @Override
    public Trace getTrace() {
        return trace;
    }

    @Override
    public boolean waitUntilDone(long timeoutMs) throws InterruptedException {
        return params.getControlHandler().waitUntilDone(timeoutMs);
    }

    @Override
    public void ack(AckToken token) {
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Visitor session " + sessionName +
                    ": Sending ack " + token.ackObject);
        }
        // No locking here; replying should be thread safe in itself
        receiver.reply((Reply)token.ackObject);
    }

    @Override
    public void abort() {
        synchronized (progress.getToken()) {
            transitionTo(new StateDescription(State.ABORTED, "Visitor aborted by user"));
        }
    }

    @Override
    public VisitorResponse getNext() {
        if (params.getLocalDataHandler() == null) {
            throw new IllegalStateException("Data has been routed to external source for this visitor");
        }
        return params.getLocalDataHandler().getNext();
    }

    @Override
    public VisitorResponse getNext(int timeoutMilliseconds) throws InterruptedException {
        if (params.getLocalDataHandler() == null) {
            throw new IllegalStateException("Data has been routed to external source for this visitor");
        }
        return params.getLocalDataHandler().getNext(timeoutMilliseconds);
    }

    /**
     * For unit test purposes only, not to be used by any external parties.
     * @return true if destroy() has been--or is being--invoked.
     */
    public boolean isDestroying() {
        synchronized (completionMonitor) {
            return destroying;
        }
    }

    @Override
    public void destroy() {
        log.log(LogLevel.DEBUG, sessionName + ": synchronous destroy() called");
        try {
            synchronized (progress.getToken()) {
                synchronized (completionMonitor) {
                    // If we are destroying the session before it has completed (e.g. because
                    // waitUntilDone timed out or an interactive visiting was interrupted)
                    // set us to aborted state so that we'll seize sending new visitors.
                    if (!done) {
                        transitionTo(new StateDescription(State.ABORTED, "Session explicitly destroyed before completion"));
                    }
                }
            }
            synchronized (completionMonitor) {
                assert(!destroying) : "Attempted to destroy VisitorSession more than once";
                destroying = true;
                while (!done) {
                    completionMonitor.wait();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                sender.destroy();
                receiver.destroy();
            } catch (Exception e) {
                log.log(LogLevel.ERROR, "Caught exception destroying communication interfaces", e);
            }
            log.log(LogLevel.DEBUG, sessionName + ": synchronous destroy() done");
        }
    }
}
