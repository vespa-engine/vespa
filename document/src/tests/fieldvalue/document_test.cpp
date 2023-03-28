// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for document.

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace document;

namespace {

TEST("require that document with id schema 'id' checks type") {
    TestDocRepo repo;
    const DataType *type = repo.getDocumentType("testdoctype1");
    ASSERT_TRUE(type);

    Document(repo.getTypeRepo(), *type, DocumentId("id:ns:testdoctype1::"));  // Should not throw

    EXPECT_EXCEPTION(Document(repo.getTypeRepo(), *type, DocumentId("id:ns:type::")),
                     vespalib::IllegalArgumentException,
                     "testdoctype1 that don't match the id (type type)");
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
