// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.model.Host.memoryOverheadGb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.MiB;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.GiB;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.GB;

/**
 * @author geirst
 */
public class NodeResourcesTuningTest {

    private static final double delta = 0.00001;
    private static final double DEFAULT_MEMORY_GAIN = 0.08;

    @Test
    void require_that_hwinfo_disk_size_is_set() {
        ProtonConfig cfg = configFromDiskSetting(100);
        assertEquals(100 * GB, cfg.hwinfo().disk().size());
    }

    @Test
    void require_that_hwinfo_memory_size_is_set() {
        assertEquals(24 * GiB, configFromMemorySetting(24 + memoryOverheadGb, 0).hwinfo().memory().size());
        assertEquals(1.9585050869E10, configFromMemorySetting(24 + memoryOverheadGb, ApplicationContainerCluster.heapSizePercentageOfTotalAvailableMemoryWhenCombinedCluster * 0.01).hwinfo().memory().size(), 1000);
    }

    @Test
    void reserved_memory_on_content_node() {
        assertEquals(0.7, memoryOverheadGb, delta);
    }

    @Test
    void require_that_hwinfo_cpu_cores_is_set() {
        ProtonConfig cfg = configFromNumCoresSetting(24);
        assertEquals(24, cfg.hwinfo().cpu().cores());
    }

    @Test
    void require_that_num_search_threads_and_summary_threads_follow_cores() {
        ProtonConfig cfg = configFromNumCoresSetting(4.5);
        assertEquals(5, cfg.numsearcherthreads());
        assertEquals(5, cfg.numsummarythreads());
        assertEquals(1, cfg.numthreadspersearch());
    }

    @Test
    void require_that_num_search_threads_considers_explict_num_threads_per_search() {
        ProtonConfig cfg = configFromNumCoresSetting(4.5, 3);
        assertEquals(15, cfg.numsearcherthreads());
        assertEquals(5, cfg.numsummarythreads());
        assertEquals(3, cfg.numthreadspersearch());
    }

    @Test
    void require_that_num_search_threads_can_only_have_4x_overcommit_rounded_up_to_num_threads_per_search() {
        ProtonConfig cfg = configFromNumCoresSetting(9, 8);
        assertEquals(40, cfg.numsearcherthreads());
        assertEquals(9, cfg.numsummarythreads());
        assertEquals(8, cfg.numthreadspersearch());
    }


    @Test
    void require_that_fast_disk_is_reflected_in_proton_config() {
        ProtonConfig cfg = configFromDiskSetting(true);
        assertEquals(200, cfg.hwinfo().disk().writespeed(), delta);
        assertEquals(100, cfg.hwinfo().disk().slowwritespeedlimit(), delta);
    }

    @Test
    void require_that_slow_disk_is_reflected_in_proton_config() {
        ProtonConfig cfg = configFromDiskSetting(false);
        assertEquals(40, cfg.hwinfo().disk().writespeed(), delta);
        assertEquals(100, cfg.hwinfo().disk().slowwritespeedlimit(), delta);
    }

    @Test
    void require_that_document_store_maxfilesize_is_set_based_on_available_memory() {
        assertDocumentStoreMaxFileSize(256 * MiB, 4);
        assertDocumentStoreMaxFileSize(256 * MiB, 6);
        assertDocumentStoreMaxFileSize(256 * MiB, 8);
        assertDocumentStoreMaxFileSize(256 * MiB, 12);
        assertDocumentStoreMaxFileSize((long) (16 * GiB * 0.02), 16);
        assertDocumentStoreMaxFileSize((long) (24 * GiB * 0.02), 24);
        assertDocumentStoreMaxFileSize((long) (32 * GiB * 0.02), 32);
        assertDocumentStoreMaxFileSize((long) (48 * GiB * 0.02), 48);
        assertDocumentStoreMaxFileSize((long) (64 * GiB * 0.02), 64);
        assertDocumentStoreMaxFileSize((long) (128 * GiB * 0.02), 128);
        assertDocumentStoreMaxFileSize((long) (256 * GiB * 0.02), 256);
        assertDocumentStoreMaxFileSize((long) (512 * GiB * 0.02), 512);
    }

