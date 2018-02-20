// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.RunnableWithTag;
import com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.Scheduler;
import com.yahoo.jdisc.Metric;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.INITIAL_BACKOFF_DELAY;
import static com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.INITIAL_WAIT_NTOKEN;
import static com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.MAX_REGISTER_BACKOFF_DELAY;
import static com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.METRICS_UPDATER_TAG;
import static com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.REDUCED_UPDATE_PERIOD;
import static com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.REGISTER_INSTANCE_TAG;
import static com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.TIMEOUT_INITIAL_WAIT_TAG;
import static com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.UPDATE_CREDENTIALS_TAG;
import static com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl.UPDATE_PERIOD;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author mortent
 * @author bjorncs
 */
public class AthenzIdentityProviderImplTest {

    private static final Metric DUMMY_METRIC = new Metric() {
        @Override
        public void set(String s, Number number, Context context) {}
        @Override
        public void add(String s, Number number, Context context) {}
        @Override
        public Context createContext(Map<String, ?> stringMap) { return null; }
    };

    private static final IdentityConfig IDENTITY_CONFIG =
            new IdentityConfig(new IdentityConfig.Builder()
                                       .service("tenantService").domain("tenantDomain").loadBalancerAddress("cfg"));

    private final Set<String> IGNORED_TASKS = Stream.of(UPDATE_CREDENTIALS_TAG, METRICS_UPDATER_TAG)
            .collect(Collectors.toSet());

    @Test
    public void athenz_credentials_are_retrieved_after_component_contruction_completed() {
        IdentityDocumentService identityDocumentService = mock(IdentityDocumentService.class);
        AthenzService athenzService = mock(AthenzService.class);
        ManualClock clock = new ManualClock(Instant.EPOCH);
        MockScheduler scheduler = new MockScheduler(clock);

        when(identityDocumentService.getSignedIdentityDocument()).thenReturn(getIdentityDocument());
        when(athenzService.sendInstanceRegisterRequest(any(), any())).thenReturn(
                new InstanceIdentity(null, "TOKEN"));
        AthenzCredentialsService credentialService =
                new AthenzCredentialsService(IDENTITY_CONFIG, identityDocumentService, athenzService, clock);

        AthenzIdentityProvider identityProvider =
                new AthenzIdentityProviderImpl(IDENTITY_CONFIG, DUMMY_METRIC, credentialService, scheduler, clock);

        List<MockScheduler.CompletedTask> expectedTasks =
                Arrays.asList(
                        new MockScheduler.CompletedTask(REGISTER_INSTANCE_TAG, Duration.ZERO),
                        new MockScheduler.CompletedTask(TIMEOUT_INITIAL_WAIT_TAG, INITIAL_WAIT_NTOKEN));
        // Don't run update credential tasks, otherwise infinite loop
        List<MockScheduler.CompletedTask> completedTasks =
                scheduler.runAllTasks(task -> !IGNORED_TASKS.contains(task.tag()));
        assertEquals(expectedTasks, completedTasks);
        assertEquals("TOKEN", identityProvider.getNToken());
    }

