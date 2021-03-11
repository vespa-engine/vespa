// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.model.search.NodeResourcesTuning.reservedMemoryGb;
import static org.junit.Assert.assertEquals;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.MB;
import static com.yahoo.vespa.model.search.NodeResourcesTuning.GB;

/**
 * @author geirst
 */
public class NodeResourcesTuningTest {

    private static final double delta = 0.00001;
    private static final double combinedFactor = 1 - 17.0/100;

    @Test
    public void require_that_hwinfo_disk_size_is_set() {
        ProtonConfig cfg = configFromDiskSetting(100);
        assertEquals(100 * GB, cfg.hwinfo().disk().size());
    }

    @Test
    public void require_that_hwinfo_memory_size_is_set() {
        assertEquals(24 * GB, configFromMemorySetting(24 + reservedMemoryGb, false).hwinfo().memory().size());
        assertEquals(combinedFactor * 24 * GB, configFromMemorySetting(24 + reservedMemoryGb, true).hwinfo().memory().size(), 1000);
    }

    @Test
    public void reserved_memory_on_content_node_is_0_5_gb() {
        assertEquals(0.5, reservedMemoryGb, delta);
    }

    private ProtonConfig getProtonMemoryConfig(List<Pair<String, String>> sdAndMode, double gb, int redundancy, int searchableCopies) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        for (Pair<String, String> sdMode : sdAndMode) {
            builder.documentdb.add(new ProtonConfig.Documentdb.Builder()
                                   .inputdoctypename(sdMode.getFirst())
                                   .configid("some/config/id/" + sdMode.getFirst())
                                   .mode(ProtonConfig.Documentdb.Mode.Enum.valueOf(sdMode.getSecond())));
        }
        return configFromMemorySetting(gb, builder);
    }

    private void verify_that_initial_numdocs_is_dependent_of_mode(int redundancy, int searchablecopies) {
        ProtonConfig cfg = getProtonMemoryConfig(Arrays.asList(new Pair<>("a", "INDEX"), new Pair<>("b", "STREAMING"), new Pair<>("c", "STORE_ONLY")), 24 + reservedMemoryGb, redundancy, searchablecopies);
        assertEquals(3, cfg.documentdb().size());
        assertEquals(1024, cfg.documentdb(0).allocation().initialnumdocs());
        assertEquals("a", cfg.documentdb(0).inputdoctypename());
        assertEquals(24 * GB / 46, cfg.documentdb(1).allocation().initialnumdocs());
        assertEquals("b", cfg.documentdb(1).inputdoctypename());
        assertEquals(24 * GB / 46, cfg.documentdb(2).allocation().initialnumdocs());
        assertEquals("c", cfg.documentdb(2).inputdoctypename());
    }

    @Test
    public void require_that_initial_numdocs_is_dependent_of_mode_and_searchablecopies() {
        verify_that_initial_numdocs_is_dependent_of_mode(2,0);
        verify_that_initial_numdocs_is_dependent_of_mode(1,1);
        verify_that_initial_numdocs_is_dependent_of_mode(3, 2);
        verify_that_initial_numdocs_is_dependent_of_mode(3, 3);
    }

    @Test
    public void require_that_hwinfo_cpu_cores_is_set() {
        ProtonConfig cfg = configFromNumCoresSetting(24);
        assertEquals(24, cfg.hwinfo().cpu().cores());
    }

    @Test
    public void require_that_num_search_threads_and_summary_threads_follow_cores() {
        ProtonConfig cfg = configFromNumCoresSetting(4.5);
        assertEquals(5, cfg.numsearcherthreads());
        assertEquals(5, cfg.numsummarythreads());
        assertEquals(1, cfg.numthreadspersearch());
    }

    @Test
    public void require_that_num_search_threads_and_considers_explict_num_threads_per_search() {
        ProtonConfig cfg = configFromNumCoresSetting(4.5, 3);
        assertEquals(15, cfg.numsearcherthreads());
        assertEquals(5, cfg.numsummarythreads());
        assertEquals(3, cfg.numthreadspersearch());
    }

    @Test
    public void require_that_fast_disk_is_reflected_in_proton_config() {
        ProtonConfig cfg = configFromDiskSetting(true);
        assertEquals(200, cfg.hwinfo().disk().writespeed(), delta);
        assertEquals(100, cfg.hwinfo().disk().slowwritespeedlimit(), delta);
    }

    @Test
    public void require_that_slow_disk_is_reflected_in_proton_config() {
        ProtonConfig cfg = configFromDiskSetting(false);
        assertEquals(40, cfg.hwinfo().disk().writespeed(), delta);
        assertEquals(100, cfg.hwinfo().disk().slowwritespeedlimit(), delta);
    }

    @Test
    public void require_that_document_store_maxfilesize_is_set_based_on_available_memory() {
        assertDocumentStoreMaxFileSize(256 * MB, 4);
        assertDocumentStoreMaxFileSize(256 * MB, 6);
        assertDocumentStoreMaxFileSize(256 * MB, 8);
        assertDocumentStoreMaxFileSize(256 * MB, 12);
        assertDocumentStoreMaxFileSize(512 * MB, 16);
        assertDocumentStoreMaxFileSize(1 * GB, 24);
        assertDocumentStoreMaxFileSize(1 * GB, 32);
        assertDocumentStoreMaxFileSize(1 * GB, 48);
        assertDocumentStoreMaxFileSize(1 * GB, 64);
        assertDocumentStoreMaxFileSize(4 * GB, 128);
        assertDocumentStoreMaxFileSize(4 * GB, 256);
        assertDocumentStoreMaxFileSize(4 * GB, 512);
    }

    @Test
    public void require_that_flush_strategy_memory_limits_are_set_based_on_available_memory() {
        assertFlushStrategyMemory(512 * MB, 4);
        assertFlushStrategyMemory(1 * GB, 8);
        assertFlushStrategyMemory(3 * GB, 24);
        assertFlushStrategyMemory(8 * GB, 64);
    }

    @Test
    public void require_that_flush_strategy_tls_size_is_set_based_on_available_disk() {
        assertFlushStrategyTlsSize(7 * GB, 100);
        assertFlushStrategyTlsSize(35 * GB, 500);
        assertFlushStrategyTlsSize(84 * GB, 1200);
        assertFlushStrategyTlsSize(100 * GB, 1720);
        assertFlushStrategyTlsSize(100 * GB, 24000);
    }

    @Test
    public void require_that_summary_read_io_is_set_based_on_disk() {
        assertSummaryReadIo(ProtonConfig.Summary.Read.Io.DIRECTIO, true);
        assertSummaryReadIo(ProtonConfig.Summary.Read.Io.MMAP, false);
    }

    @Test
    public void require_that_search_read_mmap_advise_is_set_based_on_disk() {
        assertSearchReadAdvise(ProtonConfig.Search.Mmap.Advise.RANDOM, true);
        assertSearchReadAdvise(ProtonConfig.Search.Mmap.Advise.NORMAL, false);
    }

    @Test
    public void require_that_summary_cache_max_bytes_is_set_based_on_memory() {
        assertEquals(1*GB / 20, configFromMemorySetting(1 + reservedMemoryGb, false).summary().cache().maxbytes());
        assertEquals(256*GB / 20, configFromMemorySetting(256 + reservedMemoryGb, false).summary().cache().maxbytes());
    }

    @Test
    public void require_that_summary_cache_memory_is_reduced_with_combined_cluster() {
        assertEquals(combinedFactor * 1*GB / 20, configFromMemorySetting(1 + reservedMemoryGb, true).summary().cache().maxbytes(), 1000);
        assertEquals(combinedFactor * 256*GB / 20, configFromMemorySetting(256 + reservedMemoryGb, true).summary().cache().maxbytes(), 1000);
    }

    @Test
    public void require_that_docker_node_is_tagged_with_shared_disk() {
        assertSharedDisk(true, true);
    }

    private static void assertDocumentStoreMaxFileSize(long expFileSizeBytes, int wantedMemoryGb) {
        assertEquals(expFileSizeBytes, configFromMemorySetting(wantedMemoryGb + reservedMemoryGb, false).summary().log().maxfilesize());
    }

    private static void assertFlushStrategyMemory(long expMemoryBytes, int wantedMemoryGb) {
        assertEquals(expMemoryBytes, configFromMemorySetting(wantedMemoryGb + reservedMemoryGb, false).flush().memory().maxmemory());
        assertEquals(expMemoryBytes, configFromMemorySetting(wantedMemoryGb + reservedMemoryGb, false).flush().memory().each().maxmemory());
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

    private static void assertWriteFilter(double expMemoryLimit, int memoryGb) {
        assertEquals(expMemoryLimit, configFromMemorySetting(memoryGb, false).writefilter().memorylimit(), delta);
    }

    private static ProtonConfig configFromDiskSetting(boolean fastDisk) {
        return getConfig(new FlavorsConfig.Flavor.Builder().fastDisk(fastDisk), false);
    }

    private static ProtonConfig configFromDiskSetting(int diskGb) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minDiskAvailableGb(diskGb), false);
    }

    private static ProtonConfig configFromMemorySetting(double memoryGb, boolean combined) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minMainMemoryAvailableGb(memoryGb), combined);
    }

    private static ProtonConfig configFromMemorySetting(double memoryGb, ProtonConfig.Builder builder) {
        return getConfig(new FlavorsConfig.Flavor.Builder()
                                 .minMainMemoryAvailableGb(memoryGb), builder, false);
    }

    private static ProtonConfig configFromNumCoresSetting(double numCores) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minCpuCores(numCores), false);
    }

    private static ProtonConfig configFromNumCoresSetting(double numCores, int numThreadsPerSearch) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minCpuCores(numCores),
                         new ProtonConfig.Builder(), numThreadsPerSearch, false);
    }

    private static ProtonConfig configFromEnvironmentType(boolean docker) {
        String environment = (docker ? "DOCKER_CONTAINER" : "undefined");
        return getConfig(new FlavorsConfig.Flavor.Builder().environment(environment), false);
    }

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, boolean combined) {
        return getConfig(flavorBuilder, new ProtonConfig.Builder(), combined);
    }

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, ProtonConfig.Builder protonBuilder, boolean combined) {
        flavorBuilder.name("my_flavor");
        NodeResourcesTuning tuning = new NodeResourcesTuning(new Flavor(new FlavorsConfig.Flavor(flavorBuilder)).resources(), 1, combined);
        tuning.getConfig(protonBuilder);
        return new ProtonConfig(protonBuilder);
    }

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, ProtonConfig.Builder protonBuilder,
                                          int numThreadsPerSearch, boolean combined) {
        flavorBuilder.name("my_flavor");
        NodeResourcesTuning tuning = new NodeResourcesTuning(new Flavor(new FlavorsConfig.Flavor(flavorBuilder)).resources(), numThreadsPerSearch, combined);
        tuning.getConfig(protonBuilder);
        return new ProtonConfig(protonBuilder);
    }

}
