// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import junit.framework.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createCluster;
import static junit.framework.TestCase.assertEquals;

/**
 * Unit tests for content search cluster.
 *
 * @author geirst
 */
public class ContentSearchClusterTest {

    private static double EPSILON = 0.000001;

    private static ContentCluster createClusterWithOneDocumentType() throws Exception {
        return createCluster(new ContentClusterBuilder().getXml());
    }

    private static ContentCluster createClusterWithTwoDocumentType() throws Exception {
        List<String> docTypes = Arrays.asList("foo", "bar");
        return createCluster(new ContentClusterBuilder().docTypes(docTypes).getXml(),
                ApplicationPackageUtils.generateSearchDefinitions(docTypes));
    }

    private static ProtonConfig getProtonConfig(ContentCluster cluster) {
        ProtonConfig.Builder protonCfgBuilder = new ProtonConfig.Builder();
        cluster.getSearch().getConfig(protonCfgBuilder);
        return new ProtonConfig(protonCfgBuilder);
    }

    private static void assertProtonResourceLimits(double expDiskLimit, double expMemoryLimits, String clusterXml) throws Exception {
        ProtonConfig cfg = getProtonConfig(createCluster(clusterXml));
        assertEquals(expDiskLimit, cfg.writefilter().disklimit(), EPSILON);
        assertEquals(expMemoryLimits, cfg.writefilter().memorylimit(), EPSILON);
    }

    @Test
    public void requireThatProtonInitializeThreadsIsSet() throws Exception {
        assertEquals(2, getProtonConfig(createClusterWithOneDocumentType()).initialize().threads());
        assertEquals(3, getProtonConfig(createClusterWithTwoDocumentType()).initialize().threads());
    }

    @Test
    public void requireThatProtonResourceLimitsCanBeSet() throws Exception {
        assertProtonResourceLimits(0.88, 0.77,
                new ContentClusterBuilder().protonDiskLimit(0.88).protonMemoryLimit(0.77).getXml());
    }

    @Test
    public void requireThatOnlyDiskLimitCanBeSet() throws Exception {
        assertProtonResourceLimits(0.88, 0.8,
                new ContentClusterBuilder().protonDiskLimit(0.88).getXml());
    }

    @Test
    public void requireThatOnlyMemoryLimitCanBeSet() throws Exception {
        assertProtonResourceLimits(0.8, 0.77,
                new ContentClusterBuilder().protonMemoryLimit(0.77).getXml());
    }

}
