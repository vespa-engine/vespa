// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo.Compression.NONE;
import static com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo.Compression.ZSTD;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class SyncFileInfoTest {

    private static final FileSystem fileSystem = TestFileSystem.create();

    private static final URI nodeArchiveUri = URI.create("s3://vespa-data-bucket/vespa/music/main/h432a/");
    private static final Path accessLogPath1 = fileSystem.getPath("/opt/vespa/logs/access/access.log.20210211");
    private static final Path accessLogPath2 = fileSystem.getPath("/opt/vespa/logs/access/access.log.20210212.zst");
    private static final Path accessLogPath3 = fileSystem.getPath("/opt/vespa/logs/access/access-json.log.20210213.zst");
    private static final Path accessLogPath4 = fileSystem.getPath("/opt/vespa/logs/access/JsonAccessLog.20210214.zst");
    private static final Path accessLogPath5 = fileSystem.getPath("/opt/vespa/logs/access/JsonAccessLog.container.20210214.zst");
    private static final Path accessLogPath6 = fileSystem.getPath("/opt/vespa/logs/access/JsonAccessLog.metrics-proxy.20210214.zst");
    private static final Path connectionLogPath1 = fileSystem.getPath("/opt/vespa/logs/access/ConnectionLog.20210210");
    private static final Path connectionLogPath2 = fileSystem.getPath("/opt/vespa/logs/access/ConnectionLog.20210212.zst");
    private static final Path connectionLogPath3 = fileSystem.getPath("/opt/vespa/logs/access/ConnectionLog.metrics-proxy.20210210");
    private static final Path vespaLogPath1 = fileSystem.getPath("/opt/vespa/logs/vespa.log");
    private static final Path vespaLogPath2 = fileSystem.getPath("/opt/vespa/logs/vespa.log-2021-02-12");
    private static final Path zkLogPath0 = fileSystem.getPath("/opt/vespa/logs/zookeeper.configserver.0.log");
    private static final Path zkLogPath1 = fileSystem.getPath("/opt/vespa/logs/zookeeper.configserver.1.log");

    @Test
    void access_logs() {
        assertForLogFile(accessLogPath1, null, null, true);
        assertForLogFile(accessLogPath1, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/access.log.20210211.zst", ZSTD, false);

        assertForLogFile(accessLogPath2, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/access.log.20210212.zst", NONE, true);
        assertForLogFile(accessLogPath2, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/access.log.20210212.zst", NONE, false);

        assertForLogFile(accessLogPath3, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/access-json.log.20210213.zst", NONE, true);
        assertForLogFile(accessLogPath3, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/access-json.log.20210213.zst", NONE, false);

        assertForLogFile(accessLogPath4, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/JsonAccessLog.20210214.zst", NONE, true);
        assertForLogFile(accessLogPath4, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/JsonAccessLog.20210214.zst", NONE, false);

        assertForLogFile(accessLogPath5, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/JsonAccessLog.container.20210214.zst", NONE, true);
        assertForLogFile(accessLogPath5, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/JsonAccessLog.container.20210214.zst", NONE, false);

        assertEquals(Optional.empty(), SyncFileInfo.forLogFile(nodeArchiveUri, accessLogPath6, true, ApplicationId.defaultId()));
        assertEquals(Optional.empty(), SyncFileInfo.forLogFile(nodeArchiveUri, accessLogPath6, false, ApplicationId.defaultId()));
    }

    @Test
    void connection_logs() {
        assertForLogFile(connectionLogPath1, null, null, true);
        assertForLogFile(connectionLogPath1, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/connection/ConnectionLog.20210210.zst", ZSTD, false);

        assertForLogFile(connectionLogPath2, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/connection/ConnectionLog.20210212.zst", NONE, true);
        assertForLogFile(connectionLogPath2, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/connection/ConnectionLog.20210212.zst", NONE, false);

        assertEquals(Optional.empty(), SyncFileInfo.forLogFile(nodeArchiveUri, connectionLogPath3, true, ApplicationId.defaultId()));
        assertEquals(Optional.empty(), SyncFileInfo.forLogFile(nodeArchiveUri, connectionLogPath3, false, ApplicationId.defaultId()));
    }

    @Test
    void vespa_logs() {
        new UnixPath(vespaLogPath1).createParents().createNewFile().setLastModifiedTime(Instant.parse("2022-05-09T14:22:11Z"));
        assertForLogFile(vespaLogPath1, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/vespa/vespa.log.zst", ZSTD, Duration.ofHours(1), true);
        assertForLogFile(vespaLogPath1, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/vespa/vespa.log-2022-05-09.14-22-11.zst", ZSTD, Duration.ZERO, false);

        assertForLogFile(vespaLogPath2, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/vespa/vespa.log-2021-02-12.zst", ZSTD, true);
        assertForLogFile(vespaLogPath2, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/vespa/vespa.log-2021-02-12.zst", ZSTD, false);
    }

    @Test
    void zookeeper_logs() {
        new UnixPath(zkLogPath0).createParents().createNewFile().setLastModifiedTime(Instant.parse("2022-05-13T13:13:45Z"));
        assertForLogFile(zkLogPath0, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/zookeeper/zookeeper.log.zst", ZSTD, Duration.ofHours(1), true);
        assertForLogFile(zkLogPath0, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/zookeeper/zookeeper.log-2022-05-13.13-13-45.zst", ZSTD, Duration.ZERO, false);

        new UnixPath(zkLogPath1).createParents().createNewFile().setLastModifiedTime(Instant.parse("2022-05-09T14:22:11Z"));
        assertForLogFile(zkLogPath1, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/zookeeper/zookeeper.log-2022-05-09.14-22-11.zst", ZSTD, true);
        assertForLogFile(zkLogPath1, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/zookeeper/zookeeper.log-2022-05-09.14-22-11.zst", ZSTD, false);
    }

    private static void assertForLogFile(Path srcPath, String destination, SyncFileInfo.Compression compression, boolean rotatedOnly) {
        assertForLogFile(srcPath, destination, compression, null, rotatedOnly);
    }

    private static void assertForLogFile(Path srcPath, String destination, SyncFileInfo.Compression compression, Duration minDurationBetweenSync, boolean rotatedOnly) {
        Optional<SyncFileInfo> sfi = SyncFileInfo.forLogFile(nodeArchiveUri, srcPath, rotatedOnly, ApplicationId.defaultId());
        assertEquals(destination, sfi.map(SyncFileInfo::destination).map(URI::toString).orElse(null));
        assertEquals(compression, sfi.map(SyncFileInfo::uploadCompression).orElse(null));
        assertEquals(minDurationBetweenSync, sfi.flatMap(SyncFileInfo::minDurationBetweenSync).orElse(null));
    }

}
