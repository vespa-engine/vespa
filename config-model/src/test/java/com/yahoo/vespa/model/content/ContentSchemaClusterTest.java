// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.searchlib.TranslogserverConfig;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.core.BucketspacesConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.DocType;
import com.yahoo.vespa.model.content.utils.SchemaBuilder;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.vespa.model.content.utils.ContentClusterUtils.createCluster;
import static com.yahoo.vespa.model.content.utils.SchemaBuilder.createSchemas;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for content search cluster.
 *
 * @author geirst
 */
public class ContentSchemaClusterTest {

    private static final double EPSILON = 0.000001;

    private static ContentCluster createClusterWithOneDocumentType() throws Exception {
        return createCluster(new ContentClusterBuilder().getXml());
    }

    private static ContentCluster createClusterWithTwoDocumentType() throws Exception {
        return createCluster(new ContentClusterBuilder().docTypes("foo", "bar").getXml(),
                             createSchemas("foo", "bar"));
    }

    private static ContentCluster createClusterWithGlobalType() throws Exception {
        return createClusterFromBuilderAndDocTypes(createClusterBuilderWithGlobalType(), "global", "regular");
    }

    private static ContentCluster createClusterWithoutGlobalType() throws Exception {
        return createClusterFromBuilderAndDocTypes(createClusterBuilderWithOnlyDefaultTypes(), "marve", "fleksnes");
    }

    private static ContentCluster createClusterFromBuilderAndDocTypes(ContentClusterBuilder builder, String... docTypes) throws Exception {
        builder.groupXml(joinLines("<group>",
                "<node distribution-key='0' hostalias='mockhost'/>",
                "<node distribution-key='1' hostalias='mockhost'/>",
                "</group>"));
        String clusterXml = builder.getXml();
        return createCluster(clusterXml, createSchemas(docTypes));
    }

    private static ContentClusterBuilder createClusterBuilderWithGlobalType() {
        return new ContentClusterBuilder()
                .docTypes(Arrays.asList(DocType.indexGlobal("global"), DocType.index("regular")));
    }

    private static ContentClusterBuilder createClusterBuilderWithOnlyDefaultTypes() {
        return new ContentClusterBuilder()
                .docTypes(Arrays.asList(DocType.index("marve"), DocType.index("fleksnes")));
    }

    private static ProtonConfig getProtonConfig(ContentCluster cluster) {
        var builder = new ProtonConfig.Builder();
        cluster.getSearch().getConfig(builder);
        return new ProtonConfig(builder);
    }

    private static void assertProtonResourceLimits(double expDiskLimit, double expMemoryLimit, String clusterXml) throws Exception {
        assertProtonResourceLimits(expDiskLimit, expMemoryLimit, createCluster(clusterXml));
    }

    private static void assertProtonResourceLimits(double expDiskLimit, double expMemoryLimit, ContentCluster cluster) {
        var cfg = getProtonConfig(cluster);
        assertEquals(expDiskLimit, cfg.writefilter().disklimit(), EPSILON);
        assertEquals(expMemoryLimit, cfg.writefilter().memorylimit(), EPSILON);
    }

    private static void assertClusterControllerResourceLimits(double expDiskLimit, double expMemoryLimit, String clusterXml) throws Exception {
        assertClusterControllerResourceLimits(expDiskLimit, expMemoryLimit, createCluster(clusterXml));
    }

    private static void assertClusterControllerResourceLimits(double expDiskLimit, double expMemoryLimit, ContentCluster cluster) {
        var limits = getFleetcontrollerConfig(cluster).cluster_feed_block_limit();
        assertEquals(3, limits.size());
        assertEquals(expDiskLimit, limits.get("disk"), EPSILON);
        assertEquals(expMemoryLimit, limits.get("memory"), EPSILON);
    }

    @Test
    void requireThatProtonInitializeThreadsIsSet() throws Exception {
        assertEquals(2, getProtonConfig(createClusterWithOneDocumentType()).initialize().threads());
        assertEquals(3, getProtonConfig(createClusterWithTwoDocumentType()).initialize().threads());
    }

    @Test
    void requireThatProtonResourceLimitsCanBeSet() throws Exception {
        assertProtonResourceLimits(0.88, 0.77,
                new ContentClusterBuilder().protonDiskLimit(0.88).protonMemoryLimit(0.77).getXml());
    }

    @Test
    void requireThatOnlyDiskLimitCanBeSet() throws Exception {
        assertProtonResourceLimits(0.88, 0.9,
                new ContentClusterBuilder().protonDiskLimit(0.88).getXml());
    }

    @Test
    void requireThatOnlyMemoryLimitCanBeSet() throws Exception {
        assertProtonResourceLimits(0.9, 0.77,
                new ContentClusterBuilder().protonMemoryLimit(0.77).getXml());
    }

    @Test
    void cluster_controller_resource_limits_can_be_set() throws Exception {
        assertClusterControllerResourceLimits(0.92, 0.93,
                new ContentClusterBuilder().clusterControllerDiskLimit(0.92).clusterControllerMemoryLimit(0.93).getXml());
    }

    @Test
    void resource_limits_are_derived_from_the_other_if_not_specified() throws Exception {
        var cluster = createCluster(new ContentClusterBuilder().clusterControllerDiskLimit(0.5).protonMemoryLimit(0.95).getXml());
        assertProtonResourceLimits(0.8, 0.95, cluster);
        assertClusterControllerResourceLimits(0.5, 0.94, cluster);
    }

