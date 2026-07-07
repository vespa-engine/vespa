// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/searchcore/proton/common/field_path_target.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vector>

using document::DocumentTypeRepo;
using document::new_config_builder::NewConfigBuilder;

namespace proton {

std::unique_ptr<DocumentTypeRepo> make_document_type_repo() {
    NewConfigBuilder builder;
    auto&            doc = builder.document("testdoc", 222);
    auto             int_array = doc.createArray(builder.intTypeRef()).ref();
    auto             int_wset = doc.createWset(builder.intTypeRef()).ref();
    doc.addField("aint", int_array).addField("wsint", int_wset);
    return std::make_unique<DocumentTypeRepo>(builder.config());
}

std::unique_ptr<DocumentTypeRepo> repo = make_document_type_repo();

TEST(FieldPathTargetTest, make_unsupported) {
    auto target = FieldPathTarget::unsupported();
    EXPECT_EQ(target.kind(), FieldPathTarget::Kind::UNSUPPORTED);
}

TEST(FieldPathTargetTest, make_array_index) {
    auto target = FieldPathTarget::array_index("attr_name", 5);
    EXPECT_EQ(target.kind(), FieldPathTarget::Kind::ARRAY_INDEX);
    EXPECT_EQ(target.attribute_name(), "attr_name");
    EXPECT_EQ(target.index(), 5);
}

TEST(FieldPathTargetTest, parse_array_index) {
    const auto& doc_type = *repo->getDocumentType("testdoc");

    auto target = FieldPathTarget::parse("aint[32]", doc_type);
    EXPECT_EQ(target.kind(), FieldPathTarget::Kind::ARRAY_INDEX);
    EXPECT_EQ(target.attribute_name(), "aint");
    EXPECT_EQ(target.index(), 32);
}

TEST(FieldPathTargetTest, parse_unsupported) {
    const auto& doc_type = *repo->getDocumentType("testdoc");

    std::vector<std::string> unsupported_field_paths = {"abc",      "bogus[0]",    "wsint{5}",
                                                        "aint[$x]", "aint[0].foo", "wsint[5]"};
    for (const auto& field_path : unsupported_field_paths) {
        auto target = FieldPathTarget::parse(field_path, doc_type);
        EXPECT_TRUE(target.is_unsupported());
    }
}

} // namespace proton
