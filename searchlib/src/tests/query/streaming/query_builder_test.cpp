// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/query/streaming/in_term.h>
#include <vespa/searchlib/query/streaming/near_query_node.h>
#include <vespa/searchlib/query/streaming/onear_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/integer_term_vector.h>
#include <vespa/searchlib/query/tree/query_to_protobuf.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/query/tree/string_term_vector.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::Normalizing;
using search::SerializedQueryTree;
using search::SerializedQueryTreeSP;
using search::query::IntegerTermVector;
using search::query::MultiTerm;
using search::query::QueryBuilder;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::StringTermVector;
using search::query::TermVector;
using search::query::Weight;
using search::streaming::InTerm;
using search::streaming::Query;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTermList;

namespace {

// Serialized as protobuf: the stack dump creator does not write the unique id or weight of multi-term items.
// Both formats reach the streaming query builder through the same QueryStackIterator interface.
SerializedQueryTreeSP serialize_in_term(std::unique_ptr<TermVector> terms, MultiTerm::Type type) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.add_in_term(std::move(terms), type, "field", 42, Weight(250));
    search::query::QueryToProtobuf converter;
    auto                           protoQueryTree = converter.serialize(*builder.build());
    return SerializedQueryTree::fromProtobuf(std::make_unique<decltype(protoQueryTree)>(protoQueryTree));
}

void expect_in_term_keeps_id_and_weight(std::unique_ptr<TermVector> terms, MultiTerm::Type type) {
    auto                   serializedQueryTree = serialize_in_term(std::move(terms), type);
    QueryNodeResultFactory empty;
    Query                  q(empty, *serializedQueryTree);

    // A label on the in item resolves to its unique id, so streaming must keep it, as it does for its siblings
    auto& in_term = dynamic_cast<InTerm&>(q.getRoot());
    EXPECT_EQ(42, in_term.uniqueId());
    EXPECT_EQ(Weight(250), in_term.weight());
    EXPECT_EQ(2, in_term.get_terms().size());
}

// Records which indexes the builder asks for a normalizing mode, and folds them all
class FoldingFactory : public QueryNodeResultFactory {
public:
    mutable std::vector<std::string> asked_indexes;
    Normalizing normalizing_mode(std::string_view index) const noexcept override {
        asked_indexes.emplace_back(index);
        return Normalizing::LOWERCASE_AND_FOLD;
    }
};

std::vector<std::string> alternatives(const Query& q) {
    std::vector<std::string> result;
    for (const auto& term : dynamic_cast<const InTerm&>(q.getRoot()).get_terms()) {
        result.emplace_back(term->getTermString());
    }
    return result;
}

} // namespace

TEST(StreamingQueryBuilderTest, hidden_terms_are_not_ranked) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(2);
    builder.addAndNot(2);
    builder.addStringTerm("a", "", 0, Weight(0));
    builder.addStringTerm("b", "", 0, Weight(0));
    builder.addStringTerm("c", "", 0, Weight(0));
    auto                   node = builder.build();
    std::string            stackDump = StackDumpCreator::create(*node);
    auto                   serializedQueryTree = SerializedQueryTree::fromStackDump(stackDump);
    QueryNodeResultFactory empty;
    Query                  q(empty, *serializedQueryTree);
    QueryTermList          terms;
    q.getRoot().getLeaves(terms);
    EXPECT_EQ(3, terms.size());
    EXPECT_TRUE(terms[0]->isRanked());
    EXPECT_FALSE(terms[1]->isRanked());
    EXPECT_TRUE(terms[2]->isRanked());
}

TEST(StreamingQueryBuilderTest, near_with_negative_terms) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addNear(3, 5, 1, 3); // 3 children, distance=5, 1 negative term, exclusion_distance=3
    builder.addStringTerm("a", "", 0, Weight(0));
    builder.addStringTerm("b", "", 0, Weight(0));
    builder.addStringTerm("x", "", 0, Weight(0)); // negative term
    auto node = builder.build();

    // Convert to protobuf (stack dump doesn't support negative terms)
    search::query::QueryToProtobuf converter;
    auto                           protoQueryTree = converter.serialize(*node);

    // Build streaming query from protobuf
    auto serializedQueryTree =
        SerializedQueryTree::fromProtobuf(std::make_unique<decltype(protoQueryTree)>(protoQueryTree));
    QueryNodeResultFactory empty;
    Query                  q(empty, *serializedQueryTree);

    // Verify the Near node parameters
    auto& near = dynamic_cast<search::streaming::NearQueryNode&>(q.getRoot());
    EXPECT_EQ(5, near.distance());
    EXPECT_EQ(1, near.num_negative_terms());
    EXPECT_EQ(3, near.exclusion_distance());

    // Verify term ranking status
    QueryTermList terms;
    q.getRoot().getLeaves(terms);
    EXPECT_EQ(3, terms.size());
    EXPECT_TRUE(terms[0]->isRanked());  // "a" is ranked
    EXPECT_TRUE(terms[1]->isRanked());  // "b" is ranked
    EXPECT_FALSE(terms[2]->isRanked()); // "x" is NOT ranked (negative term)
}

