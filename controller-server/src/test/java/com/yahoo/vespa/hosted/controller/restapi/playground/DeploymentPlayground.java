// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.playground;

import ai.vespa.validation.StringWrapper;
import com.yahoo.application.Networking;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;

public class DeploymentPlayground extends ControllerContainerTest {

    private final Object monitor = new Object();
    private DeploymentTester deploymentTester;

    @Override
    protected Networking networking() { return Networking.enable; }

    public static void main(String[] args) throws IOException, InterruptedException {
        DeploymentPlayground test = null;
        try {
            test = new DeploymentPlayground();
            test.startContainer();
            test.run();
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
        if (test != null && test.container != null) {
            test.stopContainer();
            test.deploymentTester.runner().shutdown();
            test.deploymentTester.upgrader().shutdown();
            test.deploymentTester.readyJobsTrigger().shutdown();
            test.deploymentTester.outstandingChangeDeployer().shutdown();
        }
    }

    public void run() throws IOException {
        ContainerTester tester = new ContainerTester(container, "");
        deploymentTester = new DeploymentTester(new ControllerTester(tester));
        deploymentTester.controllerTester().computeVersionStatus();

        AthenzDbMock.Domain domainMock = tester.athenzClientFactory().getSetup().getOrCreateDomain(AllowingFilter.domain);
        domainMock.markAsVespaTenant();
        domainMock.admin(AllowingFilter.user.getIdentity());

        ApplicationPackage applicationPackage = ApplicationPackageBuilder.fromDeploymentXml(readDeploymentXml());
        Map<String, DeploymentContext> instances = new LinkedHashMap<>();
        for (var instance : applicationPackage.deploymentSpec().instances())
              instances.put(instance.name().value(), deploymentTester.newDeploymentContext("demo", "app", instance.name().value()));

        instances.values().iterator().next().submit(applicationPackage);

        repl(instances);
    }

    static String readDeploymentXml() throws IOException {
        return Files.readString(Paths.get(System.getProperty("user.home") + "/git/" +
                                          "vespa/controller-server/src/test/java/com/yahoo/vespa/hosted/controller/restapi/playground/deployment.xml"));
    }

    void repl(Map<String, DeploymentContext> instances) throws IOException {
        String[] command;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        AtomicBoolean on = new AtomicBoolean();
        Thread auto = new Thread(() -> auto(instances, on));
        auto.setDaemon(true);
        auto.start();
        while (true) {
            try {
                command = in.readLine().trim().split("\\s+");
                if (command.length == 0 || command[0].isEmpty()) continue;
                synchronized (monitor) {
                    switch (command[0]) {
                        case "exit":
                            auto.interrupt();
                            return;
                        case "tick":
                            deploymentTester.controllerTester().computeVersionStatus();
                            deploymentTester.outstandingChangeDeployer().run();
                            deploymentTester.upgrader().run();
                            deploymentTester.triggerJobs();
                            deploymentTester.runner().run();
                            break;
                        case "run":
                            run(instances.get(command[1]), DeploymentContext::runJob, List.of(command).subList(2, command.length));
                            break;
                        case "fail":
                            run(instances.get(command[1]), DeploymentContext::failDeployment, List.of(command).subList(2, command.length));
                            break;
                        case "upgrade":
                            deploymentTester.controllerTester().upgradeSystem(new Version(command[1]));
                            deploymentTester.controllerTester().computeVersionStatus();
                            break;
                        case "submit":
                            instances.values().iterator().next().submit(ApplicationPackageBuilder.fromDeploymentXml(readDeploymentXml(),
                                                                                                                    ValidationId.deploymentRemoval),
                                                                        command.length == 1 ? 2 : Integer.parseInt(command[1]));
                            break;
                        case "resubmit":
                            instances.values().iterator().next().resubmit(ApplicationPackageBuilder.fromDeploymentXml(readDeploymentXml(),
                                                                                                                      ValidationId.deploymentRemoval));
                            break;
                        case "advance":
                            deploymentTester.clock().advance(Duration.ofMinutes(Long.parseLong(command[1])));
                            break;
                        case "auto":
                            switch (command[1]) {
                                case "on":  on.set(true); break;
                                case "off": on.set(false); break;
                                default: System.err.println("Argument to 'auto' must be 'on' or 'off'"); break;
                            }
                            break;
                        default:
                            System.err.println("Cannot run '" + String.join(" ", command) + "'");
                    }
                    Set<String> names = instances.values().iterator().next().application().deploymentSpec().instanceNames().stream().map(StringWrapper::value).collect(toSet());
                    instances.keySet().removeIf(not(names::contains));
                    names.removeIf(instances.keySet()::contains);
                    for (String name : names)
                        instances.put(name, deploymentTester.newDeploymentContext("demo", "app", name));
                }
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    void auto(Map<String, DeploymentContext> instances, AtomicBoolean on) {
        while ( ! Thread.currentThread().isInterrupted()) {
            try {
                synchronized (monitor) {
                    monitor.wait(1000);
                    if ( ! on.get())
                        continue;
                }

                deploymentTester.clock().advance(Duration.ofSeconds(60));
                deploymentTester.runner().run();
                deploymentTester.triggerJobs();
                deploymentTester.outstandingChangeDeployer().run();
                deploymentTester.controllerTester().computeVersionStatus();
                deploymentTester.upgrader().run();

                synchronized (monitor) {
                    monitor.wait(1000);
                    if ( ! on.get())
                        continue;
                }
                deploymentTester.clock().advance(Duration.ofSeconds(60));

                List<Run> active = deploymentTester.jobs().active();
                if ( ! active.isEmpty()) {
                    Run run = active.stream()
                                    .min(comparing(current -> deploymentTester.jobs().last(current.id().job()).map(Run::start)
                                                                              .orElse(Instant.EPOCH))).get();
                    if (run.versions().sourcePlatform().map(run.versions().targetPlatform()::equals).orElse(true) || Math.random() < 0.4) {
                        instances.get(run.id().application().instance().value()).runJob(run.id().type());
                    }
                }

            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    /**
     void auto(Map<String, DeploymentContext> instances, AtomicBoolean on) {
     BooleanSupplier runJob = () -> {
     List<Run> active = deploymentTester.jobs().active();
     if ( ! active.isEmpty()) {
     Run run = active.stream()
     .min(comparing(current -> deploymentTester.jobs().last(current.id().job()).map(Run::start)
     .orElse(Instant.EPOCH))).get();
     if (run.versions().sourcePlatform().map(run.versions().targetPlatform()::equals).orElse(true) || Math.random() < 0.4) {
     instances.get(run.id().application().instance().value()).runJob(run.id().type());
                    return false;
                }
            }
            return true;
        };
        List<Runnable> defaultTasks = List.of(() -> deploymentTester.runner().run(),
                                              () -> deploymentTester.triggerJobs(),
                                              () -> deploymentTester.outstandingChangeDeployer().run(),
                                              () -> deploymentTester.upgrader().run(),
                                              () -> deploymentTester.controllerTester().computeVersionStatus());
        while ( ! Thread.currentThread().isInterrupted()) {
            Deque<BooleanSupplier> tasks = new ArrayDeque<>();
            tasks.push(runJob);
            for (Runnable task : defaultTasks)
                tasks.push(() -> {
                    task.run();
                    return true;
                });

            while ( ! tasks.isEmpty())
                try {
                    synchronized (monitor) {
                        monitor.wait(1000);
                        if ( ! on.get())
                            break;

                        deploymentTester.clock().advance(Duration.ofSeconds(60));
                        BooleanSupplier task = tasks.pop();
                        if ( ! task.getAsBoolean())
                            tasks.push(task);
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                catch (Throwable t) {
                    t.printStackTrace();
                }
        }
    }
     */

    void run(DeploymentContext instance, BiConsumer<DeploymentContext, JobType> action, List<String> jobs) {
        Set<JobId> haveRun = new HashSet<>();
        boolean triggered = true;
        while (triggered) {
            List<Run> runs = deploymentTester.jobs().active(instance.instanceId());
            triggered = false;
            for (Run run : runs)
                if (jobs.isEmpty() || jobs.contains(run.id().type().jobName().replace("production-", ""))) {
                    if (haveRun.add(run.id().job())) {
                        action.accept(instance, run.id().type());
                        deploymentTester.triggerJobs();
                        triggered = true;
                    }
                }
        }
    }

    @Override
    protected String variablePartXml() {
        return """
                 <component id='com.yahoo.vespa.hosted.controller.security.AthenzAccessControlRequests'/>
                 <component id='com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade'/>
               
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiHandler'>
                   <binding>http://localhost/application/v4/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.athenz.AthenzApiHandler'>
                   <binding>http://localhost/athenz/v1/*</binding>
                 </handler>
                 <handler id='com.yahoo.vespa.hosted.controller.restapi.zone.v1.ZoneApiHandler'>
                   <binding>http://localhost/zone/v1</binding>
                   <binding>http://localhost/zone/v1/*</binding>
                 </handler>
               
                 <http>
                   <server id='default' port='8080' />
                   <filtering>
                     <request-chain id='default'>
                       <filter id='com.yahoo.jdisc.http.filter.security.cors.CorsPreflightRequestFilter'>
                         <config name="jdisc.http.filter.security.cors.cors-filter">
                           <allowedUrls>
                             <item>http://localhost:3000</item>
                             <item>http://localhost:8080</item>
                           </allowedUrls>
                         </config>
                       </filter>
                       <filter id='com.yahoo.vespa.hosted.controller.restapi.playground.AllowingFilter'/>
                       <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.ControllerAuthorizationFilter'/>
                       <binding>http://localhost/*</binding>
                     </request-chain>
                     <response-chain id='responses'>
                       <filter id='com.yahoo.jdisc.http.filter.security.cors.CorsResponseFilter'>
                         <config name="jdisc.http.filter.security.cors.cors-filter">            <allowedUrls>
                             <item>http://localhost:3000</item>
                             <item>http://localhost:8080</item>
                           </allowedUrls>
                         </config>
                       </filter>
                       <binding>http://localhost/*</binding>
                     </response-chain>
                   </filtering>
                 </http>
               """;
    }

}

