package com.yahoo.vespa.hosted.node.admin.containerdata;

import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class can be used for adding files that should be accessible in a container's file system after
 * the container has been started.
 * <p>
 * Files that are added will be copied into the destination path on the host.
 * The entry point for a Docker image will take care of copying
 * everything into it's right place (ATM only done for config server nodes)
 * Note: Creating a new instance of this will cleanup all old data in the destination path
 */
public class ContainerData {

    private static final Logger log = Logger.getLogger(ContainerData.class.getName());
    public static final Path containerDataPath = Paths.get("/home/y/var/container-data");

    private final Path destinationPathOnHost;

    private ContainerData(Environment environment, ContainerName containerName) {
        this.destinationPathOnHost = environment.pathInHostFromPathInNode(containerName, ContainerData.containerDataPath);
    }

    public static ContainerData createClean(Environment environment, ContainerName containerName) {
        ContainerData containerData = new ContainerData(environment, containerName);
        IOExceptionUtil.uncheck(containerData::cleanup);
        return containerData;
    }


    public void addFile(Path relativePathInContainer, String data) {
        if (relativePathInContainer.isAbsolute())
            throw new IllegalArgumentException("Path must be relative to root: " + relativePathInContainer);

        Path path = destinationPathOnHost.resolve(relativePathInContainer);
        if (!path.toFile().exists()) {
            IOExceptionUtil.uncheck(() -> Files.createDirectories(path.getParent()));
        }
        IOUtils.writeFile(path.toFile(), Utf8.toBytes(data));
    }

    private void cleanup() throws IOException {
        log.log(LogLevel.INFO, "Cleaning up " + destinationPathOnHost.toAbsolutePath());
        recursiveDelete(destinationPathOnHost);
    }


    /*  The below is copied from FileHelper in node-maintainer. Use methods in that class
        instead when we start depending on node-maintainer
    */

    /**
     * Similar to rm -rf file:
     * - It's not an error if file doesn't exist
     * - If file is a directory, it and all content is removed
     * - For symlinks: Only the symlink is removed, not what the symlink points to
     */
    private static void recursiveDelete(Path basePath) throws IOException {
        if (Files.isDirectory(basePath)) {
            for (Path path : listContentsOfDirectory(basePath)) {
                recursiveDelete(path);
            }
        }

        Files.deleteIfExists(basePath);
    }

    private static List<Path> listContentsOfDirectory(Path basePath) {
        try {
            return Files.list(basePath).collect(Collectors.toList());
        } catch (NoSuchFileException ignored) {
            return Collections.emptyList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list contents of directory " + basePath.toAbsolutePath(), e);
        }
    }

}
