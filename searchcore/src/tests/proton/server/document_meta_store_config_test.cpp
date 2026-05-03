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

    static ProtonConfig make_proton_config() {
        ProtonConfigBuilder builder;
        return builder;
    }

    static ProtonConfig make_proton_config(bool store_full_document_ids) {
        ProtonConfigBuilder builder;
        builder.storeFullDocumentIds = store_full_document_ids;
        return builder;
    }

    static DocumentMetaStoreConfig make_config() { return DocumentMetaStoreConfig::make(make_proton_config()); }

    static DocumentMetaStoreConfig make_config(bool store_full_document_ids) {
        return DocumentMetaStoreConfig::make(make_proton_config(store_full_document_ids));
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
    EXPECT_EQ(false, DocumentMetaStoreConfig::make().store_full_document_ids());
    EXPECT_EQ(false, make_config().store_full_document_ids()); // From default ProtonConfig
}

TEST_F(DocumentMetaStoreConfigTest, require_that_store_full_document_ids_report_works) {
    EXPECT_EQ(true, make_config(true).store_full_document_ids());
    EXPECT_EQ(false, make_config(false).store_full_document_ids());
}

TEST_F(DocumentMetaStoreConfigTest, require_that_updating_works) {
    auto config = make_config();

    EXPECT_EQ(false, config.store_full_document_ids());
    config.update(config_true);
    EXPECT_EQ(true, config.store_full_document_ids());
    config.update(config_false);
    EXPECT_EQ(false, config.store_full_document_ids());
}

TEST_F(DocumentMetaStoreConfigTest, require_that_comparison_works) {
    EXPECT_EQ(config_false, make_config(false));
    EXPECT_NE(config_true, make_config(false));
    EXPECT_EQ(config_true, make_config(true));
    EXPECT_NE(config_false, make_config(true));
}
