// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import com.yahoo.system.ProcessExecuter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

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
     DockerImage vespaDockerBase = new DockerImage("docker-registry.domain.tld:8080/vespa/ci:6.52.35");
     Path pathToSystemtestsInHost = Paths.get("/home/valerijf/dev/systemtests");
     RunSystemTests runSystemTests = new RunSystemTests(vespaDockerBase, pathToSystemtestsInHost);

     ContainerName systemtestsHost = new ContainerName("stest-1");
     // Update maven local repository and $VESPA_HOME/lib/jars with the current version of these modules inside container
     runSystemTests.updateContainerMavenLocalRepository(systemtestsHost);

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
    private final Path pathToLibJars = Paths.get(getDefaults().underVespaHome("lib/jars"));
    private final String username = System.getProperty("user.name");

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
     * This method updates container's local repository with all artifacts that are built on host machine, then
     * copies any existing and updated file from target to $VESPA_HOME/lib/jars.
     *
     * @param containerName name of the container to install modules in, if it does not exist, a new
     *                       one will be started.
     */
    void updateContainerMavenLocalRepository(ContainerName containerName) throws InterruptedException, IOException, ExecutionException {
        startSystemTestNodeIfNeeded(containerName);

        String sources = pathToVespaRepoInContainer.toString() + "/*/target/";
        String destination = pathToLibJars.toString() + "/";
        executeInContainer(containerName, "root","/bin/sh", "-c",
                "rsync --existing --update --recursive --times " + sources + " " + destination);

        executeInContainer(containerName, username, "/bin/sh", "-c", "cd " + pathToVespaRepoInContainer + ";" +
                "mvn jar:jar install:install");
    }

    private void startSystemTestNodeIfNeeded(ContainerName containerName) throws IOException, InterruptedException, ExecutionException {
        buildVespaSystestDockerImage(docker, vespaBaseImage);

        Optional<Container> container = docker.getContainer(containerName);
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
                .withUlimit("nofile", 262_144, 262_144)
                .withUlimit("nproc", 32_768, 409_600)
                .withUlimit("core", -1, -1)
                .withVolume(Paths.get(System.getProperty("user.home")).resolve(".m2").toString(),
                        Paths.get("/home/").resolve(username).resolve(".m2").toString())
                .withVolume(pathToSystemtestsInHost.toString(), pathToSystemtestsInContainer.toString())
                .withVolume(pathToVespaRepoInHost.toString(), pathToVespaRepoInContainer.toString())
                .create();

        docker.startContainer(containerName);

        String uid = new ProcessExecuter().exec(new String[]{"/bin/sh", "-c", "id -u " + username}).getSecond();
        docker.executeInContainerAsRoot(containerName, "useradd", "-u", uid.trim(), username);

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

        logger.info("Building " + SYSTEMTESTS_DOCKER_IMAGE.asString());
        docker.buildImage(systestDockerfile.toFile(), SYSTEMTESTS_DOCKER_IMAGE);
    }

    private Integer executeInContainer(ContainerName containerName, String runAsUser, String... args) throws InterruptedException {
        logger.info("Executing as '" + runAsUser + "' in '" + containerName.asString() + "': " + String.join(" ", args));
        ExecCreateCmdResponse response = docker.dockerClient.execCreateCmd(containerName.asString())
                .withCmd(args)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withUser(runAsUser)
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

        return executeInContainer(containerName, "root", combinedArgs);
    }
}
