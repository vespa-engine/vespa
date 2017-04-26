package com.yahoo.vespa.model.search;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

    private static ProtonConfig configFromDiskSetting(boolean fastDisk) {
        FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder();
        flavorBuilder.fastDisk(fastDisk);
        flavorBuilder.name("my_flavor");
        NodeFlavorTuning tuning = new NodeFlavorTuning(new Flavor(new FlavorsConfig.Flavor(flavorBuilder)));
        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        tuning.getConfig(protonBuilder);
        return new ProtonConfig(protonBuilder);
    }

}
