// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/serialization/annotationdeserializer.h>
#include <vespa/document/serialization/annotationserializer.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/fastos/file.h>

using std::ostringstream;
using std::string;
using std::vector;
using vespalib::nbostream;
using namespace document;

namespace {

template <typename T, int N> int arraysize(const T (&)[N]) { return N; }

}

TEST(StructAnnoTest, require_that_struct_fields_can_contain_annotations)
{
    DocumentTypeRepo repo(readDocumenttypesConfig(TEST_PATH("documenttypes.cfg")));

    FastOS_File file(TEST_PATH("document.dat").c_str());
    ASSERT_TRUE(file.OpenReadOnlyExisting());
    char buffer[1024];
    ssize_t size = file.Read(buffer, arraysize(buffer));
    ASSERT_TRUE(size != -1);

    nbostream stream(buffer, size);
    VespaDocumentDeserializer deserializer(repo, stream, 8);
    Document doc;
    deserializer.read(doc);

    FieldValue::UP urlRef = doc.getValue("my_url");
    ASSERT_TRUE(urlRef.get() != NULL);
    const StructFieldValue *url = dynamic_cast<const StructFieldValue*>(urlRef.get());
    ASSERT_TRUE(url != NULL);

    FieldValue::UP strRef = url->getValue("scheme");
    const StringFieldValue *str = dynamic_cast<const StringFieldValue*>(strRef.get());
    ASSERT_TRUE(str != NULL);

    auto tree = std::move(str->getSpanTrees().front());

    EXPECT_EQ("my_tree", tree->getName());
    const SimpleSpanList *root = dynamic_cast<const SimpleSpanList*>(&tree->getRoot());
    ASSERT_TRUE(root != NULL);
    EXPECT_EQ(1u, root->size());
    SimpleSpanList::const_iterator it = root->begin();
    EXPECT_EQ(Span(0, 6), (*it++));
    EXPECT_TRUE(it == root->end());

    EXPECT_EQ(1u, tree->numAnnotations());
}

GTEST_MAIN_RUN_ALL_TESTS()
