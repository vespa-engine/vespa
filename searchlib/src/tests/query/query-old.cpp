// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/query.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <limits>
#include <cmath>

using namespace search;
using namespace search::query;

void assertHit(const Hit & h, size_t expWordpos, size_t expContext, int32_t weight) {
    EXPECT_EQUAL(h.wordpos(), expWordpos);
    EXPECT_EQUAL(h.context(), expContext);
    EXPECT_EQUAL(h.weight(), weight);
}

TEST("testQueryLanguage") {
    QueryNodeResultFactory factory;
    int64_t ia(0), ib(0);
    double da(0), db(0);

    QueryTerm q(factory.create(), "7", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, 7);
    EXPECT_EQUAL(ib, 7);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, 7);
    EXPECT_EQUAL(db, 7);

    q = QueryTerm(factory.create(), "-7", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, -7);
    EXPECT_EQUAL(ib, -7);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -7);
    EXPECT_EQUAL(db, -7);

    q = QueryTerm(factory.create(), "7.5", "index", QueryTerm::WORD);
    EXPECT_TRUE(!q.getAsIntegerTerm(ia, ib));
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, 7.5);
    EXPECT_EQUAL(db, 7.5);

    q = QueryTerm(factory.create(), "-7.5", "index", QueryTerm::WORD);
    EXPECT_TRUE(!q.getAsIntegerTerm(ia, ib));
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -7.5);
    EXPECT_EQUAL(db, -7.5);

    q = QueryTerm(factory.create(), "<7", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, std::numeric_limits<int64_t>::min());
    EXPECT_EQUAL(ib, 6);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -std::numeric_limits<double>::max());
    EXPECT_LESS(db, 7);
    EXPECT_GREATER(db, 6.99);

    q = QueryTerm(factory.create(), "[;7]", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, std::numeric_limits<int64_t>::min());
    EXPECT_EQUAL(ib, 7);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -std::numeric_limits<double>::max());
    EXPECT_EQUAL(db, 7);

    q = QueryTerm(factory.create(), ">7", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, 8);
    EXPECT_EQUAL(ib, std::numeric_limits<int64_t>::max());
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_GREATER(da, 7);
    EXPECT_LESS(da, 7.01);
    EXPECT_EQUAL(db, std::numeric_limits<double>::max());

    q = QueryTerm(factory.create(), "[7;]", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, 7);
    EXPECT_EQUAL(ib, std::numeric_limits<int64_t>::max());
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, 7);
    EXPECT_EQUAL(db, std::numeric_limits<double>::max());

    q = QueryTerm(factory.create(), "[-7;7]", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, -7);
    EXPECT_EQUAL(ib, 7);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -7);
    EXPECT_EQUAL(db, 7);

    q = QueryTerm(factory.create(), "[-7.1;7.1]", "index", QueryTerm::WORD);
    EXPECT_FALSE(q.getAsIntegerTerm(ia, ib)); // This is dubious and perhaps a regression.
    EXPECT_EQUAL(ia, std::numeric_limits<int64_t>::min());
    EXPECT_EQUAL(ib, std::numeric_limits<int64_t>::max());
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -7.1);
    EXPECT_EQUAL(db, 7.1);

    q = QueryTerm(factory.create(), "[500.0;1.7976931348623157E308]", "index", QueryTerm::WORD);
    EXPECT_FALSE(q.getAsIntegerTerm(ia, ib)); // This is dubious and perhaps a regression.
    EXPECT_EQUAL(ia, std::numeric_limits<int64_t>::min());
    EXPECT_EQUAL(ib, std::numeric_limits<int64_t>::max());
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, 500.0);
    EXPECT_EQUAL(db, std::numeric_limits<double>::max());

    const double minusSeven(-7), seven(7);
    q = QueryTerm(factory.create(), "<-7;7]", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, -6);
    EXPECT_EQUAL(ib, 7);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, std::nextafterf(minusSeven, seven));
    EXPECT_EQUAL(db, seven);

    q = QueryTerm(factory.create(), "<-7;7>", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, -6);
    EXPECT_EQUAL(ib, 6);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, std::nextafterf(minusSeven, seven));
    EXPECT_EQUAL(db, std::nextafterf(seven, minusSeven));

    q = QueryTerm(factory.create(), "<1;2>", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, 2);
    EXPECT_EQUAL(ib, 1);

    q = QueryTerm(factory.create(), "[-7;7>", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, -7);
    EXPECT_EQUAL(ib, 6);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, minusSeven);
    EXPECT_EQUAL(db, std::nextafterf(seven, minusSeven));

    q = QueryTerm(factory.create(), "<-7", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, std::numeric_limits<int64_t>::min());
    EXPECT_EQUAL(ib, -8);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -std::numeric_limits<double>::max());
    EXPECT_LESS(db, -7);
    EXPECT_GREATER(db, -7.01);

    q = QueryTerm(factory.create(), "[;-7]", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, std::numeric_limits<int64_t>::min());
    EXPECT_EQUAL(ib, -7);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -std::numeric_limits<double>::max());
    EXPECT_EQUAL(db, -7);

    q = QueryTerm(factory.create(), "<;-7]", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, std::numeric_limits<int64_t>::min());
    EXPECT_EQUAL(ib, -7);
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -std::numeric_limits<double>::max());
    EXPECT_EQUAL(db, -7);

    q = QueryTerm(factory.create(), ">-7", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, -6);
    EXPECT_EQUAL(ib, std::numeric_limits<int64_t>::max());
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_GREATER(da, -7);
    EXPECT_LESS(da, -6.99);
    EXPECT_EQUAL(db, std::numeric_limits<double>::max());

    q = QueryTerm(factory.create(), "[-7;]", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, -7);
    EXPECT_EQUAL(ib, std::numeric_limits<int64_t>::max());
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -7);
    EXPECT_EQUAL(db, std::numeric_limits<double>::max());

    q = QueryTerm(factory.create(), "[-7;>", "index", QueryTerm::WORD);
    EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
    EXPECT_EQUAL(ia, -7);
    EXPECT_EQUAL(ib, std::numeric_limits<int64_t>::max());
    EXPECT_TRUE(q.getAsDoubleTerm(da, db));
    EXPECT_EQUAL(da, -7);
    EXPECT_EQUAL(db, std::numeric_limits<double>::max());

    q = QueryTerm(factory.create(), "a", "index", QueryTerm::WORD);
    EXPECT_TRUE(!q.getAsIntegerTerm(ia, ib));
    EXPECT_TRUE(!q.getAsDoubleTerm(da, db));

    q = QueryTerm(factory.create(), "word", "index", QueryTerm::WORD);
    EXPECT_TRUE(!q.isPrefix());
    EXPECT_TRUE(!q.isSubstring());
    EXPECT_TRUE(!q.isSuffix());

    q = QueryTerm(factory.create(), "prefix", "index", QueryTerm::PREFIXTERM);
    EXPECT_TRUE(q.isPrefix());
    EXPECT_TRUE(!q.isSubstring());
    EXPECT_TRUE(!q.isSuffix());

    q = QueryTerm(factory.create(), "substring", "index", QueryTerm::SUBSTRINGTERM);
    EXPECT_TRUE(!q.isPrefix());
    EXPECT_TRUE(q.isSubstring());
    EXPECT_TRUE(!q.isSuffix());

    q = QueryTerm(factory.create(), "suffix", "index", QueryTerm::SUFFIXTERM);
    EXPECT_TRUE(!q.isPrefix());
    EXPECT_TRUE(!q.isSubstring());
    EXPECT_TRUE(q.isSuffix());

    q = QueryTerm(factory.create(), "regexp", "index", QueryTerm::REGEXP);
    EXPECT_TRUE(!q.isPrefix());
    EXPECT_TRUE(!q.isSubstring());
    EXPECT_TRUE(!q.isSuffix());
    EXPECT_TRUE(q.isRegex());
}

