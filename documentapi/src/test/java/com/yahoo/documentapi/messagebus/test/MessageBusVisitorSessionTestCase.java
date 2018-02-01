// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.test;

import com.yahoo.document.BucketId;
import com.yahoo.document.DocumentId;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.*;
import com.yahoo.documentapi.messagebus.MessageBusVisitorSession;
import com.yahoo.documentapi.messagebus.loadtypes.LoadType;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RouteSpec;
import com.yahoo.messagebus.routing.RoutingTable;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.vdslib.VisitorStatistics;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

// TODO replace explicit pre-mockito mock classes with proper mockito mocks wherever possible
public class MessageBusVisitorSessionTestCase {
    private class MockSender implements MessageBusVisitorSession.Sender {
        private int maxPending = 1000;
        private int pendingCount = 0;
        private ArrayList<Message> messages = new ArrayList<Message>();
        private ReplyHandler replyHandler = null;
        private boolean destroyed = false;
        private RuntimeException exceptionOnSend = null;

        @Override
        public Result send(Message msg) {
            synchronized (this) {
                // Used to force failure during create visitors task processing
                if (exceptionOnSend != null) {
                    throw exceptionOnSend;
                }
                if (pendingCount < maxPending) {
                    messages.add(msg);
                    ++pendingCount;
                    notifyAll();
                    return Result.ACCEPTED;
                } else {
                    return new Result(1234, "too many pending messages");
                }
            }
        }

        @Override
        public void destroy() {
            synchronized (this) {
                destroyed = true;
            }
        }

        @Override
        public int getPendingCount() {
            synchronized (this) {
                return pendingCount;
            }
        }

        public boolean isDestroyed() {
            synchronized (this) {
                return destroyed;
            }
        }

        public void setExceptionOnSend(RuntimeException exceptionOnSend) {
            this.exceptionOnSend = exceptionOnSend;
        }

