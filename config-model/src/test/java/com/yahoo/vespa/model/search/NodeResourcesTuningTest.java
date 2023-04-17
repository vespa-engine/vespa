// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.model.search.NodeResourcesTuning.reservedMemoryGb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.MB;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.GB;

/**
 * @author geirst
 */
public class NodeResourcesTuningTest {

    private static final double delta = 0.00001;
    private static final double combinedFactor = 1 - 18.0/100;
    private static final double DEFAULT_MEMORY_GAIN = 0.08;

    @Test
    void require_that_hwinfo_disk_size_is_set() {
        ProtonConfig cfg = configFromDiskSetting(100);
        assertEquals(100 * GB, cfg.hwinfo().disk().size());
    }

    @Test
    void require_that_hwinfo_memory_size_is_set() {
        assertEquals(24 * GB, configFromMemorySetting(24 + reservedMemoryGb, 0).hwinfo().memory().size());
        assertEquals(combinedFactor * 24 * GB, configFromMemorySetting(24 + reservedMemoryGb, ApplicationContainerCluster.heapSizePercentageOfTotalNodeMemoryWhenCombinedCluster * 0.01).hwinfo().memory().size(), 1000);
    }

    @Test
    void reserved_memory_on_content_node_is_0_5_gb() {
        assertEquals(0.5, reservedMemoryGb, delta);
    }

    private ProtonConfig getProtonMemoryConfig(List<Pair<String, String>> sdAndMode, double gb) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        for (Pair<String, String> sdMode : sdAndMode) {
            builder.documentdb.add(new ProtonConfig.Documentdb.Builder()
                                   .inputdoctypename(sdMode.getFirst())
                                   .configid("some/config/id/" + sdMode.getFirst())
                                   .mode(ProtonConfig.Documentdb.Mode.Enum.valueOf(sdMode.getSecond())));
        }
        return configFromMemorySetting(gb, builder);
    }

    private void verify_that_initial_numdocs_is_dependent_of_mode() {
        ProtonConfig cfg = getProtonMemoryConfig(Arrays.asList(new Pair<>("a", "INDEX"), new Pair<>("b", "STREAMING"), new Pair<>("c", "STORE_ONLY")), 24 + reservedMemoryGb);
        assertEquals(3, cfg.documentdb().size());
        assertEquals(1024, cfg.documentdb(0).allocation().initialnumdocs());
        assertEquals("a", cfg.documentdb(0).inputdoctypename());
        assertEquals(24 * GB / 46, cfg.documentdb(1).allocation().initialnumdocs());
        assertEquals("b", cfg.documentdb(1).inputdoctypename());
        assertEquals(24 * GB / 46, cfg.documentdb(2).allocation().initialnumdocs());
        assertEquals("c", cfg.documentdb(2).inputdoctypename());
    }

    @Test
    void require_that_initial_numdocs_is_dependent_of_mode_and_searchablecopies() {
        verify_that_initial_numdocs_is_dependent_of_mode();

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
        assertDocumentStoreMaxFileSize(256 * MB, 4);
        assertDocumentStoreMaxFileSize(256 * MB, 6);
        assertDocumentStoreMaxFileSize(256 * MB, 8);
        assertDocumentStoreMaxFileSize(256 * MB, 12);
        assertDocumentStoreMaxFileSize((long) (16 * GB * 0.02), 16);
        assertDocumentStoreMaxFileSize((long) (24 * GB * 0.02), 24);
        assertDocumentStoreMaxFileSize((long) (32 * GB * 0.02), 32);
        assertDocumentStoreMaxFileSize((long) (48 * GB * 0.02), 48);
        assertDocumentStoreMaxFileSize((long) (64 * GB * 0.02), 64);
        assertDocumentStoreMaxFileSize((long) (128 * GB * 0.02), 128);
        assertDocumentStoreMaxFileSize((long) (256 * GB * 0.02), 256);
        assertDocumentStoreMaxFileSize((long) (512 * GB * 0.02), 512);
    }

    @Test
    void require_that_flush_strategy_memory_limits_are_set_based_on_available_memory() {
        assertFlushStrategyMemory((long) (4 * GB * DEFAULT_MEMORY_GAIN), 4);
        assertFlushStrategyMemory((long) (8 * GB * DEFAULT_MEMORY_GAIN), 8);
        assertFlushStrategyMemory((long) (24 * GB * DEFAULT_MEMORY_GAIN), 24);
        assertFlushStrategyMemory((long) (64 * GB * DEFAULT_MEMORY_GAIN), 64);
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
    void require_that_search_read_mmap_advise_is_set_based_on_disk() {
        assertSearchReadAdvise(ProtonConfig.Search.Mmap.Advise.RANDOM, true);
        assertSearchReadAdvise(ProtonConfig.Search.Mmap.Advise.NORMAL, false);
    }

    @Test
    void require_that_summary_cache_max_bytes_is_set_based_on_memory() {
        assertEquals(1 * GB / 25, configFromMemorySetting(1 + reservedMemoryGb, 0).summary().cache().maxbytes());
        assertEquals(256 * GB / 25, configFromMemorySetting(256 + reservedMemoryGb, 0).summary().cache().maxbytes());
    }

    @Test
    void require_that_summary_cache_memory_is_reduced_with_combined_cluster() {
        assertEquals(combinedFactor * 1 * GB / 25, configFromMemorySetting(1 + reservedMemoryGb, ApplicationContainerCluster.heapSizePercentageOfTotalNodeMemoryWhenCombinedCluster * 0.01).summary().cache().maxbytes(), 1000);
        assertEquals(combinedFactor * 256 * GB / 25, configFromMemorySetting(256 + reservedMemoryGb, ApplicationContainerCluster.heapSizePercentageOfTotalNodeMemoryWhenCombinedCluster * 0.01).summary().cache().maxbytes(), 1000);
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
        assertEquals(1, fromMemAndCpu(15, 8).flush().maxconcurrent());
        assertEquals(1, fromMemAndCpu(16, 8).flush().maxconcurrent());
        assertEquals(2, fromMemAndCpu(17, 8).flush().maxconcurrent());
        assertEquals(2, fromMemAndCpu(65, 8).flush().maxconcurrent()); // still capped by max
        assertEquals(2, fromMemAndCpu(65, 65).flush().maxconcurrent()); // still capped by max
    }

    private static void assertDocumentStoreMaxFileSize(long expFileSizeBytes, int wantedMemoryGb) {
        assertEquals(expFileSizeBytes, configFromMemorySetting(wantedMemoryGb + reservedMemoryGb, 0).summary().log().maxfilesize());
    }

    private static void assertFlushStrategyMemory(long expMemoryBytes, int wantedMemoryGb) {
        assertEquals(expMemoryBytes, configFromMemorySetting(wantedMemoryGb + reservedMemoryGb, 0).flush().memory().maxmemory());
        assertEquals(expMemoryBytes, configFromMemorySetting(wantedMemoryGb + reservedMemoryGb, 0).flush().memory().each().maxmemory());
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
        NodeResourcesTuning tuning = new NodeResourcesTuning(new Flavor(new FlavorsConfig.Flavor(flavorBuilder)).resources(), numThreadsPerSearch, fractionOfMemoryReserved);
        tuning.getConfig(protonBuilder);
        return new ProtonConfig(protonBuilder);
    }

}
