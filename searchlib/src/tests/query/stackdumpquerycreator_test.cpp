// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for stackdumpquerycreator.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("stackdumpquerycreator_test");
#include <vespa/searchlib/query/tree/stackdumpquerycreator.h>

using search::ParseItem;
using search::RawBuf;
using search::SerializedQueryTree;
using std::string;
using namespace search::query;

namespace {

void appendString(RawBuf &buf, const string &s) {
    buf.preAlloc(sizeof(uint32_t) + s.size());
    buf.appendCompressedPositiveNumber(s.size());
    buf.append(s.data(), s.size());
}

void appendNumTerm(RawBuf &buf, const string &term_string) {
    uint8_t typefield = static_cast<uint8_t>(ParseItem::ITEM_NUMTERM) |
                        static_cast<uint8_t>(ParseItem::IF_WEIGHT) |
                        static_cast<uint8_t>(ParseItem::IF_UNIQUEID);
    buf.append(typefield);
    buf.appendCompressedNumber(2);  // weight
    buf.appendCompressedPositiveNumber(42);  // id
    appendString(buf, "view_name");
    appendString(buf, term_string);
}

TEST(StackDumpQueryCreatorTest, requireThatTooLargeNumTermIsTreatedAsFloat) {
    const string term_string("99999999999999999999999999999999999");
    RawBuf buf(1024);
    appendNumTerm(buf, term_string);

    auto serializedQueryTree = SerializedQueryTree::fromStackDump(std::string_view(buf.GetDrainPos(), buf.GetUsedLen()));
    auto query_stack = serializedQueryTree->makeIterator();
    Node::UP node = StackDumpQueryCreator<SimpleQueryNodeTypes>::create(*query_stack);
    ASSERT_TRUE(node.get());
    auto *term = dynamic_cast<NumberTerm *>(node.get());
    ASSERT_TRUE(term);
    EXPECT_EQ(term_string, term->getTerm());
}

TEST(StackDumpQueryCreatorTest, requireThatTooLargeFloatNumTermIsTreatedAsFloat) {
    const string term_string = "1" + string(310, '0') + ".20";
    RawBuf buf(1024);
    appendNumTerm(buf, term_string);

    auto serializedQueryTree = SerializedQueryTree::fromStackDump(std::string_view(buf.GetDrainPos(), buf.GetUsedLen()));
    auto query_stack = serializedQueryTree->makeIterator();
    Node::UP node =
        StackDumpQueryCreator<SimpleQueryNodeTypes>::create(*query_stack);
    ASSERT_TRUE(node.get());
    auto *term = dynamic_cast<NumberTerm *>(node.get());
    ASSERT_TRUE(term);
    EXPECT_EQ(term_string, term->getTerm());
}

TEST(StackDumpQueryCreatorTest, require_that_PredicateQueryItem_stack_dump_item_can_be_read) {
    RawBuf buf(1024);
    uint8_t typefield = ParseItem::ITEM_PREDICATE_QUERY;
    buf.append(typefield);
    appendString(buf, "view_name");

    buf.appendCompressedNumber(2);
    appendString(buf, "key1");
    appendString(buf, "value1");
    buf.Put64ToInet(-1UL);
    appendString(buf, "key2");
    appendString(buf, "value2");
    buf.Put64ToInet(0xffffUL);

    buf.appendCompressedNumber(2);
    appendString(buf, "key3");
    buf.Put64ToInet(42UL);
    buf.Put64ToInet(-1UL);
    appendString(buf, "key4");
    buf.Put64ToInet(84UL);
    buf.Put64ToInet(0xffffUL);

    auto serializedQueryTree = SerializedQueryTree::fromStackDump(std::string_view(buf.GetDrainPos(), buf.GetUsedLen()));
    auto query_stack = serializedQueryTree->makeIterator();
    Node::UP node =
        StackDumpQueryCreator<SimpleQueryNodeTypes>::create(*query_stack);
    ASSERT_TRUE(node.get());
    auto *p = dynamic_cast<PredicateQuery *>(node.get());
    ASSERT_TRUE(p);
    const PredicateQueryTerm &term = *p->getTerm();
    ASSERT_EQ(2u, term.getFeatures().size());
    ASSERT_EQ(2u, term.getRangeFeatures().size());
    ASSERT_EQ("value1", term.getFeatures()[0].getValue());
    ASSERT_EQ(0xffffffffffffffffUL,
                 term.getFeatures()[0].getSubQueryBitmap());
    ASSERT_EQ("key2", term.getFeatures()[1].getKey());
    ASSERT_EQ(42u, term.getRangeFeatures()[0].getValue());
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
