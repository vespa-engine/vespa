// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yahoo.collections.Pair;
import com.yahoo.system.ProcessExecuter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * <pre>
 * Requires docker daemon, see node-admin/README.md for how to install and configure.
 *
 * To get started:
 *  1. Add system test host hostnames to /etc/hosts:
 *      $ sudo ./vespa/node-admin/scripts/etc-hosts.sh
 *
 *
 * Example usage:
     DockerImage vespaDockerBase = new DockerImage("docker-registry.ops.yahoo.com:4443/vespa/ci:6.52.35");
     Path pathToSystemtestsInHost = Paths.get("/home/valerijf/dev/systemtests");
     RunSystemTests runSystemTests = new RunSystemTests(vespaDockerBase, pathToSystemtestsInHost);

     ContainerName systemtestsHost = new ContainerName("stest-1");
     // Update maven local repository and /home/y/lib/jars with the current version of these modules...
     runSystemTests.mavenInstallModules(systemtestsHost, "docproc", "container-search-and-docproc", "container-dev");

     Path systemTestToRun = Paths.get("tests/search/basicsearch/basic_search.rb");
     runSystemTests.runSystemTest(systemtestsHost, systemTestToRun);
 * </pre>
 *
 * @author freva
 */
public class RunSystemTests {
    private static final DockerImage SYSTEMTESTS_DOCKER_IMAGE = new DockerImage("vespa-systest:latest");

    private final DockerImpl docker;
    private final DockerImage vespaBaseImage;
    private final Path pathToSystemtestsInHost;
    private final Path pathToSystemtestsInContainer = Paths.get("/systemtests");
    private final Path pathToVespaRepoInHost = Paths.get("").toAbsolutePath();
    private final Path pathToVespaRepoInContainer = Paths.get("/vespa");
    private final Path pathToTestRunner = pathToSystemtestsInContainer.resolve("bin/run_test.rb");
    private final Path pathToLibJars = Paths.get("/home/y/lib/jars");

    private final Logger logger = Logger.getLogger("systemtest");

    public RunSystemTests(DockerImage vespaBaseImage, Path pathToSystemtestsInHost) {
        this.docker = DockerTestUtils.getDocker();
        this.vespaBaseImage = vespaBaseImage;
        this.pathToSystemtestsInHost = pathToSystemtestsInHost;
    }

    /**
     * @param systemtestHost name of the container that will execute the test, if it does not exist, a new
     *                       one will be started.
     * @param systemtestToRun relative path from the root of systemtests to the test to run, f.ex.
     *                        tests/search/basicsearch/basic_search.rb
     */
    void runSystemTest(ContainerName systemtestHost, Path systemtestToRun, String... arguments) throws InterruptedException, ExecutionException, IOException {
        runSystemTest(Collections.singletonList(systemtestHost), systemtestToRun, arguments);
    }

    /**
     * @param systemtestHosts name of the containers that will be used in the test, if some of them doe not exist, new
     *                       ones will be started. First in list will be used as system test controller
     * @param systemtestToRun relative path from the root of systemtests to the test to run, f.ex.
     *                        tests/search/basicsearch/basic_search.rb
     */
    void runSystemTest(List<ContainerName> systemtestHosts, Path systemtestToRun, String... arguments) throws InterruptedException, ExecutionException, IOException {
        for (ContainerName systemtestHost : systemtestHosts) {
            startSystemTestNodeIfNeeded(systemtestHost);
        }

        Path pathToSystestToRun = pathToSystemtestsInContainer.resolve(systemtestToRun);

        logger.info("Running test " + pathToSystestToRun);
        Integer testExitCode = runTest(systemtestHosts.get(0), pathToSystestToRun, arguments);
        assertEquals("Test did not finish with exit code 0", Integer.valueOf(0), testExitCode);
    }

