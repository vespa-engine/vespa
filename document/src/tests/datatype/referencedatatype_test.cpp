// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/field.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>
#include <sstream>

using namespace document;

struct Fixture {
    DocumentType docType{"foo"};
    ReferenceDataType refType{docType, 12345};
};

TEST_F("Constructor generates type-parameterized name and sets type ID", Fixture) {
    EXPECT_EQUAL("Reference<foo>", f.refType.getName());
    EXPECT_EQUAL(12345, f.refType.getId());
}

TEST_F("Target document type is accessible via data type", Fixture) {
    EXPECT_EQUAL(f.docType, f.refType.getTargetType());
}

TEST_F("Empty ReferenceFieldValue instances can be created from type", Fixture) {
    auto fv = f.refType.createFieldValue();
    ASSERT_TRUE(fv.get() != nullptr);
    ASSERT_TRUE(dynamic_cast<ReferenceFieldValue*>(fv.get()) != nullptr);
    EXPECT_EQUAL(&f.refType, fv->getDataType());
}

TEST_F("operator== checks document type and type ID", Fixture) {
    EXPECT_NOT_EQUAL(f.refType, *DataType::STRING);
    EXPECT_EQUAL(f.refType, f.refType);

    DocumentType otherDocType("bar");
    ReferenceDataType refWithDifferentType(otherDocType, 12345);
    ReferenceDataType refWithSameTypeDifferentId(f.docType, 56789);

    EXPECT_NOT_EQUAL(f.refType, refWithDifferentType);
    EXPECT_NOT_EQUAL(f.refType, refWithSameTypeDifferentId);
}

TEST_F("print() emits type name and id", Fixture) {
    std::ostringstream ss;
    f.refType.print(ss, true, "");
    EXPECT_EQUAL("ReferenceDataType(foo, id 12345)", ss.str());
}

TEST_F("buildFieldPath returns empty path for empty input", Fixture) {
    FieldPath fp;
    f.refType.buildFieldPath(fp, "");
    EXPECT_TRUE(fp.empty());
}

TEST_F("buildFieldPath throws IllegalArgumentException for non-empty input", Fixture) {
    FieldPath fp;
    EXPECT_EXCEPTION(f.refType.buildFieldPath(fp, "herebedragons"),
                     vespalib::IllegalArgumentException,
                     "Reference data type does not support further field recursion: 'herebedragons'");
}

TEST_MAIN() { TEST_RUN_ALL(); }