    @Test
    void default_resource_limits_with_feed_block_in_distributor() throws Exception {
        var cluster = createCluster(new ContentClusterBuilder().getXml());
        assertProtonResourceLimits(0.9, 0.9, cluster);
        assertClusterControllerResourceLimits(0.75, 0.8, cluster);
    }

    @Test
    void requireThatGloballyDistributedDocumentTypeIsTaggedAsSuch() throws Exception {
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
    void require_that_document_types_with_references_are_topologically_sorted() throws Exception {
        ProtonConfig cfg = getProtonConfig(createClusterWithThreeDocumentTypes());
        assertEquals(3, cfg.documentdb().size());
        assertDocumentDb("c", true, cfg.documentdb(0));
        assertDocumentDb("b", true, cfg.documentdb(1));
        assertDocumentDb("a", false, cfg.documentdb(2));
    }

    private static ContentCluster createClusterWithThreeDocumentTypes() throws Exception {
        List<String> schemas = new ArrayList<>();
        schemas.add(new SchemaBuilder().name("a")
                                       .content(joinLines("field ref_to_b type reference<b> { indexing: attribute }",
                                                          "field ref_to_c type reference<c> { indexing: attribute }"))
                                       .build());
        schemas.add(new SchemaBuilder().name("b")
                                       .content("field ref_to_c type reference<c> { indexing: attribute }")
                                       .build());
        schemas.add(new SchemaBuilder().name("c").build());
        return createCluster(new ContentClusterBuilder().docTypes(List.of(DocType.index("a"),
                                                                          DocType.indexGlobal("b"),
                                                                          DocType.indexGlobal("c"))).getXml(),
                             schemas);
    }

    private static BucketspacesConfig getBucketspacesConfig(ContentCluster cluster) {
        BucketspacesConfig.Builder builder = new BucketspacesConfig.Builder();
        cluster.getConfig(builder);
        return new BucketspacesConfig(builder);
    }

    private static FleetcontrollerConfig getFleetcontrollerConfig(ContentCluster cluster) {
        var builder = new FleetcontrollerConfig.Builder();
        cluster.getConfig(builder);
        cluster.getClusterControllerConfig().getConfig(builder);
        builder.cluster_name("unknown");
        builder.index(0);
        builder.zookeeper_server("unknown");
        return new FleetcontrollerConfig(builder);
    }

    private static void assertDocumentType(String expName, String expBucketSpace, BucketspacesConfig.Documenttype docType) {
        assertEquals(expName, docType.name());
        assertEquals(expBucketSpace, docType.bucketspace());
    }

    @Test
    void require_that_document_types_belong_to_correct_bucket_spaces() throws Exception {
        BucketspacesConfig config = getBucketspacesConfig(createClusterWithGlobalType());
        assertEquals(2, config.documenttype().size());
        assertDocumentType("global", "global", config.documenttype(0));
        assertDocumentType("regular", "default", config.documenttype(1));
    }

    @Test
    void bucket_space_config_builder_returns_correct_mappings() throws Exception {
        ContentCluster cluster = createClusterWithGlobalType();
        BucketspacesConfig expected = getBucketspacesConfig(cluster);
        AllClustersBucketSpacesConfig.Cluster actual = cluster.clusterBucketSpaceConfigBuilder().build();
        assertEquals(2, expected.documenttype().size());
        assertEquals(expected.documenttype().size(), actual.documentType().size());
        assertNotNull(actual.documentType("global"));
        assertEquals("global", actual.documentType().get("global").bucketSpace());
        assertNotNull(actual.documentType("regular"));
        assertEquals("default", actual.documentType().get("regular").bucketSpace());
    }

    @Test
    void cluster_with_global_document_types_sets_cluster_controller_global_docs_config_option() throws Exception {
        ContentCluster cluster = createClusterWithGlobalType();
        assertTrue(getFleetcontrollerConfig(cluster).cluster_has_global_document_types());
    }

    @Test
    void cluster_without_global_document_types_unsets_cluster_controller_global_docs_config_option() throws Exception {
        ContentCluster cluster = createClusterWithoutGlobalType();
        assertFalse(getFleetcontrollerConfig(cluster).cluster_has_global_document_types());
    }

    TranslogserverConfig getTlsConfig(ContentCluster cluster) {
        TranslogserverConfig.Builder tlsBuilder = new TranslogserverConfig.Builder();
        cluster.getSearch().getSearchNodes().get(0).getConfig(tlsBuilder);
        return tlsBuilder.build();
    }

    @Test
    void fsync_is_controllable() throws Exception {
        assertTrue(getTlsConfig(createCluster(new ContentClusterBuilder().getXml())).usefsync());
        assertTrue(getTlsConfig(createCluster(new ContentClusterBuilder().syncTransactionLog(true).getXml())).usefsync());
        assertFalse(getTlsConfig(createCluster(new ContentClusterBuilder().syncTransactionLog(false).getXml())).usefsync());
    }

    @Test
    void verifyDefaultDocStoreCompression() throws Exception {
        ProtonConfig cfg = getProtonConfig(createCluster(new ContentClusterBuilder().getXml()));
        assertEquals(3, cfg.summary().log().chunk().compression().level());
        assertEquals(3, cfg.summary().log().compact().compression().level());
    }

    @Test
    void verifyDefaultDiskBloatFactor() throws Exception {
        var defaultCfg = getProtonConfig(createCluster(new ContentClusterBuilder().getXml()));
        assertEquals(0.25, defaultCfg.flush().memory().diskbloatfactor(), EPSILON);
        assertEquals(0.25, defaultCfg.flush().memory().each().diskbloatfactor(), EPSILON);
    }
}
