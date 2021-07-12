// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.image;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.hosted.node.admin.container.ContainerEngineMock;
import com.yahoo.vespa.hosted.node.admin.container.RegistryCredentials;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class ContainerImageDownloaderTest {

    @Test(timeout = 5_000)
    public void test_download() {
        ContainerEngineMock podman = new ContainerEngineMock().asyncImageDownload(true);
        ContainerImageDownloader downloader = new ContainerImageDownloader(podman);
        TaskContext context = new TestTaskContext();
        DockerImage image = DockerImage.fromString("registry.example.com/vespa:7.42");

        assertFalse("Download started", downloader.get(context, image, RegistryCredentials.none));
        assertFalse("Download pending", downloader.get(context, image, RegistryCredentials.none));
        podman.completeDownloadOf(image);
        boolean downloadCompleted;
        while (!(downloadCompleted = downloader.get(context, image, RegistryCredentials.none)));
        assertTrue("Download completed", downloadCompleted);
    }

}
