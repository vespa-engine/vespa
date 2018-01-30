// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.core.BucketspacesConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.DocType;
import com.yahoo.vespa.model.content.utils.SearchDefinitionBuilder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createCluster;
import static com.yahoo.vespa.model.content.utils.SearchDefinitionBuilder.createSearchDefinitions;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

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
        return createCluster(new ContentClusterBuilder().docTypes("foo", "bar").getXml(),
                createSearchDefinitions("foo", "bar"));
    }

    private static ContentCluster createClusterWithGlobalType() throws Exception {
        return createCluster(createClusterBuilderWithGlobalType().getXml(),
                createSearchDefinitions("global", "regular"));
    }

    private static ContentCluster createClusterWithMultipleBucketSpacesEnabled() throws Exception {
        ContentClusterBuilder builder = createClusterBuilderWithGlobalType();
        builder.groupXml(joinLines("<group>",
                "<node distribution-key='0' hostalias='mockhost'/>",
                "<node distribution-key='1' hostalias='mockhost'/>",
                "</group>"));
        builder.enableMultipleBucketSpaces(true);
        String clusterXml = builder.getXml();
        return createCluster(clusterXml, createSearchDefinitions("global", "regular"));
    }

    private static ContentClusterBuilder createClusterBuilderWithGlobalType() {
        return new ContentClusterBuilder()
                .docTypes(Arrays.asList(DocType.indexGlobal("global"), DocType.index("regular")));
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

    @Test
    public void requireThatGloballyDistributedDocumentTypeIsTaggedAsSuch() throws Exception {
        ProtonConfig cfg = getProtonConfig(createClusterWithGlobalType());
        assertEquals(2, cfg.documentdb().size());
        assertDocumentDb("global", true, cfg.documentdb(0));
        assertDocumentDb("regular", false, cfg.documentdb(1));
    }

    private static void assertDocumentDb(String expName, boolean expGlobal, ProtonConfig.Documentdb db) {
        assertEquals(expName, db.inputdoctypename());
        assertEquals(expGlobal, db.global());
    }

    @Test
    public void require_that_document_types_with_references_are_topologically_sorted() throws Exception {
        ProtonConfig cfg = getProtonConfig(createClusterWithThreeDocumentTypes());
        assertEquals(3, cfg.documentdb().size());
        assertDocumentDb("c", true, cfg.documentdb(0));
        assertDocumentDb("b", true, cfg.documentdb(1));
        assertDocumentDb("a", false, cfg.documentdb(2));
    }

    private static ContentCluster createClusterWithThreeDocumentTypes() throws Exception {
        List<String> searchDefinitions = new ArrayList<>();
        searchDefinitions.add(new SearchDefinitionBuilder().name("a")
                .content(joinLines("field ref_to_b type reference<b> { indexing: attribute }",
                                   "field ref_to_c type reference<c> { indexing: attribute }")).build());
        searchDefinitions.add(new SearchDefinitionBuilder().name("b")
                .content("field ref_to_c type reference<c> { indexing: attribute }").build());
        searchDefinitions.add(new SearchDefinitionBuilder().name("c").build());
        return createCluster(new ContentClusterBuilder().docTypes(Arrays.asList(
                DocType.index("a"),
                DocType.indexGlobal("b"),
                DocType.indexGlobal("c"))).getXml(),
                searchDefinitions);
    }

    private static BucketspacesConfig getBucketspacesConfig(ContentCluster cluster) {
        BucketspacesConfig.Builder builder = new BucketspacesConfig.Builder();
        cluster.getConfig(builder);
        return new BucketspacesConfig(builder);
    }

    private static void assertDocumentType(String expName, String expBucketSpace, BucketspacesConfig.Documenttype docType) {
        assertEquals(expName, docType.name());
        assertEquals(expBucketSpace, docType.bucketspace());
    }

    @Test
    public void require_that_all_document_types_belong_to_default_bucket_space_by_default() throws Exception {
        BucketspacesConfig config = getBucketspacesConfig(createClusterWithGlobalType());
        assertEquals(2, config.documenttype().size());
        assertDocumentType("global", "default", config.documenttype(0));
        assertDocumentType("regular", "default", config.documenttype(1));
        // Safeguard against flipping the switch
        assertFalse(config.enable_multiple_bucket_spaces());
    }

    @Test
    public void require_that_multiple_bucket_spaces_can_be_enabled() throws Exception {
        BucketspacesConfig config = getBucketspacesConfig(createClusterWithMultipleBucketSpacesEnabled());
        assertEquals(2, config.documenttype().size());
        assertDocumentType("global", "global", config.documenttype(0));
        assertDocumentType("regular", "default", config.documenttype(1));
        assertTrue(config.enable_multiple_bucket_spaces());
    }

}
