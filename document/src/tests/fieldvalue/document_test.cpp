// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for document.

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace document;

namespace {

TEST(DocumentTest, require_that_document_with_id_schema_id_checks_type)
{
    TestDocRepo repo;
    const DataType *type = repo.getDocumentType("testdoctype1");
    ASSERT_TRUE(type);

    Document(repo.getTypeRepo(), *type, DocumentId("id:ns:testdoctype1::"));  // Should not throw

    VESPA_EXPECT_EXCEPTION(Document(repo.getTypeRepo(), *type, DocumentId("id:ns:type::")),
                           vespalib::IllegalArgumentException,
                           "testdoctype1 that don't match the id (type type)");
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