class AllowRewrite : public QueryNodeResultFactory
{
public:
    virtual bool getRewriteFloatTerms() const override { return true; }
};

const char TERM_UNIQ = ParseItem::ITEM_TERM | ParseItem::IF_UNIQUEID;

TEST("e is not rewritten even if allowed") {
    const char term[6] = {TERM_UNIQ, 3, 1, 'c', 1, 'e'};
    vespalib::stringref stackDump(term, sizeof(term));
    EXPECT_EQUAL(6u, stackDump.size());
    AllowRewrite allowRewrite;
    const Query q(allowRewrite, stackDump);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    EXPECT_TRUE(dynamic_cast<const QueryTerm *>(&root) != nullptr);
    const QueryTerm & qt = static_cast<const QueryTerm &>(root);
    EXPECT_EQUAL("c", qt.index());
    EXPECT_EQUAL(vespalib::stringref("e"), qt.getTerm());
    EXPECT_EQUAL(3u, qt.uniqueId());
}

TEST("1.0e is not rewritten by default") {
    const char term[9] = {TERM_UNIQ, 3, 1, 'c', 4, '1', '.', '0', 'e'};
    vespalib::stringref stackDump(term, sizeof(term));
    EXPECT_EQUAL(9u, stackDump.size());
    QueryNodeResultFactory empty;
    const Query q(empty, stackDump);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    EXPECT_TRUE(dynamic_cast<const QueryTerm *>(&root) != nullptr);
    const QueryTerm & qt = static_cast<const QueryTerm &>(root);
    EXPECT_EQUAL("c", qt.index());
    EXPECT_EQUAL(vespalib::stringref("1.0e"), qt.getTerm());
    EXPECT_EQUAL(3u, qt.uniqueId());
}

