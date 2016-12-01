// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

/**
 * <pre>
 * Requires docker daemon, see {@link com.yahoo.vespa.hosted.dockerapi.DockerTestUtils} for more details.
 *
 * To get started:
 *  1. Add system test host hostnames to /etc/hosts:
 *      $ sudo ./vespa/node-admin/scripts/etc-hosts.sh
 *  2. Set environmental variable in shell or e.g. ~/.bashrc:
 *      VESPA_DOCKER_REGISTRY="docker.registry.hostname.com:1234"
 *      SYSTEMTESTS_PATH="/path/to/git/repo/systemtests/"
 *
 *
 * To run systemtests with SNAPSHOT Vespa:
 *  1. Add "--vespa-version=6-SNAPSHOT" argument to {@link #runTest(DockerImpl, ContainerName, Path, String... arguments)}
 *  2. List all the modules that need to be built and added to local repository in order in
 *          {@link #mavenInstallModules(DockerImpl, ContainerName, String... listOfModules)}
 *  3. Copy jars from {@code PATH_TO_VESPA_REPO_IN_CONTAINER.resolve(module).resolve(CONTAINER_TARGET_DIRECTORY).resolve(module.jar}
 *
 *</pre>
 * @author freva
 */
public class RunSystemTests {
    private static final DockerImage VESPA_BASE_IMAGE = new DockerImage(
            System.getenv("VESPA_DOCKER_REGISTRY") + "/vespa/ci:6.50.111");
    private static final DockerImage SYSTEMTESTS_DOCKER_IMAGE = new DockerImage("vespa-systest:latest");
    private static final String CONTAINER_TARGET_DIRECTORY = "target_container";

    private final Path PATH_TO_SYSTEMTESTS_IN_HOST = Paths.get(System.getenv("SYSTEMTESTS_PATH"));
    private final Path PATH_TO_SYSTEMTESTS_IN_CONTAINER = Paths.get("/systemtests");
    private final Path PATH_TO_VESPA_REPO_IN_HOST = Paths.get("").toAbsolutePath().getParent();
    private final Path PATH_TO_VESPA_REPO_IN_CONTAINER = Paths.get("/vespa");
    private final Path PATH_TO_TEST_RUNNER = PATH_TO_SYSTEMTESTS_IN_CONTAINER.resolve("bin/run_test.rb");

    private final Logger logger = Logger.getLogger("systemtest");

    @Test
    public void runBasicSearch() throws IOException, InterruptedException {
        DockerImpl docker = DockerTestUtils.getDocker();
        ContainerName systemTestContainerName = new ContainerName("stest-1");
        Path testToRunPath = PATH_TO_SYSTEMTESTS_IN_CONTAINER.resolve("tests/search/basicsearch/basic_search.rb");

        logger.info("Building " + SYSTEMTESTS_DOCKER_IMAGE.asString());
        buildVespaSystestDockerImage(docker, VESPA_BASE_IMAGE);

        logger.info("Starting systemtests host");
        startSystemTestNodeIfNeeded(docker, systemTestContainerName);

//        mavenInstallModules(docker, systemTestContainerName, "docproc", "container-dev");
//        executeInContainer(docker, systemTestContainerName, "cp",
//                PATH_TO_VESPA_REPO_IN_CONTAINER.resolve("docproc").resolve(CONTAINER_TARGET_DIRECTORY).resolve("docproc.jar").toString(),
//                "/home/y/lib/jars/");

        logger.info("Running test " + testToRunPath);
        Integer testExitCode = runTest(docker, systemTestContainerName, testToRunPath);
        assertEquals("Test did not finish with exit code 0", Integer.valueOf(0), testExitCode);
    }

