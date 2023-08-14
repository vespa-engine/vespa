// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.image;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.container.ContainerEngine;
import com.yahoo.vespa.hosted.node.admin.container.RegistryCredentialsProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Download a container image asynchronously.
 *
 * @author mpolden
 */
public class ContainerImageDownloader {

    private static final Logger LOG = Logger.getLogger(ContainerImageDownloader.class.getName());

    private final ContainerEngine containerEngine;
    private final Timer timer;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(
            new DaemonThreadFactory("container-image-downloader")); // Download one image at a time
    private final Set<DockerImage> pendingDownloads = Collections.synchronizedSet(new HashSet<>());

    public ContainerImageDownloader(ContainerEngine containerEngine, Timer timer) {
        this.containerEngine = Objects.requireNonNull(containerEngine);
        this.timer = Objects.requireNonNull(timer);
    }

    /**
     * Download given container image.
     *
     * @return true if the image download has completed.
     */
    public boolean get(TaskContext context, DockerImage image, RegistryCredentialsProvider credentialsProvider) {
        if (pendingDownloads.contains(image)) return false;
        if (containerEngine.hasImage(context, image)) return true;
        executorService.submit(() -> {
            try {
                Instant start = timer.currentTime();
                containerEngine.pullImage(context, image, credentialsProvider.get());
                LOG.log(Level.INFO, "Downloaded container image " + image + " in " + Duration.between(start, timer.currentTime()));
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Failed to download container image " + image, e);
            } finally {
                pendingDownloads.remove(image);
            }
        });
        pendingDownloads.add(image);
        return false;
    }

}