        public void waitForMessages(int count, long timeout) throws IllegalStateException {
            long timeoutAt = System.currentTimeMillis() + timeout;
            synchronized (this) {
                while (messages.size() < count) {
                    if (System.currentTimeMillis() >= timeoutAt) {
                        throw new IllegalStateException("Timed out waiting for " + count + " messages");
                    }
                    try {
                        this.wait(timeout);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        public int getMessageCount() {
            synchronized (this) {
                return messages.size();
            }
        }

        public Message getAndRemoveMessage(int index) {
            synchronized (this) {
                if (index >= messages.size()) {
                    throw new IllegalArgumentException("Bad message index");
                }
                return messages.remove(index);
            }
        }

        public void setReplyHandler(ReplyHandler replyHandler) {
            synchronized (this) {
                this.replyHandler = replyHandler;
            }
        }

        public void setMaxPending(int maxPending) {
            synchronized (this) {
                this.maxPending = maxPending;
            }
        }

        public void reply(Reply reply) {
             synchronized (this) {
                 if (replyHandler == null) {
                     throw new IllegalArgumentException("Reply handler has not been set");
                 }
                 --pendingCount;
                 assert(pendingCount >= 0);
             }
            replyHandler.handleReply(reply);
        }
    }

    private class MockSenderFactory implements MessageBusVisitorSession.SenderFactory {
        private MockSender sender;

        public MockSenderFactory(MockSender sender) {
            this.sender = sender;
        }

        @Override
        public MessageBusVisitorSession.Sender createSender(ReplyHandler replyHandler, VisitorParameters visitorParameters) {
            MockSender ret = sender;
            if (ret == null) {
                throw new IllegalStateException("Attempted to create mock sender twice");
            }
            ret.setReplyHandler(replyHandler);
            sender = null;
            return ret;
        }
    }

    private class MockReceiver implements MessageBusVisitorSession.Receiver {
        private ArrayList<Reply> replies = new ArrayList<Reply>();
        private MessageHandler messageHandler = null;
        private boolean destroyed = false;
        private String connectionSpec = "receiver/connection/spec";

        public ArrayList<Reply> getReplies() {
            return replies;
        }

        public void setMessageHandler(MessageHandler messageHandler) {
            this.messageHandler = messageHandler;
        }

        public boolean isDestroyed() {
            return destroyed;
        }

        @Override
        public void reply(Reply reply) {
            replies.add(reply);
        }

        public int getReplyCount() {
            return replies.size();
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        @Override
        public String getConnectionSpec() {
            return connectionSpec;
        }

        public void setConnectionSpec(String connectionSpec) {
            this.connectionSpec = connectionSpec;
        }

        /**
         * Invoke registered MessageHandler with message
         * @param message message to "send"
         */
        public void send(Message message) {
            messageHandler.handleMessage(message);
        }

        public Reply getAndRemoveReply(int index) {
            if (index >= replies.size()) {
                throw new IllegalArgumentException("Bad reply index");
            }
            return replies.remove(index);
        }

        public String repliesToString() {
            StringBuilder builder = new StringBuilder();
            for (Reply reply : replies) {
                builder.append(reply.getClass().getSimpleName());
                if (reply.hasErrors()) {
                    builder.append('(');
                    for (int i = 0; i < reply.getNumErrors(); ++i) {
                        if (i > 0) {
                            builder.append(", ");
                        }
                        Error err = reply.getError(i);
                        builder.append(DocumentProtocol.getErrorName(err.getCode()));
                        builder.append(": ");
                        builder.append(err.getMessage());
                    }
                    builder.append(')');
                }
                builder.append('\n');
            }
            return builder.toString();
        }
    }

    private class MockReceiverFactory implements MessageBusVisitorSession.ReceiverFactory {
        private MockReceiver receiver;

        private MockReceiverFactory(MockReceiver receiver) {
            this.receiver = receiver;
        }

        @Override
        public MessageBusVisitorSession.Receiver createReceiver(MessageHandler messageHandler,
                                                                 String sessionName) {
            MockReceiver ret = receiver;
            if (ret == null) {
                throw new IllegalStateException("Attempted to create mock receiver twice");
            }
            ret.setMessageHandler(messageHandler);
            receiver = null;
            return ret;
        }
    }

    public static class TaskDescriptor implements Comparable<TaskDescriptor> {
        private Runnable task;
        private long timestamp;
        private long sequenceId;

        public TaskDescriptor(Runnable task, long timestamp, long sequenceId) {
            this.task = task;
            this.timestamp = timestamp;
            this.sequenceId = sequenceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TaskDescriptor td = (TaskDescriptor) o;

            if (sequenceId != td.sequenceId) return false;
            if (timestamp != td.timestamp) return false;
            if (!task.equals(td.task)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(sequenceId, timestamp, task);
        }

        @Override
        public int compareTo(TaskDescriptor o) {
            if (timestamp < o.timestamp) return -1;
            if (timestamp > o.timestamp) return 1;
            if (sequenceId < o.sequenceId) return -1;
            if (sequenceId > o.sequenceId) return 1;
            return 0;
        }

        public Runnable getTask() {
            return task;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getSequenceId() {
            return sequenceId;
        }
    }

    /**
     * Mock the executor to keep things nicely single threaded for the testing.
     * No need to synchronize things here since we don't use multiple threads.
     */
    public class MockAsyncTaskExecutor implements MessageBusVisitorSession.AsyncTaskExecutor {
        private long sequenceCounter = 0;
        private long timeMs = 0;
        private Set<TaskDescriptor> tasks = new TreeSet<TaskDescriptor>();
        private int rejectTasksAfter = -1;

        public void setRejectTasksAfter(int rejectTasksAfter) {
            this.rejectTasksAfter = rejectTasksAfter;
        }

        private void checkTaskAcceptance() {
            if (rejectTasksAfter == 0) {
                throw new RejectedExecutionException("rejectTasksAfter is 0; rejecting task");
            } else if (rejectTasksAfter > 0) {
                --rejectTasksAfter;
            }
        }

        @Override
        public void submitTask(Runnable task) {
            checkTaskAcceptance();
            tasks.add(new TaskDescriptor(task, 0, ++sequenceCounter));
        }

        @Override
        public void scheduleTask(Runnable task, long delay, TimeUnit unit) {
            checkTaskAcceptance();
            tasks.add(new TaskDescriptor(task, timeMs + unit.toMillis(delay), ++sequenceCounter));
        }

        public Set<TaskDescriptor> getTasks() {
            return tasks;
        }

        public int getScheduledTaskCount() {
            return tasks.size();
        }

        public void setMockTimeMs(long timeMs) {
            this.timeMs = timeMs;
        }

        public void expectAndProcessTasks(int expectedTaskCount,
                                          int processCount,
                                          long[] taskRunAtTime)
        {
            if (tasks.size() != expectedTaskCount) {
                throw new IllegalStateException("Expected " + expectedTaskCount +
                        " queued tasks, found " + tasks.size());
            }
            if (taskRunAtTime != null && taskRunAtTime.length != tasks.size()) {
                throw new IllegalStateException("Task time array must be equal in size to number of tasks");
            }
            for (int i = 0; i < processCount; ++i) {
                Iterator<TaskDescriptor> iter = tasks.iterator();
                TaskDescriptor td = iter.next();
                if (taskRunAtTime != null) {
                    if (taskRunAtTime[i] != td.getTimestamp()) {
                        throw new IllegalStateException(
                                "Expected task with scheduled execution time " +
                                taskRunAtTime[i] + ", was " + td.getTimestamp());
                    }
                }
                iter.remove();
                td.getTask().run();
            }
        }

        public void expectAndProcessTasks(int expectedTaskCount, int processCount) {
            expectAndProcessTasks(expectedTaskCount, processCount, null);
        }

        public void expectAndProcessTasks(int expectedTaskCount) {
            expectAndProcessTasks(expectedTaskCount, expectedTaskCount);
        }

        public void expectAndProcessTasks(int expectedTaskCount, long[] taskRunAtTime) {
            expectAndProcessTasks(expectedTaskCount, expectedTaskCount, taskRunAtTime);
        }

        public void expectNoTasks() {
            if (!tasks.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Expected no tasks, but found these: ");
                for (TaskDescriptor td : tasks) {
                    sb.append(td.getTask()).append(" ");
                }
                throw new IllegalStateException(sb.toString());
            }
        }
    }

    private class MockClock implements MessageBusVisitorSession.Clock {
        private long monotonicTime = 0;

        @Override
        public long monotonicNanoTime() { return monotonicTime; }

        public void setMonotonicTime(long monotonicTime, TimeUnit unit) {
            this.monotonicTime = unit.toNanos(monotonicTime);
        }
    }

    private MessageBusVisitorSession createVisitorSession(MockSender sender,
                                                          MockReceiver receiver,
                                                          MockAsyncTaskExecutor executor,
                                                          VisitorParameters visitorParameters,
                                                          RoutingTable routingTable,
                                                          MockClock clock)
    {
        if (routingTable == null) {
            routingTable = new RoutingTable(new RoutingTableSpec(DocumentProtocol.NAME));
        }
        try {
            return new MessageBusVisitorSession(
                    visitorParameters,
                    executor,
                    new MockSenderFactory(sender),
                    new MockReceiverFactory(receiver),
                    routingTable,
                    clock);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Bad document selection", e);
        }
    }

    private MessageBusVisitorSession createVisitorSession(MockSender sender,
                                                          MockReceiver receiver,
                                                          MockAsyncTaskExecutor executor,
                                                          VisitorParameters visitorParameters)
    {
        return createVisitorSession(sender, receiver, executor, visitorParameters, null, new MockClock());
    }

    VisitorParameters createVisitorParameters(String selection) {
        VisitorParameters params = new VisitorParameters(selection);
        params.setRoute("storage"); // cannot be null by default
        // TODO: skip the above and rather mock cluster route resolution, since
        // this must be supported anyway!
        return params;
    }

    private String createVisitorToString(CreateVisitorMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("CreateVisitorMessage(buckets=[\n");
        for (BucketId id : msg.getBuckets()) {
            sb.append(id).append("\n");
        }
        sb.append("]\n");
        if (!"".equals(msg.getDocumentSelection())) {
            sb.append("selection='").append(msg.getDocumentSelection()).append("'\n");
        }
        if (msg.getTimeRemaining() != 5 * 60 * 1000) {
            sb.append("time remaining=").append(msg.getTimeRemaining()).append("\n");
        }
        if (msg.getFromTimestamp() != 0) {
            sb.append("from timestamp=").append(msg.getFromTimestamp()).append("\n");
        }
        if (msg.getToTimestamp() != 0) {
            sb.append("to timestamp=").append(msg.getToTimestamp()).append("\n");
        }
        if (msg.getMaxPendingReplyCount() != 32) {
            sb.append("max pending=").append(msg.getMaxPendingReplyCount()).append("\n");
        }
        if (!"[all]".equals(msg.getFieldSet())) {
            sb.append("fieldset=").append(msg.getFieldSet()).append("\n");
        }
        if (msg.getVisitInconsistentBuckets()) {
            sb.append("visit inconsistent=").append(msg.getVisitInconsistentBuckets()).append("\n");
        }
        if (msg.getVisitRemoves()) {
            sb.append("visit removes=").append(msg.getVisitRemoves()).append("\n");
        }
        if (!msg.getParameters().isEmpty()) {
            sb.append("parameters=[\n");
            for (Map.Entry<String, byte[]> kv : msg.getParameters().entrySet()) {
                sb.append(kv.getKey()).append(" -> ");
                sb.append(new String(kv.getValue(), Charset.defaultCharset()));
                sb.append("\n");
            }
            sb.append("]\n");
        }
        if (msg.getRoute() != null && !"storage".equals(msg.getRoute().toString())) {
            sb.append("route=").append(msg.getRoute()).append("\n");
        }
        if (msg.getVisitorOrdering() != 0) {
            sb.append("ordering=").append(msg.getVisitorOrdering()).append("\n");
        }
        if (msg.getMaxBucketsPerVisitor() != 1) {
            sb.append("max buckets per visitor=").append(msg.getMaxBucketsPerVisitor()).append("\n");
        }
        if (msg.getLoadType() != LoadType.DEFAULT) {
            sb.append("load type=").append(msg.getLoadType().getName()).append("\n");
        }
        if (msg.getPriority() != DocumentProtocol.Priority.NORMAL_3) {
            sb.append("priority=").append(msg.getPriority()).append("\n");
        }
        if (!"DumpVisitor".equals(msg.getLibraryName())) {
            sb.append("visitor library=").append(msg.getLibraryName()).append("\n");
        }
        if (msg.getTrace().getLevel() != 0) {
            sb.append("trace level=").append(msg.getTrace().getLevel()).append("\n");
        }
        sb.append(")");
        return sb.toString();
    }

    private CreateVisitorReply createReply(CreateVisitorMessage msg) {
        CreateVisitorReply reply = (CreateVisitorReply)msg.createReply();
        reply.setMessage(msg);
        return reply;
    }

    private String replyToCreateVisitor(MockSender sender, BucketId progress) {
        CreateVisitorMessage msg = (CreateVisitorMessage)sender.getAndRemoveMessage(0);
        CreateVisitorReply reply = createReply(msg);
        reply.setLastBucket(progress);
        sender.reply(reply);
        return createVisitorToString(msg);
    }

    private interface ReplyModifier {
        public void modify(CreateVisitorReply reply);
    }

    private String replyToCreateVisitor(MockSender sender, ReplyModifier modifier) {
        CreateVisitorMessage msg = (CreateVisitorMessage)sender.getAndRemoveMessage(0);
        CreateVisitorReply reply = createReply(msg);
        modifier.modify(reply);
        sender.reply(reply);
        return createVisitorToString(msg);
    }

    private String replyWrongDistributionToCreateVisitor(MockSender sender,
                                                         String clusterState) {
        CreateVisitorMessage msg = (CreateVisitorMessage)sender.getAndRemoveMessage(0);
        WrongDistributionReply reply = new WrongDistributionReply(clusterState);
        reply.setMessage(msg);
        reply.addError(
                new com.yahoo.messagebus.Error(
                        DocumentProtocol.ERROR_WRONG_DISTRIBUTION,
                        "i pity the fool who uses 1 distribution bit!"));
        sender.reply(reply);
        return createVisitorToString(msg);
    }

    private String replyErrorToCreateVisitor(MockSender sender, Error error) {
        CreateVisitorMessage msg = (CreateVisitorMessage)sender.getAndRemoveMessage(0);
        CreateVisitorReply reply = createReply(msg);
        reply.setMessage(msg);
        reply.addError(error);
        sender.reply(reply);
        return createVisitorToString(msg);
    }

    private class MockComponents {
        public MockSender sender;
        public MockReceiver receiver;
        public MockAsyncTaskExecutor executor;
        public VisitorParameters params;
        public MockControlHandler controlHandler;
        public MockDataHandler dataHandler;
        public MessageBusVisitorSession visitorSession;
        public MockClock clock;

        public MockComponents(VisitorParameters visitorParameters) {
            this(visitorParameters, null);
        }

        public MockComponents(VisitorParameters visitorParameters, RoutingTable routingTable) {
            sender = new MockSender();
            receiver = new MockReceiver();
            executor = new MockAsyncTaskExecutor();
            params = visitorParameters;
            controlHandler = new MockControlHandler();
            dataHandler = new MockDataHandler();
            clock = new MockClock();
            params.setControlHandler(controlHandler);
            params.setLocalDataHandler(dataHandler);
            visitorSession = createVisitorSession(sender, receiver, executor, params, routingTable, clock);
        }

        public MockComponents() {
            this(createVisitorParameters(""));
        }

        public MockComponents(String selection) {
            this(createVisitorParameters(selection));
        }

        // This seems a bit anti-pattern-ish in terms of builder usage...
        public MockComponents(MockComponentsBuilder builder) {
            sender = builder.sender;
            receiver = builder.receiver;
            executor = builder.executor;
            params = builder.params;
            controlHandler = builder.controlHandler;
            dataHandler = builder.dataHandler;
            clock = builder.clock;
            visitorSession = createVisitorSession(sender, receiver, executor, params, builder.routingTable, clock);
        }
    }

    private class MockComponentsBuilder {
        public MockSender sender = new MockSender();
        public MockReceiver receiver = new MockReceiver();
        public MockAsyncTaskExecutor executor = new MockAsyncTaskExecutor();
        public VisitorParameters params = createVisitorParameters("");
        public MockControlHandler controlHandler = new MockControlHandler();
        public MockDataHandler dataHandler = new MockDataHandler();
        public RoutingTable routingTable = null;
        public MockClock clock = new MockClock();

        public MockComponents createMockComponents() {
            return new MockComponents(this);
        }
    }

    private MockComponents createDefaultMock() {
        return new MockComponents();
    }

    private MockComponents createDefaultMock(String selection) {
        return new MockComponents(selection);
    }

    private MockComponents createDefaultMock(VisitorParameters visitorParameters) {
        return new MockComponents(visitorParameters);
    }

    private MockComponents createDefaultMock(VisitorParameters visitorParameters,
                                             RoutingTable routingTable) {
        return new MockComponents(visitorParameters, routingTable);
    }

    private void doTestSingleBucketVisit(VisitorParameters params,
                                         String expectedMessage)
    {
        MockSender sender = new MockSender();
        MockReceiver receiver = new MockReceiver();
        MockAsyncTaskExecutor executor = new MockAsyncTaskExecutor();

        MessageBusVisitorSession visitorSession = createVisitorSession(
                sender, receiver, executor, params);
        visitorSession.start();

        // Process initial task which sends a single CreateVisitor.
        executor.expectAndProcessTasks(1);
        assertEquals(expectedMessage, replyToCreateVisitor(sender, ProgressToken.FINISHED_BUCKET));
        assertFalse(visitorSession.isDone());

        // Single task for handling CreateVisitorReply.
        executor.expectAndProcessTasks(1);
        executor.expectNoTasks();
        assertTrue(visitorSession.isDone());
    }

    @Test
    public void testSendSingleCreateVisitor() {
        VisitorParameters params = createVisitorParameters("");
        Set<BucketId> bucketsToVisit = new TreeSet<BucketId>();
        BucketId bid = new BucketId(16, 1234);
        bucketsToVisit.add(bid);
        params.setBucketsToVisit(bucketsToVisit);

        String expected = "CreateVisitorMessage(buckets=[\n" +
                bid + "\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)";

        doTestSingleBucketVisit(params, expected);
    }

    /**
     * Test that using an id.user=foo selection only tries to visit a single
     * superbucket for that user.
     */
    @Test
    public void testIdUserSelection() {
        VisitorParameters params = createVisitorParameters("id.user=1234");
        String expected = "CreateVisitorMessage(buckets=[\n" +
                new BucketId(32, 1234) + "\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n" +
                "selection='id.user=1234'\n)";
        doTestSingleBucketVisit(params, expected);
    }

    @Test
    public void testMessageParameters() {
        MockSender sender = new MockSender();
        MockReceiver receiver = new MockReceiver();
        MockAsyncTaskExecutor executor = new MockAsyncTaskExecutor();
        // Test all parameters that can be forwarded except bucketsToVisit,
        // which is already explicitly tested in testSendSingleCreateVisitor().
        VisitorParameters params = new VisitorParameters("");
        params.setDocumentSelection("id.user=5678");
        params.setFromTimestamp(9001);
        params.setToTimestamp(10001);
        params.setVisitorLibrary("CoolVisitor");
        params.setLibraryParameter("groovy", "dudes");
        params.setLibraryParameter("ninja", "turtles");
        params.setMaxBucketsPerVisitor(55);
        params.setPriority(DocumentProtocol.Priority.HIGHEST);
        params.setRoute("extraterrestrial/highway");
        params.setTimeoutMs(1337);
        params.setMaxPending(111);
        params.setFieldSet("[header]");
        params.setVisitorOrdering(123);
        params.setLoadType(new LoadType(3, "samnmax", DocumentProtocol.Priority.HIGH_3));
        params.setVisitRemoves(true);
        params.setVisitInconsistentBuckets(true);
        params.setTraceLevel(9);

        MessageBusVisitorSession visitorSession = createVisitorSession(
                sender, receiver, executor, params);
        visitorSession.start();

        // Process initial task which sends a single CreateVisitor.
        executor.expectAndProcessTasks(1);

        CreateVisitorMessage msg = (CreateVisitorMessage)sender.getAndRemoveMessage(0);
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x800000000000162e)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n" +
                "selection='id.user=5678'\n" +
                "time remaining=1337\n" +
                "from timestamp=9001\n" +
                "to timestamp=10001\n" +
                "max pending=111\n" +
                "fieldset=[header]\n" +
                "visit inconsistent=true\n" +
                "visit removes=true\n" +
                "parameters=[\n" +
                "groovy -> dudes\n" +
                "ninja -> turtles\n" +
                "]\n" +
                "route=extraterrestrial/highway\n" +
                "ordering=123\n" +
                "max buckets per visitor=55\n" +
                "load type=samnmax\n" +
                "priority=HIGHEST\n" +
                "visitor library=CoolVisitor\n" +
                "trace level=9\n" +
                ")",
                createVisitorToString(msg));

        assertFalse(msg.getRetryEnabled());
    }

    @Test
    public void testBucketProgress() {
        MockComponents mc = createDefaultMock("id.user==1234");

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n" +
                "selection='id.user==1234'\n)",
                replyToCreateVisitor(mc.sender, new BucketId(33, 1234 | (1L << 32))));

        // Reply task
        mc.executor.expectAndProcessTasks(1);
        assertFalse(mc.visitorSession.isDone());
        // Should get new CreateVisitor task for sub-bucket continuation
        mc.executor.expectAndProcessTasks(1);
        CreateVisitorMessage msg2 = (CreateVisitorMessage)mc.sender.getAndRemoveMessage(0);
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x84000001000004d2)\n" +
                "]\n" +
                "selection='id.user==1234'\n)",
                createVisitorToString(msg2));

        assertEquals(mc.controlHandler.getProgress(), mc.visitorSession.getProgress());
    }

    @Test
    public void testMaxPendingVisitorsForSender() {
        MockSender sender = new MockSender();
        MockReceiver receiver = new MockReceiver();
        sender.setMaxPending(1);
        MockAsyncTaskExecutor executor = new MockAsyncTaskExecutor();
        // Visit-all will normally start with 1 distribution bit and send
        // to 2 superbuckets if allowed to do so.
        VisitorParameters params = createVisitorParameters("");
        MessageBusVisitorSession visitorSession = createVisitorSession(
                sender, receiver, executor, params);

        visitorSession.start();
        executor.expectAndProcessTasks(1);
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000000)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(sender, ProgressToken.FINISHED_BUCKET));
        executor.expectAndProcessTasks(1); // Reply
        executor.expectAndProcessTasks(1); // New visitor

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000001)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(sender, ProgressToken.FINISHED_BUCKET));
    }

    @Test
    public void testVisitAll() {
        MockSender sender = new MockSender();
        MockReceiver receiver = new MockReceiver();
        sender.setMaxPending(1000);
        MockAsyncTaskExecutor executor = new MockAsyncTaskExecutor();
        VisitorParameters params = createVisitorParameters("");
        MessageBusVisitorSession visitorSession = createVisitorSession(
                sender, receiver, executor, params);

        visitorSession.start();
        executor.expectAndProcessTasks(1);
        assertEquals(2, sender.getMessageCount());
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000000)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(sender, ProgressToken.FINISHED_BUCKET));

        executor.expectAndProcessTasks(1);
        executor.expectNoTasks(); // No new visitors yet.

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000001)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(sender, new BucketId(8, 1 | (1 << 8))));

        executor.expectAndProcessTasks(1);
        // Send new visitor for bucket 1
        executor.expectAndProcessTasks(1);

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000001)\n" +
                "BucketId(0x2000000000000001)\n" +
                "]\n)",
                replyToCreateVisitor(sender, ProgressToken.FINISHED_BUCKET));

        executor.expectAndProcessTasks(1); // Reply task
        executor.expectNoTasks(); // Visiting complete

        assertTrue(visitorSession.isDone());
    }

    @Test
    public void testWrongDistributionAdjustsDistributionBits() {
        MockSender sender = new MockSender();
        MockReceiver receiver = new MockReceiver();
        sender.setMaxPending(2);
        MockAsyncTaskExecutor executor = new MockAsyncTaskExecutor();
        VisitorParameters params = createVisitorParameters("");
        MessageBusVisitorSession visitorSession = createVisitorSession(
                sender, receiver, executor, params);

        visitorSession.start();
        executor.expectAndProcessTasks(1);
        assertEquals(2, sender.getMessageCount());
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000000)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyWrongDistributionToCreateVisitor(
                        sender, "version:2 storage:100 distributor:100 bits:16"));
        executor.expectAndProcessTasks(1); // WDR reply
        // Replying with WRONG_DISTRIBUTION when there are active visitors
        // should not send any new visitors until all active have returned.
        // This allows the visitor iterator to consistently adjust the visiting
        // progress based on the distribution bit change.
        executor.expectNoTasks();

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000001)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyWrongDistributionToCreateVisitor(
                        sender, "version:2 storage:100 distributor:100 bits:16"));
        executor.expectAndProcessTasks(1); // WDR reply
        executor.expectAndProcessTasks(1, new long[] { 0 }); // Send new visitors, no delay

        // Now with 16 distribution bits.
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x4000000000000000)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(sender, ProgressToken.FINISHED_BUCKET));

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x4000000000008000)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(sender, ProgressToken.FINISHED_BUCKET));
        // .... and 65533 more
    }

    private class MockControlHandler extends VisitorControlHandler {
        private StringBuilder builder = new StringBuilder();
        private RuntimeException exceptionOnProgress = null;
        private boolean synchronousWaitUntilDone = false;
        private boolean waiting = false;

        public void setExceptionOnProgress(RuntimeException exceptionOnProgress) {
            this.exceptionOnProgress = exceptionOnProgress;
        }

        public void setSynchronousWaitUntilDone(boolean synchronousWaitUntilDone) {
            this.synchronousWaitUntilDone = synchronousWaitUntilDone;
        }

        @Override
        public void onProgress(ProgressToken token) {
            super.onProgress(token);
            builder.append("onProgress : ");
            builder.append(token.getActiveBucketCount()).append(" active, ");
            builder.append(token.getPendingBucketCount()).append(" pending, ");
            builder.append(token.getFinishedBucketCount()).append(" finished, ");
            builder.append(token.getTotalBucketCount()).append(" total\n");
            if (exceptionOnProgress != null) {
                throw exceptionOnProgress;
            }
        }

        @Override
        public void onVisitorError(String message) {
            super.onVisitorError(message);
            builder.append("onVisitorError : ").append(message).append("\n");
        }

        @Override
        public void onVisitorStatistics(VisitorStatistics vs) {
            super.onVisitorStatistics(vs);
            builder.append("onVisitorStatistics : ");
            // Only bother with a couple of fields.
            builder.append(vs.getBucketsVisited()).append(" buckets visited, ");
            builder.append(vs.getDocumentsReturned() + vs.getSecondPassDocumentsReturned()).append(" docs returned\n");
        }

        @Override
        public void onDone(CompletionCode code, String message) {
            super.onDone(code, message);
            builder.append("onDone : ").append(code).append( " - ");
            builder.append("'").append(message).append("'\n");
        }

        @Override
        public void setSession(VisitorControlSession session) {
            super.setSession(session);
            builder.append("setSession\n");
        }

        @Override
        public void reset() {
            super.reset();
            builder.append("reset\n");
        }

        @Override
        public boolean waitUntilDone(long timeoutMs) throws InterruptedException {
            builder.append("waitUntilDone : " + timeoutMs + "\n");
            if (synchronousWaitUntilDone) {
                synchronized (this) {
                    waiting = true;
                }
                return super.waitUntilDone(timeoutMs);
            }
            return isDone();
        }

        public synchronized boolean isWaiting() {
            return waiting;
        }

        public String toString() {
            return builder.toString();
        }

        public void resetMock() {
            builder = new StringBuilder();
        }
    }

    private class MockDataHandler extends VisitorDataHandler {

        public class MessageWrapper {
            private Message message;
            private AckToken ackToken;

            public MessageWrapper(Message message, AckToken ackToken) {
                this.message = message;
                this.ackToken = ackToken;
            }

            public Message getMessage() {
                return message;
            }

            public AckToken getAckToken() {
                return ackToken;
            }
        }

        private ArrayList<MessageWrapper> messages = new ArrayList<MessageWrapper>();
        private StringBuilder builder = new StringBuilder();
        private RuntimeException exceptionOnMessage = null;

        public void setExceptionOnMessage(RuntimeException exceptionOnMessage) {
            this.exceptionOnMessage = exceptionOnMessage;
        }

        @Override
        public void setSession(VisitorControlSession session) {
            builder.append("setSession\n");
            super.setSession(session);
        }

        @Override
        public void reset() {
            builder.append("reset\n");
            super.reset();
        }

        @Override
        public VisitorResponse getNext() {
            builder.append("getNext\n");
            return new VisitorResponse(null);
        }

        @Override
        public VisitorResponse getNext(int timeoutMilliseconds) throws InterruptedException {
            builder.append("getNext : ").append(timeoutMilliseconds).append('\n');
            return new VisitorResponse(null);
        }

        @Override
        public void onDone() {
            builder.append("onDone\n");
            super.onDone();
        }

        @Override
        public void onMessage(Message m, AckToken token) {
            builder.append("onMessage\n");
            messages.add(new MessageWrapper(m, token));
            if (exceptionOnMessage != null) {
                throw exceptionOnMessage;
            }
        }

        public ArrayList<MessageWrapper> getMessages() {
            return messages;
        }

        public String toString() {
            return builder.toString();
        }

        public void resetMock() {
            builder = new StringBuilder();
        }
    }

    @Test
    public void testControlHandlerInvocationNormal() {
        MockComponents mc = createDefaultMock("id.user=1234");
        assertEquals("reset\nsetSession\n", mc.controlHandler.toString());
        mc.controlHandler.resetMock();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        replyToCreateVisitor(mc.sender, (reply) -> {
            reply.setLastBucket(ProgressToken.FINISHED_BUCKET);
            VisitorStatistics stats = new VisitorStatistics();
            stats.setBucketsVisited(11);
            stats.setDocumentsReturned(22);
            reply.setVisitorStatistics(stats);
        });
        mc.executor.expectAndProcessTasks(1);
        assertEquals("onProgress : 0 active, 0 pending, 1 finished, 1 total\n" +
                "onVisitorStatistics : 11 buckets visited, 22 docs returned\n" +
                "onDone : SUCCESS - ''\n",
                mc.controlHandler.toString());
        assertTrue(mc.visitorSession.isDone());
    }

    @Test
    public void testLocalDataHandlerInvocationWithAck() {
        MockComponents mc = createDefaultMock("id.user=1234");
        assertEquals("reset\nsetSession\n", mc.dataHandler.toString());
        mc.dataHandler.resetMock();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        // Send a remove (so we don't have to create a new doc instance)
        mc.receiver.send(new RemoveDocumentMessage(new DocumentId("doc:foo:bar")));
        mc.executor.expectAndProcessTasks(1);

        // Not yet ACKed
        assertEquals("", mc.receiver.repliesToString());

        assertEquals(1, mc.dataHandler.getMessages().size());
        MockDataHandler.MessageWrapper msg = mc.dataHandler.getMessages().get(0);
        mc.dataHandler.ack(msg.getAckToken());

        assertEquals("RemoveDocumentReply\n", mc.receiver.repliesToString());

        replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET);
        mc.executor.expectAndProcessTasks(1);
        assertEquals(
                "onMessage\n" +
                "onDone\n",
                mc.dataHandler.toString());
        assertTrue(mc.visitorSession.isDone());
    }

    @Test
    public void testCreateDefaultVisitorControlHandlerIfNoneGiven() {
        MockSender sender = new MockSender();
        MockReceiver receiver = new MockReceiver();
        MockAsyncTaskExecutor executor = new MockAsyncTaskExecutor();
        VisitorParameters params = createVisitorParameters("");
        MessageBusVisitorSession visitorSession = createVisitorSession(
                sender, receiver, executor, params);
        assertNotNull(params.getControlHandler());
    }

    @Test
    public void testNoDataHandlersImpliesVisitorDataQueue() {
        MockSender sender = new MockSender();
        MockReceiver receiver = new MockReceiver();
        MockAsyncTaskExecutor executor = new MockAsyncTaskExecutor();
        VisitorParameters params = createVisitorParameters("");
        MessageBusVisitorSession visitorSession = createVisitorSession(
                sender, receiver, executor, params);
        assertNotNull(params.getLocalDataHandler());
        assertTrue(params.getLocalDataHandler() instanceof VisitorDataQueue);
    }

    @Test
    public void testAbortVisiting() {
        MockComponents mc = createDefaultMock();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        assertEquals(2, mc.sender.getMessageCount());
        mc.controlHandler.resetMock();
        // While we have active visitors, abort visiting. Completion function
        // should not be called until we have no pending messages.
        mc.visitorSession.abort();
        assertFalse(mc.visitorSession.isDone());

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000000)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));

        mc.executor.expectAndProcessTasks(1);
        assertEquals("onProgress : 1 active, 0 pending, 1 finished, 2 total\n" +
                "onVisitorStatistics : 0 buckets visited, 0 docs returned\n",
                mc.controlHandler.toString());
        assertFalse(mc.visitorSession.isDone());
        mc.controlHandler.resetMock();

        // When aborted, no new visitors should be sent.
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000001)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(mc.sender, new BucketId(0x8400000100000001L)));

        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectAndProcessTasks(0);
        assertEquals(0, mc.sender.getMessageCount());
        assertTrue(mc.visitorSession.isDone());

        assertEquals("onProgress : 0 active, 1 pending, 1 finished, 2 total\n" +
                "onVisitorStatistics : 0 buckets visited, 0 docs returned\n" +
                "onDone : ABORTED - 'Visitor aborted by user'\n",
                mc.controlHandler.toString());
        assertEquals("ABORTED: Visitor aborted by user",
                mc.controlHandler.getResult().toString());
    }

    /**
     * Test that different sessions get different visitor names.
     */
    @Test
    public void testUniqueSessionNames() {
        MockComponents mc1 = createDefaultMock();
        MockComponents mc2 = createDefaultMock();
        assert(!mc1.visitorSession.getSessionName().equals(
                mc2.visitorSession.getSessionName()));
    }

    /**
     * Test that different visitors within the same session get different
     * names.
     */
    @Test
    public void testUniqueVisitorNames() {
        MockComponents mc = createDefaultMock();
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        assertEquals(2, mc.sender.getMessageCount());

        CreateVisitorMessage msg1 = (CreateVisitorMessage)mc.sender.getAndRemoveMessage(0);
        CreateVisitorMessage msg2 = (CreateVisitorMessage)mc.sender.getAndRemoveMessage(0);
        assert(!msg1.getInstanceId().equals(msg2.getInstanceId()));
    }

    @Test
    public void testMax1ConcurrentSendCreateVisitorsTask() {
        MockComponents mc = createDefaultMock();

        mc.executor.setMockTimeMs(1000);
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        assertEquals(2, mc.sender.getMessageCount());

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000000)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(mc.sender, new BucketId(0x8400000100000000L)));

        // Execute reply task which will schedule a SendCreateVisitors task.
        mc.executor.expectAndProcessTasks(1);
        assertEquals(1, mc.executor.getScheduledTaskCount());
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000001)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(mc.sender, new BucketId(0x8400000100000001L)));
        // Execute reply task which should _not_ schedule a SendCreateVisitors task
        // since one has already been scheduled. Note that since the second reply
        // task was directly submitted rather than scheduled, it should always be
        // executed before the SendCreateVisitors task in our deterministic test
        // environment.
        mc.executor.expectAndProcessTasks(2, 1);
        // Finally execute scheduled SendCreateVisitors task.
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks();
        assertEquals(2, mc.sender.getMessageCount());
    }

    @Test
    public void testRetryVisitorOnTransientError() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.controlHandler.resetMock();
        mc.executor.expectAndProcessTasks(1);
        replyToCreateVisitor(mc.sender, (reply) -> {
            reply.addError(new Error(
                    DocumentProtocol.ERROR_ABORTED,
                    "bucket fell down a well"));
        });
        mc.executor.expectAndProcessTasks(1); // reply
        // Must have a 100ms delay
        mc.executor.expectAndProcessTasks(1, new long[] { 100 }); // send
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n" +
                "selection='id.user==1234'\n)",
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks();
        assertTrue(mc.visitorSession.isDone());
        assertEquals("onVisitorError : ABORTED: bucket fell down a well\n" +
                "onProgress : 0 active, 0 pending, 1 finished, 1 total\n" +
                "onVisitorStatistics : 0 buckets visited, 0 docs returned\n" +
                "onDone : SUCCESS - ''\n",
                mc.controlHandler.toString());
    }

    @Test
    public void testFailVisitingOnFatalError() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.controlHandler.resetMock();
        mc.executor.expectAndProcessTasks(1);
        replyToCreateVisitor(mc.sender, (reply) -> {
            reply.addError(new Error(
                    DocumentProtocol.ERROR_INTERNAL_FAILURE,
                    "node caught fire"));
        });
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectNoTasks();
        assertEquals(0, mc.sender.getMessageCount());
        assertTrue(mc.visitorSession.isDone());

        assertEquals("onVisitorError : INTERNAL_FAILURE: node caught fire\n" +
                "onDone : FAILURE - 'INTERNAL_FAILURE: node caught fire'\n",
                mc.controlHandler.toString());
    }

    /**
     * Do not complete visiting upon fatal error until all replies have
     * been received.
     */
    @Test
    public void testWaitUntilVisitorsDoneOnFatalError() {
        MockComponents mc = createDefaultMock();
        mc.visitorSession.start();
        mc.controlHandler.resetMock(); // clear messages
        mc.executor.expectAndProcessTasks(1);
        assertEquals(2, mc.sender.getMessageCount());
        replyToCreateVisitor(mc.sender, (reply) -> {
            reply.addError(new Error(
                    DocumentProtocol.ERROR_INTERNAL_FAILURE,
                    "node fell down a well"));
        });
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectNoTasks();
        assertEquals(1, mc.sender.getMessageCount()); // no resending
        assertFalse(mc.visitorSession.isDone()); // not done yet

        replyToCreateVisitor(mc.sender, (reply) -> {
            reply.addError(new Error(
                    DocumentProtocol.ERROR_INTERNAL_FAILURE,
                    "node got hit by a falling brick"));
        });
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectNoTasks();
        assertEquals(0, mc.sender.getMessageCount()); // no resending
        assertTrue(mc.visitorSession.isDone());

        // should get first received failure message as completion failure message
        assertEquals("onVisitorError : INTERNAL_FAILURE: node fell down a well\n" +
                "onVisitorError : INTERNAL_FAILURE: node got hit by a falling brick\n" +
                "onDone : FAILURE - 'INTERNAL_FAILURE: node fell down a well'\n",
                mc.controlHandler.toString());
    }

    private void doTestEarlyCompletion(VisitorParameters visitorParameters,
                                       ReplyModifier replyModifier1,
                                       ReplyModifier replyModifier2)
    {
        MockComponents mc = createDefaultMock(visitorParameters);
        mc.controlHandler.resetMock();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        // First reply gives only 9 hits, so must send another visitor
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n" +
                "selection='id.user==1234'\n)",
                replyToCreateVisitor(mc.sender, replyModifier1));
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectAndProcessTasks(1); // new visitor
        mc.controlHandler.resetMock();
        assertEquals(1, mc.sender.getMessageCount());
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x84000001000004d2)\n" +
                "]\n" +
                "selection='id.user==1234'\n)",
                replyToCreateVisitor(mc.sender, replyModifier2));
        // we've now got enough total hits; session should be marked as
        // completed and no further visitors should be sent.
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectNoTasks();
        assertEquals(0, mc.sender.getMessageCount());

        assertEquals("onProgress : 0 active, 1 pending, 0 finished, 1 total\n" +
                "onVisitorStatistics : 2 buckets visited, 10 docs returned\n" +
                "onDone : SUCCESS - ''\n", mc.controlHandler.toString());
        assertEquals("OK: ", mc.controlHandler.getResult().toString());
    }

    /**
     * Test visitor "prematurely" completing due to max total hits being
     * reached when no other visitors are currently pending.
     */
    @Test
    public void testMaxTotalHitsEarlyCompletion() {
        VisitorParameters visitorParameters = createVisitorParameters("id.user==1234");
        visitorParameters.setMaxTotalHits(10);
        ReplyModifier replyModifier1 = (reply) -> {
            VisitorStatistics stats = new VisitorStatistics();
            stats.setBucketsVisited(1);
            stats.setDocumentsReturned(9);
            reply.setVisitorStatistics(stats);
            reply.setLastBucket(new BucketId(33, 1234 | (1L << 32)));
        };
        ReplyModifier replyModifier2 = (reply) -> {
            VisitorStatistics stats = new VisitorStatistics();
            stats.setBucketsVisited(1);
            stats.setDocumentsReturned(1);
            reply.setVisitorStatistics(stats);
            reply.setLastBucket(new BucketId(34, 1234 | (1L << 33)));
        };
        doTestEarlyCompletion(visitorParameters, replyModifier1, replyModifier2);
    }

    @Test
    public void testVisitingCompletedFromSufficientFirstPassHits() {
        VisitorParameters visitorParameters = createVisitorParameters("id.user==1234");
        visitorParameters.setMaxFirstPassHits(10);
        ReplyModifier replyModifier1 = (reply) -> {
            VisitorStatistics stats = new VisitorStatistics();
            stats.setBucketsVisited(1);
            stats.setDocumentsReturned(9);
            reply.setVisitorStatistics(stats);
            reply.setLastBucket(new BucketId(33, 1234 | (1L << 32)));
        };
        ReplyModifier replyModifier2 = (reply) -> {
            VisitorStatistics stats = new VisitorStatistics();
            stats.setBucketsVisited(1);
            stats.setDocumentsReturned(1);
            reply.setVisitorStatistics(stats);
            reply.setLastBucket(new BucketId(34, 1234 | (1L << 33)));
        };
        doTestEarlyCompletion(visitorParameters, replyModifier1, replyModifier2);
    }

    @Test
    public void testVisitingCompletedFromSecondPassHits() {
        VisitorParameters visitorParameters = createVisitorParameters("id.user==1234");
        visitorParameters.setMaxTotalHits(10);
        ReplyModifier replyModifier1 = (reply) -> {
            VisitorStatistics stats = new VisitorStatistics();
            stats.setBucketsVisited(1);
            stats.setDocumentsReturned(5);
            stats.setSecondPassDocumentsReturned(4);
            reply.setVisitorStatistics(stats);
            reply.setLastBucket(new BucketId(33, 1234 | (1L << 32)));
        };
        ReplyModifier replyModifier2 = (reply) -> {
            VisitorStatistics stats = new VisitorStatistics();
            stats.setBucketsVisited(1);
            stats.setSecondPassDocumentsReturned(1);
            reply.setVisitorStatistics(stats);
            reply.setLastBucket(new BucketId(34, 1234 | (1L << 33)));
        };
        doTestEarlyCompletion(visitorParameters, replyModifier1, replyModifier2);
    }

    /**
     * Test that waitUntilDone on the session is forwarded to the control handler.
     */
    @Test
    public void testControlHandlerWaitUntilDone() throws Exception {
        MockComponents mc = createDefaultMock();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        mc.controlHandler.resetMock();

        assertFalse(mc.visitorSession.waitUntilDone(1234)); // not completed
        assertEquals("waitUntilDone : 1234\n", mc.controlHandler.toString());
    }

    @Test
    public void testDataHandlerGetNext() throws Exception {
        MockComponents mc = createDefaultMock();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        mc.dataHandler.resetMock();

        assertNotNull(mc.visitorSession.getNext());
        assertNotNull(mc.visitorSession.getNext(1234));
        assertEquals("getNext\ngetNext : 1234\n", mc.dataHandler.toString());
    }

    @Test
    public void testNoLocalDataHandlerGetNext() throws Exception {
        MockSender sender = new MockSender();
        MockReceiver receiver = new MockReceiver();
        MockAsyncTaskExecutor executor = new MockAsyncTaskExecutor();
        VisitorParameters params = createVisitorParameters("");
        params.setRemoteDataHandler("the/moon");
        MessageBusVisitorSession visitorSession = createVisitorSession(
                sender, receiver, executor, params);

        visitorSession.start();
        executor.expectAndProcessTasks(1);

        try {
            assertNotNull(visitorSession.getNext());
            fail("No exception thrown on getNext()");
        } catch (IllegalStateException e) {
            assertEquals("Data has been routed to external source for this visitor", e.getMessage());
        }
        try {
            assertNotNull(visitorSession.getNext(1234));
            fail("No exception thrown on getNext(int)");
        } catch (IllegalStateException e) {
            assertEquals("Data has been routed to external source for this visitor", e.getMessage());
        }
    }

    private static class SharedValue<T> {
        private T value = null;

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }
    }

    void waitUntilTrue(long timeoutMs, Callable<Boolean> callable) throws Exception {
        long timeStart = System.currentTimeMillis();
        while (!callable.call()) {
            if (System.currentTimeMillis() - timeStart >= timeoutMs) {
                throw new RuntimeException("Timeout while waiting for callable to yield true");
            }
            Thread.sleep(10);
        }
    }

    /**
     * Test that calling waitUntilDone waits until session has completed.
     * Test that destroy() destroys the communication interfaces it uses.
     * @throws Exception
     */
    @Test
    public void testSynchronousWaitUntilDoneAndDestroy() throws Exception {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        mc.controlHandler.setSynchronousWaitUntilDone(true);
        mc.controlHandler.resetMock();
        final MockControlHandler controlHandler = mc.controlHandler;
        final MessageBusVisitorSession session = mc.visitorSession;
        final SharedValue<Exception> exceptionPropagator = new SharedValue<Exception>();
        final CyclicBarrier barrier = new CyclicBarrier(2);

        // Have to do this multi-threaded for once since waitUntilDone/destroy
        // are both synchronous and will not return before session is complete,
        // either through success or failure.
        Thread t = new Thread(() -> {
            try {
                boolean ok = session.waitUntilDone(20000);
                if (!session.isDone()) {
                    throw new IllegalStateException("waitUntilDone returned, but session is not marked as done");
                }
                assertTrue(ok);
                session.destroy();
                barrier.await(20000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                exceptionPropagator.setValue(e);
            }
        });
        t.start();

        try {
            waitUntilTrue(20000, () -> controlHandler.isWaiting());

            // Reply to visitor, causing session to complete
            assertEquals("CreateVisitorMessage(buckets=[\n" +
                    "BucketId(0x80000000000004d2)\n" +
                    "BucketId(0x0000000000000000)\n" +
                    "]\n" +
                    "selection='id.user==1234'\n)",
                    replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));
            mc.executor.expectAndProcessTasks(1); // reply
            mc.executor.expectNoTasks();

            barrier.await(20000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            t.interrupt();
            throw e;
        } finally {
            t.join();
        }

        if (exceptionPropagator.getValue() != null) {
            throw new IllegalStateException(
                    "Exception thrown in destruction thread",
                    exceptionPropagator.getValue());
        }

        assertTrue(mc.sender.isDestroyed());
        assertTrue(mc.receiver.isDestroyed());

        assertEquals(
                "waitUntilDone : 20000\n" +
                "onProgress : 0 active, 0 pending, 1 finished, 1 total\n" +
                "onVisitorStatistics : 0 buckets visited, 0 docs returned\n" +
                "onDone : SUCCESS - ''\n",
                mc.controlHandler.toString());
    }

    @Test
    public void testDestroyAbortsSessionIfNotDone() throws Exception {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        mc.controlHandler.setSynchronousWaitUntilDone(true);
        mc.controlHandler.resetMock();
        final MessageBusVisitorSession session = mc.visitorSession;
        final SharedValue<Exception> exceptionPropagator = new SharedValue<Exception>();
        final CyclicBarrier barrier = new CyclicBarrier(2);

        // Have to do this multi-threaded for once since destroy is
        // synchronous and any code logic bug could otherwise cause the
        // test (and thus the build) to hang indefinitely.
        // NOTE: even though the MockControlHandler itself is not thread safe,
        // the control flow of the test should guarantee there is no concurrent
        // access to it.
        Thread t = new Thread(() -> {
            try {
                session.destroy();
                if (!session.isDone()) {
                    throw new IllegalStateException("Session is not marked as done after destroy()");
                }
                barrier.await(20000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                exceptionPropagator.setValue(e);
            }
        });
        t.start();

        try {
            waitUntilTrue(20000, () -> session.isDestroying());

            // Reply to visitor. Normally, the visitor would be resent, but
            // since destroy aborts the session, this won't happen and the
            // session will be marked as completed instead.
            replyErrorToCreateVisitor(mc.sender, new Error(DocumentProtocol.ERROR_BUCKET_DELETED, "goner"));
            mc.executor.expectAndProcessTasks(1); // reply
            mc.executor.expectNoTasks();

            barrier.await(20000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            t.interrupt();
            throw e;
        } finally {
            t.join();
        }

        if (exceptionPropagator.getValue() != null) {
            throw new IllegalStateException(
                    "Exception thrown in destruction thread",
                    exceptionPropagator.getValue());
        }

        assertTrue(mc.sender.isDestroyed());
        assertTrue(mc.receiver.isDestroyed());

        assertEquals(
                "onDone : ABORTED - 'Session explicitly destroyed before completion'\n",
                mc.controlHandler.toString());
    }

    /**
     * Test that receiving a WrongDistributionReply with a cluster state
     * we cannot parse fails the visiting session. We cannot visit anything
     * if we don't have a proper state anyway, so might as well fail fast.
     */
    @Test
    public void testClusterStateParseFailure() {
        MockComponents mc = createDefaultMock();
        mc.visitorSession.start();
        mc.controlHandler.resetMock(); // clear messages
        mc.executor.expectAndProcessTasks(1);
        assertEquals(2, mc.sender.getMessageCount());

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000000)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyWrongDistributionToCreateVisitor(
                        mc.sender, "one:bad cluster:state"));
        mc.executor.expectAndProcessTasks(1); // WDR reply
        // no resending since visiting has failed
        mc.executor.expectNoTasks();
        assertFalse(mc.controlHandler.isDone());

        // Complete visiting
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000001)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyWrongDistributionToCreateVisitor(
                        mc.sender, "another:bad cluster:state"));
        mc.executor.expectAndProcessTasks(1); // WDR reply
        assertTrue(mc.controlHandler.isDone());
        assertEquals("onDone : FAILURE - 'Failed to parse cluster state 'one:bad cluster:state''\n",
                mc.controlHandler.toString());
    }

    @Test
    public void testReceiveVisitorInfoMessage() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.controlHandler.resetMock();
        mc.executor.expectAndProcessTasks(1);

        // Send a VisitorInfo back without any errors. This should trigger
        // the control handler's onProgress routine (at least this is what
        // the legacy code does, so let's go with that).
        mc.receiver.send(new VisitorInfoMessage());
        mc.executor.expectAndProcessTasks(1); // Message handler task

        assertEquals("onProgress : 1 active, 0 pending, 0 finished, 1 total\n",
                mc.controlHandler.toString());
        assertEquals("VisitorReply\n", mc.receiver.repliesToString());
        mc.receiver.getAndRemoveReply(0);

        // Send VisitorInfo with error. This should invoke the control
        // handler's onVisitorError method.
        VisitorInfoMessage errMsg = new VisitorInfoMessage();
        errMsg.setErrorMessage("bears! bears everywhere!");

        mc.receiver.send(errMsg);
        mc.controlHandler.resetMock();
        mc.executor.expectAndProcessTasks(1); // Message handler task

        replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET);
        mc.executor.expectAndProcessTasks(1); // Reply handler task

        // Visitor info with error should not fail visiting itself, this
        // is only done for _replies_ with errors.
        assertEquals(
                "onVisitorError : bears! bears everywhere!\n" +
                "onProgress : 1 active, 0 pending, 0 finished, 1 total\n" +
                "onProgress : 0 active, 0 pending, 1 finished, 1 total\n" +
                "onVisitorStatistics : 0 buckets visited, 0 docs returned\n" +
                "onDone : SUCCESS - ''\n",
                mc.controlHandler.toString());
        assertEquals("VisitorReply\n", mc.receiver.repliesToString());
    }

    RoutingTable createDummyRoutingTable() {
        RoutingTableSpec spec = new RoutingTableSpec(DocumentProtocol.NAME);
        spec.addRoute(new RouteSpec("storage/badger.bar"));
        RouteSpec storageCluster = new RouteSpec("storage/cluster.foo");
        storageCluster.addHop("bunnies");
        spec.addRoute(storageCluster);
        spec.addRoute(new RouteSpec("storage/otters.baz"));
        return new RoutingTable(spec);
    }

    /**
     * Test that we try to get a route to the storage cluster automatically if
     * the provided visitor parameter route is null.
     */
    @Test
    public void testDefaultClusterRouteResolutionNullRoute() {
        VisitorParameters visitorParameters = createVisitorParameters("");
        visitorParameters.setRoute((Route)null); // ensure route is null
        RoutingTable table = createDummyRoutingTable();

        createDefaultMock(visitorParameters, table);
        assertEquals("storage/cluster.foo", visitorParameters.getRoute().toString());
    }

    /**
     * Test that we try to get a route to the storage cluster automatically if
     * the provided route has no hops.
     */
    @Test
    public void testDefaultClusterRouteResolutionNoHops() {
        VisitorParameters visitorParameters = createVisitorParameters("");
        visitorParameters.setRoute(new Route());
        RoutingTable table = createDummyRoutingTable();

        createDefaultMock(visitorParameters, table);
        assertEquals("storage/cluster.foo", visitorParameters.getRoute().toString());
    }

    /**
     * Test that we don't try to override a valid route in the parameters.
     */
    @Test
    public void testExplicitRouteNotOverridden() {
        VisitorParameters visitorParameters = createVisitorParameters("");
        visitorParameters.setRoute("mars");
        RoutingTable table = createDummyRoutingTable();

        createDefaultMock(visitorParameters, table);
        assertEquals("mars", visitorParameters.getRoute().toString());
    }

    @Test
    public void testRoutingTableHasMultipleStorageClusters() {
        VisitorParameters visitorParameters = createVisitorParameters("");
        visitorParameters.setRoute(new Route());
        RoutingTableSpec spec = new RoutingTableSpec(DocumentProtocol.NAME);
        spec.addRoute(new RouteSpec("storage/cluster.foo"));
        spec.addRoute(new RouteSpec("storage/cluster.bar"));
        RoutingTable table = new RoutingTable(spec);

        try {
            createDefaultMock(visitorParameters, table);
            fail("No exception thrown on multiple storage clusters");
        } catch (IllegalArgumentException e) {
            assertEquals("There are multiple storage clusters in your application, " +
                         "please specify which one to visit.",
                         e.getMessage());
        }
    }

    @Test
    public void testRoutingTableHasNoStorageClusters() {
        VisitorParameters visitorParameters = createVisitorParameters("");
        visitorParameters.setRoute(new Route());
        RoutingTableSpec spec = new RoutingTableSpec(DocumentProtocol.NAME);
        spec.addRoute(new RouteSpec("storage/lobster.foo"));
        RoutingTable table = new RoutingTable(spec);

        try {
            createDefaultMock(visitorParameters, table);
            fail("No exception thrown on zero storage clusters");
        } catch (IllegalArgumentException e) {
            assertEquals("No storage cluster found in your application.",
                         e.getMessage());
        }
    }

    @Test
    public void testExecutionErrorDuringReplyHandling() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.controlHandler.resetMock();
        mc.executor.expectAndProcessTasks(1);

        // Slightly dirty; since there aren't really many paths during
        // reply handling where we can reliably force an exception to
        // happen, send a bogus visitor reply with a null result bucket which
        // will trigger NPE when the progress token tries to access it.
        replyToCreateVisitor(mc.sender, (reply) -> reply.setLastBucket(null));
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectNoTasks();
        // Session shall now have failed (and completed)
        assertEquals(0, mc.sender.getMessageCount());
        assertTrue(mc.visitorSession.isDone());

        assertEquals("onDone : FAILURE - 'Got exception of type java.lang.NullPointerException " +
                "with message 'null' while processing reply in visitor session'\n",
                mc.controlHandler.toString());
    }

    /**
     * Test branch where we don't know how to handle a certain reply type.
     * This should never happen (since we only get replies for messages we've
     * already sent) but deal with it anyway!
     */
    @Test
    public void testFailureOnUnknownReplyType() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.controlHandler.resetMock();
        mc.executor.expectAndProcessTasks(1);

        mc.sender.getAndRemoveMessage(0);
        // Make a bogus reply that we never asked for
        RemoveDocumentMessage msg = new RemoveDocumentMessage(new DocumentId("doc:foo:bar"));
        DocumentReply reply = msg.createReply();
        mc.sender.reply(reply);

        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectNoTasks();
        assertEquals(0, mc.sender.getMessageCount());
        assertTrue(mc.visitorSession.isDone());

        assertEquals("onDone : FAILURE - 'Received reply we do not know how to " +
                "handle: com.yahoo.documentapi.messagebus.protocol.RemoveDocumentReply'\n",
                mc.controlHandler.toString());
    }

    @Test
    public void testExecutionErrorInSendCreateVisitorsTask() {
        MockComponents mc = createDefaultMock();
        mc.sender.setExceptionOnSend(new IllegalArgumentException("closed, come back tomorrow"));
        mc.visitorSession.start();
        mc.controlHandler.resetMock(); // clear messages
        mc.executor.expectAndProcessTasks(1);
        assertEquals(0, mc.sender.getMessageCount());

        assertTrue(mc.controlHandler.isDone());
        assertEquals("onDone : FAILURE - 'Got exception of type java.lang.IllegalArgumentException " +
                "with message 'closed, come back tomorrow' while attempting to send visitors'\n",
                mc.controlHandler.toString());
    }

    @Test
    public void testExceptionInHandleVisitorInfoMessage() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.controlHandler.resetMock();
        mc.controlHandler.setExceptionOnProgress(new IllegalArgumentException("failed bigtime"));
        mc.executor.expectAndProcessTasks(1);

        mc.receiver.send(new VisitorInfoMessage());
        mc.executor.expectAndProcessTasks(1); // Message handler task

        // Reply with OK; session should still have failed due to the processing error
        replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET);
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks();
        assertTrue(mc.controlHandler.isDone());

        // NOTE: 1st onProgress is invoked from VisitorInfo task.
        // No onVisitorStatistics since that happens after onProgress, which throws
        assertEquals("onProgress : 1 active, 0 pending, 0 finished, 1 total\n" +
                "onProgress : 0 active, 0 pending, 1 finished, 1 total\n" +
                "onDone : FAILURE - 'Got exception of type java.lang.IllegalArgumentException " +
                "with message 'failed bigtime' while processing VisitorInfoMessage'\n",
                mc.controlHandler.toString());
        assertEquals("VisitorReply(APP_FATAL_ERROR: Got exception of type java.lang.IllegalArgumentException " +
                "with message 'failed bigtime' while processing VisitorInfoMessage)\n",
                mc.receiver.repliesToString());
    }

    @Test
    public void testExceptionInHandleDocumentMessage() {
        MockComponents mc = createDefaultMock("id.user=1234");
        mc.dataHandler.resetMock();
        mc.controlHandler.resetMock();
        mc.dataHandler.setExceptionOnMessage(new IllegalArgumentException("oh no"));

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        mc.receiver.send(new RemoveDocumentMessage(new DocumentId("doc:foo:bar")));
        mc.executor.expectAndProcessTasks(1);
        assertEquals(1, mc.dataHandler.getMessages().size());

        // Reply with OK; session should still have failed due to the processing error
        replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET);
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks();
        assertTrue(mc.controlHandler.isDone());

        assertEquals("RemoveDocumentReply(APP_FATAL_ERROR: Got exception of type java.lang.IllegalArgumentException " +
                "with message 'oh no' while processing DocumentMessage)\n",
                mc.receiver.repliesToString());

        assertEquals("onProgress : 0 active, 0 pending, 1 finished, 1 total\n" +
                "onVisitorStatistics : 0 buckets visited, 0 docs returned\n" +
                "onDone : FAILURE - 'Got exception of type java.lang.IllegalArgumentException " +
                "with message 'oh no' while processing DocumentMessage'\n",
                mc.controlHandler.toString());
    }

    @Test
    public void testSilentlyIgnoreBucketDeletedNotFoundErrors() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.controlHandler.resetMock();
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        replyErrorToCreateVisitor(mc.sender, new Error(
                DocumentProtocol.ERROR_BUCKET_NOT_FOUND,
                "dave's not here, maaan"));
        mc.executor.expectAndProcessTasks(1); // reply
        // Should just resend with a 100ms delay
        mc.executor.expectAndProcessTasks(1, new long[] { 100 });

        // Now hit it with a BUCKET_DELETED error, which is also silent
        replyErrorToCreateVisitor(mc.sender, new Error(
                DocumentProtocol.ERROR_BUCKET_DELETED,
                "dave's not here either, maaan!"));
        mc.executor.expectAndProcessTasks(1); // reply
        // Should also resend with a 100ms delay
        mc.executor.expectAndProcessTasks(1, new long[] { 100 });

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n" +
                "selection='id.user==1234'\n)",
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));
        mc.executor.expectAndProcessTasks(1);

        assertTrue(mc.controlHandler.isDone());
        assertEquals("onProgress : 0 active, 0 pending, 1 finished, 1 total\n" +
                "onVisitorStatistics : 0 buckets visited, 0 docs returned\n" +
                "onDone : SUCCESS - ''\n",
                mc.controlHandler.toString());
    }

    private String dumpProgressToken(ProgressToken token) {
        StringBuilder builder = new StringBuilder();
        builder.append("#total: ").append(token.getTotalBucketCount()).append('\n');
        builder.append("#finished: ").append(token.getFinishedBucketCount()).append('\n');
        if (token.containsFailedBuckets()) {
            builder.append("failed:\n");
            Map<BucketId, BucketId> failed = token.getFailedBuckets();
            for (Map.Entry<BucketId, BucketId> kv : failed.entrySet()) {
                builder.append(kv.getKey()).append(" : ").append(kv.getValue()).append('\n');
            }
        }
        return builder.toString();
    }

    @Test
    public void testSkipBucketOnFatalErrorReply() {
        VisitorParameters visitorParameters = createVisitorParameters("");
        visitorParameters.skipBucketsOnFatalErrors(true);
        MockComponents mc = createDefaultMock(visitorParameters);
        mc.controlHandler.resetMock();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1); // create visitors
        assertEquals(2, mc.sender.getMessageCount());

        replyErrorToCreateVisitor(mc.sender, new Error(
                DocumentProtocol.ERROR_INTERNAL_FAILURE,
                "borked"));
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks(); // no more buckets to send for--all either failed or active
        assertEquals(1, mc.sender.getMessageCount());
        assertFalse(mc.controlHandler.isDone());

        // partial bucket progress which must be remembered
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x0400000000000001)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n)",
                replyToCreateVisitor(mc.sender, new BucketId(33, 1L | (1L << 32))));
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectAndProcessTasks(1); // create visitors
        assertEquals(1, mc.sender.getMessageCount());
        assertFalse(mc.controlHandler.isDone());

        // then fail bucket #2
        replyErrorToCreateVisitor(mc.sender, new Error(
                DocumentProtocol.ERROR_INTERNAL_FAILURE,
                "more borked"));
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectNoTasks();
        assertEquals(0, mc.sender.getMessageCount());

        assertTrue(mc.controlHandler.isDone());

        // make sure progress token was updated with bad buckets and
        // remembers the initial error message
        assertNotNull(mc.controlHandler.getProgress());
        assertTrue(mc.controlHandler.getProgress().containsFailedBuckets());
        assertEquals("INTERNAL_FAILURE: borked",
                mc.controlHandler.getProgress().getFirstErrorMsg());

        assertEquals("#total: 2\n" +
                "#finished: 2\n" +
                "failed:\n" +
                "BucketId(0x0400000000000000) : BucketId(0x0000000000000000)\n" +
                "BucketId(0x0400000000000001) : BucketId(0x8400000100000001)\n",
                dumpProgressToken(mc.controlHandler.getProgress()));

        assertEquals(
                "onVisitorError : INTERNAL_FAILURE: borked\n" +
                "onProgress : 0 active, 1 pending, 1 finished, 2 total\n" +
                "onVisitorStatistics : 0 buckets visited, 0 docs returned\n" +
                "onVisitorError : INTERNAL_FAILURE: more borked\n" +
                "onDone : FAILURE - 'INTERNAL_FAILURE: borked'\n",
                mc.controlHandler.toString());
    }

    @Test
    public void testSkipBucketOnFatalMessageProcessingError() {
        VisitorParameters visitorParameters = createVisitorParameters("id.user==1234");
        visitorParameters.skipBucketsOnFatalErrors(true);
        MockComponents mc = createDefaultMock(visitorParameters);
        mc.controlHandler.resetMock();
        mc.dataHandler.resetMock();
        mc.dataHandler.setExceptionOnMessage(new IllegalArgumentException("oh no"));

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        mc.receiver.send(new RemoveDocumentMessage(new DocumentId("doc:foo:bar")));
        mc.executor.expectAndProcessTasks(1);
        assertEquals(1, mc.dataHandler.getMessages().size());

        // NOTE: current behavior does _not_ fail the session at the end of
        // visiting if the CreateVisitor replies do not also return with failure
        // since this is tied to the ProgressToken and its failed buckets list.
        // We make the simplifying assumption that failing a visitor _message_
        // will subsequently cause its reply to fail back to us, allowing us to
        // handle this as a regular skippable bucket.
        // TODO: reconsider this?

        replyErrorToCreateVisitor(mc.sender, new Error(
                DocumentProtocol.ERROR_INTERNAL_FAILURE,
                "The Borkening"));
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks();
        assertTrue(mc.controlHandler.isDone());

        // Get UNPARSEABLE rather than APP_FATAL_ERROR if skip buckets is set
        assertEquals("RemoveDocumentReply(UNPARSEABLE: Got exception of type java.lang.IllegalArgumentException " +
                "with message 'oh no' while processing DocumentMessage)\n",
                mc.receiver.repliesToString());

        assertEquals("onVisitorError : INTERNAL_FAILURE: The Borkening\n" +
                "onDone : FAILURE - 'INTERNAL_FAILURE: The Borkening'\n",
                mc.controlHandler.toString());
        assertEquals("FAILURE: INTERNAL_FAILURE: The Borkening",
                mc.controlHandler.getResult().toString());
    }

    /**
     * Test assembly of message traces in session. Trace level propagation
     * is already tested elsewhere.
     */
    @Test
    public void testMessageTracing() {
        VisitorParameters visitorParameters = createVisitorParameters("");
        visitorParameters.setTraceLevel(7);
        MockComponents mc = createDefaultMock(visitorParameters);
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        final TraceNode traceNodes[] = {
                new TraceNode().addChild("hello"),
                new TraceNode().addChild("world")
        };

        for (int i = 0; i < 2; ++i) {
            final int idx = i;
            replyToCreateVisitor(mc.sender, (reply) -> reply.getTrace().getRoot().addChild(traceNodes[idx]));
        }
        mc.executor.expectAndProcessTasks(2);
        mc.executor.expectNoTasks();
        assertTrue(mc.controlHandler.isDone());

        Trace trace = mc.visitorSession.getTrace();
        assertNotNull(trace);
        assertEquals(7, trace.getLevel());
        assertEquals(
                "<trace>\n" +
                "    <trace>\n" +
                "        <trace>\n" +
                "            hello\n" +
                "        </trace>\n" +
                "    </trace>\n" +
                "    <trace>\n" +
                "        <trace>\n" +
                "            world\n" +
                "        </trace>\n" +
                "    </trace>\n" +
                "</trace>\n",
                trace.toString());
    }

    @Test
    public void testResumeVisitingProgress() {
        MockComponents mc = createDefaultMock("id.user==1234");

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n" +
                "selection='id.user==1234'\n)",
                replyToCreateVisitor(mc.sender, new BucketId(33, 1234 | (1L << 32))));

        // Abort session to stop sending visitors. Progress should still
        // be recorded.
        mc.visitorSession.abort();
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks();
        assertTrue(mc.controlHandler.isDone());

        VisitorParameters params = createVisitorParameters("id.user==1234");
        params.setResumeToken(mc.controlHandler.getProgress());
        mc = createDefaultMock(params);
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x84000001000004d2)\n" +
                "]\n" +
                "selection='id.user==1234'\n)",
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks();
        assertTrue(mc.controlHandler.isDone());
    }

    @Test
    public void testResumeVisitingAlreadyCompleted() {
        ProgressToken token;
        // First, get a finished token
        {
            MockComponents mc = createDefaultMock("id.user==1234");
            mc.visitorSession.start();
            mc.executor.expectAndProcessTasks(1);
            replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET);
            mc.executor.expectAndProcessTasks(1);
            assertTrue(mc.controlHandler.isDone());
            token = mc.controlHandler.getProgress();
        }
        assertTrue(token.isFinished());

        VisitorParameters visitorParameters = createVisitorParameters("id.user==1234");
        visitorParameters.setResumeToken(token);
        MockComponents mc = createDefaultMock(visitorParameters);

        mc.visitorSession.start();
        mc.executor.expectNoTasks();
        assertTrue(mc.controlHandler.isDone());
    }

    @Test
    public void testLocalDataAndControlDestinations() {
        MockComponentsBuilder builder = new MockComponentsBuilder();
        builder.receiver.setConnectionSpec("foo/bar");
        builder.params = createVisitorParameters("id.user==1234");
        MockComponents mc = builder.createMockComponents();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        CreateVisitorMessage msg = (CreateVisitorMessage)mc.sender.getAndRemoveMessage(0);
        // Local connection spec will be used for both control and data destinations
        assertEquals("foo/bar", msg.getControlDestination());
        assertEquals("foo/bar", msg.getDataDestination());
    }

    @Test
    public void testRemoteDataDestination() {
        MockComponentsBuilder builder = new MockComponentsBuilder();
        builder.receiver.setConnectionSpec("curiosity");
        builder.params = createVisitorParameters("id.user==1234");
        builder.params.setLocalDataHandler(null);
        builder.params.setRemoteDataHandler("odyssey");
        MockComponents mc = builder.createMockComponents();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        CreateVisitorMessage msg = (CreateVisitorMessage)mc.sender.getAndRemoveMessage(0);
        assertEquals("curiosity", msg.getControlDestination());
        assertEquals("odyssey", msg.getDataDestination());
    }

    @Test
    public void testExceptionIfNoDataDestinationSet() {
        MockComponentsBuilder builder = new MockComponentsBuilder();
        builder.receiver.setConnectionSpec(null);
        builder.params = createVisitorParameters("id.user==1234");
        builder.params.setLocalDataHandler(null);
        builder.params.setRemoteDataHandler(null);
        try {
            builder.createMockComponents();
            fail("No exception thrown on missing data destination");
        } catch (IllegalStateException e) {
            assertEquals("No data destination specified", e.getMessage());
        }
    }

    /**
     * Test that failing to submit a new message handling task causes
     * a reply to immediately generated and sent. This must happen or
     * the other endpoint will never receive a reply (until the local
     * node's process/message bus goes down).
     */
    @Test
    public void testImmediatelyReplyIfMessageTaskSubmitFails() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        mc.executor.setRejectTasksAfter(0);

        mc.receiver.send(new VisitorInfoMessage());
        mc.executor.expectNoTasks();

        assertEquals("VisitorReply(ABORTED: Visitor session has been aborted)\n",
                mc.receiver.repliesToString());
    }

    /**
     * We cannot reliably handle reply tasks failing to be submitted, since
     * the reply task performs all our internal state handling logic. As such,
     * we just immediately go into a failure destruction mode as soon as this
     * happens, in which we do not wait for any active messages to be replied
     * to.
     */
    @Test
    public void testImmediatelyDestroySessionIfReplyTaskSubmitFails() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.controlHandler.resetMock();
        mc.executor.expectAndProcessTasks(1);
        mc.executor.setRejectTasksAfter(0);

        replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET);
        mc.executor.expectNoTasks();
        assertTrue(mc.controlHandler.isDone());
        assertEquals("onDone : FAILURE - 'Failed to submit reply task to executor service: rejectTasksAfter is 0; rejecting task'\n",
                mc.controlHandler.toString());
    }

    @Test
    public void testDynamicallyIncreaseMaxBucketsPerVisitorOption() {
        VisitorParameters visitorParameters = createVisitorParameters("id.user==1234");
        visitorParameters.setDynamicallyIncreaseMaxBucketsPerVisitor(true);
        visitorParameters.setMaxBucketsPerVisitor(2);
        visitorParameters.setDynamicMaxBucketsIncreaseFactor(10);
        visitorParameters.setMaxFirstPassHits(10);
        MockComponents mc = createDefaultMock(visitorParameters);

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n" +
                "selection='id.user==1234'\n" +
                "max buckets per visitor=2\n)",
                replyToCreateVisitor(mc.sender, new BucketId(33, 1234 | (1L << 32))));
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectAndProcessTasks(1); // send create visitors

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x84000001000004d2)\n" +
                "]\n" +
                "selection='id.user==1234'\n" +
                "max buckets per visitor=20\n)",
                replyToCreateVisitor(mc.sender, new BucketId(34, 1234 | (1L << 33))));

        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectAndProcessTasks(1); // send create visitors

        // Saturate at 128
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x88000002000004d2)\n" +
                "]\n" +
                "selection='id.user==1234'\n" +
                "max buckets per visitor=128\n)",
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));
    }

    @Test
    public void testVisitorTimeoutsNotConsideredFatal() {
        VisitorParameters visitorParameters = createVisitorParameters("id.user==1234");
        MockComponents mc = createDefaultMock(visitorParameters);
        mc.controlHandler.resetMock();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1); // create visitors
        assertEquals(1, mc.sender.getMessageCount());

        replyErrorToCreateVisitor(mc.sender, new Error(ErrorCode.TIMEOUT, "out of time!"));
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectAndProcessTasks(1, new long[] { 100 }); // delayed create visitors
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                "BucketId(0x80000000000004d2)\n" +
                "BucketId(0x0000000000000000)\n" +
                "]\n" +
                "selection='id.user==1234'\n)",
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));
        mc.executor.expectAndProcessTasks(1); // reply
    }

    /**
     * Test that there is no race condition between a reply is handed off
     * to the executor service via a task (thus decrementing the pending count
     * for the sender) and the session checking for completion early, e.g.
     * because of an error transitioning it into a failure state.
     */
    @Test
    public void testNoRaceConditionForPendingReplyTasks() {
        MockComponents mc = createDefaultMock();
        mc.visitorSession.start();
        mc.controlHandler.resetMock(); // clear messages
        mc.executor.expectAndProcessTasks(1);
        assertEquals(2, mc.sender.getMessageCount());
        replyToCreateVisitor(mc.sender, (reply) -> {
            reply.addError(new Error(
                    DocumentProtocol.ERROR_INTERNAL_FAILURE,
                    "node fell down a well"));
        });
        replyToCreateVisitor(mc.sender, (reply) -> {
            reply.addError(new Error(
                    DocumentProtocol.ERROR_INTERNAL_FAILURE,
                    "node got hit by a falling brick"));
        });

        // Now 2 pending reply tasks, but 0 pending messages. Ergo, using
        // the sender as a ground truth to determine whether or not we have
        // completed will cause a race condition.
        mc.executor.expectAndProcessTasks(2);
        mc.executor.expectNoTasks();
        assertEquals(0, mc.sender.getMessageCount()); // no resending
        assertTrue(mc.visitorSession.isDone());

        // should get first received failure message as completion failure message
        assertEquals("onVisitorError : INTERNAL_FAILURE: node fell down a well\n" +
                "onVisitorError : INTERNAL_FAILURE: node got hit by a falling brick\n" +
                "onDone : FAILURE - 'INTERNAL_FAILURE: node fell down a well'\n",
                mc.controlHandler.toString());
    }

    @Test
    public void testReplyErrorIfInfoMessageArrivesAfterDone() {
        MockComponents mc = createDefaultMock("id.user==1234");
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET);
        mc.executor.expectAndProcessTasks(1);

        mc.receiver.send(new VisitorInfoMessage());
        mc.executor.expectAndProcessTasks(1);
        // Should not be passed on to data handler
        assertEquals(0, mc.dataHandler.getMessages().size());

        assertEquals("VisitorReply(APP_FATAL_ERROR: Visitor has been shut down)\n",
                mc.receiver.repliesToString());
    }

    @Test
    public void testReplyErrorIfLocalDataHandlerIsNull() {
        MockComponentsBuilder builder = new MockComponentsBuilder();
        builder.params = createVisitorParameters("id.user==1234");
        builder.params.setLocalDataHandler(null);
        builder.params.setRemoteDataHandler("odyssey");
        MockComponents mc = builder.createMockComponents();

        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);

        mc.receiver.send(new RemoveDocumentMessage(new DocumentId("doc:foo:bar")));
        mc.executor.expectAndProcessTasks(1);

        assertEquals("RemoveDocumentReply(APP_FATAL_ERROR: Visitor data with no local data destination)\n",
                mc.receiver.repliesToString());
    }

    private MockComponents createTimeoutMocksAtInitialTime(long messageTimeoutMillis, long sessionTimeoutMillis,
                                                           long currentTimeMillis, int maxPending) {
        MockComponentsBuilder builder = new MockComponentsBuilder();
        builder.params.setTimeoutMs(messageTimeoutMillis);
        builder.params.setSessionTimeoutMs(sessionTimeoutMillis);
        builder.params.setControlHandler(builder.controlHandler);
        MockComponents mc = builder.createMockComponents();
        mc.sender.setMaxPending(maxPending);
        mc.clock.setMonotonicTime(currentTimeMillis, TimeUnit.MILLISECONDS); // Baseline time

        mc.visitorSession.start();
        mc.controlHandler.resetMock(); // clear messages
        mc.executor.expectAndProcessTasks(1);
        return mc;
    }

    @Test
    public void visitor_command_timeout_set_to_min_of_message_timeout_and_remaining_session_timeout() {
        MockComponents mc = createTimeoutMocksAtInitialTime(6_000, 10_000, 10_000, 1);

        // Superbucket 1 of 2
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                        "BucketId(0x0400000000000000)\n" +
                        "BucketId(0x0000000000000000)\n" +
                        "]\n" +
                        "time remaining=6000\n)",
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));

        mc.clock.setMonotonicTime(15, TimeUnit.SECONDS); // 5 seconds elapsed from baseline
        mc.executor.expectAndProcessTasks(1); // reply
        mc.executor.expectAndProcessTasks(1); // send create visitors
        // Superbucket 2 of 2
        assertEquals("CreateVisitorMessage(buckets=[\n" +
                        "BucketId(0x0400000000000001)\n" +
                        "BucketId(0x0000000000000000)\n" +
                        "]\n" +
                        "time remaining=5000\n)", // No timeout greater than 5s can be used, or session will have timed out
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));
    }

    @Test
    public void infinite_session_timeout_does_not_affect_message_timeout() {
        MockComponents mc = createTimeoutMocksAtInitialTime(6_000, -1, 10_000, 1);

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                        "BucketId(0x0400000000000000)\n" +
                        "BucketId(0x0000000000000000)\n" +
                        "]\n" +
                        "time remaining=6000\n)",
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));
    }

    @Test
    public void message_timeout_greater_than_session_timeout_is_bounded() {
        MockComponents mc = createTimeoutMocksAtInitialTime(6_000, 3_000, 10_000, 1);

        assertEquals("CreateVisitorMessage(buckets=[\n" +
                        "BucketId(0x0400000000000000)\n" +
                        "BucketId(0x0000000000000000)\n" +
                        "]\n" +
                        "time remaining=3000\n)",
                replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET));
    }

    @Test
    public void fail_session_with_timeout_if_timeout_has_elapsed() {
        MockComponents mc = createTimeoutMocksAtInitialTime(1_000, 4_000, 20_000, 1);

        replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET); // Super bucket 1 of 2
        mc.clock.setMonotonicTime(24_000, TimeUnit.MILLISECONDS); // 4 second timeout expired

        // Reply task processing shall discover that timeout has expired
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks(); // No further send tasks enqueued
        assertTrue(mc.controlHandler.isDone());
        assertEquals("onProgress : 0 active, 1 pending, 1 finished, 2 total\n" +
                        "onVisitorStatistics : 0 buckets visited, 0 docs returned\n" +
                        "onDone : TIMEOUT - 'Session timeout of 4000 ms expired'\n",
                mc.controlHandler.toString());
    }

    @Test
    public void timeout_with_pending_messages_does_not_close_session_until_all_replies_received() {
        MockComponents mc = createTimeoutMocksAtInitialTime(1_000, 5_000, 20_000, 2);

        assertEquals(2, mc.sender.getMessageCount());

        replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET); // Super bucket 1 of 2
        mc.clock.setMonotonicTime(25_000, TimeUnit.MILLISECONDS);

        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks(); // No further send tasks enqueued
        assertFalse(mc.controlHandler.isDone()); // Still pending messages, session _not_ yet done.

        replyToCreateVisitor(mc.sender, ProgressToken.FINISHED_BUCKET); // Super bucket 2 of 2
        mc.controlHandler.resetMock();
        mc.executor.expectAndProcessTasks(1);
        mc.executor.expectNoTasks(); // No further send tasks enqueued
        assertTrue(mc.controlHandler.isDone()); // Now it's done.

        assertEquals("onProgress : 0 active, 0 pending, 2 finished, 2 total\n" +
                        "onVisitorStatistics : 0 buckets visited, 0 docs returned\n" +
                        "onDone : TIMEOUT - 'Session timeout of 5000 ms expired'\n",
                mc.controlHandler.toString());
    }

    @Test
    public void visit_default_bucket_space_unless_explicitly_given() {
        MockComponents mc = createDefaultMock("");
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        CreateVisitorMessage cmd = (CreateVisitorMessage)mc.sender.getAndRemoveMessage(0);
        assertEquals("default", cmd.getBucketSpace());
    }

    @Test
    public void explicitly_provided_bucket_space_is_propagated_to_visitor_commands() {
        MockComponents mc = createDefaultMock("");
        mc.params.setBucketSpace("upside down");
        mc.visitorSession.start();
        mc.executor.expectAndProcessTasks(1);
        CreateVisitorMessage cmd = (CreateVisitorMessage)mc.sender.getAndRemoveMessage(0);
        assertEquals("upside down", cmd.getBucketSpace());
    }

    /**
     * TODOs:
     *   - parameter validation (max pending, ...)
     *   - thread safety stress test
     *   - [add percent finished to progress file; ticket 5360824]
     */

    // TODO: consider refactoring locking granularity
    // TODO: figure out if we risk a re-run of the "too many tasks" issue
}
