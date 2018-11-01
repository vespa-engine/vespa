// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.orchestrator.TestIds;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.curator.SessionFailRetryLoop.SessionFailedException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.KillSession;
import org.apache.curator.test.TestingServer;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ZookeeperStatusServiceTest {
    private TestingServer testingServer;
    private ZookeeperStatusService zookeeperStatusService;
    private Curator curator;

    @Before
    public void setUp() throws Exception {
        Logger.getLogger("").setLevel(LogLevel.WARNING);

        testingServer = new TestingServer();
        curator = createConnectedCurator(testingServer);
        zookeeperStatusService = new ZookeeperStatusService(curator);
    }

    private static Curator createConnectedCuratorFramework(TestingServer server) throws InterruptedException {
        return createConnectedCurator(server);
    }

    private static Curator createConnectedCurator(TestingServer server) throws InterruptedException {
        Curator curator = Curator.create(server.getConnectString());
        curator.framework().blockUntilConnected(1, TimeUnit.MINUTES);
        return curator;
    }

    @After
    public void tearDown() throws Exception {
        if (curator != null) { //teardown is called even if setUp fails.
            curator.close();
            curator = null;
        }
        if (testingServer != null) {
            testingServer.close();
        }
    }

    @Test
    public void host_state_for_unknown_hosts_is_no_remarks() {
        assertThat(
                zookeeperStatusService.forApplicationInstance(TestIds.APPLICATION_INSTANCE_REFERENCE)
                        .getHostStatus(TestIds.HOST_NAME1),
                is(HostStatus.NO_REMARKS));
    }

    @Test
    public void setting_host_state_is_idempotent() {
        try (MutableStatusRegistry statusRegistry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(
                TestIds.APPLICATION_INSTANCE_REFERENCE)) {

            //shuffling to catch "clean database" failures for all cases.
            for (HostStatus hostStatus: shuffledList(HostStatus.values())) {
                doTimes(2, () -> {
                    statusRegistry.setHostState(
                            TestIds.HOST_NAME1,
                            hostStatus);

                    assertThat(statusRegistry.getHostStatus(
                                    TestIds.HOST_NAME1),
                            is(hostStatus));
                });
            }
        }
    }

    @Test
    public void locks_are_exclusive() throws Exception {
        try (Curator curator = createConnectedCuratorFramework(testingServer)) {
            ZookeeperStatusService zookeeperStatusService2 = new ZookeeperStatusService(curator);

            final CompletableFuture<Void> lockedSuccessfullyFuture;
            try (MutableStatusRegistry statusRegistry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(
                    TestIds.APPLICATION_INSTANCE_REFERENCE)) {

                lockedSuccessfullyFuture = CompletableFuture.runAsync(() -> {
                    try (MutableStatusRegistry statusRegistry2 = zookeeperStatusService2
                            .lockApplicationInstance_forCurrentThreadOnly(TestIds.APPLICATION_INSTANCE_REFERENCE))
                    {
                    }
                });

                try {
                    lockedSuccessfullyFuture.get(3, TimeUnit.SECONDS);
                    fail("Both zookeeper host status services locked simultaneously for the same application instance");
                } catch (TimeoutException ignored) {
                }
            }

            lockedSuccessfullyFuture.get(1, TimeUnit.MINUTES);
        }
    }

    @Test
    public void failing_to_get_lock_closes_SessionFailRetryLoop() throws Exception {
        try (Curator curator = createConnectedCuratorFramework(testingServer)) {
            ZookeeperStatusService zookeeperStatusService2 = new ZookeeperStatusService(curator);

            try (MutableStatusRegistry statusRegistry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(
                    TestIds.APPLICATION_INSTANCE_REFERENCE)) {

                //must run in separate thread, since having 2 locks in the same thread fails
                CompletableFuture<Void> resultOfZkOperationAfterLockFailure = CompletableFuture.runAsync(() -> {
                    try {
                        zookeeperStatusService2.lockApplicationInstance_forCurrentThreadOnly(
                                TestIds.APPLICATION_INSTANCE_REFERENCE,1);
                        fail("Both zookeeper host status services locked simultaneously for the same application instance");
                    } catch (RuntimeException e) {
                    }

                    killSession(curator.framework(), testingServer);

                    //Throws SessionFailedException if the SessionFailRetryLoop has not been closed.
                    zookeeperStatusService2.forApplicationInstance(TestIds.APPLICATION_INSTANCE_REFERENCE)
                            .getHostStatus(TestIds.HOST_NAME1);
                });

                assertThat(resultOfZkOperationAfterLockFailure, notHoldsException());
            }
        }
    }

    //IsNot does not delegate to matcher.describeMismatch. See the related issue
    //https://code.google.com/p/hamcrest/issues/detail?id=107  Confusing failure description when using negation
    //Creating not(holdsException) directly instead.
    private Matcher<Future<?>> notHoldsException() {
        return new TypeSafeMatcher<Future<?>>() {
            @Override
            protected boolean matchesSafely(Future<?> item) {
                return !getException(item).isPresent();
            }

            private Optional<Throwable> getException(Future<?> item) {
                try {
                    item.get();
                    return Optional.empty();
                } catch (ExecutionException e) {
                    return Optional.of(e.getCause());
                } catch (InterruptedException e) {
                    return Optional.of(e);
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("notHoldsException()");
            }

            @Override
            protected void describeMismatchSafely(Future<?> item, Description mismatchDescription) {
                getException(item).ifPresent( throwable ->
                        mismatchDescription
                                .appendText("Got exception: ")
                                .appendText(ExceptionUtils.getMessage(throwable))
                                .appendText(ExceptionUtils.getFullStackTrace(throwable)));
            }
        };
    }

    private static void killSession(CuratorFramework curatorFramework, TestingServer testingServer) {
        try {
            KillSession.kill(curatorFramework.getZookeeperClient().getZooKeeper(), testingServer.getConnectString());
        } catch (Exception e) {
            throw new RuntimeException("Failed killing session. ", e);
        }
    }

    @Test
    public void suspend_and_resume_application_works_and_is_symmetric() {

        // Initial state is NO_REMARK
        assertThat(
                zookeeperStatusService
                        .forApplicationInstance(TestIds.APPLICATION_INSTANCE_REFERENCE)
                        .getApplicationInstanceStatus(),
                is(ApplicationInstanceStatus.NO_REMARKS));

        // Suspend
        try (MutableStatusRegistry statusRegistry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(
                TestIds.APPLICATION_INSTANCE_REFERENCE)) {
            statusRegistry.setApplicationInstanceStatus(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN);
        }

        assertThat(
                zookeeperStatusService
                        .forApplicationInstance(TestIds.APPLICATION_INSTANCE_REFERENCE)
                        .getApplicationInstanceStatus(),
                is(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN));

        // Resume
        try (MutableStatusRegistry statusRegistry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(
                TestIds.APPLICATION_INSTANCE_REFERENCE)) {
            statusRegistry.setApplicationInstanceStatus(ApplicationInstanceStatus.NO_REMARKS);
        }

        assertThat(
                zookeeperStatusService
                        .forApplicationInstance(TestIds.APPLICATION_INSTANCE_REFERENCE)
                        .getApplicationInstanceStatus(),
                is(ApplicationInstanceStatus.NO_REMARKS));
    }

    @Test
    public void suspending_two_applications_returns_two_applications() {
        Set<ApplicationInstanceReference> suspendedApps
                = zookeeperStatusService.getAllSuspendedApplications();
        assertThat(suspendedApps.size(), is(0));

        try (MutableStatusRegistry statusRegistry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(
                TestIds.APPLICATION_INSTANCE_REFERENCE)) {
            statusRegistry.setApplicationInstanceStatus(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN);
        }

        try (MutableStatusRegistry statusRegistry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(
                TestIds.APPLICATION_INSTANCE_REFERENCE2)) {
            statusRegistry.setApplicationInstanceStatus(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN);
        }

        suspendedApps = zookeeperStatusService.getAllSuspendedApplications();
        assertThat(suspendedApps.size(), is(2));
        assertThat(suspendedApps, hasItem(TestIds.APPLICATION_INSTANCE_REFERENCE));
        assertThat(suspendedApps, hasItem(TestIds.APPLICATION_INSTANCE_REFERENCE2));
    }

    private static void assertSessionFailed(Runnable statusServiceOperations) {
        try {
            statusServiceOperations.run();
            fail("Expected session expired exception");
        } catch (RuntimeException e) {
            if (!(e.getCause() instanceof SessionFailedException)) {
                throw e;
            }
        }
    }

    //TODO: move to vespajlib
    private static <T> List<T> shuffledList(T[] values) {
        //new ArrayList necessary to avoid "write through" behaviour
        List<T> list = new ArrayList<>(Arrays.asList(values));
        Collections.shuffle(list);
        return list;
    }

    //TODO: move to vespajlib
    private static void doTimes(int numberOfIterations, Runnable runnable) {
        for (int i = 0; i < numberOfIterations; i++) {
            runnable.run();
        }
    }
}
