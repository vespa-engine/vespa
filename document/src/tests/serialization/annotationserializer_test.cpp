// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
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
#include <algorithm>
#include <fstream>
#include <optional>


using std::fstream;
using std::ostringstream;
using std::string;
using std::vector;
using vespalib::nbostream;
using namespace document;

namespace {

template <typename T, int N> int arraysize(const T (&)[N]) { return N; }

void
read_span_trees(const string &file_name, const FixedTypeRepo &repo, std::optional<StringFieldValue::SpanTrees>& span_trees)
{
    FastOS_File file(file_name.c_str());
    ASSERT_TRUE(file.OpenReadOnlyExisting());
    char buffer[1024];
    ssize_t size = file.Read(buffer, arraysize(buffer));
    ASSERT_TRUE(size != -1);

    nbostream stream(buffer, size);
    VespaDocumentDeserializer deserializer(repo, stream, 8);
    StringFieldValue value;
    deserializer.read(value);

    EXPECT_EQ(0u, stream.size());
    ASSERT_TRUE(value.hasSpanTrees());
    span_trees = value.getSpanTrees();
}

}

TEST(AnnotationSerializerTest, require_that_simple_span_tree_is_deserialized)
{
    DocumentTypeRepo type_repo(readDocumenttypesConfig(TEST_PATH("annotation.serialize.test.repo.cfg")));
    FixedTypeRepo repo(type_repo);
    std::optional<StringFieldValue::SpanTrees> span_trees;
    ASSERT_NO_FATAL_FAILURE(read_span_trees(TEST_PATH("test_data_serialized_simple"), repo, span_trees));
    auto span_tree = std::move(span_trees.value().front());

    EXPECT_EQ("html", span_tree->getName());
    const SimpleSpanList *root = dynamic_cast<const SimpleSpanList *>(&span_tree->getRoot());
    ASSERT_TRUE(root);
    EXPECT_EQ(5u, root->size());
    SimpleSpanList::const_iterator it = root->begin();
    EXPECT_EQ(Span(0, 19), (*it++));
    EXPECT_EQ(Span(19, 5), (*it++));
    EXPECT_EQ(Span(24, 21), (*it++));
    EXPECT_EQ(Span(45, 23), (*it++));
    EXPECT_EQ(Span(68, 14), (*it++));
    EXPECT_TRUE(it == root->end());
}

struct AnnotationComparator {
    vector<string> expect;
    vector<string> actual;
    template <typename ITR>
    AnnotationComparator &addActual(ITR pos, ITR end) {
        for (; pos != end; ++pos) {
            actual.push_back(pos->toString());
        }
        return *this;
    }
    AnnotationComparator &addExpected(const string &e) {
        expect.push_back(e);
        return *this;
    }
    void compare() {
        std::sort(expect.begin(), expect.end());
        std::sort(actual.begin(), actual.end());
        EXPECT_EQ(expect.size(), actual.size());
        for (size_t i = 0; i < expect.size() && i < actual.size(); ++i) {
            EXPECT_EQ(expect[i].size(), actual[i].size());
            EXPECT_EQ(expect[i], actual[i]);
        }
    }
};

