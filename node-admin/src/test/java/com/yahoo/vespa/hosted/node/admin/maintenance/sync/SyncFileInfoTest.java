// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author freva
 */
public class SyncFileInfoTest {

    private static final FileSystem fileSystem = TestFileSystem.create();

    private static final String bucket = "logs-region-acdf21";
    private static final ApplicationId application = ApplicationId.from("tenant", "application", "instance");
    private static final HostName hostname = HostName.from("h12352a.env.region-1.vespa.domain.example");
    private static final Path accessLogPath = fileSystem.getPath("/opt/vespa/logs/qrs/access.json-20210212.zst");
    private static final Path vespaLogPath = fileSystem.getPath("/opt/vespa/logs/vespa.log-2021-02-12");

    @Test
    public void tenant_access_log() {
        SyncFileInfo sfi = SyncFileInfo.tenantAccessLog(bucket, application, hostname, accessLogPath);
        assertEquals(Paths.get("/tenant.application.instance/h12352a/logs/access/access.json-20210212.zst"), sfi.destPath());
        assertEquals(bucket, sfi.bucketName());
        assertNotEquals(ZstdCompressingInputStream.class, getInputStreamType(sfi));
    }

    @Test
    public void tenant_vespa_log() {
        SyncFileInfo sfi = SyncFileInfo.tenantVespaLog(bucket, application, hostname, vespaLogPath);
        assertEquals(Paths.get("/tenant.application.instance/h12352a/logs/vespa/vespa.log-2021-02-12.zst"), sfi.destPath());
        assertEquals(ZstdCompressingInputStream.class, getInputStreamType(sfi));
    }

    @Test
    public void infra_access_log() {
        SyncFileInfo sfi = SyncFileInfo.infrastructureAccessLog(bucket, hostname, accessLogPath);
        assertEquals(Paths.get("/infrastructure/h12352a/logs/access/access.json-20210212.zst"), sfi.destPath());
        assertNotEquals(ZstdCompressingInputStream.class, getInputStreamType(sfi));
    }

    @Test
    public void infra_vespa_log() {
        SyncFileInfo sfi = SyncFileInfo.infrastructureVespaLog(bucket, hostname, vespaLogPath);
        assertEquals(Paths.get("/infrastructure/h12352a/logs/vespa/vespa.log-2021-02-12.zst"), sfi.destPath());
        assertEquals(ZstdCompressingInputStream.class, getInputStreamType(sfi));
    }

    @BeforeClass
    public static void setup() throws IOException {
        Files.createDirectories(vespaLogPath.getParent());
        Files.createFile(vespaLogPath);
        Files.createDirectories(accessLogPath.getParent());
        Files.createFile(accessLogPath);
    }

    private static Class<? extends InputStream> getInputStreamType(SyncFileInfo syncFileInfo) {
        try (InputStream inputStream = syncFileInfo.inputStream()) {
            return inputStream.getClass();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}