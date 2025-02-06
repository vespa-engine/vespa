// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/field.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

using namespace document;

struct Fixture {
    DocumentType docType{"foo"};
    ReferenceDataType refType{docType, 12345};
};

TEST(ReferenceDataTypeTest, Constructor_generates_type_parameterized_name_and_sets_type_ID)
{
    Fixture f;
    EXPECT_EQ("Reference<foo>", f.refType.getName());
    EXPECT_EQ(12345, f.refType.getId());
}

TEST(ReferenceDataTypeTest, Target_document_type_is_accessible_via_data_type)
{
    Fixture f;
    EXPECT_EQ(f.docType, f.refType.getTargetType());
}

TEST(ReferenceDataTypeTest, Empty_ReferenceFieldValue_instances_can_be_created_from_type)
{
    Fixture f;
    auto fv = f.refType.createFieldValue();
    ASSERT_TRUE(fv.get() != nullptr);
    ASSERT_TRUE(dynamic_cast<ReferenceFieldValue*>(fv.get()) != nullptr);
    EXPECT_EQ(&f.refType, fv->getDataType());
}

TEST(ReferenceDataTypeTest, operator_equals_checks_document_type_and_type_ID)
{
    Fixture f;
    EXPECT_NE(f.refType, *DataType::STRING);
    EXPECT_EQ(f.refType, f.refType);

    DocumentType otherDocType("bar");
    ReferenceDataType refWithDifferentType(otherDocType, 12345);
    ReferenceDataType refWithSameTypeDifferentId(f.docType, 56789);

    EXPECT_NE(f.refType, refWithDifferentType);
    EXPECT_NE(f.refType, refWithSameTypeDifferentId);
}

TEST(ReferenceDataTypeTest, print_emits_type_name_and_id)
{
    Fixture f;
    std::ostringstream ss;
    f.refType.print(ss, true, "");
    EXPECT_EQ("ReferenceDataType(foo, id 12345)", ss.str());
}

TEST(ReferenceDataTypeTest, buildFieldPath_returns_empty_path_for_empty_input)
{
    Fixture f;
    FieldPath fp;
    f.refType.buildFieldPath(fp, "");
    EXPECT_TRUE(fp.empty());
}

TEST(ReferenceDataTypeTest, buildFieldPath_throws_IllegalArgumentException_for_non_empty_input)
{
    Fixture f;
    FieldPath fp;
    VESPA_EXPECT_EXCEPTION(f.refType.buildFieldPath(fp, "herebedragons"),
                           vespalib::IllegalArgumentException,
                           "Reference data type does not support further field recursion: 'herebedragons'");
}

GTEST_MAIN_RUN_ALL_TESTS()