TEST(AnnotationSerializerTest, require_that_advanced_span_tree_is_deserialized)
{
    DocumentTypeRepo type_repo(readDocumenttypesConfig(TEST_PATH("annotation.serialize.test.repo.cfg")));
    FixedTypeRepo repo(type_repo, "my_document");
    std::optional<StringFieldValue::SpanTrees> span_trees;
    ASSERT_NO_FATAL_FAILURE(read_span_trees(TEST_PATH("test_data_serialized_advanced"), repo, span_trees));
    auto span_tree = std::move(span_trees.value().front());

    EXPECT_EQ("html", span_tree->getName());
    const SpanList *root = dynamic_cast<const SpanList *>(&span_tree->getRoot());
    ASSERT_TRUE(root);
    EXPECT_EQ(4u, root->size());
    SpanList::const_iterator it = root->begin();
    EXPECT_EQ(Span(0, 6), *(static_cast<Span *>(*it++)));
    AlternateSpanList *alt_list = dynamic_cast<AlternateSpanList *>(*it++);
    EXPECT_EQ(Span(27, 9), *(static_cast<Span *>(*it++)));
    EXPECT_EQ(Span(36, 8), *(static_cast<Span *>(*it++)));
    EXPECT_TRUE(it == root->end());

    ASSERT_TRUE(alt_list);
    EXPECT_EQ(2u, alt_list->getNumSubtrees());
    EXPECT_EQ(0.9, alt_list->getProbability(0));
    EXPECT_EQ(0.1, alt_list->getProbability(1));
    EXPECT_EQ(4u, alt_list->getSubtree(0).size());
    it = alt_list->getSubtree(0).begin();
    EXPECT_EQ(Span(6, 3), *(static_cast<Span *>(*it++)));
    EXPECT_EQ(Span(9, 10), *(static_cast<Span *>(*it++)));
    EXPECT_EQ(Span(19, 4), *(static_cast<Span *>(*it++)));
    EXPECT_EQ(Span(23, 4), *(static_cast<Span *>(*it++)));
    EXPECT_TRUE(it == alt_list->getSubtree(0).end());
    EXPECT_EQ(2u, alt_list->getSubtree(1).size());
    it = alt_list->getSubtree(1).begin();
    EXPECT_EQ(Span(6, 13), *(static_cast<Span *>(*it++)));
    EXPECT_EQ(Span(19, 8), *(static_cast<Span *>(*it++)));
    EXPECT_TRUE(it == alt_list->getSubtree(1).end());

    EXPECT_EQ(12u, span_tree->numAnnotations());

    AnnotationComparator comparator;
    comparator.addActual(span_tree->begin(), span_tree->end())
        .addExpected("Annotation(AnnotationType(20001, begintag)\n"
                     "Span(6, 3))")
        .addExpected("Annotation(AnnotationType(20000, text)\n"
                     "Span(9, 10))")
        .addExpected("Annotation(AnnotationType(20000, text)\n"
                     "Span(19, 4))")
        .addExpected("Annotation(AnnotationType(20002, endtag)\n"
                     "Span(23, 4))")
        .addExpected("Annotation(AnnotationType(20000, text)\n"
                     "Span(6, 13))")
        .addExpected("Annotation(AnnotationType(20003, body)\n"
                     "Span(19, 8))")
        .addExpected("Annotation(AnnotationType(20004, paragraph)\n"
                     "AlternateSpanList(\n"
                     "  Probability 0.9 : SpanList(\n"
                     "    Span(6, 3)\n"
                     "    Span(9, 10)\n"
                     "    Span(19, 4)\n"
                     "    Span(23, 4)\n"
                     "  )\n"
                     "  Probability 0.1 : SpanList(\n"
                     "    Span(6, 13)\n"
                     "    Span(19, 8)\n"
                     "  )\n"
                     "))")
        .addExpected("Annotation(AnnotationType(20001, begintag)\n"
                     "Span(0, 6))")
        .addExpected("Annotation(AnnotationType(20000, text)\n"
                     "Span(27, 9))")
        .addExpected("Annotation(AnnotationType(20002, endtag)\n"
                     "Span(36, 8))")
        .addExpected("Annotation(AnnotationType(20003, body)\n"
                     "SpanList(\n"
                     "  Span(0, 6)\n"
                     "  AlternateSpanList(\n"
                     "    Probability 0.9 : SpanList(\n"
                     "      Span(6, 3)\n"
                     "      Span(9, 10)\n"
                     "      Span(19, 4)\n"
                     "      Span(23, 4)\n"
                     "    )\n"
                     "    Probability 0.1 : SpanList(\n"
                     "      Span(6, 13)\n"
                     "      Span(19, 8)\n"
                     "    )\n"
                     "  )\n"
                     "  Span(27, 9)\n"
                     "  Span(36, 8)\n"
                     "))")
        .addExpected("Annotation(AnnotationType(20005, city)\n"
                     "Struct annotation.city(\n"
                     "  position - Struct myposition(\n"
                     "    latitude - 37,\n"
                     "    longitude - -122\n"
                     "  ),\n"
                     "  references - Array(size: 2,\n"
                     "    AnnotationReferenceFieldValue(n),\n"
                     "    AnnotationReferenceFieldValue(n)\n"
                     "  )\n"
                     "))");
    comparator.compare();
}

TEST(AnnotationSerializerTest, require_that_span_tree_can_be_serialized)
{
    DocumentTypeRepo type_repo(
            readDocumenttypesConfig(TEST_PATH("annotation.serialize.test.repo.cfg")));
    FixedTypeRepo repo(type_repo, "my_document");
    string file_name = TEST_PATH("test_data_serialized_advanced");

    FastOS_File file(file_name.c_str());
    ASSERT_TRUE(file.OpenReadOnlyExisting());
    char buffer[1024];
    ssize_t size = file.Read(buffer, arraysize(buffer));
    ASSERT_TRUE(size != -1);

    nbostream stream(buffer, size);
    VespaDocumentDeserializer deserializer(repo, stream, 8);
    StringFieldValue value;
    deserializer.read(value);

    auto span_tree = std::move(value.getSpanTrees().front());
    EXPECT_EQ("html", span_tree->getName());
    EXPECT_EQ(0u, stream.size());

    stream.clear();
    VespaDocumentSerializer serializer(stream);
    serializer.write(value);
    EXPECT_EQ(size, static_cast<ssize_t>(stream.size()));
    int diff_count = 0;
    for (size_t i = 0; i < stream.size(); ++i) {
        if (buffer[i] != stream.peek()[i]) {
            ++diff_count;
        }
        EXPECT_EQ((int) buffer[i], (int) stream.peek()[i]);
    }
    EXPECT_EQ(0, diff_count);
}

TEST(AnnotationSerializerTest, require_that_unknown_annotation_is_skipped)
{
    AnnotationType type(42, "my type");
    Annotation annotation(type, FieldValue::UP(new StringFieldValue("foo")));
    nbostream stream;
    AnnotationSerializer serializer(stream);
    serializer.write(annotation);

    DocumentTypeRepo type_repo;  // Doesn't know any annotation types.
    FixedTypeRepo repo(type_repo);
    AnnotationDeserializer deserializer(repo, stream, 8);
    Annotation a;
    deserializer.readAnnotation(a);
    EXPECT_FALSE(a.valid());
    EXPECT_EQ(0u, stream.size());
}

GTEST_MAIN_RUN_ALL_TESTS()
