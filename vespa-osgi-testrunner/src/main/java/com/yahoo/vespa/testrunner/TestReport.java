// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.cd.InconclusiveTestException;
import com.yahoo.collections.Comparables;
import com.yahoo.vespa.testrunner.TestRunner.Suite;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.UniqueId.Segment;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.util.Arrays.copyOf;

/**
 * @author jonmv
 */
public class TestReport {

    private final Object monitor = new Object();
    private final Set<TestIdentifier> complete = new HashSet<>();
    private final Clock clock;
    private final ContainerNode root;
    private final Suite suite;
    private NamedNode current;
    private TestPlan plan;

    private TestReport(Clock clock, Suite suite, ContainerNode root) {
        this.clock = clock;
        this.root = root;
        this.current = root;
        this.suite = suite;
    }

    TestReport(Clock clock, Suite suite) {
        this(clock, suite, new ContainerNode(null, null, toString(suite), clock.instant()));
    }

    static TestReport createFailed(Clock clock, Suite suite, Throwable thrown) {
        if (thrown instanceof OutOfMemoryError) throw (Error) thrown;
        TestReport failed = new TestReport(clock, suite);
        failed.complete();
        failed.root().children.add(new FailureNode(failed.root(), clock.instant(), thrown, suite));
        return failed;
    }

    /** Verify the path from the root to the current node corresponds to the given id. */
    private void verifyStructure(NamedNode node, UniqueId id) {
        Deque<String> path = new ArrayDeque<>();
        while (node != root)  {
            path.push(node.id);
            node = node.parent;
        }
        Deque<String> segments = new ArrayDeque<>();
        if (id != null) for (Segment segment : id.getSegments())
            segments.add(segment.getValue());

        if ( ! List.copyOf(path).equals(List.copyOf(segments)))
            throw new IllegalStateException("test node " + segments + " referenced, but expected " + path);
    }

    void start(TestPlan plan) {
        synchronized (monitor) {
            this.plan = plan;
        }
    }

    void start(TestIdentifier id) {
        synchronized (monitor) {
            NamedNode child = id.isTest() ? new TestNode(current, id.getUniqueIdObject().getLastSegment().getValue(), id.getDisplayName(), clock.instant())
                                          : new ContainerNode(current, id.getUniqueIdObject().getLastSegment().getValue(), id.getDisplayName(), clock.instant());
            verifyStructure(child, id.getUniqueIdObject());
            current.children.add(child);
            current = child;
        }
    }

    ContainerNode complete() {
        synchronized (monitor) {
            complete(null);
            return root();
        }
    }

    private NamedNode complete(TestIdentifier id) {
        verifyStructure(current, id == null ? null : id.getUniqueIdObject());

        Set<TestIdentifier> incomplete = id != null ? plan.getChildren(id) : plan != null ? plan.getRoots() : Set.of();
        for (TestIdentifier child : incomplete) if ( ! complete.contains(child)) skip(child);
        complete.add(id);

        current.end = clock.instant();
        NamedNode node = current;
        current = current.parent;
        return node;
    }

    NamedNode skip(TestIdentifier id) {
        synchronized (monitor) {
            start(id);
            current.status = Status.skipped;
            return complete(id);
        }
    }

    NamedNode abort(TestIdentifier id) {
        synchronized (monitor) {
            current.status = Status.aborted;
            return complete(id);
        }
    }

    NamedNode complete(TestIdentifier id, Throwable thrown) {
        synchronized (monitor) {
            Status status = Status.successful;
            if (thrown != null) {
                FailureNode failure = new FailureNode(current, clock.instant(), thrown, suite);
                current.children.add(failure);
                status = failure.status();
            }
            current.status = status;
            return complete(id);
        }
    }

    void log(LogRecord record) {
        synchronized (monitor) {
            if (record.getThrown() != null) trimStackTraces(record.getThrown(), JunitRunner.class.getName());
            if ( ! (current.children.peekLast() instanceof OutputNode))
                current.children.add(new OutputNode(current));

            ((OutputNode) current.children.peekLast()).log.add(record);
        }
    }

    public TestReport mergedWith(TestReport other) {
        synchronized (monitor) {
            synchronized (other.monitor) {
                if (current != null || other.current != null)
                    throw new IllegalArgumentException("can only merge completed test reports");

                if (root.start().isAfter(other.root.start()))
                    throw new IllegalArgumentException("appended test report cannot have started before the one appended to");

                ContainerNode newRoot = new ContainerNode(null, null, root.name(), root.start());
                newRoot.children.addAll(root.children);
                newRoot.children.addAll(other.root.children);
                TestReport merged = new TestReport(clock, suite, newRoot);
                merged.complete();
                return merged;
            }
        }
    }

    public ContainerNode root() {
        synchronized (monitor) {
            return root;
        }
    }

    public static class Node {

        final Deque<Node> children = new ArrayDeque<>();
        final NamedNode parent;

