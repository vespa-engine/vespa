// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.config.model.test.TestRoot;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class RankProfileRegistryTest {
    private static final String TESTDIR = "src/test/cfg/search/data/v2/inherited_rankprofiles";

    @Test
    public void testRankProfileInheritance() {
        TestRoot root = new TestDriver().buildModel(FilesApplicationPackage.fromFile(new File(TESTDIR)));
        RankProfilesConfig left = root.getConfig(RankProfilesConfig.class, "inherit/search/cluster.inherit/left");
        RankProfilesConfig right = root.getConfig(RankProfilesConfig.class, "inherit/search/cluster.inherit/right");
        assertThat(left.rankprofile().size(), is(3));
        assertThat(right.rankprofile().size(), is(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRankProfileDuplicateNameIsIllegal() {
        Search search = new Search("foo", null);
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(search);
        RankProfile barRankProfile = new RankProfile("bar", search, rankProfileRegistry);
        rankProfileRegistry.addRankProfile(barRankProfile);
        rankProfileRegistry.addRankProfile(barRankProfile);
    }

    @Test
    public void testRankProfileDuplicateNameLegalForOverridableRankProfiles() {
        Search search = new Search("foo", null);
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(search);

        for (String rankProfileName : RankProfileRegistry.overridableRankProfileNames) {
            assertNull(rankProfileRegistry.getRankProfile(search, rankProfileName).getMacros().get("foo"));
            RankProfile rankProfileWithAddedMacro = new RankProfile(rankProfileName, search, rankProfileRegistry);
            rankProfileWithAddedMacro.addMacro("foo", true);
            rankProfileRegistry.addRankProfile(rankProfileWithAddedMacro);
            assertNotNull(rankProfileRegistry.getRankProfile(search, rankProfileName).getMacros().get("foo"));
        }
    }

}
