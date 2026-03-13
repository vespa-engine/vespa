// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/streaming/dot_product_term.h>
#include <vespa/searchlib/query/streaming/equiv_query_node.h>
#include <vespa/searchlib/query/streaming/in_term.h>
#include <vespa/searchlib/query/streaming/phrase_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/nearest_neighbor_query_node.h>
#include <vespa/searchlib/query/streaming/wand_term.h>
#include <vespa/searchlib/query/streaming/weighted_set_term.h>
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
using search::common::ElementIds;
using search::fef::SimpleTermData;
using search::fef::MatchData;
using search::fef::test::IndexEnvironment;
using search::SerializedQueryTree;

void assertHit(const Hit & h, uint32_t exp_field_id, uint32_t exp_element_id, int32_t exp_element_weight, size_t exp_position) {
    EXPECT_EQ(h.field_id(), exp_field_id);
    EXPECT_EQ(h.element_id(), exp_element_id);
    EXPECT_EQ(h.element_weight(), exp_element_weight);
    EXPECT_EQ(h.position(), exp_position);
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
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, 7);
        EXPECT_EQ(db, 7);
    }

    {
        QueryTerm q(factory.create(), "-7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -7);
        EXPECT_EQ(ib, -7);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -7);
        EXPECT_EQ(db, -7);
    }
    {
        QueryTerm q(factory.create(), "+7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, 7);
        EXPECT_EQ(ib, 7);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, 7);
        EXPECT_EQ(db, 7);
    }

    {
        QueryTerm q(factory.create(), "7.5", "index", TermType::WORD);
        EXPECT_TRUE(!q.getAsIntegerTerm(ia, ib));
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, 7.5);
        EXPECT_EQ(db, 7.5);
    }

    {
        QueryTerm q(factory.create(), "-7.5", "index", TermType::WORD);
        EXPECT_TRUE(!q.getAsIntegerTerm(ia, ib));
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -7.5);
        EXPECT_EQ(db, -7.5);
    }

    {
        QueryTerm q(factory.create(), "<7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, 6);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::infinity());
        EXPECT_LT(db, 7);
        EXPECT_GT(db, 6.99);
    }

    {
        QueryTerm q(factory.create(), "[;7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, 7);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::infinity());
        EXPECT_EQ(db, 7);
    }

    {
        QueryTerm q(factory.create(), ">7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, 8);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_GT(da, 7);
        EXPECT_LT(da, 7.01);
        EXPECT_EQ(db, std::numeric_limits<double>::infinity());
    }

    {
        QueryTerm q(factory.create(), "[7;]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, 7);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, 7);
        EXPECT_EQ(db, std::numeric_limits<double>::infinity());
    }

    {
        QueryTerm q(factory.create(), "[-7;7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -7);
        EXPECT_EQ(ib, 7);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -7);
        EXPECT_EQ(db, 7);
    }

    {
        QueryTerm q(factory.create(), "[-7.1;7.1]", "index", TermType::WORD);
        EXPECT_FALSE(q.getAsIntegerTerm(ia, ib)); // This is dubious and perhaps a regression.
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -7.1);
        EXPECT_EQ(db, 7.1);
    }

    {
        QueryTerm q(factory.create(), "[500.0;1.7976931348623157E308]", "index", TermType::WORD);
        EXPECT_FALSE(q.getAsIntegerTerm(ia, ib)); // This is dubious and perhaps a regression.
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, 500.0);
        EXPECT_EQ(db, std::numeric_limits<double>::max());
    }

    const double minusSeven(-7), seven(7);
    {
        QueryTerm q(factory.create(), "<-7;7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -6);
        EXPECT_EQ(ib, 7);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, std::nextafter(minusSeven, seven));
        EXPECT_EQ(db, seven);
    }

    {
        QueryTerm q(factory.create(), "<-7;7>", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -6);
        EXPECT_EQ(ib, 6);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, std::nextafter(minusSeven, seven));
        EXPECT_EQ(db, std::nextafter(seven, minusSeven));
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
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, minusSeven);
        EXPECT_EQ(db, std::nextafter(seven, minusSeven));
    }

    {
        QueryTerm q(factory.create(), "<-7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, -8);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::infinity());
        EXPECT_LT(db, -7);
        EXPECT_GT(db, -7.01);
    }

    {
        QueryTerm q(factory.create(), "[;-7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, -7);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::infinity());
        EXPECT_EQ(db, -7);
    }

    {
        QueryTerm q(factory.create(), "<;-7]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, std::numeric_limits<int64_t>::min());
        EXPECT_EQ(ib, -7);
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -std::numeric_limits<double>::infinity());
        EXPECT_EQ(db, -7);
    }

    {
        QueryTerm q(factory.create(), ">-7", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -6);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_GT(da, -7);
        EXPECT_LT(da, -6.99);
        EXPECT_EQ(db, std::numeric_limits<double>::infinity());
    }

    {
        QueryTerm q(factory.create(), "[-7;]", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -7);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -7);
        EXPECT_EQ(db, std::numeric_limits<double>::infinity());
    }

    {
        QueryTerm q(factory.create(), "[-7;>", "index", TermType::WORD);
        EXPECT_TRUE(q.getAsIntegerTerm(ia, ib));
        EXPECT_EQ(ia, -7);
        EXPECT_EQ(ib, std::numeric_limits<int64_t>::max());
        EXPECT_TRUE(q.getAsFloatTerm(da, db));
        EXPECT_EQ(da, -7);
        EXPECT_EQ(db, std::numeric_limits<double>::infinity());
    }

    {
        QueryTerm q(factory.create(), "a", "index", TermType::WORD);
        EXPECT_TRUE(!q.getAsIntegerTerm(ia, ib));
        EXPECT_TRUE(!q.getAsFloatTerm(da, db));
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
    explicit AllowRewrite(std::string_view index) noexcept : _allowedIndex(index) {}
    bool allow_float_terms_rewrite(std::string_view index) const noexcept override { return index == _allowedIndex; }
private:
    std::string _allowedIndex;
};

const char TERM_UNIQ = static_cast<char>(ParseItem::ITEM_TERM) | static_cast<char>(ParseItem::IF_UNIQUEID);

TEST(StreamingQueryTest, e_is_not_rewritten_even_if_allowed)
{
    const char term[6] = {TERM_UNIQ, 3, 1, 'c', 1, 'e'};
    std::string_view stackDump(term, sizeof(term));
    EXPECT_EQ(6u, stackDump.size());
    auto serializedQueryTree = SerializedQueryTree::fromStackDump(stackDump);
    AllowRewrite allowRewrite("c");
    const Query q(allowRewrite, *serializedQueryTree);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    EXPECT_TRUE(dynamic_cast<const QueryTerm *>(&root) != nullptr);
    const auto & qt = static_cast<const QueryTerm &>(root);
    EXPECT_EQ("c", qt.index());
    EXPECT_EQ(std::string_view("e"), qt.getTerm());
    EXPECT_EQ(3u, qt.uniqueId());
}

TEST(StreamingQueryTest, onedot0e_is_not_rewritten_by_default)
{
    const char term[9] = {TERM_UNIQ, 3, 1, 'c', 4, '1', '.', '0', 'e'};
    std::string_view stackDump(term, sizeof(term));
    EXPECT_EQ(9u, stackDump.size());
    auto serializedQueryTree = SerializedQueryTree::fromStackDump(stackDump);
    AllowRewrite empty("nix");
    const Query q(empty, *serializedQueryTree);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    EXPECT_TRUE(dynamic_cast<const QueryTerm *>(&root) != nullptr);
    const auto & qt = static_cast<const QueryTerm &>(root);
    EXPECT_EQ("c", qt.index());
    EXPECT_EQ(std::string_view("1.0e"), qt.getTerm());
    EXPECT_EQ(3u, qt.uniqueId());
}

TEST(StreamingQueryTest, onedot0e_is_rewritten_if_allowed_too)
{
    const char term[9] = {TERM_UNIQ, 3, 1, 'c', 4, '1', '.', '0', 'e'};
    std::string_view stackDump(term, sizeof(term));
    EXPECT_EQ(9u, stackDump.size());
    auto serializedQueryTree = SerializedQueryTree::fromStackDump(stackDump);
    AllowRewrite empty("c");
    const Query q(empty, *serializedQueryTree);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    EXPECT_TRUE(dynamic_cast<const EquivQueryNode *>(&root) != nullptr);
    const auto & equiv = static_cast<const EquivQueryNode &>(root);
    EXPECT_EQ(2u, equiv.get_terms().size());
    EXPECT_TRUE(dynamic_cast<const QueryTerm *>(equiv.get_terms()[0].get()) != nullptr);
    {
        const auto & qt = static_cast<const QueryTerm &>(*equiv.get_terms()[0]);
        EXPECT_EQ("c", qt.index());
        EXPECT_EQ(std::string_view("1.0e"), qt.getTerm());
        EXPECT_EQ(3u, qt.uniqueId());
    }
    EXPECT_TRUE(dynamic_cast<const PhraseQueryNode *>(equiv.get_terms()[1].get()) != nullptr);
    {
        const auto & phrase = static_cast<const PhraseQueryNode &>(*equiv.get_terms()[1]);
        EXPECT_EQ(2u, phrase.get_terms().size());
         {
            const auto & qt = *phrase.get_terms()[0];
            EXPECT_EQ("c", qt.index());
            EXPECT_EQ(std::string_view("1"), qt.getTerm());
            EXPECT_EQ(0u, qt.uniqueId());
        }
        {
            const auto & qt = *phrase.get_terms()[1];
            EXPECT_EQ("c", qt.index());
            EXPECT_EQ(std::string_view("0e"), qt.getTerm());
            EXPECT_EQ(0u, qt.uniqueId());
        }
    }
}

TEST(StreamingQueryTest, negative_integer_is_rewritten_if_allowed_for_string_field)
{
    const char term[7] = {TERM_UNIQ, 3, 1, 'c', 2, '-', '5'};
    std::string_view stackDump(term, sizeof(term));
    EXPECT_EQ(7u, stackDump.size());
    auto serializedQueryTree = SerializedQueryTree::fromStackDump(stackDump);
    AllowRewrite empty("c");
    const Query q(empty, *serializedQueryTree);
    EXPECT_TRUE(q.valid());
    auto& root = q.getRoot();
    auto& equiv = dynamic_cast<const EquivQueryNode &>(root);
    EXPECT_EQ(2u, equiv.get_terms().size());
    {
        auto& qt = *equiv.get_terms()[0];
        EXPECT_EQ("c", qt.index());
        EXPECT_EQ(std::string_view("-5"), qt.getTerm());
        EXPECT_EQ(3u, qt.uniqueId());
    }
    {
        auto& qt = *equiv.get_terms()[1];
        EXPECT_EQ("c", qt.index());
        EXPECT_EQ(std::string_view("5"), qt.getTerm());
        EXPECT_EQ(0u, qt.uniqueId());
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
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*node);

    QueryNodeResultFactory empty;
    Query q(empty, *serializedQueryTree);
    QueryTermList terms;
    q.getLeaves(terms);
    ASSERT_TRUE(terms.size() == 4);
    PhraseQueryNode* null = nullptr;
    EXPECT_EQ(null, dynamic_cast<PhraseQueryNode*>(terms[0]));
    EXPECT_NE(null, dynamic_cast<PhraseQueryNode*>(terms[1]));
    EXPECT_EQ(null, dynamic_cast<PhraseQueryNode*>(terms[2]));
    EXPECT_NE(null, dynamic_cast<PhraseQueryNode*>(terms[3]));
    {
        auto& pts = dynamic_cast<PhraseQueryNode&>(*terms[1]).get_terms();
        ASSERT_TRUE(pts.size() == 3);
    }
    {
        auto& pts = dynamic_cast<PhraseQueryNode&>(*terms[3]).get_terms();
        ASSERT_TRUE(pts.size() == 2);
    }
}

TEST(StreamingQueryTest, test_hit)
{
    // field id
    assertHit(Hit(  1, 0, 1, 0),   1, 0, 1, 0);
    assertHit(Hit(255, 0, 1, 0), 255, 0, 1, 0);
    assertHit(Hit(256, 0, 1, 0), 256, 0, 1, 0);

    // positions
    assertHit(Hit(0, 0,  0,        0), 0, 0,  0,        0);
    assertHit(Hit(0, 0,  1,      256), 0, 0,  1,      256);
    assertHit(Hit(0, 0, -1, 16777215), 0, 0, -1, 16777215);
    assertHit(Hit(0, 0,  1, 16777216), 0, 0,  1, 16777216);

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

void assertIllegalRangeQueries(const QueryTermSimple & qt) {
    QueryTermSimple::RangeResult<int64_t> ires = qt.getRange<int64_t>();
    EXPECT_EQ(false, ires.valid);
    QueryTermSimple::RangeResult<double> fres = qt.getRange<double>();
    EXPECT_EQ(false, fres.valid);
}

TEST(StreamingQueryTest, require_safe_parsing_of_illegal_ranges) {
    // The 2 below are created when naively splitting numeric terms by dot.
    // T=A.B => T EQUIV PHRASE(A, B)
    assertIllegalRangeQueries(QueryTermSimple("[1", TermType::WORD));
    assertIllegalRangeQueries(QueryTermSimple(".1;2.1]", TermType::WORD));
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
    EXPECT_TRUE(ascending_query.getAsFloatTerm(low_double, high_double));
    EXPECT_EQ(std::numeric_limits<int64_t>::min(), low_integer);
    EXPECT_EQ(std::numeric_limits<int64_t>::max(), high_integer);
    EXPECT_EQ(-std::numeric_limits<double>::infinity(), low_double);
    EXPECT_EQ(std::numeric_limits<double>::infinity(), high_double);
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
    EXPECT_TRUE(descending_query.getAsFloatTerm(low_double, high_double));
    EXPECT_EQ(std::numeric_limits<int64_t>::min(), low_integer);
    EXPECT_EQ(std::numeric_limits<int64_t>::max(), high_integer);
    EXPECT_EQ(-std::numeric_limits<double>::infinity(), low_double);
    EXPECT_EQ(std::numeric_limits<double>::infinity(), high_double);
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
    QueryTermSimple term(R"(<form><iframe+&#09;&#10;&#11;+src=\"javascript&#58;alert(1)\"&#11;&#10;&#09;;>)", TermType::WORD);
    EXPECT_FALSE(term.isValid());
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
    NearestNeighborTerm::HnswParams hnsw_params;
    hnsw_params.distance_threshold = distance_threshold;
    hnsw_params.explore_additional_hits = explore_additional_hits;
    builder.add_nearest_neighbor_term("qtensor", "field", id, Weight(weight), target_num_hits, allow_approximate, hnsw_params);
    auto build_node = builder.build();
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*build_node);
    QueryNodeResultFactory empty;
    Query q(empty, *serializedQueryTree);
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
    search::streaming::InTerm term({}, "index", std::move(term_vector), Normalizing::NONE);
    SimpleTermData td;
    td.addField(10);
    td.addField(11);
    td.addField(12);
    td.lookupField(10)->setHandle(0);
    td.lookupField(12)->setHandle(1);
    EXPECT_FALSE(term.evaluate());
    term.reset();
    auto& q = *term.get_terms().front();
    q.add(11, 0, 1, 0);
    q.add(12, 0, 1, 0);
    EXPECT_TRUE(term.evaluate());
    MatchData md(MatchData::params().numTermFields(2));
    IndexEnvironment ie;
    term.unpack_match_data(23, td, md, ie, ElementIds::select_all());
    auto tmd0 = md.resolveTermField(0);
    EXPECT_FALSE(tmd0->has_data(23));
    auto tmd2 = md.resolveTermField(1);
    EXPECT_TRUE(tmd2->has_ranking_data(23));
}

TEST(StreamingQueryTest, dot_product_term)
{
    search::streaming::DotProductTerm term({}, "index", 2);
    term.add_term(std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), "7", "", QueryTermSimple::Type::WORD));
    term.get_terms().back()->setWeight(Weight(27));
    term.add_term(std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), "9", "", QueryTermSimple::Type::WORD));
    term.get_terms().back()->setWeight(Weight(2));
    EXPECT_EQ(2, term.get_terms().size());
    SimpleTermData td;
    td.addField(10);
    td.addField(11);
    td.addField(12);
    td.lookupField(10)->setHandle(0);
    td.lookupField(12)->setHandle(1);
    EXPECT_FALSE(term.evaluate());
    term.reset();
    auto& q0 = *term.get_terms()[0];
    q0.add(11, 0, -13, 0);
    q0.add(12, 0, -17, 0);
    auto& q1 = *term.get_terms()[1];
    q1.add(11, 0, 4, 0);
    q1.add(12, 0, 9, 0);
    EXPECT_TRUE(term.evaluate());
    MatchData md(MatchData::params().numTermFields(2));
    IndexEnvironment ie;
    term.unpack_match_data(23, td, md, ie, ElementIds::select_all());
    auto tmd0 = md.resolveTermField(0);
    EXPECT_FALSE(tmd0->has_data(23));
    auto tmd1 = md.resolveTermField(1);
    EXPECT_TRUE(tmd1->has_ranking_data(23));
    EXPECT_EQ(-17 * 27 + 9 * 2, tmd1->getRawScore());
}

