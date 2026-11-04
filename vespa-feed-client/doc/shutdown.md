# Vespa feed-client shutdown: failure modes and changes

This document accompanies the Java feed-client shutdown contribution. It describes the implementation through `f96c9e24790`, compared with baseline `a6384ff4a54`. The affected code is `HttpFeedClient`, `HttpRequestStrategy`, the new `ShutdownLifecycle`, and the public `ShutdownHandle` API.

The client now tracks accepted operations from request construction through public result publication. Callers share one shutdown outcome, can escalate a graceful close to abort, and can observe completion without owning cleanup. The table below covers the changed paths and new API controls. Some handoff races and resource-release issues remain open; they are listed at the end.

## Why the PR is needed

A request can belong to the client before it has a strategy slot. It can also remain unfinished after its transport response arrives. Closing based only on strategy counters misses those intervals. Separately, starting cleanup is different from finishing it: concurrent callers need the same retained outcome, and a throwing transport must not skip the remaining worker cleanup.

## Design decisions

| Choice | Why | Tradeoff |
| --- | --- | --- |
| One monitor for admission and lifecycle transitions | Admission and shutdown agree on which work the client owns | Adds bookkeeping and synchronization; impact measurements are pending |
| Track operations separately from strategy slots | Construction, result mapping and cancelled public results remain accounted for | Operations remain retained until their internal work resolves |
| Reuse existing retry, throttling and per-document sequencing | Keep this change focused on ownership and shutdown | Existing policy and handoff limitations still need integration testing |
| One cleanup worker and shared outcome | Repeated callers do not start cleanup twice or lose its failure | Worker-start failure handling is still unfinished |
| A read-only shutdown handle | Waiting, timing out or being interrupted does not cancel shared shutdown | Existing callback code that blocks in close must use closeAsync and wait elsewhere |
| Identify known progress threads | Prevent a thread from waiting for shutdown that needs that thread | This does not detect arbitrary user dependency cycles |
| Use finally for remaining cleanup | One transport failure must not skip executor shutdown | FAILED records an unsuccessful cleanup, not complete resource release |

## Reading the examples and tests

Snippets are shortened Java excerpts or API examples. Comments omit surrounding code; they are not standalone programs. Each case links to the test source and names the relevant methods. Latches and controlled dependencies force the important ordering. Timeouts bound waits and detect hangs; timing-sensitive checks still need reruns on the maintainer’s environment.

The affected API/client/CLI reactor passed all 52 tests on JDK 17, including the normal install-phase ABI and packaging checks. The archive timestamp was set explicitly to a valid reproducible value. The test refactor uses JUnit parameterization for both progress-thread contexts, bounded future completion for callback assertions and shared latch/clock fixtures. The public API additions are recorded in the ABI specification.

The generated lifecycle test uses 64 fixed seeds with 100 commands each and prints the history on failure. Earlier mutation checks caught dropped internal-resolution ownership and abort reverting to graceful drain. These are component results; full Vespa integration and impact measurements remain outstanding.

## Failure-mode table