TEST(StreamingQueryBuilderTest, onear_with_negative_terms) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addONear(3, 5, 2, 3); // 3 children, distance=5, 2 negative terms, exclusion_distance=3
    builder.addStringTerm("a", "", 0, Weight(0));
    builder.addStringTerm("x", "", 0, Weight(0)); // negative term
    builder.addStringTerm("y", "", 0, Weight(0)); // negative term
    auto node = builder.build();

    // Convert to protobuf (stack dump doesn't support negative terms)
    search::query::QueryToProtobuf converter;
    auto                           protoQueryTree = converter.serialize(*node);

    // Build streaming query from protobuf
    auto serializedQueryTree =
        SerializedQueryTree::fromProtobuf(std::make_unique<decltype(protoQueryTree)>(protoQueryTree));
    QueryNodeResultFactory empty;
    Query                  q(empty, *serializedQueryTree);

    // Verify the ONear node parameters
    auto& onear = dynamic_cast<search::streaming::ONearQueryNode&>(q.getRoot());
    EXPECT_EQ(5, onear.distance());
    EXPECT_EQ(2, onear.num_negative_terms());
    EXPECT_EQ(3, onear.exclusion_distance());

    // Verify term ranking status
    QueryTermList terms;
    q.getRoot().getLeaves(terms);
    EXPECT_EQ(3, terms.size());
    EXPECT_TRUE(terms[0]->isRanked());  // "a" is ranked
    EXPECT_FALSE(terms[1]->isRanked()); // "x" is NOT ranked (negative term)
    EXPECT_FALSE(terms[2]->isRanked()); // "y" is NOT ranked (negative term)
}

TEST(StreamingQueryBuilderTest, string_range_term_is_constructed) {
    QueryBuilder<SimpleQueryNodeTypes> builder;

    builder.add_string_range_term(search::query::StringRange(std::in_place, "aaa", true, "zzz", false, 100), "", 42,
                                  Weight(24));
    auto node = builder.build();

    // Convert to protobuf
    search::query::QueryToProtobuf converter;
    auto                           protoQueryTree = converter.serialize(*node);

    // Build streaming query from protobuf
    auto serializedQueryTree =
        SerializedQueryTree::fromProtobuf(std::make_unique<decltype(protoQueryTree)>(protoQueryTree));
    QueryNodeResultFactory empty;
    Query                  q(empty, *serializedQueryTree);

    // Verify the term is a ranked string-range term
    auto& string_range_term = dynamic_cast<search::streaming::QueryTerm&>(q.getRoot());
    EXPECT_TRUE(string_range_term.is_string_range());
    EXPECT_TRUE(string_range_term.isRanked());
    EXPECT_EQ(42, string_range_term.uniqueId());
    EXPECT_EQ(Weight(24), string_range_term.weight());

    // Verify the string-range specs
    auto term_spec = string_range_term.get_string_range_spec();
    ASSERT_TRUE(term_spec);
    ASSERT_TRUE(term_spec->left);
    EXPECT_EQ("aaa", *term_spec->left);
    EXPECT_TRUE(term_spec->left_closed);
    ASSERT_TRUE(term_spec->right);
    EXPECT_EQ("zzz", *term_spec->right);
    EXPECT_FALSE(term_spec->right_closed);
}

TEST(StreamingQueryBuilderTest, string_in_term_keeps_unique_id_and_weight) {
    auto terms = std::make_unique<StringTermVector>(2);
    terms->addTerm("a");
    terms->addTerm("b");
    expect_in_term_keeps_id_and_weight(std::move(terms), MultiTerm::Type::STRING);
}

TEST(StreamingQueryBuilderTest, numeric_in_term_keeps_unique_id_and_weight) {
    auto terms = std::make_unique<IntegerTermVector>(2);
    terms->addTerm(7);
    terms->addTerm(9);
    expect_in_term_keeps_id_and_weight(std::move(terms), MultiTerm::Type::INTEGER);
}

TEST(StreamingQueryBuilderTest, string_in_term_is_normalized_by_its_own_index) {
    auto terms = std::make_unique<StringTermVector>(2);
    terms->addTerm("FOO");
    terms->addTerm("Bar");
    auto           serializedQueryTree = serialize_in_term(std::move(terms), MultiTerm::Type::STRING);
    FoldingFactory factory;
    Query          q(factory, *serializedQueryTree);
    EXPECT_EQ((std::vector<std::string>{"field"}), factory.asked_indexes);
    EXPECT_EQ((std::vector<std::string>{"foo", "bar"}), alternatives(q));
}

TEST(StreamingQueryBuilderTest, numeric_in_term_is_not_normalized) {
    auto terms = std::make_unique<IntegerTermVector>(2);
    terms->addTerm(-3);
    terms->addTerm(7);
    auto           serializedQueryTree = serialize_in_term(std::move(terms), MultiTerm::Type::INTEGER);
    FoldingFactory factory;
    Query          q(factory, *serializedQueryTree);
    EXPECT_TRUE(factory.asked_indexes.empty());
    EXPECT_EQ((std::vector<std::string>{"-3", "7"}), alternatives(q));
}
