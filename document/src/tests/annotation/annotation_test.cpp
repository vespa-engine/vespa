// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for annotation.

#include <stdlib.h>
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/annotation/spantreevisitor.h>
#include <vespa/document/datatype/annotationreferencedatatype.h>
#include <vespa/document/datatype/annotationtype.h>
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/primitivedatatype.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/fieldvalue/annotationreferencefieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <memory>

using std::unique_ptr;
using namespace document;

namespace {

AnnotationType text_type(1, "text");
AnnotationType begin_tag(2, "begintag");
AnnotationType end_tag(3, "endtag");
AnnotationType body_type(4, "body");
AnnotationType header_type(5, "header");
AnnotationType city_type(6, "city");
AnnotationType markup_type(7, "markup");

template <typename T>
unique_ptr<T> makeUP(T *p) { return unique_ptr<T>(p); }

TEST("requireThatSpansHaveOrder") {
    Span span(10, 10);
    Span before(5, 3);
    Span overlap_start(8, 10);
    Span contained(12, 3);
    Span overlap_end(15, 10);
    Span after(21, 10);
    Span overlap_complete(5, 20);
    Span shorter(10, 5);
    Span longer(10, 15);
    EXPECT_TRUE(span > before);
    EXPECT_TRUE(span > overlap_start);
    EXPECT_TRUE(span < contained);
    EXPECT_TRUE(span < overlap_end);
    EXPECT_TRUE(span < after);
    EXPECT_TRUE(span > overlap_complete);
    EXPECT_TRUE(span > shorter);
    EXPECT_TRUE(span < longer);
    EXPECT_TRUE(!(span < span));
}

TEST("requireThatSimpleSpanTreeCanBeBuilt") {
    SpanList::UP root(new SpanList);
    root->add(makeUP(new Span(0, 19)));
    root->add(makeUP(new Span(19, 5)));
    root->add(makeUP(new Span(24, 21)));
    root->add(makeUP(new Span(45, 23)));
    root->add(makeUP(new Span(68, 14)));

    EXPECT_EQUAL(5u, root->size());
    SpanList::const_iterator it = root->begin();
    EXPECT_EQUAL(Span(0, 19), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(19, 5), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(24, 21), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(45, 23), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(68, 14), *(static_cast<Span *>(*it++)));
    EXPECT_TRUE(it == root->end());

    SpanTree tree("html", std::move(root));
}

TEST("requireThatSpanTreeCanHaveAnnotations") {
    SpanList::UP root_owner(new SpanList);
    SpanList *root = root_owner.get();
    SpanTree tree("html", std::move(root_owner));

    Span &span1 = root->add(makeUP(new Span(0, 19)));
    tree.annotate(span1, markup_type);

    Span &span2 = root->add(makeUP(new Span(19, 5)));
    tree.annotate(span2, text_type);

    EXPECT_EQUAL(2u, tree.numAnnotations());
    SpanTree::const_iterator it = tree.begin();

    EXPECT_EQUAL(Annotation(markup_type), *it++);
    EXPECT_EQUAL(Annotation(text_type), *it++);
    EXPECT_TRUE(it == tree.end());
}

TEST("requireThatSpanTreeCanHaveMultipleLevels") {
    SpanList::UP root_owner(new SpanList);
    SpanList *root = root_owner.get();
    SpanTree tree("html", std::move(root_owner));

    SpanList::UP header(new SpanList);
    tree.annotate(header->add(makeUP(new Span(6, 6))), begin_tag);
    tree.annotate(header->add(makeUP(new Span(12, 7))), begin_tag);
    tree.annotate(header->add(makeUP(new Span(19, 5))), text_type);
    tree.annotate(header->add(makeUP(new Span(24, 8))), end_tag);
    tree.annotate(header->add(makeUP(new Span(32, 7))), end_tag);
    tree.annotate(*header, header_type);

    SpanList::UP body(new SpanList);
    tree.annotate(body->add(makeUP(new Span(39, 6))), begin_tag);
    tree.annotate(body->add(makeUP(new Span(45, 23))), text_type);
    tree.annotate(body->add(makeUP(new Span(68, 7))), end_tag);
    tree.annotate(*body, body_type);

    tree.annotate(root->add(makeUP(new Span(0, 6))), begin_tag);
    root->add(std::move(std::move(header)));
    root->add(std::move(std::move(body)));
    tree.annotate(root->add(makeUP(new Span(75, 7))), end_tag);

    EXPECT_EQUAL(12u, tree.numAnnotations());
    SpanTree::const_iterator it = tree.begin();
    EXPECT_EQUAL(Annotation(begin_tag), *it++);
    EXPECT_EQUAL(Annotation(begin_tag), *it++);
    EXPECT_EQUAL(Annotation(text_type), *it++);
    EXPECT_EQUAL(Annotation(end_tag), *it++);
    EXPECT_EQUAL(Annotation(end_tag), *it++);
    EXPECT_EQUAL(Annotation(header_type), *it++);
    EXPECT_EQUAL(Annotation(begin_tag), *it++);
    EXPECT_EQUAL(Annotation(text_type), *it++);
    EXPECT_EQUAL(Annotation(end_tag), *it++);
    EXPECT_EQUAL(Annotation(body_type), *it++);
    EXPECT_EQUAL(Annotation(begin_tag), *it++);
    EXPECT_EQUAL(Annotation(end_tag), *it++);
    EXPECT_TRUE(it == tree.end());
}

TEST("requireThatAnnotationsCanHaveValues") {
    PrimitiveDataType double_type(DataType::T_DOUBLE);
    StructDataType city_data_type("city");
    city_data_type.addField(Field("latitude", 0, double_type));
    city_data_type.addField(Field("longitude", 1, double_type));

    auto position = std::make_unique<StructFieldValue>(city_data_type);
    position->setValue("latitude", DoubleFieldValue(37.774929));
    position->setValue("longitude", DoubleFieldValue(-122.419415));
    StructFieldValue original(*position);

    Annotation city(city_type, std::move(position));

    EXPECT_TRUE(*city.getFieldValue() == original);
}

TEST("requireThatAnnotationsCanReferenceAnnotations") {
    auto root = std::make_unique<SpanList>();
    SpanTree tree("html", std::move(root));
    size_t san_index = tree.annotate(Annotation(text_type));
    size_t fran_index = tree.annotate(Annotation(text_type));

    AnnotationReferenceDataType annotation_ref_type(text_type, 101);
    ArrayDataType array_type(annotation_ref_type);
    StructDataType city_data_type("name", 42);
    city_data_type.addField(Field("references", 0, array_type));

    auto city_data = std::make_unique<StructFieldValue>(city_data_type);
    ArrayFieldValue ref_list(array_type);
    ref_list.add(AnnotationReferenceFieldValue(annotation_ref_type, san_index));
    ref_list.add(AnnotationReferenceFieldValue(annotation_ref_type, fran_index));
    city_data->setValue("references", ref_list);
    StructFieldValue original(*city_data);

    Annotation city(city_type, std::move(city_data));

    ASSERT_TRUE(city.getFieldValue());
    EXPECT_EQUAL(original, *city.getFieldValue());
}

const double prob0 = 0.6;
const double prob1 = 0.4;

TEST("requireThatAlternateSpanListHoldsMultipleLists") {
    AlternateSpanList span_list;
    span_list.add(0, makeUP(new Span(0, 19)));
    span_list.add(0, makeUP(new Span(19, 5)));
    span_list.add(1, makeUP(new Span(0, 5)));
    span_list.add(1, makeUP(new Span(5, 19)));
    span_list.setProbability(0, prob0);
    span_list.setProbability(1, prob1);

    EXPECT_EQUAL(2u, span_list.getNumSubtrees());
    EXPECT_EQUAL(2u, span_list.getSubtree(0).size());
    EXPECT_EQUAL(2u, span_list.getSubtree(1).size());
    EXPECT_EQUAL(prob0, span_list.getProbability(0));
    EXPECT_EQUAL(prob1, span_list.getProbability(1));

    SpanList::const_iterator it = span_list.getSubtree(0).begin();
    EXPECT_EQUAL(Span(0, 19), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(19, 5), *(static_cast<Span *>(*it++)));
    EXPECT_TRUE(it == span_list.getSubtree(0).end());

    it = span_list.getSubtree(1).begin();
    EXPECT_EQUAL(Span(0, 5), *(static_cast<Span *>(*it++)));
    EXPECT_EQUAL(Span(5, 19), *(static_cast<Span *>(*it++)));
    EXPECT_TRUE(it == span_list.getSubtree(1).end());
}

struct MySpanTreeVisitor : SpanTreeVisitor {
    int span_count;
    int span_list_count;
    int alt_span_list_count;

