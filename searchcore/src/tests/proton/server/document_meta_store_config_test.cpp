// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-proton.h>
#include <vespa/searchcore/proton/server/document_meta_store_config.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::DocumentMetaStoreConfig;
using ProtonConfig = vespa::config::search::core::ProtonConfig;
using ProtonConfigBuilder = vespa::config::search::core::ProtonConfigBuilder;

class DocumentMetaStoreConfigTest : public ::testing::Test {
public:
    DocumentMetaStoreConfigTest();
    ~DocumentMetaStoreConfigTest() override;

    static ProtonConfig make_default_proton_config() {
        ProtonConfigBuilder builder;
        return builder;
    }

    static ProtonConfig make_proton_config() {
        ProtonConfigBuilder builder;
        // First DocumentDB: false
        builder.documentdb.emplace_back(ProtonConfigBuilder::Documentdb());
        builder.documentdb.back().documentIdAttribute = false;
        // Second DocumentDB: true
        builder.documentdb.emplace_back(ProtonConfigBuilder::Documentdb());
        builder.documentdb.back().documentIdAttribute = true;
        // Third DocumentDB: default
        builder.documentdb.emplace_back(ProtonConfigBuilder::Documentdb());
        return builder;
    }

    static DocumentMetaStoreConfig make_default_config() {
        return DocumentMetaStoreConfig::make(make_default_proton_config(), 0);
    }

    static DocumentMetaStoreConfig make_config(bool document_id_attribute) {
        return DocumentMetaStoreConfig::make(make_proton_config(), document_id_attribute);
    }

protected:
    DocumentMetaStoreConfig config_false;
    DocumentMetaStoreConfig config_true;
};

DocumentMetaStoreConfigTest::DocumentMetaStoreConfigTest()
    : config_false(make_config(false)), config_true(make_config(true)) {
}

DocumentMetaStoreConfigTest::~DocumentMetaStoreConfigTest() = default;

TEST_F(DocumentMetaStoreConfigTest, require_that_store_full_document_ids_default_is_false) {
    // Without ProtonConfig
    EXPECT_FALSE(DocumentMetaStoreConfig::make().store_full_document_ids());
    // Default ProtonConfig
    EXPECT_FALSE(DocumentMetaStoreConfig::make(make_default_proton_config(), 0).store_full_document_ids());
    // Default from ProtonConfigBuilder::Documentdb()
    EXPECT_FALSE(DocumentMetaStoreConfig::make(make_proton_config(), 2).store_full_document_ids());
    // With present settings but invalid index
    ASSERT_LE(make_proton_config().documentdb.size(), 3);
    EXPECT_FALSE(DocumentMetaStoreConfig::make(make_proton_config(), 3).store_full_document_ids());
}

TEST_F(DocumentMetaStoreConfigTest, require_that_store_full_document_ids_report_works) {
    EXPECT_TRUE(make_config(true).store_full_document_ids());
    EXPECT_FALSE(make_config(false).store_full_document_ids());
}

TEST_F(DocumentMetaStoreConfigTest, require_that_updating_works) {
    auto config = make_default_config();

    EXPECT_FALSE(config.store_full_document_ids());
    config.update(config_true);
    EXPECT_TRUE(config.store_full_document_ids());
    config.update(config_false);
    EXPECT_FALSE(config.store_full_document_ids());
}

TEST_F(DocumentMetaStoreConfigTest, require_that_comparison_works) {
    EXPECT_EQ(config_false, make_config(false));
    EXPECT_NE(config_true, make_config(false));
    EXPECT_EQ(config_true, make_config(true));
    EXPECT_NE(config_false, make_config(true));
}