        Node(NamedNode parent) {
            this.parent = parent;
        }

        Status status() {
            int status = 0;
            for (Node node : children)
                status = Math.max(status, node.status().ordinal());

            return Status.values()[status];
        }

        Map<Status, Long> tally() {
            Map<Status, Long> tally = new EnumMap<>(Status.class);
            for (Node child : children)
                child.tally().forEach((status, count) -> tally.merge(status, count, Long::sum));

            return tally;
        }

        public Queue<Node> children() {
            return children;
        }

    }

    static abstract class NamedNode extends Node {

        private final String id;
        private final String name;
        private final Instant start;
        private Status status;
        private Instant end;

        NamedNode(NamedNode parent, String id, String name, Instant now) {
            super(parent);
            this.id = id;
            this.name = name;
            this.start = now;
        }

        @Override
        public Status status() {
            Status aggregate = super.status();
            return status == null ? aggregate : Comparables.max(status, aggregate);
        }

        public String name() {
            return name;
        }

        public Instant start() {
            return start;
        }

        public Duration duration() {
            return Duration.between(start, end);
        }

    }

    public static class ContainerNode extends NamedNode {

        ContainerNode(NamedNode parent, String name, String display, Instant now) {
            super(parent, name, display, now);
        }

    }

    public static class TestNode extends NamedNode {

        TestNode(NamedNode parent, String name, String display, Instant now) {
            super(parent, name, display, now);
        }

        @Override
        public Map<Status, Long> tally() {
            return Map.of(status(), 1L);
        }

    }

    public static class OutputNode extends Node {

        private final ArrayDeque<LogRecord> log = new ArrayDeque<>();

        public OutputNode(NamedNode parent) {
            super(parent);
        }

        public Queue<LogRecord> log() {
            return log;
        }

    }

    public static class FailureNode extends NamedNode {

        private final Throwable thrown;
        private final Suite suite;

        public FailureNode(NamedNode parent, Instant now, Throwable thrown, Suite suite) {
            super(parent, null, thrown.toString(), now);
            trimStackTraces(thrown, JunitRunner.class.getName());
            this.thrown = thrown;
            this.suite = suite;

            LogRecord record = new LogRecord(levelOf(status()), null);
            record.setThrown(thrown);
            record.setInstant(now);
            OutputNode child = new OutputNode(this);
            child.log.add(record);
            children.add(child);
        }

        public Throwable thrown() {
            return thrown;
        }

        @Override
        public Duration duration() {
            return Duration.ZERO;
        }

        @Override
        public Status status() {
            return suite == Suite.PRODUCTION_TEST && thrown instanceof InconclusiveTestException
                   ? Status.inconclusive
                   : thrown instanceof AssertionError ? Status.failed : Status.error;
        }

    }

    public enum Status {

        // Must be kept in order of increasing importance.
        skipped,
        aborted,
        successful,
        inconclusive,
        failed,
        error;

    }

    static Level levelOf(Status status) {
        return status.compareTo(Status.failed) >= 0 ? Level.SEVERE : status == Status.successful ? Level.INFO : Level.WARNING;
    }

    /**
     * Recursively trims stack traces for the given throwable and its causes/suppressed.
     * This is based on the assumption that the relevant stack is anything above the first native
     * reflection invocation, above any frame in the given root class.
     */
    static void trimStackTraces(Throwable thrown, String testFrameworkRootClass) {
        if (thrown == null)
            return;

        StackTraceElement[] stack = thrown.getStackTrace();
        int i = 0;
        int previousNativeFrame = -1;
        int cutoff = 0;
        boolean rootedInTestFramework = false;
        while (++i < stack.length) {
            rootedInTestFramework |= testFrameworkRootClass.equals(stack[i].getClassName());
            if (stack[i].isNativeMethod())
                previousNativeFrame = i; // Native method invokes the first user test frame.
            if (rootedInTestFramework && previousNativeFrame > 0) {
                cutoff = previousNativeFrame;
                break;
            }
            boolean isDynamicTestInvocation = "org.junit.jupiter.engine.descriptor.DynamicTestTestDescriptor".equals(stack[i].getClassName());
            if (isDynamicTestInvocation) {
                cutoff = i;
                break;
            }
        }
        thrown.setStackTrace(copyOf(stack, cutoff));

        for (Throwable suppressed : thrown.getSuppressed())
            trimStackTraces(suppressed, testFrameworkRootClass);

        trimStackTraces(thrown.getCause(), testFrameworkRootClass);
    }

    private static String toString(Suite suite) {
        if (suite == null) return "Tests";
        switch (suite) {
            case SYSTEM_TEST: return "System test";
            case STAGING_SETUP_TEST: return "Staging setup";
            case STAGING_TEST: return "Staging test";
            case PRODUCTION_TEST: return "Production test";
            default: throw new IllegalArgumentException("unexpected suite '" + suite + "'");
        }
    }

}
