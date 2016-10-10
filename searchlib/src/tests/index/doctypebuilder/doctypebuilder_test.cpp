// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("doctypebuilder_test");
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchlib/index/doctypebuilder.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace document;

namespace search {
namespace index {

TEST("testSearchDocType") {
    Schema s;
    s.addIndexField(Schema::IndexField("ia", Schema::STRING));
    s.addIndexField(Schema::IndexField("ib", Schema::STRING, Schema::ARRAY));
    s.addIndexField(Schema::IndexField("ic", Schema::STRING, Schema::WEIGHTEDSET));
    s.addUriIndexFields(Schema::IndexField("iu", Schema::STRING));
    s.addUriIndexFields(Schema::IndexField("iau",
                                Schema::STRING,
                                Schema::ARRAY));
    s.addUriIndexFields(Schema::IndexField("iwu",
                                Schema::STRING,
                                Schema::WEIGHTEDSET));
    s.addAttributeField(Schema::AttributeField("aa", Schema::INT32));
    s.addAttributeField(Schema::AttributeField("spos",
                                Schema::INT64));
    s.addAttributeField(Schema::AttributeField("apos",
                                Schema::INT64,
                                Schema::ARRAY));
    s.addAttributeField(Schema::AttributeField("wpos",
                                Schema::INT64,
                                Schema::WEIGHTEDSET));
    s.addSummaryField(Schema::SummaryField("sa", Schema::STRING));

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
    s.addIndexField(Schema::IndexField("array1", Schema::STRING,
                                       Schema::ARRAY));
    s.addIndexField(Schema::IndexField("array2", Schema::STRING,
                                       Schema::ARRAY));
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
