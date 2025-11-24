// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>

using std::string;
using namespace document::new_config_builder;
using namespace document;

namespace {

const string type_name = "test";
const int32_t doc_type_id = 787121340;

std::shared_ptr<const DocumenttypesConfig>
makeDocumentTypesConfig(const string &field_name)
{
    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    doc.addField(field_name, builder.primitiveStringType());
    return std::make_shared<const DocumenttypesConfig>(builder.config());
}

TEST(DocumentTypeRepoFactoryTest, require_that_equal_configs_gives_same_repo)
{
    auto config1 = makeDocumentTypesConfig("a");
    auto config2 = makeDocumentTypesConfig("b");
    auto config3 = std::make_shared<const DocumenttypesConfig>(*config1);
    auto config4 = std::make_shared<const DocumenttypesConfig>(*config2);
    auto repo1 = DocumentTypeRepoFactory::make(*config1);
    auto repo2 = DocumentTypeRepoFactory::make(*config2);
    auto repo3 = DocumentTypeRepoFactory::make(*config3);
    auto repo4 = DocumentTypeRepoFactory::make(*config4);
    EXPECT_NE(repo1, repo2);
    EXPECT_EQ(repo1, repo3);
    EXPECT_NE(repo1, repo4);
    EXPECT_NE(repo2, repo3);
    EXPECT_EQ(repo2, repo4);
    EXPECT_NE(repo3, repo4);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
