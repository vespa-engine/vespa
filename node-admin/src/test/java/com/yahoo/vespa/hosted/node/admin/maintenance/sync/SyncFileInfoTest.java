// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;

import static com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo.Compression.NONE;
import static com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo.Compression.ZSTD;
import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class SyncFileInfoTest {

    private static final FileSystem fileSystem = TestFileSystem.create();

    private static final String bucket = "logs-region-acdf21";
    private static final ApplicationId application = ApplicationId.from("ten", "app", "ins");
    private static final HostName hostname = HostName.from("h12352a.env.region-1.vespa.domain.example");
    private static final Path accessLogPath1 = fileSystem.getPath("/opt/vespa/logs/qrs/access.log.20210211");
    private static final Path accessLogPath2 = fileSystem.getPath("/opt/vespa/logs/qrs/access.log.20210212.zst");
    private static final Path accessLogPath3 = fileSystem.getPath("/opt/vespa/logs/qrs/access-json.log.20210213.zst");
    private static final Path accessLogPath4 = fileSystem.getPath("/opt/vespa/logs/qrs/JsonAccessLog.default.20210214.zst");
    private static final Path connectionLogPath1 = fileSystem.getPath("/opt/vespa/logs/qrs/ConnectionLog.default.20210210");
    private static final Path connectionLogPath2 = fileSystem.getPath("/opt/vespa/logs/qrs/ConnectionLog.default.20210212.zst");
    private static final Path vespaLogPath1 = fileSystem.getPath("/opt/vespa/logs/vespa.log");
    private static final Path vespaLogPath2 = fileSystem.getPath("/opt/vespa/logs/vespa.log-2021-02-12");

    @Test
    public void tenant_log() {
        assertTenantSyncFileInfo(accessLogPath1, null, null);
        assertTenantSyncFileInfo(accessLogPath2, "ten/app/ins/h12352a/logs/access/access.log.20210212.zst", NONE);
        assertTenantSyncFileInfo(accessLogPath3, "ten/app/ins/h12352a/logs/access/access-json.log.20210213.zst", NONE);
        assertTenantSyncFileInfo(accessLogPath4, "ten/app/ins/h12352a/logs/access/JsonAccessLog.default.20210214.zst", NONE);

        assertTenantSyncFileInfo(connectionLogPath1, null, null);
        assertTenantSyncFileInfo(connectionLogPath2, "ten/app/ins/h12352a/logs/connection/ConnectionLog.default.20210212.zst", NONE);

        assertTenantSyncFileInfo(vespaLogPath1, null, null);
        assertTenantSyncFileInfo(vespaLogPath2, "ten/app/ins/h12352a/logs/vespa/vespa.log-2021-02-12.zst", ZSTD);
    }

    @Test
    public void infra_vespa_log() {
        SyncFileInfo sfi = SyncFileInfo.infrastructureVespaLog(bucket, hostname, vespaLogPath2);
        assertEquals("infrastructure/h12352a/logs/vespa/vespa.log-2021-02-12.zst", sfi.destPath().toString());
        assertEquals(ZSTD, sfi.uploadCompression());
    }

    private static void assertTenantSyncFileInfo(Path srcPath, String destPath, SyncFileInfo.Compression compression) {
        Optional<SyncFileInfo> sfi = SyncFileInfo.tenantLog(bucket, application, hostname, srcPath);
        assertEquals(destPath, sfi.map(SyncFileInfo::destPath).map(Path::toString).orElse(null));
        assertEquals(compression, sfi.map(SyncFileInfo::uploadCompression).orElse(null));
    }
}