| Case | Failure mode | Status |
| --- | --- | --- |
| [V01](#v01) | Close overtakes admitted request construction | Changed in this branch |
| [V02](#v02) | Abort reports completion while construction is held | Changed in this branch |
| [V03](#v03) | Response mapping outlives graceful drain | Changed in this branch |
| [V04](#v04) | Cancelling the public result hides transport work | Changed in this branch |
| [V05](#v05) | A construction exception strands admission | Changed in this branch |
| [V06](#v06) | Abort cannot interrupt a graceful drain | Changed in this branch |
| [V07](#v07) | A progress thread waits for its own shutdown | Changed in this branch |
| [V08](#v08) | A second destroy returns before cleanup finishes | Changed in this branch |
| [V09](#v09) | A cleanup failure disappears for later callers | Changed in this branch |
| [V10](#v10) | Transport close skips worker cleanup when it throws | Changed in this branch |
| [V11](#v11) | Cleanup waits while holding the accounting monitor | Changed; focused regression pending |
| [V12](#v12) | Success is reported before dispatcher exit | Changed in this branch |
| [V13](#v13) | A capacity waiter resumes after abort | Changed; handoff races remain open |
| [V14](#v14) | The dispatch loop sends queued work after abort | Changed in this branch |
| [V15](#v15) | An observer interferes with shared shutdown | New API contract and controls |
| [V16](#v16) | A snapshot reports contradictory state | New API validation |

<a id="v01"></a>

## V01: Close overtakes admitted request construction

A send can pass the open check and pause while constructing its request. Graceful close could drain an empty strategy and destroy it before that send registers.

Admit under the lifecycle monitor before construction. Graceful shutdown waits for admitted construction before draining the strategy.

```java
ShutdownLifecycle.Operation operation = lifecycle.admit();
try {
    // Construct and enqueue the request.
} finally {
    lifecycle.registered(operation);
}
```

![Close overtakes admitted request construction: Send passes open check → Construction pauses → Close destroys strategy; Record admission → Wait for registration → Then drain strategy](shutdown-diagrams/images/V01.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `gracefulCloseWaitsForAdmittedConstruction`.

<a id="v02"></a>

## V02: Abort reports completion while construction is held

Abort should start cleanup promptly, but held request construction still belongs to the client. Reporting successful termination at that point loses track of it.

Start abort cleanup without waiting for construction, then wait for outstanding operations before publishing successful termination. A failed cleanup remains FAILED, not a claim of release.

```java
requestStrategy.destroy();
if (failure == null)
    lifecycle.awaitOperations(() -> true);
lifecycle.finish(failure);
```

![Abort reports completion while construction is held: Construction held → Abort cleans strategy → Success reported early; Start abort cleanup → Retain held operation → Finish after resolution](shutdown-diagrams/images/V02.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `abortDoesNotCertifyTerminationWhileConstructionIsOwned`.

<a id="v03"></a>

## V03: Response mapping outlives graceful drain

The transport response can be ready while conversion to the public Result is still blocked. Strategy counters alone do not cover that work.

Retain the operation through internal resolution and public result publication. This does not promise to wait for arbitrary user continuations on caller-owned threads.

```java
operations.removeIf(operation -> operation.registered
        && operation.resolved && operation.result.isDone());
```

![Response mapping outlives graceful drain: Transport response ready → Result mapping held → Close misses mapping; Track public operation → Finish mapping → Publish result](shutdown-diagrams/images/V03.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `responseMappingRemainsOwnedUntilPublicResultIsPublished`.

<a id="v04"></a>

## V04: Cancelling the public result hides transport work

A caller can cancel its result before the underlying transport operation resolves. Using only isDone would remove that operation too early.

Require internal resolution as well as public completion before dropping ownership. Cancellation of the public future does not itself stop the transport.

```java
result.cancel(false);
// Ownership remains until internal resolution.
```

![Cancelling the public result hides transport work: Caller cancels result → Future is done → Transport still pending; Keep internal state → Wait for resolution → Then remove ownership](shutdown-diagrams/images/V04.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `cancelledPublicResultDoesNotDischargeTransportOwnership`.

<a id="v05"></a>

## V05: A construction exception strands admission

If request construction throws after admission, a missing discharge would leave shutdown waiting on work that will never register.

Publish the failure and run registration cleanup in finally, including synchronous failures.

```java
catch (Throwable failure) {
    operation.publish(null, failure);
    throw failure;
} finally {
    lifecycle.registered(operation);
}
```

![A construction exception strands admission: Admit request → Construction throws → Admission stranded; Publish failure → Run finally cleanup → Release admission](shutdown-diagrams/images/V05.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `constructionFailureDischargesAdmission`.

<a id="v06"></a>

## V06: Abort cannot interrupt a graceful drain

A later abort request could leave the first closer waiting in an unconditional graceful drain. Repeated graceful requests must also not reverse an abort.

Share the lifecycle state and let the drain loop observe escalation. Only the first request starts cleanup.

```java
while (draining.getAsBoolean() && inflight.get() > 0)
    Thread.sleep(10);
```

![Abort cannot interrupt a graceful drain: Graceful drain waits → Second caller aborts → Drain keeps waiting; Share shutdown state → Observe ABORTING → Proceed to cleanup](shutdown-diagrams/images/V06.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `abortEscalatesWhileRealStrategyIsDraining`, `repeatedCloseSharesCleanupAndAllowsEscalation`.

<a id="v07"></a>

## V07: A progress thread waits for its own shutdown

A result callback, cleanup owner, dispatcher or result-policy callback can block on shutdown that needs that same thread to continue.

Reject a blocking wait from known progress-owning threads while shutdown is unfinished. closeAsync initiates shutdown without waiting; an independent thread can await the shared handle.

```java
ShutdownHandle handle = client.closeAsync(false);
// On an independent thread:
handle.await();
```

![A progress thread waits for its own shutdown: Callback calls close → Shutdown needs callback → Self-dependent wait; Initiate closeAsync → Reject local blocking wait → Await independently](shutdown-diagrams/images/V07.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `callbackWaitIsRejectedWhileShutdownContinues`, `cleanupCannotWaitForItsOwnTermination`, `strategyProgressThreadCannotWaitForShutdown` (both dispatcher and result-policy parameters).

<a id="v08"></a>

## V08: A second destroy returns before cleanup finishes

An atomic started flag prevents duplicate cleanup, but it does not make later callers wait for the first caller’s cleanup result.

Keep one destruction result. Repeated callers join it and observe the same success or failure.

```java
if (destroyed.compareAndSet(false, true)) {
    // Perform cleanup and complete destruction.
}
destruction.join();
```

![A second destroy returns before cleanup finishes: First destroy blocks → Second sees started flag → Second returns early; One cleanup owner → Shared destruction result → Both callers wait](shutdown-diagrams/images/V08.png)

Evidence: [HttpRequestStrategyTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpRequestStrategyTest.java): `concurrentDestroyWaitsForTransportCleanup`, `transportCleanupFailureDoesNotLeaveResultWorkerRunning`.

<a id="v09"></a>

## V09: A cleanup failure disappears for later callers

Without a retained outcome, a later observer can miss an earlier cleanup failure.

Publish FAILED with the original cause. Repeated waits report that failure; FAILED does not mean every resource was released.

```java
ShutdownHandle handle = client.closeAsync(true);
handle.await(); // Throws FeedException on cleanup failure.
// handle.snapshot().failure() retains the cause.
```

![A cleanup failure disappears for later callers: Cleanup throws → First caller sees error → Later caller misses it; Retain failure → Publish FAILED → Late wait reports error](shutdown-diagrams/images/V09.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `cleanupFailureRemainsObservableToLateWaiters`.

<a id="v10"></a>

## V10: Transport close skips worker cleanup when it throws

A transport-close exception could skip shutdown of the result executor or the reset-accounting executor.

Use finally blocks to attempt both executor cleanups even when transport close throws. The injected tests check that the captured workers exit and the failure remains visible.

```java
try {
    cluster.close();
} finally {
    resultExecutor.shutdown();
    // Wait for executor termination.
}
```

![Transport close skips worker cleanup when it throws: Transport close throws → Next cleanup skipped → Worker left alive; Attempt transport close → Always shut down workers → Keep failure visible](shutdown-diagrams/images/V10.png)

Evidence: [HttpRequestStrategyTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpRequestStrategyTest.java): `transportCleanupFailureDoesNotLeaveResultWorkerRunning`, `transportCleanupFailureDoesNotLeaveResetAccountingWorkerRunning`.

<a id="v11"></a>

## V11: Cleanup waits while holding the accounting monitor

Reset-accounting tasks need the same monitor that close previously held while awaiting their executor. That wait could prevent those tasks from finishing.

Release the monitor before waiting for executor termination. Existing reset and shutdown tests exercise the surrounding path; this case still needs a dedicated ordered regression.

```java
try {
    synchronized (monitor) {
        try { delegate.close(); }
        finally { executor.shutdown(); }
    }
} finally {
    // Existing timeout and interruption handling omitted.
    executor.awaitTermination(1, TimeUnit.MINUTES);
}
```

![Cleanup waits while holding the accounting monitor: Close holds monitor → Waits for accounting task → Task needs monitor; Request executor shutdown → Release monitor → Then await termination](shutdown-diagrams/images/V11.png)

Evidence: [HttpRequestStrategyTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpRequestStrategyTest.java): `testResettingCluster`, `testShutdown`. Source-order correction; dedicated regression pending.

<a id="v12"></a>

## V12: Success is reported before dispatcher exit

Closing shared resources does not establish that a dispatcher paused elsewhere has stopped.

Join the dispatcher after shared cleanup on the successful path, avoiding self-join. The test holds the dispatcher, verifies the shutdown wait remains pending, then releases it.

```java
destruction.join();
if (Thread.currentThread() != dispatcher)
    dispatcher.join();
```

![Success is reported before dispatcher exit: Dispatcher paused → Resources close → Success reported early; Finish shared cleanup → Join dispatcher → Then report success](shutdown-diagrams/images/V12.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `successfulTerminationWaitsForDispatcherExit`.

<a id="v13"></a>

## V13: A capacity waiter resumes after abort

An operation registered before abort can be waiting for a slot. When capacity becomes available, it must not start new work after cleanup.

Check the abort flag while waiting and again before acquisition. Discharge the unacquired registration when acquisition fails. The check and increment are still separate operations; broader interleavings remain to be checked.

```java
if (!acquireSlot()) {
    inflightById.remove(documentId, result);
    result.complete();
    return result;
}
```

![A capacity waiter resumes after abort: Waiter registered → Abort runs cleanup → Waiter acquires slot; Observe abort → Remove registration → Do not offer request](shutdown-diagrams/images/V13.png)

Evidence: [HttpRequestStrategyTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpRequestStrategyTest.java): `capacityWaiterCannotAcquireOrOfferAfterAbort`.

<a id="v14"></a>

## V14: The dispatch loop sends queued work after abort

After a held dispatch returns, the inner loop could continue taking requests that were already queued when abort began.

Check the abort flag before polling for the next queued request. The test holds the first dispatch, queues another, aborts, then checks that the queued request is not sent. This is not an atomic guarantee across a concurrent check-and-poll race.

```java
private boolean poll() {
    if (destroyed.get()) return false;
    Runnable task = queue.poll();
    // Existing dispatch path follows.
}
```

![The dispatch loop sends queued work after abort: First dispatch held → Abort starts → Loop sends next request; First dispatch returns → Check abort before poll → Leave queued work unsent](shutdown-diagrams/images/V14.png)

Evidence: [HttpRequestStrategyTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpRequestStrategyTest.java): `abortDoesNotDispatchQueuedRequestsAfterBlockedDispatchReturns`.

<a id="v15"></a>

## V15: An observer interferes with shared shutdown

A timeout or interruption should end only that caller’s wait. Observation should not initiate shutdown or cancel another caller’s cleanup.

Expose a read-only shared handle. The blocking close wrapper restores the interrupt flag; independent observers can still await the same shutdown.

```java
ShutdownHandle handle = client.termination();
handle.await(Duration.ofMillis(50));
// Timeout ends this wait, not shared shutdown.
```

![An observer interferes with shared shutdown: Observer waits → Timeout or interruption → Shared cleanup at risk; End only this wait → Keep shared shutdown → Other observers continue](shutdown-diagrams/images/V15.png)

Evidence: [HttpFeedClientShutdownTest](../../vespa-feed-client/src/test/java/ai/vespa/feed/client/impl/HttpFeedClientShutdownTest.java): `observationDoesNotInitiateAndTimeoutDoesNotClose`, `interruptedObserverDoesNotInterruptSharedShutdown`.

<a id="v16"></a>

## V16: A snapshot reports contradictory state

A public snapshot could claim success while retaining outstanding work, or attach a failure to a non-failed phase.

Validate phase, failure and ownership counts at construction. FAILED may retain outstanding work; TERMINATED may not.

```java
if (phase == Phase.TERMINATED && outstandingOperations != 0)
    throw new IllegalArgumentException(
        "Successful termination cannot retain outstanding operations");
```

![A snapshot reports contradictory state: Snapshot says TERMINATED → Outstanding work is 1 → Contradictory report; Validate phase and counts → Reject contradiction → Expose consistent snapshot](shutdown-diagrams/images/V16.png)

Evidence: [ShutdownHandleTest](../../vespa-feed-client-api/src/test/java/ai/vespa/feed/client/ShutdownHandleTest.java): `snapshotRejectsContradictoryOutcomeAndOwnership`.

## What this branch does not finish

| Open item | Why it matters | Next check |
| --- | --- | --- |
| Idle reset leaves the old delegate open | No later completion may arrive to close it | Add old-generation retirement and a before/after resource test |
| Late completion after callback-executor shutdown | Rejected accounting can leave old resources open | Test an ordered late completion with real transport cleanup |
| Atomic registry, capacity and queue handoffs | Abort checks and subsequent actions are separate | Force abort between check and action, including same-ID successors |
| Shutdown worker cannot start | Admission may close without a worker publishing an outcome | Inject thread-start failure and retain an observable failure |
| Executor timeout or interruption | Current paths can warn and continue without confirming worker exit | Hold a worker through the timeout and check the claimed terminal state |
| Independent stall diagnostics | Early stalls may never reach the existing termination warning | Add bounded diagnostics with controlled time and a blocked logging handler |
| Delegate visibility and wider cleanup combinations | Source inspection leaves concurrency and resource questions | Check stats/reset callers, rejection, partial close and multiple failures |

The separate source experiments reproduced the idle-reset and late-callback resource failures. They are follow-up defects, not fixes supplied by this branch. The full Jetty integration environment is unavailable here, and GitHub Actions is blocked by billing. A Vespa maintainer needs to run the before/after cases with the real transport and check worker, socket and file retention.

Performance measurements remain deferred. The agreed comparison uses distinct-ID and same-ID workloads at low and saturated load, reporting throughput, submission/shutdown latency, allocation and retained resources. A repeatable throughput or p95 submission-latency regression above 5% needs review. No performance acceptance is claimed.

## Review and rerun

Run the affected Maven reactor with the repository’s supported JDK and normal checks:

```sh
mvn -pl vespa-feed-client-api,vespa-feed-client,vespa-feed-client-cli -am test
```

For individual ordering tests, use the method names beside each case. Keep the baseline failure, changed-version result and environment together. Extend the held-dependency cases with real HTTP delays, connection resets, same-ID chains and combined cleanup failures before treating this as ready for production.

The rendered diagrams accompany this document. Report-generation tooling and planning records are retained separately in the research garden.
