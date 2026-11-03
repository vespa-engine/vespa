// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/document/update/assignfieldpathupdate.h>
#include <vespa/searchcore/proton/common/field_path_target.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vector>

using document::AssignFieldPathUpdate;
using document::DocumentTypeRepo;
using document::Field;
using document::IntFieldValue;
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
    auto target = FieldPathTarget::unsupported(Field());
    EXPECT_EQ(target.kind(), FieldPathTarget::Kind::UNSUPPORTED);
    EXPECT_EQ(target.field(), nullptr);
}

TEST(FieldPathTargetTest, make_assign_element) {
    const auto& doc_type = *repo->getDocumentType("testdoc");

    auto target = FieldPathTarget::assign_element(doc_type.getField("aint"), 5);
    EXPECT_EQ(target.kind(), FieldPathTarget::Kind::ASSIGN_ELEMENT);
    EXPECT_EQ(target.attribute_name(), "aint");
    EXPECT_EQ(target.index(), 5);
    ASSERT_NE(target.field(), nullptr);
}

TEST(FieldPathTargetTest, parse_value_assign_to_array_index) {
    const auto& doc_type = *repo->getDocumentType("testdoc");

    AssignFieldPathUpdate update(doc_type, "aint[32]", "", IntFieldValue::make(7));
    auto                  target = FieldPathTarget::parse(update, doc_type);
    EXPECT_EQ(target.kind(), FieldPathTarget::Kind::ASSIGN_ELEMENT);
    EXPECT_EQ(target.attribute_name(), "aint");
    EXPECT_EQ(target.index(), 32);
    ASSERT_NE(target.field(), nullptr);
}

TEST(FieldPathTargetTest, parse_expression_assign_to_array_index_is_unsupported) {
    const auto& doc_type = *repo->getDocumentType("testdoc");

    // Valid array element path, but the assign carries an expression instead of a value.
    AssignFieldPathUpdate update("aint[3]", "", "5");
    auto                  target = FieldPathTarget::parse(update, doc_type);
    EXPECT_TRUE(target.is_unsupported());
    ASSERT_NE(target.field(), nullptr);
    EXPECT_EQ(target.attribute_name(), "aint");
}

TEST(FieldPathTargetTest, parse_unsupported) {
    const auto& doc_type = *repo->getDocumentType("testdoc");

    std::vector<std::string> unsupported_field_paths = {"abc", "bogus[0]", "wsint{5}", "aint[0].foo", "wsint[5]"};
    for (const auto& field_path : unsupported_field_paths) {
        AssignFieldPathUpdate update(field_path, "", "some_string");
        auto                  target = FieldPathTarget::parse(update, doc_type);
        EXPECT_TRUE(target.is_unsupported()) << "field path: " << field_path;
    }
}

} // namespace proton
