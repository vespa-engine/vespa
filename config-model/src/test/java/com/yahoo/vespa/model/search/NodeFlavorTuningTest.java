// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static com.yahoo.vespa.model.search.NodeFlavorTuning.MB;
import static com.yahoo.vespa.model.search.NodeFlavorTuning.GB;

/**
 * @author geirst
 */
public class NodeFlavorTuningTest {

    private static double delta = 0.00001;

    @Test
    public void require_that_hwinfo_disk_size_is_set() {
        ProtonConfig cfg = configFromDiskSetting(100);
        assertEquals(100 * GB, cfg.hwinfo().disk().size());
    }

    @Test
    public void require_that_hwinfo_memory_size_is_set() {
        ProtonConfig cfg = configFromMemorySetting(24);
        assertEquals(24 * GB, cfg.hwinfo().memory().size());
    }

    private ProtonConfig getProtonMemoryConfig(List<Pair<String, String>> sdAndMode, int gb, int redundancy, int searchableCopies) {
        ProtonConfig.Builder builder = new ProtonConfig.Builder();
        for (Pair<String, String> sdMode : sdAndMode) {
            builder.documentdb.add(new ProtonConfig.Documentdb.Builder()
                                   .inputdoctypename(sdMode.getFirst())
                                   .configid("some/config/id/" + sdMode.getFirst())
                                   .mode(ProtonConfig.Documentdb.Mode.Enum.valueOf(sdMode.getSecond())));
        }
        return configFromMemorySetting(gb, builder, redundancy, searchableCopies);
    }
    private void verify_that_initial_numdocs_is_dependent_of_mode(int redundancy, int searchablecopies) {
        int divisor = Math.max(redundancy, searchablecopies);
        ProtonConfig cfg = getProtonMemoryConfig(Arrays.asList(new Pair<>("a", "INDEX"), new Pair<>("b", "STREAMING"), new Pair<>("c", "STORE_ONLY")), 24, redundancy, searchablecopies);
        assertEquals(3, cfg.documentdb().size());
        assertEquals(1024, cfg.documentdb(0).allocation().initialnumdocs());
        assertEquals("a", cfg.documentdb(0).inputdoctypename());
        assertEquals(402653184/divisor, cfg.documentdb(1).allocation().initialnumdocs());
        assertEquals("b", cfg.documentdb(1).inputdoctypename());
        assertEquals(402653184/divisor, cfg.documentdb(2).allocation().initialnumdocs());
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
        assertEquals(1*GB/20, configFromMemorySetting(1).summary().cache().maxbytes());
        assertEquals(256*GB/20, configFromMemorySetting(256).summary().cache().maxbytes());
    }

    @Test
    public void require_that_docker_node_is_tagged_with_shared_disk() {
        assertSharedDisk(true, true);
        assertSharedDisk(false, false);
    }

    @Test
    public void require_that_write_filter_memory_limit_is_scaled() {
        assertWriteFilter(0.7, 8);
        assertWriteFilter(0.75, 16);
        assertWriteFilter(0.775, 32);
        assertWriteFilter(0.7875, 64);
    }

    private static void assertDocumentStoreMaxFileSize(long expFileSizeBytes, int memoryGb) {
        assertEquals(expFileSizeBytes, configFromMemorySetting(memoryGb).summary().log().maxfilesize());
    }

    private static void assertFlushStrategyMemory(long expMemoryBytes, int memoryGb) {
        assertEquals(expMemoryBytes, configFromMemorySetting(memoryGb).flush().memory().maxmemory());
        assertEquals(expMemoryBytes, configFromMemorySetting(memoryGb).flush().memory().each().maxmemory());
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
        assertEquals(expMemoryLimit, configFromMemorySetting(memoryGb).writefilter().memorylimit(), delta);
    }

    private static ProtonConfig configFromDiskSetting(boolean fastDisk) {
        return getConfig(new FlavorsConfig.Flavor.Builder().
                fastDisk(fastDisk));
    }

    private static ProtonConfig configFromDiskSetting(int diskGb) {
        return getConfig(new FlavorsConfig.Flavor.Builder().
                minDiskAvailableGb(diskGb));
    }

    private static ProtonConfig configFromMemorySetting(int memoryGb) {
        return getConfig(new FlavorsConfig.Flavor.Builder().
                minMainMemoryAvailableGb(memoryGb));
    }
    private static ProtonConfig configFromMemorySetting(int memoryGb, ProtonConfig.Builder builder, int redundancy, int searchableCopies) {
        return getConfig(new FlavorsConfig.Flavor.Builder().
                minMainMemoryAvailableGb(memoryGb), builder, redundancy, searchableCopies);
    }

    private static ProtonConfig configFromNumCoresSetting(double numCores) {
        return getConfig(new FlavorsConfig.Flavor.Builder().minCpuCores(numCores));
    }

    private static ProtonConfig configFromEnvironmentType(boolean docker) {
        String environment = (docker ? "DOCKER_CONTAINER" : "undefined");
        return getConfig(new FlavorsConfig.Flavor.Builder().environment(environment));
    }

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder) {
        return getConfig(flavorBuilder, new ProtonConfig.Builder());
    }
    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, ProtonConfig.Builder protonBuilder) {
        return getConfig(flavorBuilder, protonBuilder, 1,1);
    }
    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder, ProtonConfig.Builder protonBuilder, int redundancy, int searchableCopies) {
        flavorBuilder.name("my_flavor");
        NodeFlavorTuning tuning = new NodeFlavorTuning(new Flavor(new FlavorsConfig.Flavor(flavorBuilder)), redundancy, searchableCopies);
        tuning.getConfig(protonBuilder);
        return new ProtonConfig(protonBuilder);
    }

}