TEST("1.0e is rewritten if allowed too.") {
    const char term[9] = {TERM_UNIQ, 3, 1, 'c', 4, '1', '.', '0', 'e'};
    vespalib::stringref stackDump(term, sizeof(term));
    EXPECT_EQUAL(9u, stackDump.size());
    AllowRewrite empty;
    const Query q(empty, stackDump);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    EXPECT_TRUE(dynamic_cast<const EquivQueryNode *>(&root) != nullptr);
    const EquivQueryNode & equiv = static_cast<const EquivQueryNode &>(root);
    EXPECT_EQUAL(2u, equiv.size());
    EXPECT_TRUE(dynamic_cast<const QueryTerm *>(equiv[0].get()) != nullptr);
    {
        const QueryTerm & qt = static_cast<const QueryTerm &>(*equiv[0]);
        EXPECT_EQUAL("c", qt.index());
        EXPECT_EQUAL(vespalib::stringref("1.0e"), qt.getTerm());
        EXPECT_EQUAL(3u, qt.uniqueId());
    }
    EXPECT_TRUE(dynamic_cast<const PhraseQueryNode *>(equiv[1].get()) != nullptr);
    {
        const PhraseQueryNode & phrase = static_cast<const PhraseQueryNode &>(*equiv[1]);
        EXPECT_EQUAL(2u, phrase.size());
        EXPECT_TRUE(dynamic_cast<const QueryTerm *>(phrase[0].get()) != nullptr);
        {
            const QueryTerm & qt = static_cast<const QueryTerm &>(*phrase[0]);
            EXPECT_EQUAL("c", qt.index());
            EXPECT_EQUAL(vespalib::stringref("1"), qt.getTerm());
            EXPECT_EQUAL(0u, qt.uniqueId());
        }
        EXPECT_TRUE(dynamic_cast<const QueryTerm *>(phrase[1].get()) != nullptr);
        {
            const QueryTerm & qt = static_cast<const QueryTerm &>(*phrase[1]);
            EXPECT_EQUAL("c", qt.index());
            EXPECT_EQUAL(vespalib::stringref("0e"), qt.getTerm());
            EXPECT_EQUAL(0u, qt.uniqueId());
        }
    }
}