    MySpanTreeVisitor()
        : span_count(0), span_list_count(0), alt_span_list_count(0) {}

    void visitChildren(const SpanList &node) {
        for (SpanList::const_iterator
                 it = node.begin(); it != node.end(); ++it) {
            (*it)->accept(*this);
        }
    }

    void visitChildren(const SimpleSpanList &node) {
        for (SimpleSpanList::const_iterator
                 it = node.begin(); it != node.end(); ++it) {
            (*it).accept(*this);
        }
    }

    void visit(const Span &) override { ++span_count; }
    void visit(const SpanList &node) override {
        ++span_list_count;
        visitChildren(node);
    }
    void visit(const SimpleSpanList &node) override {
        ++span_list_count;
        visitChildren(node);
    }
    void visit(const AlternateSpanList &node) override {
        ++alt_span_list_count;
        for (size_t i = 0; i < node.getNumSubtrees(); ++i) {
            visitChildren(node.getSubtree(i));
        }
    }
};

TEST("requireThatSpanTreeCanBeVisited") {
    SpanList::UP root(new SpanList);
    root->add(makeUP(new Span(0, 19)));
    AlternateSpanList::UP alt_list(new AlternateSpanList);
    alt_list->add(0, makeUP(new Span(19, 5)));
    alt_list->add(1, makeUP(new Span(24, 21)));
    root->add(std::move(alt_list));

    SpanTree tree("html", std::move(root));

    MySpanTreeVisitor visitor;
    tree.accept(visitor);

    EXPECT_EQUAL(3, visitor.span_count);
    EXPECT_EQUAL(1, visitor.span_list_count);
    EXPECT_EQUAL(1, visitor.alt_span_list_count);
}

TEST("requireThatDefaultAnnotationTypesHaveDefaultDataTypes") {
    ASSERT_TRUE(AnnotationType::TERM->getDataType());
    EXPECT_EQUAL(*DataType::STRING, *AnnotationType::TERM->getDataType());
    ASSERT_TRUE(AnnotationType::TOKEN_TYPE->getDataType());
    EXPECT_EQUAL(*DataType::INT, *AnnotationType::TOKEN_TYPE->getDataType());
}

TEST("require that SpanTrees can be compared") {
    SpanList::UP root(new SpanList);
    root->add(makeUP(new Span(0, 19)));
    SpanTree tree1("html", std::move(root));

    root.reset(new SpanList);
    root->add(makeUP(new Span(0, 18)));
    SpanTree tree2("html", std::move(root));

    EXPECT_EQUAL(0, tree1.compare(tree1));
    EXPECT_LESS(0, tree1.compare(tree2));
    EXPECT_GREATER(0, tree2.compare(tree1));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