    /**
     * This method runs mvn install inside container to update container's local repository, but because it runs as
     * root we have to move around the target/ otherwise mvn will fail on host next time you run it.
     */
    private void mavenInstallModules(DockerImpl docker, ContainerName containerName, String... modules) throws InterruptedException, IOException {
        for (String module : modules) {
            Path pathToModule = PATH_TO_VESPA_REPO_IN_CONTAINER.resolve(module);
            Path pathToTarget = PATH_TO_VESPA_REPO_IN_HOST.resolve(module).resolve("target");
            Path pathToTargetBackup = PATH_TO_VESPA_REPO_IN_HOST.resolve(module).resolve("target_bcp");

            if (Files.exists(pathToTarget)) {
                Files.move(pathToTarget, pathToTargetBackup, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                assert 0 == executeInContainer(docker, containerName, "mvn", "-DskipTests", "-e",
                        "-f=" + pathToModule.resolve("pom.xml"), "install");
            } finally {
                executeInContainer(docker, containerName, "mv",
                        PATH_TO_VESPA_REPO_IN_CONTAINER.resolve(module).resolve("target").toString(),
                        PATH_TO_VESPA_REPO_IN_CONTAINER.resolve(module).resolve(CONTAINER_TARGET_DIRECTORY).toString());

                if (Files.exists(pathToTargetBackup)) {
                    Files.move(pathToTargetBackup, pathToTarget);
                }
            }
        }
    }

    private void startSystemTestNodeIfNeeded(Docker docker, ContainerName containerName) throws UnknownHostException, InterruptedException {
        Optional<Container> container = docker.getContainer(containerName.asString());
        if (container.isPresent()) {
            if (container.get().isRunning) return;
            else docker.deleteContainer(containerName);
        }

        InetAddress nodeInetAddress = InetAddress.getByName(containerName.asString());
        docker.createContainerCommand(
                SYSTEMTESTS_DOCKER_IMAGE,
                containerName,
                containerName.asString())
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                .withIpAddress(nodeInetAddress)
                .withEnvironment("USER", "root")
                .withUlimit("nofile", 16384, 16384)
                .withUlimit("nproc", 409600, 409600)
                .withUlimit("core", -1, -1)
                .withVolume(Paths.get(System.getProperty("user.home")).resolve(".m2/settings.xml").toString(), "/root/.m2/settings.xml")
                .withVolume("/etc/hosts", "/etc/hosts")
                .withVolume(PATH_TO_SYSTEMTESTS_IN_HOST.toString(), PATH_TO_SYSTEMTESTS_IN_CONTAINER.toString())
                .withVolume(PATH_TO_VESPA_REPO_IN_HOST.toString(), PATH_TO_VESPA_REPO_IN_CONTAINER.toString())
                .create();

        docker.startContainer(containerName);
        // TODO: Should check something to see if node_server.rb is ready
        Thread.sleep(1000);
    }

    private void buildVespaSystestDockerImage(Docker docker, DockerImage vespaBaseImage) throws IOException {
        Path systestBuildDirectory = Paths.get("src/test/resources/systest/");
        Path systestDockerfile = systestBuildDirectory.resolve("Dockerfile");

        String dockerfileTemplate = new String(Files.readAllBytes(systestBuildDirectory.resolve("Dockerfile.template")))
                .replaceAll("\\$VESPA_BASE_IMAGE", vespaBaseImage.asString());
        Files.write(systestDockerfile, dockerfileTemplate.getBytes());

        docker.buildImage(systestDockerfile.toFile(), SYSTEMTESTS_DOCKER_IMAGE);
    }

    private static Integer executeInContainer(DockerImpl docker, ContainerName containerName, String... args) throws InterruptedException {
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

    private Integer runTest(DockerImpl docker, ContainerName containerName, Path testToRun, String... args) throws InterruptedException {
        String[] combinedArgs = new String[args.length + 2];
        combinedArgs[0] = PATH_TO_TEST_RUNNER.toString();
        combinedArgs[1] = testToRun.toString();
        System.arraycopy(args, 0, combinedArgs, 2, args.length);

        return executeInContainer(docker, containerName, combinedArgs);
    }
}