TEST("testGetQueryParts") {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(4);
    {
        builder.addStringTerm("a", "", 0, Weight(0));
        builder.addPhrase(3, "", 0, Weight(0));
        {
            builder.addStringTerm("b", "", 0, Weight(0));
            builder.addStringTerm("c", "", 0, Weight(0));
            builder.addStringTerm("d", "", 0, Weight(0));
        }
        builder.addStringTerm("e", "", 0, Weight(0));
        builder.addPhrase(2, "", 0, Weight(0));
        {
            builder.addStringTerm("f", "", 0, Weight(0));
            builder.addStringTerm("g", "", 0, Weight(0));
        }
    }
    Node::UP node = builder.build();
    vespalib::string stackDump = StackDumpCreator::create(*node);

    QueryNodeResultFactory empty;
    Query q(empty, stackDump);
    QueryTermList terms;
    QueryNodeRefList phrases;
    q.getLeafs(terms);
    q.getPhrases(phrases);
    ASSERT_TRUE(terms.size() == 7);
    ASSERT_TRUE(phrases.size() == 2);
    {
        QueryTermList pts;
        phrases[0]->getLeafs(pts);
        ASSERT_TRUE(pts.size() == 3);
        for (size_t i = 0; i < 3; ++i) {
            EXPECT_EQUAL(pts[i], terms[i + 1]);
        }
    }
    {
        QueryTermList pts;
        phrases[1]->getLeafs(pts);
        ASSERT_TRUE(pts.size() == 2);
        for (size_t i = 0; i < 2; ++i) {
            EXPECT_EQUAL(pts[i], terms[i + 5]);
        }
    }
}

TEST("testPhraseEvaluate") {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addPhrase(3, "", 0, Weight(0));
    {
        builder.addStringTerm("a", "", 0, Weight(0));
        builder.addStringTerm("b", "", 0, Weight(0));
        builder.addStringTerm("c", "", 0, Weight(0));
    }
    Node::UP node = builder.build();
    vespalib::string stackDump = StackDumpCreator::create(*node);
    QueryNodeResultFactory empty;
    Query q(empty, stackDump);
    QueryNodeRefList phrases;
    q.getPhrases(phrases);
    QueryTermList terms;
    q.getLeafs(terms);
    for (QueryTerm * qt : terms) {
        qt->resizeFieldId(1);
    }

    // field 0
    terms[0]->add(0, 0, 0, 1);
    terms[1]->add(1, 0, 0, 1);
    terms[2]->add(2, 0, 0, 1);
    terms[0]->add(7, 0, 0, 1);
    terms[1]->add(8, 0, 0, 1);
    terms[2]->add(9, 0, 0, 1);
    // field 1
    terms[0]->add(4, 1, 0, 1);
    terms[1]->add(5, 1, 0, 1);
    terms[2]->add(6, 1, 0, 1);
    // field 2 (not complete match)
    terms[0]->add(1, 2, 0, 1);
    terms[1]->add(2, 2, 0, 1);
    terms[2]->add(4, 2, 0, 1);
    // field 3
    terms[0]->add(0, 3, 0, 1);
    terms[1]->add(1, 3, 0, 1);
    terms[2]->add(2, 3, 0, 1);
    // field 4 (not complete match)
    terms[0]->add(1, 4, 0, 1);
    terms[1]->add(2, 4, 0, 1);
    // field 5 (not complete match)
    terms[0]->add(2, 5, 0, 1);
    terms[1]->add(1, 5, 0, 1);
    terms[2]->add(0, 5, 0, 1);
    HitList hits;
    PhraseQueryNode * p = static_cast<PhraseQueryNode *>(phrases[0]);
    p->evaluateHits(hits);
    ASSERT_TRUE(hits.size() == 4);
    EXPECT_EQUAL(hits[0].wordpos(), 2u);
    EXPECT_EQUAL(hits[0].context(), 0u);
    EXPECT_EQUAL(hits[1].wordpos(), 9u);
    EXPECT_EQUAL(hits[1].context(), 0u);
    EXPECT_EQUAL(hits[2].wordpos(), 6u);
    EXPECT_EQUAL(hits[2].context(), 1u);
    EXPECT_EQUAL(hits[3].wordpos(), 2u);
    EXPECT_EQUAL(hits[3].context(), 3u);
    ASSERT_TRUE(p->getFieldInfoSize() == 4);
    EXPECT_EQUAL(p->getFieldInfo(0).getHitOffset(), 0u);
    EXPECT_EQUAL(p->getFieldInfo(0).getHitCount(),  2u);
    EXPECT_EQUAL(p->getFieldInfo(1).getHitOffset(), 2u);
    EXPECT_EQUAL(p->getFieldInfo(1).getHitCount(),  1u);
    EXPECT_EQUAL(p->getFieldInfo(2).getHitOffset(), 0u); // invalid, but will never be used
    EXPECT_EQUAL(p->getFieldInfo(2).getHitCount(),  0u);
    EXPECT_EQUAL(p->getFieldInfo(3).getHitOffset(), 3u);
    EXPECT_EQUAL(p->getFieldInfo(3).getHitCount(),  1u);
}

