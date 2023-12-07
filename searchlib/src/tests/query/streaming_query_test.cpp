// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/query/streaming/in_term.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/nearest_neighbor_query_node.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/query/tree/string_term_vector.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <limits>
#include <cmath>

using namespace search;
using namespace search::query;
using namespace search::streaming;
using TermType = QueryTerm::Type;
using search::fef::SimpleTermData;
using search::fef::MatchData;

void assertHit(const Hit & h, size_t expWordpos, size_t expContext, int32_t weight) {
    EXPECT_EQ(h.wordpos(), expWordpos);
    EXPECT_EQ(h.context(), expContext);
    EXPECT_EQ(h.weight(), weight);
}

TEST(StreamingQueryTest, test_query_language)
{
    QueryNodeResultFactory factory;
    int64_t ia(0), ib(0);
    double da(0), db(0);

    {
        QueryTerm q(factory.create(), "7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, 7);
        EXPECT_EQ(ib, 7);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, 7);
        EXPECT_EQ(db, 7);
    }

    {
        QueryTerm q(factory.create(), "-7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -7);
        EXPECT_EQ(ib, -7);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -7);
        EXPECT_EQ(db, -7);
    }

    {
        QueryTerm q(factory.create(), "7.5", "index", TermType::WORD);
        EXPECT_TRUE(!q.getAsIntegerTerm(ia, ib));
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, 7.5);
        EXPECT_EQ(db, 7.5);
    }

    {
        QueryTerm q(factory.create(), "-7.5", "index", TermType::WORD);
        EXPECT_TRUE(!q.getAsIntegerTerm(ia, ib));
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -7.5);
        EXPECT_EQ(db, -7.5);
    }

    {
        QueryTerm q(factory.create(), "<7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, 6);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::max());
        EXPECT_LT(db, 7);
        EXPECT_GT(db, 6.99);
    }

    {
        QueryTerm q(factory.create(), "[;7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, 7);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::max());
        EXPECT_EQ(db, 7);
    }

    {
        QueryTerm q(factory.create(), ">7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, 8);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_GT(da, 7);
        EXPECT_LT(da, 7.01);
        EXPECT_EQ(db, std::numeric_limits<double>::max());
    }

    {
        QueryTerm q(factory.create(), "[7;]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, 7);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, 7);
        EXPECT_EQ(db, std::numeric_limits<double>::max());
    }

    {
        QueryTerm q(factory.create(), "[-7;7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -7);
        EXPECT_EQ(ib, 7);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -7);
        EXPECT_EQ(db, 7);
    }

    {
        QueryTerm q(factory.create(), "[-7.1;7.1]", "index", TermType::WORD);
        EXPECT_FALSE(q.getAsIntegerTerm(ia, ib)); // This is dubious and perhaps a regression.
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -7.1);
        EXPECT_EQ(db, 7.1);
    }

    {
        QueryTerm q(factory.create(), "[500.0;1.7976931348623157E308]", "index", TermType::WORD);
        EXPECT_FALSE(q.getAsIntegerTerm(ia, ib)); // This is dubious and perhaps a regression.
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, 500.0);
        EXPECT_EQ(db, std::numeric_limits<double>::max());
    }

    const double minusSeven(-7), seven(7);
    {
        QueryTerm q(factory.create(), "<-7;7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -6);
        EXPECT_EQ(ib, 7);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, std::nextafterf(minusSeven, seven));
        EXPECT_EQ(db, seven);
    }

    {
        QueryTerm q(factory.create(), "<-7;7>", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -6);
        EXPECT_EQ(ib, 6);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, std::nextafterf(minusSeven, seven));
        EXPECT_EQ(db, std::nextafterf(seven, minusSeven));
    }

    {
        QueryTerm q(factory.create(), "<1;2>", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, 2);
        EXPECT_EQ(ib, 1);
    }

    {
        QueryTerm q(factory.create(), "[-7;7>", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -7);
        EXPECT_EQ(ib, 6);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, minusSeven);
        EXPECT_EQ(db, std::nextafterf(seven, minusSeven));
    }

    {
        QueryTerm q(factory.create(), "<-7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, -8);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::max());
        EXPECT_LT(db, -7);
        EXPECT_GT(db, -7.01);
    }

    {
        QueryTerm q(factory.create(), "[;-7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, -7);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::max());
        EXPECT_EQ(db, -7);
    }

    {
        QueryTerm q(factory.create(), "<;-7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, -7);
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::max());
        EXPECT_EQ(db, -7);
    }

    {
        QueryTerm q(factory.create(), ">-7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -6);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_GT(da, -7);
        EXPECT_LT(da, -6.99);
        EXPECT_EQ(db, std::numeric_limits<double>::max());
    }

    {
        QueryTerm q(factory.create(), "[-7;]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -7);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -7);
        EXPECT_EQ(db, std::numeric_limits<double>::max());
    }

    {
        QueryTerm q(factory.create(), "[-7;>", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -7);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsDoubleTerm(da, db));
        EXPECT_EQ(da, -7);
        EXPECT_EQ(db, std::numeric_limits<double>::max());
    }

    {
        QueryTerm q(factory.create(), "a", "index", TermType::WORD);
        EXPECT_TRUE(!q.getAsIntegerTerm(ia, ib));
        EXPECT_TRUE(!q.getAsDoubleTerm(da, db));
    }

    {
        QueryTerm q(factory.create(), "word", "index", TermType::WORD);
        EXPECT_TRUE(!q.isPrefix());
        EXPECT_TRUE(!q.isSubstring());
        EXPECT_TRUE(!q.isSuffix());
    }

    {
        QueryTerm q(factory.create(), "prefix", "index", TermType::PREFIXTERM);
        EXPECT_TRUE(q.isPrefix());
        EXPECT_TRUE(!q.isSubstring());
        EXPECT_TRUE(!q.isSuffix());
    }

    {
        QueryTerm q(factory.create(), "substring", "index", TermType::SUBSTRINGTERM);
        EXPECT_TRUE(!q.isPrefix());
        EXPECT_TRUE(q.isSubstring());
        EXPECT_TRUE(!q.isSuffix());
    }

    {
        QueryTerm q(factory.create(), "suffix", "index", TermType::SUFFIXTERM);
        EXPECT_TRUE(!q.isPrefix());
        EXPECT_TRUE(!q.isSubstring());
        EXPECT_TRUE(q.isSuffix());
    }

    {
        QueryTerm q(factory.create(), "regexp", "index", TermType::REGEXP);
        EXPECT_TRUE(!q.isPrefix());
        EXPECT_TRUE(!q.isSubstring());
        EXPECT_TRUE(!q.isSuffix());
        EXPECT_TRUE(q.isRegex());
    }
}

class AllowRewrite : public QueryNodeResultFactory
{
public:
    virtual bool getRewriteFloatTerms() const override { return true; }
};

const char TERM_UNIQ = static_cast<char>(ParseItem::ITEM_TERM) | static_cast<char>(ParseItem::IF_UNIQUEID);

TEST(StreamingQueryTest, e_is_not_rewritten_even_if_allowed)
{
    const char term[6] = {TERM_UNIQ, 3, 1, 'c', 1, 'e'};
    vespalib::stringref stackDump(term, sizeof(term));
    EXPECT_EQ(6u, stackDump.size());
    AllowRewrite allowRewrite;
    const Query q(allowRewrite, stackDump);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    EXPECT_TRUE(dynamic_cast<const QueryTerm *>(&root) != nullptr);
    const QueryTerm & qt = static_cast<const QueryTerm &>(root);
    EXPECT_EQ("c", qt.index());
    EXPECT_EQ(vespalib::stringref("e"), qt.getTerm());
    EXPECT_EQ(3u, qt.uniqueId());
}

TEST(StreamingQueryTest, onedot0e_is_not_rewritten_by_default)
{
    const char term[9] = {TERM_UNIQ, 3, 1, 'c', 4, '1', '.', '0', 'e'};
    vespalib::stringref stackDump(term, sizeof(term));
    EXPECT_EQ(9u, stackDump.size());
    QueryNodeResultFactory empty;
    const Query q(empty, stackDump);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    EXPECT_TRUE(dynamic_cast<const QueryTerm *>(&root) != nullptr);
    const QueryTerm & qt = static_cast<const QueryTerm &>(root);
    EXPECT_EQ("c", qt.index());
    EXPECT_EQ(vespalib::stringref("1.0e"), qt.getTerm());
    EXPECT_EQ(3u, qt.uniqueId());
}

TEST(StreamingQueryTest, onedot0e_is_rewritten_if_allowed_too)
{
    const char term[9] = {TERM_UNIQ, 3, 1, 'c', 4, '1', '.', '0', 'e'};
    vespalib::stringref stackDump(term, sizeof(term));
    EXPECT_EQ(9u, stackDump.size());
    AllowRewrite empty;
    const Query q(empty, stackDump);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    EXPECT_TRUE(dynamic_cast<const EquivQueryNode *>(&root) != nullptr);
    const EquivQueryNode & equiv = static_cast<const EquivQueryNode &>(root);
    EXPECT_EQ(2u, equiv.size());
    EXPECT_TRUE(dynamic_cast<const QueryTerm *>(equiv[0].get()) != nullptr);
    {
        const QueryTerm & qt = static_cast<const QueryTerm &>(*equiv[0]);
        EXPECT_EQ("c", qt.index());
        EXPECT_EQ(vespalib::stringref("1.0e"), qt.getTerm());
        EXPECT_EQ(3u, qt.uniqueId());
    }
    EXPECT_TRUE(dynamic_cast<const PhraseQueryNode *>(equiv[1].get()) != nullptr);
    {
        const PhraseQueryNode & phrase = static_cast<const PhraseQueryNode &>(*equiv[1]);
        EXPECT_EQ(2u, phrase.size());
        EXPECT_TRUE(dynamic_cast<const QueryTerm *>(phrase[0].get()) != nullptr);
        {
            const QueryTerm & qt = static_cast<const QueryTerm &>(*phrase[0]);
            EXPECT_EQ("c", qt.index());
            EXPECT_EQ(vespalib::stringref("1"), qt.getTerm());
            EXPECT_EQ(0u, qt.uniqueId());
        }
        EXPECT_TRUE(dynamic_cast<const QueryTerm *>(phrase[1].get()) != nullptr);
        {
            const QueryTerm & qt = static_cast<const QueryTerm &>(*phrase[1]);
            EXPECT_EQ("c", qt.index());
            EXPECT_EQ(vespalib::stringref("0e"), qt.getTerm());
            EXPECT_EQ(0u, qt.uniqueId());
        }
    }
}

TEST(StreamingQueryTest, test_get_query_parts)
{
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
    q.getLeaves(terms);
    q.getPhrases(phrases);
    ASSERT_TRUE(terms.size() == 7);
    ASSERT_TRUE(phrases.size() == 2);
    {
        QueryTermList pts;
        phrases[0]->getLeaves(pts);
        ASSERT_TRUE(pts.size() == 3);
        for (size_t i = 0; i < 3; ++i) {
            EXPECT_EQ(pts[i], terms[i + 1]);
        }
    }
    {
        QueryTermList pts;
        phrases[1]->getLeaves(pts);
        ASSERT_TRUE(pts.size() == 2);
        for (size_t i = 0; i < 2; ++i) {
            EXPECT_EQ(pts[i], terms[i + 5]);
        }
    }
}

TEST(StreamingQueryTest, test_phrase_evaluate)
{
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
    q.getLeaves(terms);
    for (QueryTerm * qt : terms) {
        qt->resizeFieldId(1);
    }

    // field 0
    terms[0]->add(0, 0, 0, 1);
    terms[1]->add(1, 0, 0, 1);
    terms[2]->add(2, 0, 0, 1);
    terms[0]->add(7, 0, 0, 1);
    terms[1]->add(8, 0, 1, 1);
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
    ASSERT_EQ(3u, hits.size());
    EXPECT_EQ(hits[0].wordpos(), 2u);
    EXPECT_EQ(hits[0].context(), 0u);
    EXPECT_EQ(hits[1].wordpos(), 6u);
    EXPECT_EQ(hits[1].context(), 1u);
    EXPECT_EQ(hits[2].wordpos(), 2u);
    EXPECT_EQ(hits[2].context(), 3u);
    ASSERT_EQ(4u, p->getFieldInfoSize());
    EXPECT_EQ(p->getFieldInfo(0).getHitOffset(), 0u);
    EXPECT_EQ(p->getFieldInfo(0).getHitCount(),  1u);
    EXPECT_EQ(p->getFieldInfo(1).getHitOffset(), 1u);
    EXPECT_EQ(p->getFieldInfo(1).getHitCount(),  1u);
    EXPECT_EQ(p->getFieldInfo(2).getHitOffset(), 0u); // invalid, but will never be used
    EXPECT_EQ(p->getFieldInfo(2).getHitCount(),  0u);
    EXPECT_EQ(p->getFieldInfo(3).getHitOffset(), 2u);
    EXPECT_EQ(p->getFieldInfo(3).getHitCount(),  1u);
    EXPECT_TRUE(p->evaluate());
}

TEST(StreamingQueryTest, test_hit)
{
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
    QueryTermSimple q(term, TermType::WORD);
    QueryTermSimple::RangeResult<int8_t> res = q.getRange<int8_t>();
    EXPECT_EQ(true, res.valid);
    EXPECT_EQ(expAdjusted, res.adjusted);
    EXPECT_EQ(expLow, (int64_t)res.low);
    EXPECT_EQ(expHigh, (int64_t)res.high);
}

void assertInt32Range(const std::string &term, bool expAdjusted, int64_t expLow, int64_t expHigh) {
    QueryTermSimple q(term, TermType::WORD);
    QueryTermSimple::RangeResult<int32_t> res = q.getRange<int32_t>();
    EXPECT_EQ(true, res.valid);
    EXPECT_EQ(expAdjusted, res.adjusted);
    EXPECT_EQ(expLow, (int64_t)res.low);
    EXPECT_EQ(expHigh, (int64_t)res.high);
}

void assertInt64Range(const std::string &term, bool expAdjusted, int64_t expLow, int64_t expHigh) {
    QueryTermSimple q(term, TermType::WORD);
    QueryTermSimple::RangeResult<int64_t> res = q.getRange<int64_t>();
    EXPECT_EQ(true, res.valid);
    EXPECT_EQ(expAdjusted, res.adjusted);
    EXPECT_EQ(expLow, (int64_t)res.low);
    EXPECT_EQ(expHigh, (int64_t)res.high);
}

TEST(StreamingQueryTest, require_that_int8_limits_are_enforced)
{
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

TEST(StreamingQueryTest, require_that_int32_limits_are_enforced)
{
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

TEST(StreamingQueryTest, require_that_int64_limits_are_enforced)
{
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

TEST(StreamingQueryTest, require_sensible_rounding_when_using_integer_attributes)
{
    assertInt64Range("1.2", false, 1, 1);
    assertInt64Range("1.51", false, 2, 2);
    assertInt64Range("2.49", false, 2, 2);
}

TEST(StreamingQueryTest, require_that_we_can_take_floating_point_values_in_range_search_too)
{
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

TEST(StreamingQueryTest, require_that_we_handle_empty_range_as_expected)
{
    assertInt64Range("[1;1]", false, 1, 1);
    assertInt64Range("<1;1]", false, 2, 1);
    assertInt64Range("[0;1>", false, 0, 0);
    assertInt64Range("[1;1>", false, 1, 0);
    assertInt64Range("<1;1>", false, 2, 0);
}

TEST(StreamingQueryTest, require_that_ascending_range_can_be_specified_with_limit_only)
{
    int64_t low_integer = 0;
    int64_t high_integer = 0;
    double low_double = 0.0;
    double high_double = 0.0;

    QueryNodeResultFactory eqnr;
    QueryTerm ascending_query(eqnr.create(), "[;;500]", "index", TermType::WORD);

    EXPECT_TRUE(ascending_query.getAsIntegerTerm(low_integer, high_integer));
    EXPECT_TRUE(ascending_query.getAsDoubleTerm(low_double, high_double));
    EXPECT_EQ(std::numeric_limits<int64_t>::min(), low_integer);
    EXPECT_EQ(std::numeric_limits<int64_t>::max(), high_integer);
    EXPECT_EQ(-std::numeric_limits<double>::max(), low_double);
    EXPECT_EQ(std::numeric_limits<double>::max(), high_double);
    EXPECT_EQ(500, ascending_query.getRangeLimit());
}

TEST(StreamingQueryTest, require_that_descending_range_can_be_specified_with_limit_only)
{
    int64_t low_integer = 0;
    int64_t high_integer = 0;
    double low_double = 0.0;
    double high_double = 0.0;

    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500]", "index", TermType::WORD);

    EXPECT_TRUE(descending_query.getAsIntegerTerm(low_integer, high_integer));
    EXPECT_TRUE(descending_query.getAsDoubleTerm(low_double, high_double));
    EXPECT_EQ(std::numeric_limits<int64_t>::min(), low_integer);
    EXPECT_EQ(std::numeric_limits<int64_t>::max(), high_integer);
    EXPECT_EQ(-std::numeric_limits<double>::max(), low_double);
    EXPECT_EQ(std::numeric_limits<double>::max(), high_double);
    EXPECT_EQ(-500, descending_query.getRangeLimit());
}

TEST(StreamingQueryTest, require_that_correctly_specified_diversity_can_be_parsed)
{
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78]", "index", TermType::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQ(-500, descending_query.getRangeLimit());
    EXPECT_EQ("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQ(78u, descending_query.getMaxPerGroup());
    EXPECT_EQ(std::numeric_limits<uint32_t>::max(), descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST(StreamingQueryTest, require_that_correctly_specified_diversity_with_cutoff_groups_can_be_parsed)
{
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;93]", "index", TermType::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQ(-500, descending_query.getRangeLimit());
    EXPECT_EQ("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQ(78u, descending_query.getMaxPerGroup());
    EXPECT_EQ(93u, descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST(StreamingQueryTest, require_that_correctly_specified_diversity_with_cutoff_groups_can_be_parsed_2)
{
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;13]", "index", TermType::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQ(-500, descending_query.getRangeLimit());
    EXPECT_EQ("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQ(78u, descending_query.getMaxPerGroup());
    EXPECT_EQ(13u, descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST(StreamingQueryTest, require_that_correctly_specified_diversity_with_incorrect_cutoff_groups_can_be_parsed)
{
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;a13.9]", "index", TermType::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQ(-500, descending_query.getRangeLimit());
    EXPECT_EQ("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQ(78u, descending_query.getMaxPerGroup());
    EXPECT_EQ(std::numeric_limits<uint32_t>::max(), descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST(StreamingQueryTest, require_that_correctly_specified_diversity_with_cutoff_strategy_can_be_parsed)
{
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;93;anything but strict]", "index", TermType::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQ(-500, descending_query.getRangeLimit());
    EXPECT_EQ("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQ(78u, descending_query.getMaxPerGroup());
    EXPECT_EQ(93u, descending_query.getDiversityCutoffGroups());
    EXPECT_FALSE(descending_query.getDiversityCutoffStrict());
}

TEST(StreamingQueryTest, require_that_correctly_specified_diversity_with_strict_cutoff_strategy_can_be_parsed)
{
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56;78;93;strict]", "index", TermType::WORD);
    EXPECT_TRUE(descending_query.isValid());
    EXPECT_EQ(-500, descending_query.getRangeLimit());
    EXPECT_EQ("ab56", descending_query.getDiversityAttribute());
    EXPECT_EQ(78u, descending_query.getMaxPerGroup());
    EXPECT_EQ(93u, descending_query.getDiversityCutoffGroups());
    EXPECT_TRUE(descending_query.getDiversityCutoffStrict());
}

TEST(StreamingQueryTest, require_that_incorrectly_specified_diversity_can_be_parsed)
{
    QueryNodeResultFactory eqnr;
    QueryTerm descending_query(eqnr.create(), "[;;-500;ab56]", "index", TermType::WORD);
    EXPECT_FALSE(descending_query.isValid());
}

TEST(StreamingQueryTest, require_that_we_do_not_break_the_stack_on_bad_query)
{
    QueryTermSimple term("<form><iframe+&#09;&#10;&#11;+src=\\\"javascript&#58;alert(1)\\\"&#11;&#10;&#09;;>", TermType::WORD);
    EXPECT_FALSE(term.isValid());
}

TEST(StreamingQueryTest, a_unhandled_sameElement_stack)
{
    const char * stack = "\022\002\026xyz_abcdefghij_xyzxyzxQ\001\vxxxxxx_name\034xxxxxx_xxxx_xxxxxxx_xxxxxxxxE\002\005delta\b<0.00393";
    vespalib::stringref stackDump(stack);
    EXPECT_EQ(85u, stackDump.size());
    AllowRewrite empty;
    const Query q(empty, stackDump);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    auto sameElement = dynamic_cast<const SameElementQueryNode *>(&root);
    EXPECT_TRUE(sameElement != nullptr);
    EXPECT_EQ(2u, sameElement->size());
    EXPECT_EQ("xyz_abcdefghij_xyzxyzx", sameElement->getIndex());
    auto term0 = dynamic_cast<const QueryTerm *>((*sameElement)[0].get());
    EXPECT_TRUE(term0 != nullptr);
    auto term1 = dynamic_cast<const QueryTerm *>((*sameElement)[1].get());
    EXPECT_TRUE(term1 != nullptr);
}

namespace {
    void verifyQueryTermNode(const vespalib::string & index, const QueryNode *node) {
        EXPECT_TRUE(dynamic_cast<const QueryTerm *>(node) != nullptr);
        EXPECT_EQ(index, node->getIndex());
    }
}

TEST(StreamingQueryTest, test_same_element_evaluate)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addSameElement(3, "field", 0, Weight(0));
    {
        builder.addStringTerm("a", "f1", 0, Weight(0));
        builder.addStringTerm("b", "f2", 1, Weight(0));
        builder.addStringTerm("c", "f3", 2, Weight(0));
    }
    Node::UP node = builder.build();
    vespalib::string stackDump = StackDumpCreator::create(*node);
    QueryNodeResultFactory empty;
    Query q(empty, stackDump);
    SameElementQueryNode * sameElem = dynamic_cast<SameElementQueryNode *>(&q.getRoot());
    EXPECT_TRUE(sameElem != nullptr);
    EXPECT_EQ("field", sameElem->getIndex());
    EXPECT_EQ(3u, sameElem->size());
    verifyQueryTermNode("field.f1", (*sameElem)[0].get());
    verifyQueryTermNode("field.f2", (*sameElem)[1].get());
    verifyQueryTermNode("field.f3", (*sameElem)[2].get());

    QueryTermList terms;
    q.getLeaves(terms);
    EXPECT_EQ(3u, terms.size());
    for (QueryTerm * qt : terms) {
        qt->resizeFieldId(3);
    }

    // field 0
    terms[0]->add(1, 0, 0, 10);
    terms[0]->add(2, 0, 1, 20);
    terms[0]->add(3, 0, 2, 30);
    terms[0]->add(4, 0, 3, 40);
    terms[0]->add(5, 0, 4, 50);
    terms[0]->add(6, 0, 5, 60);

    terms[1]->add(7, 1, 0, 70);
    terms[1]->add(8, 1, 1, 80);
    terms[1]->add(9, 1, 2, 90);
    terms[1]->add(10, 1, 4, 100);
    terms[1]->add(11, 1, 5, 110);
    terms[1]->add(12, 1, 6, 120);

    terms[2]->add(13, 2, 0, 130);
    terms[2]->add(14, 2, 2, 140);
    terms[2]->add(15, 2, 4, 150);
    terms[2]->add(16, 2, 5, 160);
    terms[2]->add(17, 2, 6, 170);
    HitList hits;
    sameElem->evaluateHits(hits);
    EXPECT_EQ(4u, hits.size());
    EXPECT_EQ(0u, hits[0].wordpos());
    EXPECT_EQ(2u, hits[0].context());
    EXPECT_EQ(0u, hits[0].elemId());
    EXPECT_EQ(130,  hits[0].weight());

    EXPECT_EQ(0u, hits[1].wordpos());
    EXPECT_EQ(2u, hits[1].context());
    EXPECT_EQ(2u, hits[1].elemId());
    EXPECT_EQ(140,  hits[1].weight());

    EXPECT_EQ(0u, hits[2].wordpos());
    EXPECT_EQ(2u, hits[2].context());
    EXPECT_EQ(4u, hits[2].elemId());
    EXPECT_EQ(150,  hits[2].weight());

    EXPECT_EQ(0u, hits[3].wordpos());
    EXPECT_EQ(2u, hits[3].context());
    EXPECT_EQ(5u, hits[3].elemId());
    EXPECT_EQ(160,  hits[3].weight());
    EXPECT_TRUE(sameElem->evaluate());
}

TEST(StreamingQueryTest, test_nearest_neighbor_query_node)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    constexpr double distance_threshold = 35.5;
    constexpr int32_t id = 42;
    constexpr int32_t weight = 1;
    constexpr uint32_t target_num_hits = 100;
    constexpr bool allow_approximate = false;
    constexpr uint32_t explore_additional_hits = 800;
    constexpr double distance = 0.5;
    builder.add_nearest_neighbor_term("qtensor", "field", id, Weight(weight), target_num_hits, allow_approximate, explore_additional_hits, distance_threshold);
    auto build_node = builder.build();
    auto stack_dump = StackDumpCreator::create(*build_node);
    QueryNodeResultFactory empty;
    Query q(empty, stack_dump);
    auto* qterm = dynamic_cast<QueryTerm *>(&q.getRoot());
    EXPECT_TRUE(qterm != nullptr);
    auto* node = dynamic_cast<NearestNeighborQueryNode *>(&q.getRoot());
    EXPECT_TRUE(node != nullptr);
    EXPECT_EQ(node, qterm->as_nearest_neighbor_query_node());
    EXPECT_EQ("qtensor", node->get_query_tensor_name());
    EXPECT_EQ("field", node->getIndex());
    EXPECT_EQ(id, static_cast<int32_t>(node->uniqueId()));
    EXPECT_EQ(weight, node->weight().percent());
    EXPECT_EQ(distance_threshold, node->get_distance_threshold());
    EXPECT_FALSE(node->get_distance().has_value());
    EXPECT_FALSE(node->evaluate());
    node->set_distance(distance);
    EXPECT_TRUE(node->get_distance().has_value());
    EXPECT_EQ(distance, node->get_distance().value());
    EXPECT_TRUE(node->evaluate());
    node->reset();
    EXPECT_FALSE(node->get_distance().has_value());
    EXPECT_FALSE(node->evaluate());
}

TEST(StreamingQueryTest, test_in_term)
{
    auto term_vector = std::make_unique<StringTermVector>(1);
    term_vector->addTerm("7");
    search::streaming::InTerm term({}, "index", std::move(term_vector));
    SimpleTermData td;
    td.addField(10);
    td.addField(11);
    td.addField(12);
    td.lookupField(10)->setHandle(0);
    td.lookupField(12)->setHandle(1);
    EXPECT_FALSE(term.evaluate());
    auto& q = *term.get_terms().front();
    q.add(0, 11, 0, 1);
    q.add(0, 12, 0, 1);
    EXPECT_TRUE(term.evaluate());
    MatchData md(MatchData::params().numTermFields(2));
    term.unpack_match_data(23, td, md);
    auto tmd0 = md.resolveTermField(0);
    EXPECT_NE(23, tmd0->getDocId());
    auto tmd2 = md.resolveTermField(1);
    EXPECT_EQ(23, tmd2->getDocId());
}

TEST(StreamingQueryTest, control_the_size_of_query_terms)
{
    EXPECT_EQ(112u, sizeof(QueryTermSimple));
    EXPECT_EQ(128u, sizeof(QueryTermUCS4));
    EXPECT_EQ(272u, sizeof(QueryTerm));
}

GTEST_MAIN_RUN_ALL_TESTS()