    @Test
    public void register_instance_uses_exponential_backoff() {
        AthenzCredentialsService credentialService = mock(AthenzCredentialsService.class);
        when(credentialService.registerInstance())
                .thenThrow(new RuntimeException("#1"))
                .thenThrow(new RuntimeException("#2"))
                .thenThrow(new RuntimeException("#3"))
                .thenThrow(new RuntimeException("#4"))
                .thenThrow(new RuntimeException("#5"))
                .thenReturn(new AthenzCredentials("TOKEN", null, null, null, Instant.now()));

        ManualClock clock = new ManualClock(Instant.EPOCH);
        MockScheduler scheduler = new MockScheduler(clock);
        AthenzIdentityProvider identityProvider =
                new AthenzIdentityProviderImpl(IDENTITY_CONFIG, DUMMY_METRIC, credentialService, scheduler, clock);

        List<MockScheduler.CompletedTask> expectedTasks =
                Arrays.asList(
                        new MockScheduler.CompletedTask(REGISTER_INSTANCE_TAG, Duration.ZERO),
                        new MockScheduler.CompletedTask(REGISTER_INSTANCE_TAG, INITIAL_BACKOFF_DELAY),
                        new MockScheduler.CompletedTask(TIMEOUT_INITIAL_WAIT_TAG, INITIAL_WAIT_NTOKEN),
                        new MockScheduler.CompletedTask(REGISTER_INSTANCE_TAG, INITIAL_BACKOFF_DELAY.multipliedBy(2)),
                        new MockScheduler.CompletedTask(REGISTER_INSTANCE_TAG, INITIAL_BACKOFF_DELAY.multipliedBy(4)),
                        new MockScheduler.CompletedTask(REGISTER_INSTANCE_TAG, INITIAL_BACKOFF_DELAY.multipliedBy(8)),
                        new MockScheduler.CompletedTask(REGISTER_INSTANCE_TAG, MAX_REGISTER_BACKOFF_DELAY));
        // Don't run update credential tasks, otherwise infinite loop
        List<MockScheduler.CompletedTask> completedTasks =
                scheduler.runAllTasks(task -> !IGNORED_TASKS.contains(task.tag()));
        assertEquals(expectedTasks, completedTasks);
        assertEquals("TOKEN", identityProvider.getNToken());
    }