    @Test
    void require_that_flush_strategy_memory_limits_are_set_based_on_available_memory() {
        assertFlushStrategyMemory((long) (4 * GiB * DEFAULT_MEMORY_GAIN), 4);
        assertFlushStrategyMemory((long) (8 * GiB * DEFAULT_MEMORY_GAIN), 8);
        assertFlushStrategyMemory((long) (24 * GiB * DEFAULT_MEMORY_GAIN), 24);
        assertFlushStrategyMemory((long) (64 * GiB * DEFAULT_MEMORY_GAIN), 64);
    }

    @Test
    void require_that_flush_strategy_tls_size_is_set_based_on_available_disk() {
        assertFlushStrategyTlsSize(2 * GB, 10);
        assertFlushStrategyTlsSize(2 * GB, 100);
        assertFlushStrategyTlsSize(10 * GB, 500);
        assertFlushStrategyTlsSize(24 * GB, 1200);
        assertFlushStrategyTlsSize(100 * GB, 24000);
    }

    @Test
    void require_that_summary_read_io_is_set_based_on_disk() {
        assertSummaryReadIo(ProtonConfig.Summary.Read.Io.DIRECTIO, true);
        assertSummaryReadIo(ProtonConfig.Summary.Read.Io.MMAP, false);
    }

    @Test
    void require_that_summary_cache_max_bytes_is_set_based_on_memory() {
        assertEquals(1 * GiB / 25, configFromMemorySetting(1 + memoryOverheadGb, 0).summary().cache().maxbytes());
        assertEquals(256 * GiB / 25, configFromMemorySetting(256 + memoryOverheadGb, 0).summary().cache().maxbytes());
    }

    @Test
    void require_that_summary_cache_memory_is_reduced_with_combined_cluster() {
        assertEquals(3.2641751E7, configFromMemorySetting(1 + memoryOverheadGb, ApplicationContainerCluster.heapSizePercentageOfTotalAvailableMemoryWhenCombinedCluster * 0.01).summary().cache().maxbytes(), 1000);
        assertEquals(8.356288371E9, configFromMemorySetting(256 + memoryOverheadGb, ApplicationContainerCluster.heapSizePercentageOfTotalAvailableMemoryWhenCombinedCluster * 0.01).summary().cache().maxbytes(), 1000);
    }

    @Test
    void require_that_docker_node_is_tagged_with_shared_disk() {
        assertSharedDisk(true, true);
    }

