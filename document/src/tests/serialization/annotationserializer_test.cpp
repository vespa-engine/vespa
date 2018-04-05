// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/serialization/annotationdeserializer.h>
#include <vespa/document/serialization/annotationserializer.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/fastos/file.h>
#include <fstream>
#include <algorithm>


using document::DocumenttypesConfig;
using std::fstream;
using std::ostringstream;
using std::string;
using std::vector;
using vespalib::nbostream;
using namespace document;

namespace {

class Test : public vespalib::TestApp {
    StringFieldValue::SpanTrees readSpanTree(const string &file_name, const FixedTypeRepo &repo);

    void requireThatSimpleSpanTreeIsDeserialized();
    void requireThatAdvancedSpanTreeIsDeserialized();
    void requireThatSpanTreeCanBeSerialized();
    void requireThatUnknownAnnotationIsSkipped();

public:
    int Main() override;
};

int
Test::Main()
{
    if (getenv("TEST_SUBSET") != 0) { return 0; }
    TEST_INIT("annotationserializer_test");
    TEST_DO(requireThatSimpleSpanTreeIsDeserialized());
    TEST_DO(requireThatAdvancedSpanTreeIsDeserialized());
    TEST_DO(requireThatSpanTreeCanBeSerialized());
    TEST_DO(requireThatUnknownAnnotationIsSkipped());

    TEST_DONE();
}

template <typename T, int N> int arraysize(const T (&)[N]) { return N; }

StringFieldValue::SpanTrees
Test::readSpanTree(const string &file_name, const FixedTypeRepo &repo) {
    FastOS_File file(file_name.c_str());
    ASSERT_TRUE(file.OpenReadOnlyExisting());
    char buffer[1024];
    ssize_t size = file.Read(buffer, arraysize(buffer));
    ASSERT_TRUE(size != -1);

    nbostream stream(buffer, size);
    VespaDocumentDeserializer deserializer(repo, stream, 8);
    StringFieldValue value;
    deserializer.read(value);

    EXPECT_EQUAL(0u, stream.size());
    ASSERT_TRUE(value.hasSpanTrees());
    return value.getSpanTrees();
}

void Test::requireThatSimpleSpanTreeIsDeserialized() {
    DocumentTypeRepo type_repo(readDocumenttypesConfig(TEST_PATH("annotation.serialize.test.repo.cfg")));
    FixedTypeRepo repo(type_repo);
    SpanTree::UP span_tree = std::move(readSpanTree(TEST_PATH("test_data_serialized_simple"), repo).front());

    EXPECT_EQUAL("html", span_tree->getName());
    const SimpleSpanList *root = dynamic_cast<const SimpleSpanList *>(&span_tree->getRoot());
    ASSERT_TRUE(root);
    EXPECT_EQUAL(5u, root->size());
    SimpleSpanList::const_iterator it = root->begin();
    EXPECT_EQUAL(Span(0, 19), (*it++));
    EXPECT_EQUAL(Span(19, 5), (*it++));
    EXPECT_EQUAL(Span(24, 21), (*it++));
    EXPECT_EQUAL(Span(45, 23), (*it++));
    EXPECT_EQUAL(Span(68, 14), (*it++));
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
        EXPECT_EQUAL(expect.size(), actual.size());
        for (size_t i = 0; i < expect.size() && i < actual.size(); ++i) {
            EXPECT_EQUAL(expect[i].size(), actual[i].size());
            EXPECT_EQUAL(expect[i], actual[i]);
        }
    }
};

void Test::requireThatAdvancedSpanTreeIsDeserialized() {
    DocumentTypeRepo type_repo(readDocumenttypesConfig(TEST_PATH("annotation.serialize.test.repo.cfg")));
    FixedTypeRepo repo(type_repo, "my_document");
    SpanTree::UP span_tree = std::move(readSpanTree(TEST_PATH("test_data_serialized_advanced"),
                                                    repo).front());

    EXPECT_EQUAL("html", span_tree->getName());
    const SpanList *root = dynamic_cast<const SpanList *>(&span_tree->getRoot());
    ASSERT_TRUE(root);
    EXPECT_EQUAL(4u, root->size());
    SpanList::const_iterator it = root->begin();
    EXPECT_EQUAL(Span(0, 6), *(static_cast<Span *>(*it++)));
    AlternateSpanList *alt_list = dynamic_cast<AlternateSpanList *>(*it++);
    EXPECT_EQUAL(Span(27, 9), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(36, 8), *(static_cast<Span *>(*it++)));
    EXPECT_TRUE(it == root->end());

    ASSERT_TRUE(alt_list);
    EXPECT_EQUAL(2u, alt_list->getNumSubtrees());
    EXPECT_EQUAL(0.9, alt_list->getProbability(0));
    EXPECT_EQUAL(0.1, alt_list->getProbability(1));
    EXPECT_EQUAL(4u, alt_list->getSubtree(0).size());
    it = alt_list->getSubtree(0).begin();
    EXPECT_EQUAL(Span(6, 3), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(9, 10), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(19, 4), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(23, 4), *(static_cast<Span *>(*it++)));
    EXPECT_TRUE(it == alt_list->getSubtree(0).end());
    EXPECT_EQUAL(2u, alt_list->getSubtree(1).size());
    it = alt_list->getSubtree(1).begin();
    EXPECT_EQUAL(Span(6, 13), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(19, 8), *(static_cast<Span *>(*it++)));
    EXPECT_TRUE(it == alt_list->getSubtree(1).end());

    EXPECT_EQUAL(12u, span_tree->numAnnotations());

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
    TEST_DO(comparator.compare());
}

void Test::requireThatSpanTreeCanBeSerialized() {
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

    SpanTree::UP span_tree = std::move(value.getSpanTrees().front());
    EXPECT_EQUAL("html", span_tree->getName());
    EXPECT_EQUAL(0u, stream.size());

    stream.clear();
    VespaDocumentSerializer serializer(stream);
    serializer.write(value);
    EXPECT_EQUAL(size, static_cast<ssize_t>(stream.size()));
    int diff_count = 0;
    for (size_t i = 0; i < stream.size(); ++i) {
        if (buffer[i] != stream.peek()[i]) {
            ++diff_count;
        }
        EXPECT_EQUAL((int) buffer[i], (int) stream.peek()[i]);
    }
    EXPECT_EQUAL(0, diff_count);
}

void Test::requireThatUnknownAnnotationIsSkipped() {
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
    EXPECT_EQUAL(0u, stream.size());
}

}  // namespace

TEST_APPHOOK(Test);