TEST("testHit") {
    // positions (0 - (2^24-1))
    assertHit(Hit(0,        0, 0, 0),        0, 0, 0);
    assertHit(Hit(256,      0, 0, 1),      256, 0, 1);
    assertHit(Hit(16777215, 0, 0, -1), 16777215, 0, -1);
    assertHit(Hit(16777216, 0, 0, 1),        0, 1, 1); // overflow

    // contexts (0 - 255)
    assertHit(Hit(0,   1, 0, 1), 0,   1, 1);
    assertHit(Hit(0, 255, 0, 1), 0, 255, 1);
    assertHit(Hit(0, 256, 0, 1), 0,   0, 1); // overflow
}

void assertInt8Range(const std::string &term, bool expAdjusted, int64_t expLow, int64_t expHigh) {
    QueryTermSimple q(term, QueryTermSimple::WORD);
    QueryTermSimple::RangeResult<int8_t> res = q.getRange<int8_t>();
    EXPECT_EQUAL(true, res.valid);
    EXPECT_EQUAL(expAdjusted, res.adjusted);
    EXPECT_EQUAL(expLow, (int64_t)res.low);
    EXPECT_EQUAL(expHigh, (int64_t)res.high);
}

void assertInt32Range(const std::string &term, bool expAdjusted, int64_t expLow, int64_t expHigh) {
    QueryTermSimple q(term, QueryTermSimple::WORD);
    QueryTermSimple::RangeResult<int32_t> res = q.getRange<int32_t>();
    EXPECT_EQUAL(true, res.valid);
    EXPECT_EQUAL(expAdjusted, res.adjusted);
    EXPECT_EQUAL(expLow, (int64_t)res.low);
    EXPECT_EQUAL(expHigh, (int64_t)res.high);
}

void assertInt64Range(const std::string &term, bool expAdjusted, int64_t expLow, int64_t expHigh) {
    QueryTermSimple q(term, QueryTermSimple::WORD);
    QueryTermSimple::RangeResult<int64_t> res = q.getRange<int64_t>();
    EXPECT_EQUAL(true, res.valid);
    EXPECT_EQUAL(expAdjusted, res.adjusted);
    EXPECT_EQUAL(expLow, (int64_t)res.low);
    EXPECT_EQUAL(expHigh, (int64_t)res.high);
}

TEST("requireThatInt8LimitsAreEnforced") {
    //std::numeric_limits<int8_t>::min() -> -128
    //std::numeric_limits<int8_t>::max() -> 127

    assertInt8Range("-129", true, -128, -128);
    assertInt8Range("-128", false, -128, -128);
    assertInt8Range("127", false, 127, 127);
    assertInt8Range("128", true, 127, 127);
    assertInt8Range("[-129;0]", true, -128, 0);
    assertInt8Range("[-128;0]", false, -128, 0);
    assertInt8Range("[0;127]", false, 0, 127);
    assertInt8Range("[0;128]", true, 0, 127);
    assertInt8Range("[-130;-129]", true, -128, -128);
    assertInt8Range("[128;129]", true, 127, 127);
    assertInt8Range("[-129;128]", true, -128, 127);
}

