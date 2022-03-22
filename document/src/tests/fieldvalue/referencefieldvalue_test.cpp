// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/field.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/vespalib/testkit/testapp.h>
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

TEST_F("Default-constructed reference is empty and bound to type", Fixture) {
    ReferenceFieldValue fv(f.refType);
    ASSERT_TRUE(fv.getDataType() != nullptr);
    EXPECT_EQUAL(f.refType, *fv.getDataType());
    ASSERT_FALSE(fv.hasValidDocumentId());
}

TEST_F("Reference can be constructed with document ID", Fixture) {
    ReferenceFieldValue fv(f.refType, DocumentId("id:ns:foo::itsa-me"));
    ASSERT_TRUE(fv.getDataType() != nullptr);
    EXPECT_EQUAL(f.refType, *fv.getDataType());
    ASSERT_TRUE(fv.hasValidDocumentId());
    EXPECT_EQUAL(DocumentId("id:ns:foo::itsa-me"), fv.getDocumentId());
}

TEST_F("Exception is thrown if constructor doc ID type does not match referenced document type", Fixture) {
    EXPECT_EXCEPTION(
            ReferenceFieldValue(f.refType, DocumentId("id:ns:bar::wario-time")),
            IllegalArgumentException,
            "Can't assign document ID 'id:ns:bar::wario-time' (of type 'bar') "
            "to reference of document type 'foo'");
}

TEST_F("assign()ing a non-reference field value throws exception", Fixture) {
    ReferenceFieldValue fv(f.refType);
    EXPECT_EXCEPTION(fv.assign(StringFieldValue("waluigi time!!")),
                     IllegalArgumentException,
                     "Can't assign field value of type String to a "
                     "ReferenceFieldValue");
}

TEST_F("Can explicitly assign new document ID to reference", Fixture) {
    ReferenceFieldValue fv(f.refType);
    fv.setDeserializedDocumentId(DocumentId("id:ns:foo::yoshi-eggs"));

    ASSERT_TRUE(fv.hasValidDocumentId());
    EXPECT_EQUAL(DocumentId("id:ns:foo::yoshi-eggs"), fv.getDocumentId());
    // Type remains unchanged
    EXPECT_EQUAL(f.refType, *fv.getDataType());
}

TEST_F("Exception is thrown if explicitly assigned doc ID does not have same type as reference target type", Fixture) {
    ReferenceFieldValue fv(f.refType);

    EXPECT_EXCEPTION(
            fv.setDeserializedDocumentId(DocumentId("id:ns:bar::another-castle")),
            IllegalArgumentException,
            "Can't assign document ID 'id:ns:bar::another-castle' (of type "
            "'bar') to reference of document type 'foo'");
}

TEST_F("assign()ing another reference field value assigns doc ID and type", Fixture) {
    ReferenceFieldValue src(f.refType, DocumentId("id:ns:foo::yoshi"));
    ReferenceFieldValue dest(f.otherRefType);

    dest.assign(src);
    ASSERT_TRUE(dest.hasValidDocumentId());
    EXPECT_EQUAL(src.getDocumentId(), dest.getDocumentId());
    EXPECT_EQUAL(src.getDataType(), dest.getDataType());
}


TEST_F("clone()ing creates new instance with same ID and type", Fixture) {
    ReferenceFieldValue src(f.refType, DocumentId("id:ns:foo::yoshi"));

    std::unique_ptr<ReferenceFieldValue> cloned(src.clone());
    ASSERT_TRUE(cloned);
    ASSERT_TRUE(cloned->hasValidDocumentId());
    EXPECT_EQUAL(src.getDocumentId(), cloned->getDocumentId());
    EXPECT_EQUAL(src.getDataType(), cloned->getDataType());
}

TEST_F("Can clone() value without document ID", Fixture) {
    ReferenceFieldValue src(f.refType);

    std::unique_ptr<ReferenceFieldValue> cloned(src.clone());
    ASSERT_TRUE(cloned);
    EXPECT_FALSE(cloned->hasValidDocumentId());
    EXPECT_EQUAL(src.getDataType(), cloned->getDataType());
}

TEST_F("compare() orders first on type ID, then on document ID", Fixture) {
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
    EXPECT_EQUAL(0, fvType1Id1.compare(fvType1Id1));
    EXPECT_EQUAL(0, fvType1Id2.compare(fvType1Id2));
    EXPECT_EQUAL(0, fvType2Id1.compare(fvType2Id1));
}

TEST_F("print() includes reference type and document ID", Fixture) {
    ReferenceFieldValue src(f.refType, DocumentId("id:ns:foo::yoshi"));
    std::ostringstream ss;
    src.print(ss, false, "");
    EXPECT_EQUAL("ReferenceFieldValue(ReferenceDataType(foo, id 12345), "
                 "DocumentId(id:ns:foo::yoshi))", ss.str());
}

TEST_F("print() only indents start of output line", Fixture) {
    ReferenceFieldValue src(f.refType, DocumentId("id:ns:foo::yoshi"));
    std::ostringstream ss;
    src.print(ss, false, "    ");
    EXPECT_EQUAL("    ReferenceFieldValue(ReferenceDataType(foo, id 12345), "
                 "DocumentId(id:ns:foo::yoshi))", ss.str());
}

TEST_MAIN() { TEST_RUN_ALL(); }

