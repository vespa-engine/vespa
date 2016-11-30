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
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

/**
 * Requires docker daemon, see {@link com.yahoo.vespa.hosted.dockerapi.DockerTestUtils} for more details.
 *
 * To get started:
 *  1. Add system test host hostnames to /etc/hosts:
 *      $ sudo ./vespa/node-admin/scripts/etc-hosts.sh
 *  2. Set environmental variable in shell or e.g. ~/.bashrc:
 *      VESPA_DOCKER_REGISTRY="<registry hostname>:<port>"
 *
 * @author freva
 */
public class RunSystemTests {
    private static final DockerImage VESPA_BASE_IMAGE = new DockerImage(
            System.getenv("VESPA_DOCKER_REGISTRY") + "/vespa/ci:6.50.111");
    private static final DockerImage SYSTEM_TEST_DOCKER_IMAGE = new DockerImage("vespa-systest:latest");
    private static final Path PATH_TO_SYSTEM_TESTS_IN_CONTAINER = Paths.get("/systemtests");

    private final Logger logger = Logger.getLogger("RunVespaLocal");

//    @Test
    public void runBasicSearch() throws IOException, InterruptedException {
        DockerImpl docker = DockerTestUtils.getDocker();
        ContainerName systemTestContainerName = new ContainerName("stest-1");
        Path testToRunPath = PATH_TO_SYSTEM_TESTS_IN_CONTAINER.resolve("tests/search/basicsearch/basic_search.rb");

        logger.info("Building " + SYSTEM_TEST_DOCKER_IMAGE.asString());
        buildVespaSystestDockerImage(docker, VESPA_BASE_IMAGE);

        logger.info("Starting systemtests host");
        Path pathToSystemsTestsRepo = Paths.get("/home/valerijf/dev/systemtests/");
        startSystemTestNodeIfNeeded(docker, systemTestContainerName, pathToSystemsTestsRepo);

        logger.info("Running test " + testToRunPath);
        Integer testExitCode = executeTestInHost(docker, testToRunPath, systemTestContainerName);
        assertEquals("Test did not finish with exit code 0", Integer.valueOf(0), testExitCode);
    }

    private Integer executeTestInHost(DockerImpl docker, Path pathToTest, ContainerName hostToExecuteTest) throws InterruptedException {
        ExecCreateCmdResponse response = docker.dockerClient.execCreateCmd(hostToExecuteTest.asString())
                .withCmd(PATH_TO_SYSTEM_TESTS_IN_CONTAINER.resolve("bin/run_test.rb").toString(), pathToTest.toString())
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        ExecStartCmd execStartCmd = docker.dockerClient.execStartCmd(response.getId());
        execStartCmd.exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();

        InspectExecResponse state = docker.dockerClient.inspectExecCmd(execStartCmd.getExecId()).exec();
        return state.getExitCode();
    }

    private void startSystemTestNodeIfNeeded(Docker docker, ContainerName containerName, Path pathToSystemsTestsRepo) throws UnknownHostException {
        Optional<Container> container = docker.getContainer(containerName.asString());
        if (container.isPresent()) {
            if (container.get().isRunning) return;
            else docker.deleteContainer(containerName);
        }

        InetAddress nodeInetAddress = InetAddress.getByName(containerName.asString());
        docker.createContainerCommand(
                SYSTEM_TEST_DOCKER_IMAGE,
                containerName,
                containerName.asString())
                .withNetworkMode(DockerImpl.DOCKER_CUSTOM_MACVLAN_NETWORK_NAME)
                .withIpAddress(nodeInetAddress)
                .withEnvironment("USER", "root")
                .withUlimit("nofile", 16384, 16384)
                .withUlimit("nproc", 409600, 409600)
                .withUlimit("core", -1, -1)
                .withVolume("/etc/hosts", "/etc/hosts")
                .withVolume(pathToSystemsTestsRepo.toString(), PATH_TO_SYSTEM_TESTS_IN_CONTAINER.toString())
                .create();

        docker.startContainer(containerName);
    }

    private void buildVespaSystestDockerImage(Docker docker, DockerImage vespaBaseImage) throws IOException {
        Path systestBuildDirectory = Paths.get("src/test/resources/systest/");
        Path systestDockerfile = systestBuildDirectory.resolve("Dockerfile");

        String dockerfileTemplate = new String(Files.readAllBytes(systestBuildDirectory.resolve("Dockerfile.template")))
                .replaceAll("\\$VESPA_BASE_IMAGE", vespaBaseImage.asString());
        Files.write(systestDockerfile, dockerfileTemplate.getBytes());

        docker.buildImage(systestDockerfile.toFile(), SYSTEM_TEST_DOCKER_IMAGE);
    }
}