TEST("requireThatInt32LimitsAreEnforced") {
    //std::numeric_limits<int32_t>::min() -> -2147483648
    //std::numeric_limits<int32_t>::max() -> 2147483647

    int64_t min = std::numeric_limits<int32_t>::min();
    int64_t max = std::numeric_limits<int32_t>::max();

    assertInt32Range("-2147483649", true, min, min);
    assertInt32Range("-2147483648", false, min, min);
    assertInt32Range("2147483647", false, max, max);
    assertInt32Range("2147483648", true, max, max);
    assertInt32Range("[-2147483649;0]", true, min, 0);
    assertInt32Range("[-2147483648;0]", false, min, 0);
    assertInt32Range("[0;2147483647]", false, 0, max);
    assertInt32Range("[0;2147483648]", true, 0, max);
    assertInt32Range("[-2147483650;-2147483649]", true, min, min);
    assertInt32Range("[2147483648;2147483649]", true, max, max);
    assertInt32Range("[-2147483649;2147483648]", true, min, max);
}

TEST("requireThatInt64LimitsAreEnforced") {
    //std::numeric_limits<int64_t>::min() -> -9223372036854775808
    //std::numeric_limits<int64_t>::max() -> 9223372036854775807

    int64_t min = std::numeric_limits<int64_t>::min();
    int64_t max = std::numeric_limits<int64_t>::max();

    assertInt64Range("-9223372036854775809", false, min, min);
    assertInt64Range("-9223372036854775808", false, min, min);
    assertInt64Range("9223372036854775807", false, max, max);
    assertInt64Range("9223372036854775808", false, max, max);
    assertInt64Range("[-9223372036854775809;0]", false, min, 0);
    assertInt64Range("[-9223372036854775808;0]", false, min, 0);
    assertInt64Range("[0;9223372036854775807]", false, 0, max);
    assertInt64Range("[0;9223372036854775808]", false, 0, max);
    assertInt64Range("[-9223372036854775810;-9223372036854775809]", false, min, min);
    assertInt64Range("[9223372036854775808;9223372036854775809]", false, max, max);
    assertInt64Range("[-9223372036854775809;9223372036854775808]", false, min, max);
}

TEST("require sensible rounding when using integer attributes.") {
    assertInt64Range("1.2", false, 1, 1);
    assertInt64Range("1.51", false, 2, 2);
    assertInt64Range("2.49", false, 2, 2);
}

TEST("require that we can take floating point values in range search too.") {
    assertInt64Range("[1;2]", false, 1, 2);
    assertInt64Range("[1.1;2.1]", false, 2, 2);
    assertInt64Range("[1.9;3.9]", false, 2, 3);
    assertInt64Range("[1.9;3.9]", false, 2, 3);
    assertInt64Range("[1.0;3.0]", false, 1, 3);
    assertInt64Range("<1.0;3.0>", false, 2, 2);
    assertInt64Range("[500.0;1.7976931348623157E308]", false, 500, std::numeric_limits<int64_t>::max());
    assertInt64Range("[500.0;1.6976931348623157E308]", false, 500, std::numeric_limits<int64_t>::max());
    assertInt64Range("[-1.7976931348623157E308;500.0]", false, std::numeric_limits<int64_t>::min(), 500);
    assertInt64Range("[-1.6976931348623157E308;500.0]", false, std::numeric_limits<int64_t>::min(), 500);
    assertInt64Range("[10;-10]", false, 10, -10);
    assertInt64Range("[10.0;-10.0]", false, 10, -10);
    assertInt64Range("[1.6976931348623157E308;-1.6976931348623157E308]", false, std::numeric_limits<int64_t>::max(), std::numeric_limits<int64_t>::min());
    assertInt64Range("[1.7976931348623157E308;-1.7976931348623157E308]", false, std::numeric_limits<int64_t>::max(), std::numeric_limits<int64_t>::min());
}

TEST("require that we handle empty range as expected") {
    assertInt64Range("[1;1]", false, 1, 1);
    assertInt64Range("<1;1]", false, 2, 1);
    assertInt64Range("[0;1>", false, 0, 0);
    assertInt64Range("[1;1>", false, 1, 0);
    assertInt64Range("<1;1>", false, 2, 0);
}

