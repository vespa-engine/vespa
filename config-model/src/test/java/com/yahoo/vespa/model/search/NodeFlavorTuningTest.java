package com.yahoo.vespa.model.search;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static com.yahoo.vespa.model.search.NodeFlavorTuning.MB;
import static com.yahoo.vespa.model.search.NodeFlavorTuning.GB;

/**
 * @author geirst
 */
public class NodeFlavorTuningTest {

    @Test
    public void require_that_fast_disk_is_reflected_in_proton_config() {
        ProtonConfig cfg = configFromDiskSetting(true);
        assertEquals(200, cfg.hwinfo().disk().writespeed(), 0.001);
        assertEquals(100, cfg.hwinfo().disk().slowwritespeedlimit(), 0.001);
    }

    @Test
    public void require_that_slow_disk_is_reflected_in_proton_config() {
        ProtonConfig cfg = configFromDiskSetting(false);
        assertEquals(40, cfg.hwinfo().disk().writespeed(), 0.001);
        assertEquals(100, cfg.hwinfo().disk().slowwritespeedlimit(), 0.001);
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

    private static ProtonConfig getConfig(FlavorsConfig.Flavor.Builder flavorBuilder) {
        flavorBuilder.name("my_flavor");
        NodeFlavorTuning tuning = new NodeFlavorTuning(new Flavor(new FlavorsConfig.Flavor(flavorBuilder)));
        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        tuning.getConfig(protonBuilder);
        return new ProtonConfig(protonBuilder);
    }

}