    @Test
    public void failed_credentials_updates_will_schedule_retries() {
        IdentityDocumentService identityDocumentService = mock(IdentityDocumentService.class);
        AthenzService athenzService = mock(AthenzService.class);
        ManualClock clock = new ManualClock(Instant.EPOCH);
        MockScheduler scheduler = new MockScheduler(clock);

        when(identityDocumentService.getSignedIdentityDocument()).thenReturn(getIdentityDocument());
        when(athenzService.sendInstanceRegisterRequest(any(), any())).thenReturn(
                new InstanceIdentity(null, "TOKEN"));
        when(athenzService.sendInstanceRefreshRequest(anyString(), anyString(), anyString(),
                                                      anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("#1"))
                .thenThrow(new RuntimeException("#2"))
                .thenThrow(new RuntimeException("#3"))
                .thenReturn(new InstanceIdentity(null, "TOKEN"));
        AthenzCredentialsService credentialService =
                new AthenzCredentialsService(IDENTITY_CONFIG, identityDocumentService, athenzService, clock);

        AthenzIdentityProvider identityProvider =
                new AthenzIdentityProviderImpl(IDENTITY_CONFIG, DUMMY_METRIC, credentialService, scheduler, clock);

        List<MockScheduler.CompletedTask> expectedTasks =
                Arrays.asList(
                        new MockScheduler.CompletedTask(REGISTER_INSTANCE_TAG, Duration.ZERO),
                        new MockScheduler.CompletedTask(TIMEOUT_INITIAL_WAIT_TAG, INITIAL_WAIT_NTOKEN),
                        new MockScheduler.CompletedTask(UPDATE_CREDENTIALS_TAG, UPDATE_PERIOD),
                        new MockScheduler.CompletedTask(UPDATE_CREDENTIALS_TAG, UPDATE_PERIOD),
                        new MockScheduler.CompletedTask(UPDATE_CREDENTIALS_TAG, REDUCED_UPDATE_PERIOD),
                        new MockScheduler.CompletedTask(UPDATE_CREDENTIALS_TAG, REDUCED_UPDATE_PERIOD),
                        new MockScheduler.CompletedTask(UPDATE_CREDENTIALS_TAG, UPDATE_PERIOD));
        AtomicInteger counter = new AtomicInteger(0);
        List<MockScheduler.CompletedTask> completedTasks =
                scheduler.runAllTasks(task -> !task.tag().equals(METRICS_UPDATER_TAG) &&
                        counter.getAndIncrement() < expectedTasks.size());
        assertEquals(expectedTasks, completedTasks);
        assertEquals("TOKEN", identityProvider.getNToken());
    }

    private static String getIdentityDocument() {
        return "{\n" +
               "  \"identity-document\": \"eyJwcm92aWRlci11bmlxdWUtaWQiOnsidGVuYW50IjoidGVuYW50IiwiYXBwbGljYXRpb24iOiJhcHBsaWNhdGlvbiIsImVudmlyb25tZW50IjoiZGV2IiwicmVnaW9uIjoidXMtbm9ydGgtMSIsImluc3RhbmNlIjoiZGVmYXVsdCIsImNsdXN0ZXItaWQiOiJkZWZhdWx0IiwiY2x1c3Rlci1pbmRleCI6MH0sImNvbmZpZ3NlcnZlci1ob3N0bmFtZSI6ImxvY2FsaG9zdCIsImluc3RhbmNlLWhvc3RuYW1lIjoieC55LmNvbSIsImNyZWF0ZWQtYXQiOjE1MDg3NDgyODUuNzQyMDAwMDAwfQ==\",\n" +
               "  \"signature\": \"kkEJB/98cy1FeXxzSjtvGH2a6BFgZu/9/kzCcAqRMZjENxnw5jyO1/bjZVzw2Sz4YHPsWSx2uxb32hiQ0U8rMP0zfA9nERIalSP0jB/hMU8laezGhdpk6VKZPJRC6YKAB9Bsv2qUIfMsSxkMqf66GUvjZAGaYsnNa2yHc1jIYHOGMeJO+HNPYJjGv26xPfAOPIKQzs3RmKrc3FoweTCsIwm5oblqekdJvVWYe0obwlOSB5uwc1zpq3Ie1QBFtJRuCGMVHg1pDPxXKBHLClGIrEvzLmICy6IRdHszSO5qiwujUD7sbrbM0sB/u0cYucxbcsGRUmBvme3UAw2mW9POVQ==\",\n" +
               "  \"signing-key-version\": 0,\n" +
               "  \"provider-unique-id\": \"tenant.application.dev.us-north-1.default.default.0\",\n" +
               "  \"dns-suffix\": \"dnsSuffix\",\n" +
               "  \"provider-service\": \"service\",\n" +
               "  \"zts-endpoint\": \"localhost/zts\", \n" +
               "  \"document-version\": 1\n" +
               "}";

    }

    private static class MockScheduler implements Scheduler {

        private final PriorityQueue<DelayedTask> tasks = new PriorityQueue<>();
        private final ManualClock clock;

        MockScheduler(ManualClock clock) {
            this.clock = clock;
        }

        @Override
        public void schedule(RunnableWithTag task, Duration delay) {
            tasks.offer(new DelayedTask(task, delay, clock.instant().plus(delay)));
        }

        List<CompletedTask> runAllTasks(Predicate<RunnableWithTag> filter) {
            List<CompletedTask> completedTasks = new ArrayList<>();
            while (!tasks.isEmpty()) {
                DelayedTask task = tasks.poll();
                RunnableWithTag runnable = task.runnableWithTag;
                if (filter.test(runnable)) {
                    clock.setInstant(task.startTime);
                    runnable.run();
                    completedTasks.add(new CompletedTask(runnable.tag(), task.delay));
                }
            }
            return completedTasks;
        }

        private static class DelayedTask implements Comparable<DelayedTask> {
            final RunnableWithTag runnableWithTag;
            final Duration delay;
            final Instant startTime;

            DelayedTask(RunnableWithTag runnableWithTag, Duration delay, Instant startTime) {
                this.runnableWithTag = runnableWithTag;
                this.delay = delay;
                this.startTime = startTime;
            }

            @Override
            public int compareTo(DelayedTask other) {
                return this.startTime.compareTo(other.startTime);
            }
        }

        private static class CompletedTask {
            final String tag;
            final Duration delay;

            CompletedTask(String tag, Duration delay) {
                this.tag = tag;
                this.delay = delay;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                CompletedTask that = (CompletedTask) o;
                return Objects.equals(tag, that.tag) &&
                        Objects.equals(delay, that.delay);
            }

            @Override
            public int hashCode() {
                return Objects.hash(tag, delay);
            }

            @Override
            public String toString() {
                return "CompletedTask{" +
                        "tag='" + tag + '\'' +
                        ", delay=" + delay +
                        '}';
            }
        }
    }
}