TEST("require that ascending range can be specified with limit only") {
    int64_t low_integer = 0;
    int64_t high_integer = 0;
    double low_double = 0.0;
    double high_double = 0.0;

    QueryNodeResultFactory eqnr;
    QueryTerm ascending_query(eqnr.create(), "[;;500]", "index", QueryTerm::WORD);

    EXPECT_TRUE(ascending_query.getAsIntegerTerm(low_integer, high_integer));
    EXPECT_TRUE(ascending_query.getAsDoubleTerm(low_double, high_double));
    EXPECT_EQUAL(std::numeric_limits<int64_t>::min(), low_integer);
    EXPECT_EQUAL(std::numeric_limits<int64_t>::max(), high_integer);
    EXPECT_EQUAL(-std::numeric_limits<double>::max(), low_double);
    EXPECT_EQUAL(std::numeric_limits<double>::max(), high_double);
    EXPECT_EQUAL(500, ascending_query.getRangeLimit());
}

TEST("require that descending range can be specified with limit only") {
    int64_t low_integer = 0;
    int64_t high_integer = 0;
    double low_double = 0.0;
    double high_double = 0.0;

    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500]", "index", QueryTerm::WORD);

    EXPECT_TRUE(descending_query.getAsIntegerTerm(low_integer, high_integer));
    EXPECT_TRUE(descending_query.getAsDoubleTerm(low_double, high_double));
    EXPECT_EQUAL(std::numeric_limits<int64_t>::min(), low_integer);
    EXPECT_EQUAL(std::numeric_limits<int64_t>::max(), high_integer);
    EXPECT_EQUAL(-std::numeric_limits<double>::max(), low_double);
    EXPECT_EQUAL(std::numeric_limits<double>::max(), high_double);
    EXPECT_EQUAL(-500, descending_query.getRangeLimit());
}

TEST("require that correctly specified diversity can be parsed") {
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78]", "index", QueryTerm::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQUAL(-500, descending_query.getRangeLimit());
    EXPECT_EQUAL("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQUAL(78u, descending_query.getMaxPerGroup());
    EXPECT_EQUAL(std::numeric_limits<uint32_t>::max(), descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST("require that correctly specified diversity with cutoff groups can be parsed") {
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;93]", "index", QueryTerm::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQUAL(-500, descending_query.getRangeLimit());
    EXPECT_EQUAL("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQUAL(78u, descending_query.getMaxPerGroup());
    EXPECT_EQUAL(93u, descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST("require that correctly specified diversity with cutoff groups can be parsed") {
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;13]", "index", QueryTerm::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQUAL(-500, descending_query.getRangeLimit());
    EXPECT_EQUAL("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQUAL(78u, descending_query.getMaxPerGroup());
    EXPECT_EQUAL(13u, descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST("require that correctly specified diversity with incorrect cutoff groups can be parsed") {
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;a13.9]", "index", QueryTerm::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQUAL(-500, descending_query.getRangeLimit());
    EXPECT_EQUAL("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQUAL(78u, descending_query.getMaxPerGroup());
    EXPECT_EQUAL(std::numeric_limits<uint32_t>::max(), descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST("require that correctly specified diversity with cutoff strategy can be parsed") {
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;93;anything but strict]", "index", QueryTerm::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQUAL(-500, descending_query.getRangeLimit());
    EXPECT_EQUAL("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQUAL(78u, descending_query.getMaxPerGroup());
    EXPECT_EQUAL(93u, descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST("require that correctly specified diversity with strict cutoff strategy can be parsed") {
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;93;strict]", "index", QueryTerm::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQUAL(-500, descending_query.getRangeLimit());
    EXPECT_EQUAL("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQUAL(78u, descending_query.getMaxPerGroup());
    EXPECT_EQUAL(93u, descending_query.getDiversityCutoffGroups());
    EXPECT_TRUE(descending_query.getDiversityCutoffStrict());
}

TEST("require that incorrectly specified diversity can be parsed") {
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56]", "index", QueryTerm::WORD);
    EXPECT_FALSE(descending_query.isValid());
}

TEST("require that we do not break the stack on bad query") {
    QueryTermSimple term("<form><iframe+&#09;&#10;&#11;+src=\\\"javascript&#58;alert(1)\\\"&#11;&#10;&#09;;>", QueryTerm::WORD);
    EXPECT_FALSE(term.isValid());
}

TEST_MAIN() { TEST_RUN_ALL(); }