namespace {

constexpr double exp_wand_score_field_12 = 13 * 27 + 4 * 2;
constexpr double exp_wand_score_field_11 = 17 * 27 + 9 * 2;

void
check_wand_term(double limit, const std::string& label)
{
    SCOPED_TRACE(label);
    search::streaming::WandTerm term({}, "index", 2);
    term.add_term(std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), "7", "", QueryTermSimple::Type::WORD));
    term.get_terms().back()->setWeight(Weight(27));
    term.add_term(std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), "9", "", QueryTermSimple::Type::WORD));
    term.get_terms().back()->setWeight(Weight(2));
    EXPECT_EQ(2, term.get_terms().size());
    term.set_score_threshold(limit);
    SimpleTermData td;
    /*
     * Search in fields 10, 11 and 12 (cf. fieldset in schema).
     * Fields 11 and 12 have content for doc containing the keys.
     * Fields 10 and 12 have valid handles and can be used for ranking.
     * Field 11 does not have a valid handle, thus no associated match data.
     */
    td.addField(10);
    td.addField(11);
    td.addField(12);
    td.lookupField(10)->setHandle(0);
    td.lookupField(12)->setHandle(1);
    EXPECT_FALSE(term.evaluate());
    term.reset();
    auto& q0 = *term.get_terms()[0];
    q0.add(11, 0, 17, 0);
    q0.add(12, 0, 13, 0);
    auto& q1 = *term.get_terms()[1];
    q1.add(11, 0, 9, 0);
    q1.add(12, 0, 4, 0);
    EXPECT_EQ(limit < exp_wand_score_field_11, term.evaluate());
    MatchData md(MatchData::params().numTermFields(2));
    IndexEnvironment ie;
    term.unpack_match_data(23, td, md, ie, ElementIds::select_all());
    auto tmd0 = md.resolveTermField(0);
    EXPECT_FALSE(tmd0->has_data(23));
    auto tmd1 = md.resolveTermField(1);
    if (limit < exp_wand_score_field_12) {
        EXPECT_TRUE(tmd1->has_ranking_data(23));
        EXPECT_EQ(exp_wand_score_field_12, tmd1->getRawScore());
    } else {
        EXPECT_FALSE(tmd1->has_data(23));
    }
}

}

