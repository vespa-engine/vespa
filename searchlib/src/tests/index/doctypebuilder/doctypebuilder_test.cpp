// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchlib/index/doctypebuilder.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace document;

namespace search {
namespace index {

using schema::CollectionType;
using schema::DataType;

TEST("testSearchDocType") {
    Schema s;
    s.addIndexField(Schema::IndexField("ia", DataType::STRING));
    s.addIndexField(Schema::IndexField("ib", DataType::STRING, CollectionType::ARRAY));
    s.addIndexField(Schema::IndexField("ic", DataType::STRING, CollectionType::WEIGHTEDSET));
    s.addUriIndexFields(Schema::IndexField("iu", DataType::STRING));
    s.addUriIndexFields(Schema::IndexField("iau", DataType::STRING, CollectionType::ARRAY));
    s.addUriIndexFields(Schema::IndexField("iwu", DataType::STRING, CollectionType::WEIGHTEDSET));
    s.addAttributeField(Schema::AttributeField("aa", DataType::INT32));
    s.addAttributeField(Schema::AttributeField("spos", DataType::INT64));
    s.addAttributeField(Schema::AttributeField("apos", DataType::INT64, CollectionType::ARRAY));
    s.addAttributeField(Schema::AttributeField("wpos", DataType::INT64, CollectionType::WEIGHTEDSET));
    s.addSummaryField(Schema::SummaryField("sa", DataType::STRING));

    DocTypeBuilder docTypeBuilder(s);
    document::DocumenttypesConfig config = docTypeBuilder.makeConfig();
    DocumentTypeRepo repo(config);
    const DocumentType *docType = repo.getDocumentType("searchdocument");
    ASSERT_TRUE(docType);
    EXPECT_EQUAL(11u, docType->getFieldCount());

    EXPECT_EQUAL("String", docType->getField("ia").getDataType().getName());
    EXPECT_EQUAL("Array<String>",
                 docType->getField("ib").getDataType().getName());
    EXPECT_EQUAL("WeightedSet<String>",
                 docType->getField("ic").getDataType().getName());
    EXPECT_EQUAL("url", docType->getField("iu").getDataType().getName());
    EXPECT_EQUAL("Array<url>",
                 docType->getField("iau").getDataType().getName());
    EXPECT_EQUAL("WeightedSet<url>",
                 docType->getField("iwu").getDataType().getName());

    EXPECT_EQUAL("Int", docType->getField("aa").getDataType().getName());
    EXPECT_EQUAL("Long", docType->getField("spos").getDataType().getName());
    EXPECT_EQUAL("Array<Long>",
                 docType->getField("apos").getDataType().getName());
    EXPECT_EQUAL("WeightedSet<Long>",
                 docType->getField("wpos").getDataType().getName());
    EXPECT_EQUAL("String", docType->getField("sa").getDataType().getName());
}

TEST("require that multiple fields can have the same type") {
    Schema s;
    s.addIndexField(Schema::IndexField("array1", DataType::STRING, CollectionType::ARRAY));
    s.addIndexField(Schema::IndexField("array2", DataType::STRING, CollectionType::ARRAY));
    DocTypeBuilder docTypeBuilder(s);
    document::DocumenttypesConfig config = docTypeBuilder.makeConfig();
    DocumentTypeRepo repo(config);
    const DocumentType *docType = repo.getDocumentType("searchdocument");
    ASSERT_TRUE(docType);
    EXPECT_EQUAL(2u, docType->getFieldCount());

    EXPECT_EQUAL("Array<String>",
                 docType->getField("array1").getDataType().getName());
    EXPECT_EQUAL("Array<String>",
                 docType->getField("array2").getDataType().getName());
}

}  // namespace index
}  // namespace search

TEST_MAIN() { TEST_RUN_ALL(); }