    /**
     * This method runs mvn install inside container to update container's local repository, then copies any
     * existing and updated file from target to /home/y/lib/jars.
     *
     * Because it runs as root we have to temporarily move around the target/ otherwise mvn will overwrite it and
     * as root, making mvn on host fail next time it runs.
     *
     * @param containerName name of the container to install modules in, if it does not exist, a new
     *                       one will be started.
     * @param modules list of modules to install in order
     */
    void mavenInstallModules(ContainerName containerName, String... modules) throws InterruptedException, IOException, ExecutionException {
        startSystemTestNodeIfNeeded(containerName);

        for (String module : modules) {
            Path pathToTargetInHost = pathToVespaRepoInHost.resolve(module).resolve("target");
            Path pathToTargetBackupInHost = pathToVespaRepoInHost.resolve(module).resolve("target_bcp");

            if (Files.exists(pathToTargetInHost)) {
                Files.move(pathToTargetInHost, pathToTargetBackupInHost, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        try {
            String projects = Arrays.stream(modules)
                    .collect(Collectors.joining(","));
            executeInContainer(containerName,
                    "/bin/sh", "-c", "cd /vespa; mvn -DskipTests -Dmaven.javadoc.skip=true -e " +
                            "-pl=" + projects + " install");

            for (String module : modules) {
                Path pathToModule = pathToVespaRepoInContainer.resolve(module);
                executeInContainer(containerName, "rsync", "--archive", "--existing", "--update",
                        pathToModule.resolve("target").toString() + "/", pathToLibJars.toString() + "/");
            }
        } finally {
            for (String module : modules) {
                Path pathToModule = pathToVespaRepoInContainer.resolve(module);
                Path pathToTargetInHost = pathToVespaRepoInHost.resolve(module).resolve("target");
                Path pathToTargetBackupInHost = pathToVespaRepoInHost.resolve(module).resolve("target_bcp");
                executeInContainer(containerName, "rm", "-rf", pathToModule.resolve("target").toString());

                if (Files.exists(pathToTargetBackupInHost)) {
                    Files.move(pathToTargetBackupInHost, pathToTargetInHost);
                }
            }
        }
    }

    /**
     * Builds all modules that depend on 'modules' and 'modules' themselves in the order they need to be built
     */
    void mavenInstallModulesThatDependOn(ContainerName containerName, String... modules) throws IOException, ExecutionException, InterruptedException {
        logger.info("Building dependency graph...");
        Path outputFile = Paths.get("/tmp/vespa-maven-dependencies");
        if (Files.exists(outputFile)) {
            Files.delete(outputFile);
        }

        String[] command = new String[]{"mvn", "dependency:tree", "-Dincludes=com.yahoo.vespa",
                "-DoutputFile=" + outputFile, "-DoutputType=dot", "-DappendOutput=true"};
        ProcessExecuter processExecuter = new ProcessExecuter();
        Pair<Integer, String> result = processExecuter.exec(command);
        if (result.getFirst() != 0) {
            throw new RuntimeException("Failed to get maven dependency tree: " + result.getSecond());
        }

        String modulePattern = "[a-z-]+";
        Pattern dependencyPattern = Pattern.compile("com\\.yahoo\\.vespa:(" + modulePattern + "):.* -> " +
                ".*com\\.yahoo\\.vespa:(" + modulePattern + "):.*", Pattern.CASE_INSENSITIVE);
        String dependenciesRaw = new String(Files.readAllBytes(outputFile));
        Matcher m = dependencyPattern.matcher(dependenciesRaw);

        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        while (m.find()) {
            if (! dependencyGraph.containsKey(m.group(2))) {
                dependencyGraph.put(m.group(2), new HashSet<>());
            }

            dependencyGraph.get(m.group(2)).add(m.group(1));
        }

        List<String> buildOrder = new ArrayList<>();
        for (String module: modules) {
            dfs(module, dependencyGraph, buildOrder);
        }

        Collections.reverse(buildOrder);
        mavenInstallModules(containerName, buildOrder.stream().toArray(String[]::new));
    }

    private static void dfs(String root, Map<String, Set<String>> dependencies, List<String> order) {
        if (! order.contains(root)) {
            if (dependencies.containsKey(root)) {
                for (String child : dependencies.get(root)) {
                    dfs(child, dependencies, order);
                }
            }

            order.add(root);
        }
    }

    private void startSystemTestNodeIfNeeded(ContainerName containerName) throws IOException, InterruptedException, ExecutionException {
        logger.info("Building " + SYSTEMTESTS_DOCKER_IMAGE.asString());
        buildVespaSystestDockerImage(docker, vespaBaseImage);

        Optional<Container> container = docker.getContainer(containerName.asString());
        if (container.isPresent()) {
            if (container.get().state.isRunning()) return;
            else docker.deleteContainer(containerName);
        }

        logger.info("Starting systemtests container " + containerName.asString());
        InetAddress nodeInetAddress = InetAddress.getByName(containerName.asString());
        docker.createContainerCommand(
                SYSTEMTESTS_DOCKER_IMAGE,
                containerName,
                containerName.asString())
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                .withIpAddress(nodeInetAddress)
                .withEnvironment("USER", "root")
                .withEnvironment("VESPA_SYSTEM_TEST_USE_TLS", "false")
                .withUlimit("nofile", 16384, 16384)
                .withUlimit("nproc", 409600, 409600)
                .withUlimit("core", -1, -1)
                .withVolume(Paths.get(System.getProperty("user.home")).resolve(".m2/settings.xml").toString(), "/root/.m2/settings.xml")
                .withVolume(pathToSystemtestsInHost.toString(), pathToSystemtestsInContainer.toString())
                .withVolume(pathToVespaRepoInHost.toString(), pathToVespaRepoInContainer.toString())
                .create();

        docker.startContainer(containerName);
        docker.dockerClient.copyArchiveToContainerCmd(containerName.asString())
                .withHostResource("/etc/hosts").withRemotePath("/etc/").exec();

        // TODO: Should check something to see if node_server.rb is ready
        Thread.sleep(1000);
    }

    private void buildVespaSystestDockerImage(Docker docker, DockerImage vespaBaseImage) throws IOException, ExecutionException, InterruptedException {
        if (!docker.imageIsDownloaded(vespaBaseImage)) {
            logger.info("Pulling " + vespaBaseImage.asString() + " (This may take a while)");
            docker.pullImageAsync(vespaBaseImage).get();
        }

        Path systestBuildDirectory = pathToVespaRepoInHost.resolve("docker-api/src/test/resources/systest/");
        Path systestDockerfile = systestBuildDirectory.resolve("Dockerfile");

        String dockerfileTemplate = new String(Files.readAllBytes(systestBuildDirectory.resolve("Dockerfile.template")))
                .replaceAll("\\$VESPA_BASE_IMAGE", vespaBaseImage.asString());
        Files.write(systestDockerfile, dockerfileTemplate.getBytes());

        docker.buildImage(systestDockerfile.toFile(), SYSTEMTESTS_DOCKER_IMAGE);
    }

    private Integer executeInContainer(ContainerName containerName, String... args) throws InterruptedException {
        logger.info("Executing in container: " + String.join(" ", args));
        ExecCreateCmdResponse response = docker.dockerClient.execCreateCmd(containerName.asString())
                .withCmd(args)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        ExecStartCmd execStartCmd = docker.dockerClient.execStartCmd(response.getId());
        execStartCmd.exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();

        InspectExecResponse state = docker.dockerClient.inspectExecCmd(execStartCmd.getExecId()).exec();
        return state.getExitCode();
    }

    private Integer runTest(ContainerName containerName, Path testToRun, String... args) throws InterruptedException {
        String[] combinedArgs = new String[args.length + 2];
        combinedArgs[0] = pathToTestRunner.toString();
        combinedArgs[1] = testToRun.toString();
        System.arraycopy(args, 0, combinedArgs, 2, args.length);

        return executeInContainer(containerName, combinedArgs);
    }
}
