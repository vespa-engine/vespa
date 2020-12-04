// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for stackdumpquerycreator.

#include <vespa/log/log.h>
LOG_SETUP("stackdumpquerycreator_test");

#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpquerycreator.h>
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/testkit/testapp.h>

using search::ParseItem;
using search::RawBuf;
using search::SimpleQueryStackDumpIterator;
using std::string;
using namespace search::query;

namespace {

template <typename T>
void append(RawBuf &buf, T i) {
    buf.preAlloc(sizeof(T));
    buf.PutToInet(i);
}

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

TEST("requireThatTooLargeNumTermIsTreatedAsFloat") {
    const string term_string("99999999999999999999999999999999999");
    RawBuf buf(1024);
    appendNumTerm(buf, term_string);

    SimpleQueryStackDumpIterator query_stack(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
    Node::UP node =
        StackDumpQueryCreator<SimpleQueryNodeTypes>::create(query_stack);
    ASSERT_TRUE(node.get());
    NumberTerm *term = dynamic_cast<NumberTerm *>(node.get());
    ASSERT_TRUE(term);
    EXPECT_EQUAL(term_string, term->getTerm());
}

TEST("requireThatTooLargeFloatNumTermIsTreatedAsFloat") {
    const string term_string = "1" + string(310, '0') + ".20";
    RawBuf buf(1024);
    appendNumTerm(buf, term_string);

    SimpleQueryStackDumpIterator
        query_stack(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
    Node::UP node =
        StackDumpQueryCreator<SimpleQueryNodeTypes>::create(query_stack);
    ASSERT_TRUE(node.get());
    NumberTerm *term = dynamic_cast<NumberTerm *>(node.get());
    ASSERT_TRUE(term);
    EXPECT_EQUAL(term_string, term->getTerm());
}

TEST("require that PredicateQueryItem stack dump item can be read") {
    RawBuf buf(1024);
    uint8_t typefield = ParseItem::ITEM_PREDICATE_QUERY;
    buf.append(typefield);
    appendString(buf, "view_name");

    buf.appendCompressedNumber(2);
    appendString(buf, "key1");
    appendString(buf, "value1");
    buf.Put64ToInet(-1ULL);
    appendString(buf, "key2");
    appendString(buf, "value2");
    buf.Put64ToInet(0xffffULL);

    buf.appendCompressedNumber(2);
    appendString(buf, "key3");
    buf.Put64ToInet(42ULL);
    buf.Put64ToInet(-1ULL);
    appendString(buf, "key4");
    buf.Put64ToInet(84ULL);
    buf.Put64ToInet(0xffffULL);

    SimpleQueryStackDumpIterator
        query_stack(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
    Node::UP node =
        StackDumpQueryCreator<SimpleQueryNodeTypes>::create(query_stack);
    ASSERT_TRUE(node.get());
    PredicateQuery *p = dynamic_cast<PredicateQuery *>(node.get());
    ASSERT_TRUE(p);
    const PredicateQueryTerm &term = *p->getTerm();
    ASSERT_EQUAL(2u, term.getFeatures().size());
    ASSERT_EQUAL(2u, term.getRangeFeatures().size());
    ASSERT_EQUAL("value1", term.getFeatures()[0].getValue());
    ASSERT_EQUAL(0xffffffffffffffffULL,
                 term.getFeatures()[0].getSubQueryBitmap());
    ASSERT_EQUAL("key2", term.getFeatures()[1].getKey());
    ASSERT_EQUAL(42u, term.getRangeFeatures()[0].getValue());
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