TEST(StreamingQueryTest, wand_term)
{
    check_wand_term(0.0, "no limit");
    check_wand_term(exp_wand_score_field_12 - 1, "score above limit");
    check_wand_term(exp_wand_score_field_12, "score at limit");
    check_wand_term(exp_wand_score_field_12 + 1, "score below limit");
    check_wand_term(exp_wand_score_field_11 - 1, "hidden score above limit");
    check_wand_term(exp_wand_score_field_11, "hidden score at limit");
    check_wand_term(exp_wand_score_field_11 + 1, "hidden score below limit");
}

TEST(StreamingQueryTest, weighted_set_term)
{
    search::streaming::WeightedSetTerm term({}, "index", 2);
    term.add_term(std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), "7", "", QueryTermSimple::Type::WORD));
    term.get_terms().back()->setWeight(Weight(4));
    term.add_term(std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), "9", "", QueryTermSimple::Type::WORD));
    term.get_terms().back()->setWeight(Weight(13));
    EXPECT_EQ(2, term.get_terms().size());
    SimpleTermData td;
    /*
     * Search in fields 10, 11 and 12 (cf. fieldset in schema).
     * Fields 11 and 12 have content for doc containing the keys.
     * Fields 10 and 12 have valid handles and can be used for ranking.
     * Field 11 does not have a valid handle, thus no associated match data.
     */
    td.addField(10);
    td.addField(11);
    td.addField(12);
    td.lookupField(10)->setHandle(0);
    td.lookupField(12)->setHandle(1);
    EXPECT_FALSE(term.evaluate());
    term.reset();
    auto& q0 = *term.get_terms()[0];
    q0.add(11, 0, 10, 0);
    q0.add(12, 0, 10, 0);
    auto& q1 = *term.get_terms()[1];
    q1.add(11, 0, 10, 0);
    q1.add(12, 0, 10, 0);
    EXPECT_TRUE(term.evaluate());
    MatchData md(MatchData::params().numTermFields(2));
    IndexEnvironment ie;
    term.unpack_match_data(23, td, md, ie, ElementIds::select_all());
    auto tmd0 = md.resolveTermField(0);
    EXPECT_FALSE(tmd0->has_data(23));
    auto tmd1 = md.resolveTermField(1);
    EXPECT_TRUE(tmd1->has_ranking_data(23));
    using Weights = std::vector<int32_t>;
    Weights weights;
    for (auto& pos : *tmd1) {
        weights.emplace_back(pos.getElementWeight());
    }
    EXPECT_EQ((Weights{13, 4}), weights);
}

TEST(StreamingQueryTest, control_the_size_of_query_terms)
{
    EXPECT_EQ(32u + sizeof(std::string), sizeof(QueryTermSimple));
    EXPECT_EQ(48u + sizeof(std::string), sizeof(QueryTermUCS4));
    EXPECT_EQ(128u + 2*sizeof(std::string), sizeof(QueryTerm));
}

GTEST_MAIN_RUN_ALL_TESTS()
