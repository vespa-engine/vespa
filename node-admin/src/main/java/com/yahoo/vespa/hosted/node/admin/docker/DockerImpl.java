// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import com.spotify.docker.client.ContainerNotFoundException;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ExecCreateParam;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerState;
import com.spotify.docker.client.messages.ExecState;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.RemovedImage;
import com.yahoo.log.LogLevel;
import com.yahoo.nodeadmin.docker.DockerConfig;
import com.yahoo.vespa.applicationmodel.HostName;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author stiankri
 */
public class DockerImpl implements Docker {
    private static final Logger log = Logger.getLogger(DockerImpl.class.getName());

    private static final int SECONDS_TO_WAIT_BEFORE_KILLING = 10;
    private static final String FRAMEWORK_CONTAINER_PREFIX = "/";
    static final String[] COMMAND_GET_VESPA_VERSION = new String[]{"vespa-nodectl", "vespa-version"};
    private static final Pattern VESPA_VERSION_PATTERN = Pattern.compile("^(\\S*)$", Pattern.MULTILINE);

    private static final String LABEL_NAME_MANAGEDBY = "com.yahoo.vespa.managedby";
    private static final String LABEL_VALUE_MANAGEDBY = "node-admin";
    private static final Map<String,String> CONTAINER_LABELS = new HashMap<>();
    private static DateFormat filenameFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    static {
        CONTAINER_LABELS.put(LABEL_NAME_MANAGEDBY, LABEL_VALUE_MANAGEDBY);
        filenameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static final Path RELATIVE_APPLICATION_STORAGE_PATH = Paths.get("home/docker/container-storage");
    private static final Path APPLICATION_STORAGE_PATH_FOR_NODE_ADMIN = Paths.get("/host").resolve(RELATIVE_APPLICATION_STORAGE_PATH);
    private static final Path APPLICATION_STORAGE_PATH_FOR_HOST = Paths.get("/").resolve(RELATIVE_APPLICATION_STORAGE_PATH);

    private static final List<String> DIRECTORIES_TO_MOUNT = Arrays.asList(
            getDefaults().underVespaHome("logs"),
            getDefaults().underVespaHome("var/cache"),
            getDefaults().underVespaHome("var/crash"),
            getDefaults().underVespaHome("var/db/jdisc"),
            getDefaults().underVespaHome("var/db/vespa"),
            getDefaults().underVespaHome("var/jdisc_container"),
            getDefaults().underVespaHome("var/jdisc_core"),
            getDefaults().underVespaHome("var/logstash-forwarder"),
            getDefaults().underVespaHome("var/maven"),
            getDefaults().underVespaHome("var/scoreboards"),
            getDefaults().underVespaHome("var/service"),
            getDefaults().underVespaHome("var/share"),
            getDefaults().underVespaHome("var/spool"),
            getDefaults().underVespaHome("var/vespa"),
            getDefaults().underVespaHome("var/yca"),
            getDefaults().underVespaHome("var/ycore++"),
            getDefaults().underVespaHome("var/zookeeper"));

    private final DockerClient docker;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Object monitor = new Object();

    @GuardedBy("monitor")
    private final Map<DockerImage, CompletableFuture<DockerImage>> scheduledPulls = new HashMap<>();

    public DockerImpl(final DockerClient dockerClient) {
        this.docker = dockerClient;
    }

    @Inject
    public DockerImpl(final DockerConfig config) {
        this(DefaultDockerClient.builder().
                uri(config.uri()).
                dockerCertificates(certificates(config)).
                readTimeoutMillis(TimeUnit.MINUTES.toMillis(30)). // Some operations may take minutes.
                build());
    }


    private static DockerCertificates certificates(DockerConfig config) {
        try {
            return DockerCertificates.builder()
                    .caCertPath(Paths.get(config.caCertPath()))
                    .clientCertPath(Paths.get(config.clientCertPath()))
                    .clientKeyPath(Paths.get(config.clientKeyPath()))
                    .build().get();
        } catch (DockerCertificateException e) {
            throw new RuntimeException("Failed configuring certificates for contacting docker daemon.", e);
        }
    }

    @Override
    public CompletableFuture<DockerImage> pullImageAsync(final DockerImage image) {
        // We define the task before we create the CompletableFuture, to ensure that the local future variable cannot
        // be accessed by the task, forcing it to always go through removeScheduledPoll() before completing the task.
        final Runnable task = () -> {
            try {
                docker.pull(image.asString());
                removeScheduledPoll(image).complete(image);
            } catch (InterruptedException e) {
                removeScheduledPoll(image).completeExceptionally(e);
            } catch (DockerException e) {
                if (imageIsDownloaded(image)) {
                    /* TODO: the docker client is not in sync with the server protocol causing it to throw
                     * "java.io.IOException: Stream closed", even if the pull succeeded; thus ignoring here
                     */
                    removeScheduledPoll(image).complete(image);
                } else {
                    removeScheduledPoll(image).completeExceptionally(e);
                }
            } catch (RuntimeException e) {
                removeScheduledPoll(image).completeExceptionally(e);
                throw e;
            }
        };

        final CompletableFuture<DockerImage> completionListener;
        synchronized (monitor) {
            if (scheduledPulls.containsKey(image)) {
                return scheduledPulls.get(image);
            }
            completionListener = new CompletableFuture<>();
            scheduledPulls.put(image, completionListener);
        }
        executor.submit(task);
        return completionListener;
    }

    private CompletableFuture<DockerImage> removeScheduledPoll(final DockerImage image) {
        synchronized (monitor) {
            return scheduledPulls.remove(image);
        }
    }

    /**
     * Check if a given image is already in the local registry
     */
    @Override
    public boolean imageIsDownloaded(final DockerImage dockerImage) {
        try {
            List<Image> images = docker.listImages(DockerClient.ListImagesParam.allImages());
            return images.stream().
                    flatMap(image -> image.repoTags().stream()).
                    anyMatch(tag -> tag.equals(dockerImage.asString()));
        } catch (DockerException|InterruptedException e) {
            throw new RuntimeException("Failed to list image name: '" + dockerImage + "'", e);
        }
    }

    /**
     * Delete application storage, implemented by moving it away for later cleanup
     */
    @Override
    public void deleteApplicationStorage(ContainerName containerName) throws IOException {
        Path from = applicationStoragePathForNodeAdmin(containerName.asString());
        if (!Files.exists(from)) {
            log.log(LogLevel.INFO, "The application storage at " + from + " doesn't exist");
            return;
        }
        Path to = applicationStoragePathForNodeAdmin("cleanup_" + containerName.asString() + "_" + filenameFormatter
                .format(Date.from(Instant.now())));
        log.log(LogLevel.INFO, "Deleting application storage by moving it from " + from + " to " + to);
        Files.move(from, to);
    }

    @Override
    public void startContainer(
            final DockerImage dockerImage,
            final HostName hostName,
            final ContainerName containerName,
            double minCpuCores,
            double minDiskAvailableGb,
            double minMainMemoryAvailableGb) {
        try {
            final double GIGA = Math.pow(2.0, 30.0);
            // TODO: Enforce disk constraints
            // TODO: Consider if CPU shares or quoata should be set. For now we are just assuming they are
            // nicely controlled by docker.
            ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder().
                    image(dockerImage.asString()).
                    labels(CONTAINER_LABELS).
                    hostConfig(
                            HostConfig.builder()
                                    .networkMode("none")
                                    .binds(applicationStorageToMount(containerName.asString()))
                                    .build())
                    .env("CONFIG_SERVER_ADDRESS=" + Joiner.on(',').join(Environment.getConfigServerHosts())).
                            hostname(hostName.s());
            if (minMainMemoryAvailableGb > 0.00001) {
                containerConfigBuilder.memory((long) (GIGA * minMainMemoryAvailableGb));
            }
            docker.createContainer(containerConfigBuilder.build(), containerName.asString());
            //HostConfig hostConfig = HostConfig.builder().create();
            docker.startContainer(containerName.asString());

            ContainerInfo containerInfo = docker.inspectContainer(containerName.asString());
            ContainerState state = containerInfo.state();

            if (state.running()) {
                Integer pid = state.pid();
                if (pid == null) {
                    throw new DockerException("PID of running container for host " + hostName + " is null");
                }
                setupContainerNetworking(containerName, hostName, pid);
            }

        } catch (IOException | DockerException | InterruptedException e) {
            throw new RuntimeException("Failed to start container " + containerName.asString(), e);
        }
    }

    @Override
    public String getVespaVersion(final ContainerName containerName) {
        ProcessResult result = executeInContainer(containerName, COMMAND_GET_VESPA_VERSION);
        if (!result.isSuccess()) {
            throw new RuntimeException("Container " + containerName.asString() + ": Command "
                    + Arrays.toString(COMMAND_GET_VESPA_VERSION) + " failed: " + result);
        }
        return parseVespaVersion(result.getOutput())
                .orElseThrow(() -> new RuntimeException(
                        "Container " + containerName.asString() + ": Failed to parse vespa version from "
                                + result.getOutput()));
    }

    // Returns empty if vespa version cannot be parsed.
    static Optional<String> parseVespaVersion(final String rawVespaVersion) {
        final Matcher matcher = VESPA_VERSION_PATTERN.matcher(rawVespaVersion);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    @Override
    public ProcessResult executeInContainer(ContainerName containerName, String... args) {
        assert args.length >= 1;
        try {
            final String execId = docker.execCreate(
                    containerName.asString(),
                    args,
                    ExecCreateParam.attachStdout(),
                    ExecCreateParam.attachStderr());

            try (final LogStream stream = docker.execStart(execId)) {
                // This will block until program exits
                final String output = stream.readFully();

                final ExecState state = docker.execInspect(execId);
                assert !state.running();
                Integer exitCode = state.exitCode();
                assert exitCode != null;

                return new ProcessResult(exitCode, output);
            }
        } catch (DockerException | InterruptedException e) {
            throw new RuntimeException("Container " + containerName.asString()
                    + " failed to execute " + Arrays.toString(args));
        }
    }

    private void setupContainerNetworking(ContainerName containerName,
                                          HostName hostName,
                                          int containerPid) throws UnknownHostException {
        InetAddress inetAddress = InetAddress.getByName(hostName.s());
        String ipAddress = inetAddress.getHostAddress();

        final List<String> command = new LinkedList<>();
        command.add("sudo");
        command.add(getDefaults().underVespaHome("libexec/vespa/node-admin/configure-container-networking.py"));

        Environment.NetworkType networkType = Environment.networkType();
        if (networkType != Environment.NetworkType.normal) {
            command.add("--" + networkType);
        }
        command.add(Integer.toString(containerPid));
        command.add(ipAddress);

        for (int retry = 0; retry < 30; ++retry) {
            try {
                runCommand(command);
                log.log(LogLevel.INFO, "Container " + containerName.asString() + ": Done setting up network");
                return;
            } catch (Exception e) {
                final int sleepSecs = 3;
                log.log(LogLevel.WARNING, "Container " + containerName.asString()
                        + ": Failed to configure network with command " + command
                        + ", will retry in " + sleepSecs + " seconds", e);
                try {
                    Thread.sleep(sleepSecs * 1000);
                } catch (InterruptedException e1) {
                    log.log(LogLevel.WARNING, "Sleep interrupted", e1);
                }
            }
        }
    }

    private void runCommand(final List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        String output = CharStreams.toString(new InputStreamReader(process.getInputStream()));
        int resultCode = process.waitFor();
        if (resultCode != 0) {
            throw new Exception("Command " + Joiner.on(' ').join(command) + " failed: " + output);
        }
    }

    static List<String> applicationStorageToMount(String containerName) {
        // From-paths when mapping volumes are as seen by the Docker daemon (host)
        Path destination = applicationStoragePathForHost(containerName);

        return Stream.concat(
                        Stream.of("/etc/hosts:/etc/hosts"),
                        DIRECTORIES_TO_MOUNT.stream()
                                .map(directory -> bindDirective(destination, directory)))
                .collect(Collectors.toList());
    }

    private static Path applicationStoragePathForHost(String containerName) {
        return APPLICATION_STORAGE_PATH_FOR_HOST.resolve(containerName);
    }

    public static Path applicationStoragePathForNodeAdmin(String containerName) {
        return APPLICATION_STORAGE_PATH_FOR_NODE_ADMIN.resolve(containerName);
    }

    private static String bindDirective(Path applicationStorageStorage, String directory) {
        if (!directory.startsWith("/")) {
            throw new RuntimeException("Expected absolute path, got " + directory);
        }

        Path hostPath = applicationStorageStorage.resolve(directory.substring(1));
        return hostPath.toFile().getAbsolutePath() + ":" + directory;
    }

    @Override
    public void stopContainer(final ContainerName containerName) {
        Optional<com.spotify.docker.client.messages.Container> dockerContainer = getContainerFromName(containerName, true);
        if (dockerContainer.isPresent()) {
            try {
                docker.stopContainer(dockerContainer.get().id(), SECONDS_TO_WAIT_BEFORE_KILLING);
            } catch (DockerException|InterruptedException e) {
                throw new RuntimeException("Failed to stop container", e);
            }
        }
    }

    @Override
    public void deleteContainer(ContainerName containerName) {
        Optional<com.spotify.docker.client.messages.Container> dockerContainer = getContainerFromName(containerName, true);
        if (dockerContainer.isPresent()) {
            try {
                docker.removeContainer(dockerContainer.get().id());
            } catch (DockerException|InterruptedException e) {
                throw new RuntimeException("Failed to delete container", e);
            }
        }
    }

    @Override
    public List<Container> getAllManagedContainers() {
        try {

            return docker.listContainers(DockerClient.ListContainersParam.allContainers(true)).stream().
                    filter(this::isManaged).
                    flatMap(this::asContainer).
                    collect(Collectors.toList());
        } catch (DockerException|InterruptedException e) {
            throw new RuntimeException("Failed to delete container", e);
        }
    }

    @Override
    public Optional<Container> getContainer(HostName hostname) {
        // TODO Don't rely on getAllManagedContainers
        return getAllManagedContainers().stream()
                .filter(c -> Objects.equals(hostname, c.hostname))
                .findFirst();
    }

    private Stream<Container> asContainer(com.spotify.docker.client.messages.Container dockerClientContainer) {
        try {
            final ContainerInfo containerInfo = docker.inspectContainer(dockerClientContainer.id());
            return Stream.of(new Container(
                    new HostName(containerInfo.config().hostname()),
                    new DockerImage(dockerClientContainer.image()),
                    new ContainerName(decode(containerInfo.name())),
                    containerInfo.state().running()));
        } catch(ContainerNotFoundException e) {
            return Stream.empty();
        } catch (InterruptedException|DockerException e) {
            //TODO: do proper exception handling
            throw new RuntimeException("Failed talking to docker daemon", e);
        }
    }


    private Optional<com.spotify.docker.client.messages.Container> getContainerFromName(
            final ContainerName containerName, final boolean alsoGetStoppedContainers) {
        try {
            return docker.listContainers(DockerClient.ListContainersParam.allContainers(alsoGetStoppedContainers)).stream().
                    filter(this::isManaged).
                    filter(container -> matchName(container, containerName.asString())).
                    findFirst();
        } catch (DockerException|InterruptedException e) {
            throw new RuntimeException("Failed to get container from name", e);
        }
    }

    private boolean isManaged(final com.spotify.docker.client.messages.Container container) {
        final Map<String, String> labels = container.labels();
        if (labels == null) {
            return false;
        }
        return LABEL_VALUE_MANAGEDBY.equals(labels.get(LABEL_NAME_MANAGEDBY));
    }

    private boolean matchName(com.spotify.docker.client.messages.Container container, String targetName) {
        return container.names().stream().anyMatch(encodedName -> decode(encodedName).equals(targetName));
    }

    private String decode(String encodedContainerName) {
        return encodedContainerName.substring(FRAMEWORK_CONTAINER_PREFIX.length());
    }

    @Override
    public void deleteImage(final DockerImage dockerImage) {
        try {
            log.info("Deleting docker image " + dockerImage);
            final List<RemovedImage> removedImages = docker.removeImage(dockerImage.asString());
            for (RemovedImage removedImage : removedImages) {
                log.info("Result of deleting docker image " + dockerImage + ": " + removedImage);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interrupt", e);
        } catch (DockerException e) {
            log.log(Level.WARNING, "Could not delete docker image " + dockerImage, e);
        }
    }

    @Override
    public Set<DockerImage> getUnusedDockerImages() {
        // Description of concepts and relationships:
        // - a docker image has an id, and refers to its parent image (if any) by image id.
        // - a docker image may, in addition to id,  have multiple tags, but each tag identifies exactly one image.
        // - a docker container refers to its image (exactly one) either by image id or by image tag.
        // What this method does to find images considered unused, is build a tree of dependencies
        // (e.g. container->tag->image->image) and identify image nodes whose only children (if any) are leaf tags.
        // In other words, an image node with no children, or only tag children having no children themselves is unused.
        // An image node with an image child is considered used.
        // An image node with a container child is considered used.
        // An image node with a tag child with a container child is considered used.
        try {
            final Map<String, DockerObject> objects = new HashMap<>();
            final Map<String, String> dependencies = new HashMap<>();

            // Populate maps with images (including tags) and their dependencies (parents).
            for (Image image : docker.listImages(DockerClient.ListImagesParam.allImages())) {
                objects.put(image.id(), new DockerObject(image.id(), DockerObjectType.IMAGE));
                if (image.parentId() != null && !image.parentId().isEmpty()) {
                    dependencies.put(image.id(), image.parentId());
                }
                for (String tag : image.repoTags()) {
                    objects.put(tag, new DockerObject(tag, DockerObjectType.IMAGE_TAG));
                    dependencies.put(tag, image.id());
                }
            }

            // Populate maps with containers and their dependency to the image they run on.
            for (com.spotify.docker.client.messages.Container container : docker.listContainers(DockerClient.ListContainersParam.allContainers(true))) {
                objects.put(container.id(), new DockerObject(container.id(), DockerObjectType.CONTAINER));
                dependencies.put(container.id(), container.image());
            }

            // Now update every object with its dependencies.
            dependencies.forEach((fromId, toId) -> {
                Optional.ofNullable(objects.get(toId))
                        .ifPresent(obj -> obj.addDependee(objects.get(fromId)));
            });

            // Find images that are not in use (i.e. leafs not used by any containers).
            return objects.values().stream()
                    .filter(dockerObject -> dockerObject.type == DockerObjectType.IMAGE)
                    .filter(dockerObject -> !dockerObject.isInUse())
                    .map(obj -> obj.id)
                    .map(DockerImage::new)
                    .collect(Collectors.toSet());
        } catch (InterruptedException|DockerException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    // Helper enum for calculating which images are unused.
    private enum DockerObjectType {
        IMAGE_TAG, IMAGE, CONTAINER
    }

    // Helper class for calculating which images are unused.
    private static class DockerObject {
        public final String id;
        public final DockerObjectType type;
        private final List<DockerObject> dependees = new LinkedList<>();

        public DockerObject(final String id, final DockerObjectType type) {
            this.id = id;
            this.type = type;
        }

        public boolean isInUse() {
            if (type == DockerObjectType.CONTAINER) {
                return true;
            }

            if (dependees.isEmpty()) {
                return false;
            }

            if (type == DockerObjectType.IMAGE) {
                if (dependees.stream().anyMatch(obj -> obj.type == DockerObjectType.IMAGE)) {
                    return true;
                }
            }

            return dependees.stream().anyMatch(DockerObject::isInUse);
        }

        public void addDependee(final DockerObject dockerObject) {
            dependees.add(dockerObject);
        }

        @Override
        public String toString() {
            return "DockerObject {"
                    + " id=" + id
                    + " type=" + type.name().toLowerCase()
                    + " inUse=" + isInUse()
                    + " dependees=" + dependees.stream().map(obj -> obj.id).collect(Collectors.toList())
                    + " }";
        }
    }
}