    private static ProtonConfig fromMemAndCpu(int gb, int vcpu) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minMainMemoryAvailableGb(gb).minCpuCores(vcpu));
    }
    @Test
    public void require_that_concurrent_flush_threads_is_1_with_low_memory() {
        assertEquals(1, fromMemAndCpu(1, 8).flush().maxconcurrent());
        assertEquals(1, fromMemAndCpu(10, 8).flush().maxconcurrent());
        assertEquals(1, fromMemAndCpu(11, 8).flush().maxconcurrent());
        assertEquals(2, fromMemAndCpu(12, 8).flush().maxconcurrent());
        assertEquals(2, fromMemAndCpu(65, 8).flush().maxconcurrent()); // still capped by max
        assertEquals(2, fromMemAndCpu(65, 65).flush().maxconcurrent()); // still capped by max
    }

    private static void assertDocumentStoreMaxFileSize(long expFileSizeBytes, int wantedMemoryGb) {
        assertEquals(expFileSizeBytes, configFromMemorySetting(wantedMemoryGb + memoryOverheadGb, 0).summary().log().maxfilesize());
    }

    private static void assertFlushStrategyMemory(long expMemoryBytes, int wantedMemoryGb) {
        assertEquals(expMemoryBytes, configFromMemorySetting(wantedMemoryGb + memoryOverheadGb, 0).flush().memory().maxmemory());
        assertEquals(expMemoryBytes, configFromMemorySetting(wantedMemoryGb + memoryOverheadGb, 0).flush().memory().each().maxmemory());
    }

    private static void assertFlushStrategyTlsSize(long expTlsSizeBytes, int diskGb) {
        assertEquals(expTlsSizeBytes, configFromDiskSetting(diskGb).flush().memory().maxtlssize());
    }

    private static void assertSummaryReadIo(ProtonConfig.Summary.Read.Io.Enum expValue, boolean fastDisk) {
        assertEquals(expValue, configFromDiskSetting(fastDisk).summary().read().io());
    }

    private static void assertSearchReadAdvise(ProtonConfig.Search.Mmap.Advise.Enum expValue, boolean fastDisk) {
        assertEquals(expValue, configFromDiskSetting(fastDisk).search().mmap().advise());
    }

    private static void assertSharedDisk(boolean sharedDisk, boolean docker) {
        assertEquals(sharedDisk, configFromEnvironmentType(docker).hwinfo().disk().shared());
    }

    private static ProtonConfig configFromDiskSetting(boolean fastDisk) {
        return getConfig(new FlavorsConfig.Flavor.Builder().fastDisk(fastDisk));
    }

    private static ProtonConfig configFromDiskSetting(int diskGb) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minDiskAvailableGb(diskGb), 0);
    }

    private static ProtonConfig configFromMemorySetting(double memoryGb, double fractionOfMemoryReserved) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minMainMemoryAvailableGb(memoryGb), fractionOfMemoryReserved);
    }

    private static ProtonConfig configFromMemorySetting(double memoryGb, ProtonConfig.Builder builder) {
        return getConfig(new FlavorsConfig.Flavor.Builder()
                                 .minMainMemoryAvailableGb(memoryGb), builder);
    }

    private static ProtonConfig configFromNumCoresSetting(double numCores) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minCpuCores(numCores));
    }

    private static ProtonConfig configFromNumCoresSetting(double numCores, int numThreadsPerSearch) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minCpuCores(numCores),
                         new ProtonConfig.Builder(), numThreadsPerSearch);
    }

    private static ProtonConfig configFromEnvironmentType(boolean docker) {
        String environment = (docker ? "DOCKER_CONTAINER" : "undefined");
        return getConfig(new FlavorsConfig.Flavor.Builder().environment(environment));
    }

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder) {
        return getConfig(flavorBuilder, new ProtonConfig.Builder());
    }

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, double fractionOfMemoryReserved) {
        return getConfig(flavorBuilder, new ProtonConfig.Builder(), fractionOfMemoryReserved);
    }

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, ProtonConfig.Builder protonBuilder) {
        return getConfig(flavorBuilder, protonBuilder,1);
    }
    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, ProtonConfig.Builder protonBuilder, double fractionOfMemoryReserved) {
        return getConfig(flavorBuilder, protonBuilder, 1, fractionOfMemoryReserved);
    }

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, ProtonConfig.Builder protonBuilder,
                                          int numThreadsPerSearch) {
        return getConfig(flavorBuilder, protonBuilder, numThreadsPerSearch, 0);
    }

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, ProtonConfig.Builder protonBuilder,
                                          int numThreadsPerSearch, double fractionOfMemoryReserved) {
        flavorBuilder.name("my_flavor");
        NodeResourcesTuning tuning = new NodeResourcesTuning(new Flavor(new FlavorsConfig.Flavor(flavorBuilder)).resources(),
                numThreadsPerSearch, fractionOfMemoryReserved);
        tuning.getConfig(protonBuilder);
        return new ProtonConfig(protonBuilder);
    }

}
