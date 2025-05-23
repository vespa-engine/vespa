// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>

using std::string;
using namespace document::config_builder;
using namespace document;

namespace {

const string type_name = "test";
const int32_t doc_type_id = 787121340;
const string header_name = type_name + ".header";
const string body_name = type_name + ".body";

std::shared_ptr<const DocumenttypesConfig>
makeDocumentTypesConfig(const string &field_name)
{
    document::config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name,
                     Struct(header_name),
                     Struct(body_name).addField(field_name,
                                                DataType::T_STRING));
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
