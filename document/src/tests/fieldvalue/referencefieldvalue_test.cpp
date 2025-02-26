// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/field.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>

using namespace document;

namespace {

struct Fixture {
    DocumentType docType{"foo"};
    ReferenceDataType refType{docType, 12345};

    DocumentType otherDocType{"bar"};
    ReferenceDataType otherRefType{otherDocType, 54321};
    Fixture();
    ~Fixture();
};

    Fixture::Fixture() = default;
    Fixture::~Fixture() = default;
}

using vespalib::IllegalArgumentException;

TEST(ReferenceFieldValueTest, Default_constructed_reference_is_empty_and_bound_to_type)
{
    Fixture f;
    ReferenceFieldValue fv(f.refType);
    ASSERT_TRUE(fv.getDataType() != nullptr);
    EXPECT_EQ(f.refType, *fv.getDataType());
    ASSERT_FALSE(fv.hasValidDocumentId());
}

TEST(ReferenceFieldValueTest, Reference_can_be_constructed_with_document_ID)
{
    Fixture f;
    ReferenceFieldValue fv(f.refType, DocumentId("id:ns:foo::itsa-me"));
    ASSERT_TRUE(fv.getDataType() != nullptr);
    EXPECT_EQ(f.refType, *fv.getDataType());
    ASSERT_TRUE(fv.hasValidDocumentId());
    EXPECT_EQ(DocumentId("id:ns:foo::itsa-me"), fv.getDocumentId());
}

TEST(ReferenceFieldValueTest, Exception_is_thrown_if_constructor_doc_ID_type_does_not_match_referenced_document_type)
{
    Fixture f;
    VESPA_EXPECT_EXCEPTION(ReferenceFieldValue(f.refType, DocumentId("id:ns:bar::wario-time")),
                           IllegalArgumentException,
                           "Can't assign document ID 'id:ns:bar::wario-time' (of type 'bar') "
                           "to reference of document type 'foo'");
}

TEST(ReferenceFieldValueTest, assigning_a_non_reference_field_value_throws_exception)
{
    Fixture f;
    ReferenceFieldValue fv(f.refType);
    VESPA_EXPECT_EXCEPTION(fv.assign(StringFieldValue("waluigi time!!")),
                           IllegalArgumentException,
                           "Can't assign field value of type String to a "
                           "ReferenceFieldValue");
}

TEST(ReferenceFieldValueTest, Can_explicitly_assign_new_document_ID_to_reference)
{
    Fixture f;
    ReferenceFieldValue fv(f.refType);
    fv.setDeserializedDocumentId(DocumentId("id:ns:foo::yoshi-eggs"));

    ASSERT_TRUE(fv.hasValidDocumentId());
    EXPECT_EQ(DocumentId("id:ns:foo::yoshi-eggs"), fv.getDocumentId());
    // Type remains unchanged
    EXPECT_EQ(f.refType, *fv.getDataType());
}

TEST(ReferenceFieldValueTest, Exception_is_thrown_if_explicitly_assigned_doc_ID_does_not_have_same_type_as_reference_target_type)
{
    Fixture f;
    ReferenceFieldValue fv(f.refType);

    VESPA_EXPECT_EXCEPTION(fv.setDeserializedDocumentId(DocumentId("id:ns:bar::another-castle")),
                           IllegalArgumentException,
                           "Can't assign document ID 'id:ns:bar::another-castle' (of type "
                           "'bar') to reference of document type 'foo'");
}

TEST(ReferenceFieldValueTest, assigning_another_reference_field_value_assigns_doc_ID_and_type)
{
    Fixture f;
    ReferenceFieldValue src(f.refType, DocumentId("id:ns:foo::yoshi"));
    ReferenceFieldValue dest(f.otherRefType);

    dest.assign(src);
    ASSERT_TRUE(dest.hasValidDocumentId());
    EXPECT_EQ(src.getDocumentId(), dest.getDocumentId());
    EXPECT_EQ(src.getDataType(), dest.getDataType());
}


TEST(ReferenceFieldValueTest, cloning_creates_new_instance_with_same_ID_and_type)
{
    Fixture f;
    ReferenceFieldValue src(f.refType, DocumentId("id:ns:foo::yoshi"));

    std::unique_ptr<ReferenceFieldValue> cloned(src.clone());
    ASSERT_TRUE(cloned);
    ASSERT_TRUE(cloned->hasValidDocumentId());
    EXPECT_EQ(src.getDocumentId(), cloned->getDocumentId());
    EXPECT_EQ(src.getDataType(), cloned->getDataType());
}

TEST(ReferenceFieldValueTest, Can_clone_value_without_document_ID)
{
    Fixture f;
    ReferenceFieldValue src(f.refType);

    std::unique_ptr<ReferenceFieldValue> cloned(src.clone());
    ASSERT_TRUE(cloned);
    EXPECT_FALSE(cloned->hasValidDocumentId());
    EXPECT_EQ(src.getDataType(), cloned->getDataType());
}

TEST(ReferenceFieldValueTest, compare_orders_first_on_type_ID_then_on_document_ID)
{
    Fixture f;
    // foo type has id 12345
    ReferenceFieldValue fvType1Id1(f.refType, DocumentId("id:ns:foo::AA"));
    ReferenceFieldValue fvType1Id2(f.refType, DocumentId("id:ns:foo::AB"));
    // bar type has id 54321
    ReferenceFieldValue fvType2Id1(f.otherRefType, DocumentId("id:ns:bar::AA"));
    ReferenceFieldValue fvType2Id2(f.otherRefType, DocumentId("id:ns:bar::AB"));

    // Different types
    EXPECT_TRUE(fvType1Id1.compare(fvType2Id1) < 0);
    EXPECT_TRUE(fvType2Id1.compare(fvType1Id1) > 0);

    // Same types, different IDs
    EXPECT_TRUE(fvType1Id1.compare(fvType1Id2) < 0);
    EXPECT_TRUE(fvType1Id2.compare(fvType1Id1) > 0);
    EXPECT_TRUE(fvType2Id1.compare(fvType2Id2) < 0);

    // Different types and IDs
    EXPECT_TRUE(fvType1Id1.compare(fvType2Id2) < 0);
    EXPECT_TRUE(fvType2Id2.compare(fvType1Id1) > 0);

    // Equal types and ID 
    EXPECT_EQ(0, fvType1Id1.compare(fvType1Id1));
    EXPECT_EQ(0, fvType1Id2.compare(fvType1Id2));
    EXPECT_EQ(0, fvType2Id1.compare(fvType2Id1));
}

TEST(ReferenceFieldValueTest, print_includes_reference_type_and_document_ID)
{
    Fixture f;
    ReferenceFieldValue src(f.refType, DocumentId("id:ns:foo::yoshi"));
    std::ostringstream ss;
    src.print(ss, false, "");
    EXPECT_EQ("ReferenceFieldValue(ReferenceDataType(foo, id 12345), "
                 "DocumentId(id:ns:foo::yoshi))", ss.str());
}

TEST(ReferenceFieldValueTest, print_only_indents_start_of_output_line)
{
    Fixture f;
    ReferenceFieldValue src(f.refType, DocumentId("id:ns:foo::yoshi"));
    std::ostringstream ss;
    src.print(ss, false, "    ");
    EXPECT_EQ("    ReferenceFieldValue(ReferenceDataType(foo, id 12345), "
                 "DocumentId(id:ns:foo::yoshi))", ss.str());
}

GTEST_MAIN_RUN_ALL_TESTS()
