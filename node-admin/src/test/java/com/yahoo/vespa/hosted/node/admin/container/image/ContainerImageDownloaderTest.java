// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.image;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.hosted.node.admin.container.ContainerEngineMock;
import com.yahoo.vespa.hosted.node.admin.container.RegistryCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
public class ContainerImageDownloaderTest {

    @Test
    @Timeout(5_000)
    void test_download() {
        ContainerEngineMock podman = new ContainerEngineMock().asyncImageDownload(true);
        ContainerImageDownloader downloader = new ContainerImageDownloader(podman);
        TaskContext context = new TestTaskContext();
        DockerImage image = DockerImage.fromString("registry.example.com/repo/vespa:7.42");

        assertFalse(downloader.get(context, image, RegistryCredentials.none), "Download started");
        assertFalse(downloader.get(context, image, RegistryCredentials.none), "Download pending");
        podman.completeDownloadOf(image);
        boolean downloadCompleted;
        while (!(downloadCompleted = downloader.get(context, image, RegistryCredentials.none))) ;
        assertTrue(downloadCompleted, "Download completed");
    }

}
