// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.exception.ExceptionUtils;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.test.TestTimer;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.TestIds;
import com.yahoo.vespa.service.monitor.AntiServiceMonitor;
import com.yahoo.vespa.service.monitor.CriticalRegion;
import com.yahoo.yolean.Exceptions;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.KillSession;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.data.Stat;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ZkStatusServiceTest {
    private TestingServer testingServer;
    private ZkStatusService statusService;
    private Curator curator;
    private final Timer timer = mock(Timer.class);
    private final Metric metric = mock(Metric.class);
    private final OrchestratorContext context = mock(OrchestratorContext.class);
    private final CriticalRegion criticalRegion = mock(CriticalRegion.class);
    private final AntiServiceMonitor antiServiceMonitor = mock(AntiServiceMonitor.class);

    @Captor
    private ArgumentCaptor<Map<String, String>> captor;

    @Before
    public void setUp() throws Exception {
        Logger.getLogger("").setLevel(Level.WARNING);

        testingServer = new TestingServer();
        curator = createConnectedCurator(testingServer);
        statusService = new ZkStatusService(curator, metric, timer, antiServiceMonitor);
        when(context.getTimeLeft()).thenReturn(Duration.ofSeconds(10));
        when(context.isProbe()).thenReturn(false);
        when(timer.currentTime()).thenReturn(Instant.ofEpochMilli(1));
        when(timer.toUtcClock()).thenReturn(new ManualClock(Instant.ofEpochMilli(1)));
        when(antiServiceMonitor.disallowDuperModelLockAcquisition(any())).thenReturn(criticalRegion);
    }

    private static Curator createConnectedCurator(TestingServer server) throws InterruptedException {
        Curator curator = Curator.create(server.getConnectString(), Optional.empty());
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
        assertEquals(HostStatus.NO_REMARKS,
                statusService.getHostInfo(TestIds.APPLICATION_INSTANCE_REFERENCE, TestIds.HOST_NAME1).status());
    }

    @Test
    public void setting_host_state_is_idempotent() {
        when(timer.currentTime()).thenReturn(
                Instant.ofEpochMilli((1)),
                Instant.ofEpochMilli((3)),
                Instant.ofEpochMilli(6));

        try (ApplicationLock lock = statusService.lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE)) {

            //shuffling to catch "clean database" failures for all cases.
            for (HostStatus hostStatus: shuffledList(HostStatus.NO_REMARKS, HostStatus.ALLOWED_TO_BE_DOWN)) {
                for (int i = 0; i < 2; i++) {
                    lock.setHostState(TestIds.HOST_NAME1, hostStatus);

                    assertEquals(hostStatus, lock.getHostInfos().getOrNoRemarks(TestIds.HOST_NAME1).status());
                }
            }
        }
    }

    @Test
    public void locks_are_exclusive() throws Exception {
        ZkStatusService zkStatusService2 =
                new ZkStatusService(curator, mock(Metric.class), new TestTimer(), antiServiceMonitor);

        final CompletableFuture<Void> lockedSuccessfullyFuture;
        try (ApplicationLock lock = statusService
                .lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE)) {

            lockedSuccessfullyFuture = CompletableFuture.runAsync(() -> {
                try (ApplicationLock lock2 = zkStatusService2
                        .lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE))
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

    @Test
    public void failing_to_get_lock_closes_SessionFailRetryLoop() {
        when(context.getTimeLeft()).thenReturn(Duration.ofMillis(100));
        ZkStatusService zkStatusService2 =
                new ZkStatusService(curator, mock(Metric.class), new TestTimer(), antiServiceMonitor);

        try (ApplicationLock lock = statusService
                .lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE)) {

            // must run in separate thread, since having 2 locks in the same thread fails
            CompletableFuture<Void> resultOfZkOperationAfterLockFailure = CompletableFuture.runAsync(() -> {
                try {
                    zkStatusService2.lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE);
                    fail("Both zookeeper host status services locked simultaneously for the same application instance");
                }
                catch (RuntimeException expected) { }
                finally {
                    killSession(curator.framework(), testingServer);
                }

                // Throws SessionFailedException if the SessionFailRetryLoop has not been closed.
                lock.getHostInfos().getOrNoRemarks(TestIds.HOST_NAME1);
            });

            assertThat(resultOfZkOperationAfterLockFailure, notHoldsException());
        }
    }

    //IsNot does not delegate to matcher.describeMismatch. See the related issue
    //https://code.google.com/p/hamcrest/issues/detail?id=107  Confusing failure description when using negation
    //Creating not(holdsException) directly instead.
    private Matcher<Future<?>> notHoldsException() {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(Future<?> item) {
                return getException(item).isEmpty();
            }

            private Optional<Throwable> getException(Future<?> item) {
                try {
                    item.get();
                    return Optional.empty();
                }
                catch (ExecutionException e) {
                    return Optional.of(e.getCause());
                }
                catch (InterruptedException e) {
                    return Optional.of(e);
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("notHoldsException()");
            }

            @Override
            protected void describeMismatchSafely(Future<?> item, Description mismatchDescription) {
                getException(item).ifPresent(throwable ->
                                                     mismatchDescription
                                                             .appendText("Got exception: ")
                                                             .appendText(Exceptions.toMessageString(throwable))
                                                             .appendText(ExceptionUtils.getStackTraceRecursivelyAsString(throwable)));
            }
        };
    }

    @SuppressWarnings("deprecation")
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
        assertEquals(ApplicationInstanceStatus.NO_REMARKS,
                statusService.getApplicationInstanceStatus(TestIds.APPLICATION_INSTANCE_REFERENCE));

        // Suspend
        try (ApplicationLock lock = statusService
                .lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE)) {
            lock.setApplicationInstanceStatus(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN);
        }

        assertEquals(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN,
                statusService.getApplicationInstanceStatus(TestIds.APPLICATION_INSTANCE_REFERENCE));

        // Resume
        try (ApplicationLock lock = statusService
                .lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE)) {
            lock.setApplicationInstanceStatus(ApplicationInstanceStatus.NO_REMARKS);
        }

        assertEquals(ApplicationInstanceStatus.NO_REMARKS,
                statusService.getApplicationInstanceStatus(TestIds.APPLICATION_INSTANCE_REFERENCE));
    }

    @Test
    public void suspending_two_applications_returns_two_applications() {
        Set<ApplicationInstanceReference> suspendedApps = statusService.getAllSuspendedApplications();
        assertEquals(0, suspendedApps.size());

        try (ApplicationLock statusRegistry = statusService
                .lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE)) {
            statusRegistry.setApplicationInstanceStatus(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN);
        }

        try (ApplicationLock lock = statusService
                .lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE2)) {
            lock.setApplicationInstanceStatus(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN);
        }

        suspendedApps = statusService.getAllSuspendedApplications();
        assertEquals(2, suspendedApps.size());
        assertTrue(suspendedApps.contains(TestIds.APPLICATION_INSTANCE_REFERENCE));
        assertTrue(suspendedApps.contains(TestIds.APPLICATION_INSTANCE_REFERENCE2));
    }

    @Test
    public void zookeeper_cleanup() {
        HostName strayHostname = new HostName("stray1.com");

        verify(antiServiceMonitor, times(0)).disallowDuperModelLockAcquisition(any());
        try (ApplicationLock lock = statusService.lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE)) {
            verify(antiServiceMonitor, times(1)).disallowDuperModelLockAcquisition(any());

            lock.setApplicationInstanceStatus(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN);
            lock.setHostState(TestIds.HOST_NAME1, HostStatus.ALLOWED_TO_BE_DOWN);

            lock.setHostState(strayHostname, HostStatus.PERMANENTLY_DOWN);

            verify(criticalRegion, times(0)).close();
        }
        verify(criticalRegion, times(1)).close();

        verify(antiServiceMonitor, times(1)).disallowDuperModelLockAcquisition(any());
        try (ApplicationLock lock = statusService.lockApplication(context, TestIds.APPLICATION_INSTANCE_REFERENCE2)) {
            verify(antiServiceMonitor, times(2)).disallowDuperModelLockAcquisition(any());

            lock.setApplicationInstanceStatus(ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN);

            verify(criticalRegion, times(1)).close();
        }
        verify(criticalRegion, times(2)).close();

        ApplicationId applicationId = OrchestratorUtil.toApplicationId(TestIds.APPLICATION_INSTANCE_REFERENCE);
        assertEquals("test-tenant:test-application:test-environment:test-region:test-instance-key",
                TestIds.APPLICATION_INSTANCE_REFERENCE.asString());
        assertEquals("test-tenant:test-application:test-instance-key", applicationId.serializedForm());
        assertEquals("host1", TestIds.HOST_NAME1.s());

        String hostStatusPath = "/vespa/host-status/" + applicationId.serializedForm() + "/hosts/" + TestIds.HOST_NAME1.s();
        String lock2Path = "/vespa/host-status-service/" + TestIds.APPLICATION_INSTANCE_REFERENCE.asString() + "/lock2";
        String applicationStatusPath = "/vespa/application-status-service/" + TestIds.APPLICATION_INSTANCE_REFERENCE.asString();
        assertZkPathExists(true, hostStatusPath);
        assertZkPathExists(true, lock2Path);
        assertZkPathExists(true, applicationStatusPath);

        String strayHostStatusPath = "/vespa/host-status/" + applicationId.serializedForm() + "/hosts/" + strayHostname.s();
        String strayHostStatusServicePath = "/vespa/host-status-service/" + TestIds.APPLICATION_INSTANCE_REFERENCE.asString() +
                "/hosts-allowed-down/stray2.com";
        String strayApplicationStatusPath = "/vespa/application-status-service/" + TestIds.APPLICATION_INSTANCE_REFERENCE2.asString();

        createZkNodes(strayHostStatusServicePath);
        assertZkPathExists(true, strayHostStatusPath);
        assertZkPathExists(true, strayHostStatusServicePath);
        assertZkPathExists(true, strayApplicationStatusPath);

        statusService.onApplicationActivate(TestIds.APPLICATION_INSTANCE_REFERENCE2, makeHostnameSet("host1", "host2"));

        // Nothing has been deleted
        assertZkPathExists(true, hostStatusPath);
        assertZkPathExists(true, lock2Path);
        assertZkPathExists(true, applicationStatusPath);
        assertZkPathExists(true, strayHostStatusPath);
        assertZkPathExists(true, strayHostStatusServicePath);
        assertZkPathExists(true, strayApplicationStatusPath);

        statusService.onApplicationActivate(TestIds.APPLICATION_INSTANCE_REFERENCE,
                makeHostnameSet(TestIds.HOST_NAME1.s(), "host3"));

        // Stray hosts for app1 has been deleted
        assertZkPathExists(true, hostStatusPath);
        assertZkPathExists(true, lock2Path);
        assertZkPathExists(true, applicationStatusPath);
        assertZkPathExists(false, strayHostStatusPath);
        assertZkPathExists(true, strayHostStatusServicePath);
        assertZkPathExists(true, strayApplicationStatusPath);

        statusService.onApplicationRemove(TestIds.APPLICATION_INSTANCE_REFERENCE);

        // Application removed => only lock2 path and other apps are left
        assertZkPathExists(false, hostStatusPath);
        assertZkPathExists(true, lock2Path);
        assertZkPathExists(false, applicationStatusPath);
        assertZkPathExists(false, strayHostStatusPath);
        assertZkPathExists(false, strayHostStatusServicePath);
        assertZkPathExists(true, strayApplicationStatusPath);

    }

    private Set<HostName> makeHostnameSet(String... hostnames) {
        return Stream.of(hostnames).map(HostName::new).collect(Collectors.toSet());
    }

    private void assertZkPathExists(boolean exists, String path) {
        final Stat stat;
        try {
            stat = curator.framework().checkExists().forPath(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals(exists, stat != null);
    }

    private void createZkNodes(String... paths) {
        Stream.of(paths).forEach(path -> {
            try {
                curator.framework().create().creatingParentsIfNeeded().forPath(path);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    //TODO: move to vespajlib
    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <T> List<T> shuffledList(T... values) {
        //new ArrayList necessary to avoid "write through" behaviour
        List<T> list = new ArrayList<>(Arrays.asList(values));
        Collections.shuffle(list);
        return list;
    }

}
