// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.playground;

import com.yahoo.application.Networking;
import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

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

        Map<String, DeploymentContext> instances = new LinkedHashMap<>();
        for (String name : List.of("alpha", "beta", "prod5", "prod25", "prod100"))
              instances.put(name, deploymentTester.newDeploymentContext("gemini", "core", name));

        instances.values().iterator().next().submit(ApplicationPackageBuilder.fromDeploymentXml(readDeploymentXml())).deploy();

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
                            instances.values().iterator().next().submit(ApplicationPackageBuilder.fromDeploymentXml(readDeploymentXml()));
                            break;
                        case "resubmit":
                            instances.values().iterator().next().resubmit(ApplicationPackageBuilder.fromDeploymentXml(readDeploymentXml()));
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
                    monitor.wait(6000);
                    if ( ! on.get())
                        continue;

                    System.err.println("auto running");
                    deploymentTester.clock().advance(Duration.ofSeconds(60));
                    deploymentTester.controllerTester().computeVersionStatus();
                    deploymentTester.outstandingChangeDeployer().run();
                    deploymentTester.upgrader().run();
                    deploymentTester.triggerJobs();
                    deploymentTester.runner().run();
                    for (Run run : deploymentTester.jobs().active())
                        if (run.versions().sourcePlatform().map(run.versions().targetPlatform()::equals).orElse(true) || Math.random() < 0.4)
                            instances.get(run.id().application().instance().value()).runJob(run.id().type());
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
        return "  <component id='com.yahoo.vespa.hosted.controller.security.AthenzAccessControlRequests'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade'/>\n" +

               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiHandler'>\n" +
               "    <binding>http://*/application/v4/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.athenz.AthenzApiHandler'>\n" +
               "    <binding>http://*/athenz/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <handler id='com.yahoo.vespa.hosted.controller.restapi.zone.v1.ZoneApiHandler'>\n" +
               "    <binding>http://*/zone/v1</binding>\n" +
               "    <binding>http://*/zone/v1/*</binding>\n" +
               "  </handler>\n" +

               "  <http>\n" +
               "    <server id='default' port='8080' />\n" +
               "    <filtering>\n" +
               "      <request-chain id='default'>\n" +
               "        <filter id='com.yahoo.jdisc.http.filter.security.cors.CorsPreflightRequestFilter'>\n" +
               "          <config name=\"jdisc.http.filter.security.cors.cors-filter\">" +
               "            <allowedUrls>\n" +
               "              <item>http://localhost:3000</item>\n" +
               "              <item>http://localhost:8080</item>\n" +
               "            </allowedUrls>\n" +
               "          </config>\n" +
               "        </filter>\n" +
               "        <filter id='com.yahoo.vespa.hosted.controller.restapi.playground.AllowingFilter'/>\n" +
               "        <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.ControllerAuthorizationFilter'/>\n" +
               "        <binding>http://*/*</binding>\n" +
               "      </request-chain>\n" +
               "      <response-chain id='responses'>\n" +
               "        <filter id='com.yahoo.jdisc.http.filter.security.cors.CorsResponseFilter'>\n" +
               "          <config name=\"jdisc.http.filter.security.cors.cors-filter\">" +
               "            <allowedUrls>\n" +
               "              <item>http://localhost:3000</item>\n" +
               "              <item>http://localhost:8080</item>\n" +
               "            </allowedUrls>\n" +
               "          </config>\n" +
               "        </filter>\n" +
               "        <binding>http://*/*</binding>\n" +
               "      </response-chain>\n" +
               "    </filtering>\n" +
               "  </http>\n";
    }

}

