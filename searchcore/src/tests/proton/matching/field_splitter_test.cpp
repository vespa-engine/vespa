// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Comprehensive unit tests for FieldSplitter

#include <vespa/log/log.h>
LOG_SETUP("field_splitter_test");

#include <vespa/searchcore/proton/matching/field_splitter.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/resolveviewvisitor.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/searchlib/query/tree/weighted_string_term_vector.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>

using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::test::IndexEnvironment;
using search::query::Node;
using search::query::QueryBuilder;
using search::query::Weight;
using search::query::WeightedStringTermVector;
using std::string;
using namespace proton::matching;
using CollectionType = FieldInfo::CollectionType;

namespace {

// Test constants
const string TERM = "test_term";
const string VIEW = "test_view";
const string FIELD1 = "field1";
const string FIELD2 = "field2";
const string FIELD3 = "field3";
const uint32_t TERM_ID = 42;
const Weight TERM_WEIGHT(100);

//==============================================================================
// Test Fixture
//==============================================================================

class FieldSplitterTest : public ::testing::Test {
protected:
    IndexEnvironment index_env;
    ViewResolver view_resolver;

    FieldSplitterTest() {
        // Set up index environment with fields
        index_env.getFields().emplace_back(FieldType::INDEX, CollectionType::SINGLE, FIELD1, 0);
        index_env.getFields().emplace_back(FieldType::INDEX, CollectionType::SINGLE, FIELD2, 1);
        index_env.getFields().emplace_back(FieldType::INDEX, CollectionType::SINGLE, FIELD3, 2);

        // Set up view resolver to map VIEW to multiple fields
        view_resolver.add(VIEW, FIELD1);
        view_resolver.add(VIEW, FIELD2);
        view_resolver.add(VIEW, FIELD3);
    }

    ~FieldSplitterTest() override = default;

    // Helper to resolve views on a node
    void resolveViews(Node& node) {
        ResolveViewVisitor visitor(view_resolver, index_env);
        node.accept(visitor);
    }

    // Helper to build and split a query
    Node::UP buildAndSplit(Node::UP node) {
        resolveViews(*node);
        return FieldSplitter::split_terms(std::move(node));
    }
};

//==============================================================================
// Simple Term Tests
//==============================================================================

TEST_F(FieldSplitterTest, single_field_string_term_not_split) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(TERM, FIELD1, TERM_ID, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* term_node = dynamic_cast<ProtonStringTerm*>(result.get());
    ASSERT_TRUE(term_node);
    EXPECT_EQ(1u, term_node->numFields());
    EXPECT_EQ(FIELD1, term_node->field(0).getName());
}

TEST_F(FieldSplitterTest, multi_field_view_splits_term_into_or) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(TERM, VIEW, TERM_ID, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* or_node = dynamic_cast<ProtonOr*>(result.get());
    ASSERT_TRUE(or_node);
    EXPECT_EQ(3u, or_node->getChildren().size());  // VIEW maps to 3 fields

    // Check first child
    auto* term1 = dynamic_cast<ProtonStringTerm*>(or_node->getChildren()[0]);
    ASSERT_TRUE(term1);
    EXPECT_EQ(TERM, term1->getTerm());
    EXPECT_EQ(1u, term1->numFields());
    EXPECT_EQ(FIELD1, term1->field(0).getName());

    // Check second child
    auto* term2 = dynamic_cast<ProtonStringTerm*>(or_node->getChildren()[1]);
    ASSERT_TRUE(term2);
    EXPECT_EQ(TERM, term2->getTerm());
    EXPECT_EQ(1u, term2->numFields());
    EXPECT_EQ(FIELD2, term2->field(0).getName());

    // Check third child
    auto* term3 = dynamic_cast<ProtonStringTerm*>(or_node->getChildren()[2]);
    ASSERT_TRUE(term3);
    EXPECT_EQ(TERM, term3->getTerm());
    EXPECT_EQ(1u, term3->numFields());
    EXPECT_EQ(FIELD3, term3->field(0).getName());
}

TEST_F(FieldSplitterTest, number_term_with_multi_field_view_splits) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addNumberTerm("123", VIEW, TERM_ID, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* or_node = dynamic_cast<ProtonOr*>(result.get());
    ASSERT_TRUE(or_node);
    EXPECT_EQ(3u, or_node->getChildren().size());
}

//==============================================================================
// Phrase Tests
//==============================================================================

TEST_F(FieldSplitterTest, phrase_with_single_field_not_split) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addPhrase(2, FIELD1, TERM_ID, TERM_WEIGHT);
    builder.addStringTerm("hello", FIELD1, 1, TERM_WEIGHT);
    builder.addStringTerm("world", FIELD1, 2, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* phrase_node = dynamic_cast<ProtonPhrase*>(result.get());
    ASSERT_TRUE(phrase_node);
    EXPECT_EQ(1u, phrase_node->numFields());
    EXPECT_EQ(2u, phrase_node->getChildren().size());
}

TEST_F(FieldSplitterTest, phrase_with_multi_field_view_splits) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addPhrase(2, VIEW, TERM_ID, TERM_WEIGHT);
    builder.addStringTerm("hello", VIEW, 1, TERM_WEIGHT);
    builder.addStringTerm("world", VIEW, 2, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* or_node = dynamic_cast<ProtonOr*>(result.get());
    ASSERT_TRUE(or_node);
    EXPECT_EQ(3u, or_node->getChildren().size());  // VIEW maps to 3 fields

    // Check first phrase
    auto* phrase1 = dynamic_cast<ProtonPhrase*>(or_node->getChildren()[0]);
    ASSERT_TRUE(phrase1);
    EXPECT_EQ(1u, phrase1->numFields());
    EXPECT_EQ(FIELD1, phrase1->field(0).getName());
    EXPECT_EQ(2u, phrase1->getChildren().size());

    // Check second phrase
    auto* phrase2 = dynamic_cast<ProtonPhrase*>(or_node->getChildren()[1]);
    ASSERT_TRUE(phrase2);
    EXPECT_EQ(1u, phrase2->numFields());
    EXPECT_EQ(FIELD2, phrase2->field(0).getName());
    EXPECT_EQ(2u, phrase2->getChildren().size());
}

//==============================================================================
// Intermediate Node Tests (AND, OR, etc.)
//==============================================================================

TEST_F(FieldSplitterTest, and_node_preserves_structure) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addAnd(2);
    builder.addStringTerm("term1", VIEW, 1, TERM_WEIGHT);  // Will split
    builder.addStringTerm("term2", FIELD1, 2, TERM_WEIGHT);  // Won't split
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* and_node = dynamic_cast<ProtonAnd*>(result.get());
    ASSERT_TRUE(and_node);
    EXPECT_EQ(2u, and_node->getChildren().size());

    // First child should be OR (split term with multi-field VIEW)
    auto* or_node = dynamic_cast<ProtonOr*>(and_node->getChildren()[0]);
    ASSERT_TRUE(or_node);
    EXPECT_EQ(3u, or_node->getChildren().size());

    // Second child should be single term (not split)
    auto* term_node = dynamic_cast<ProtonStringTerm*>(and_node->getChildren()[1]);
    ASSERT_TRUE(term_node);
    EXPECT_EQ(1u, term_node->numFields());
}

//==============================================================================
// Equiv Node Tests
//==============================================================================

TEST_F(FieldSplitterTest, equiv_with_single_field_not_split) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addEquiv(2, TERM_ID, TERM_WEIGHT);
    builder.addStringTerm("synonym1", FIELD1, 1, TERM_WEIGHT);
    builder.addStringTerm("synonym2", FIELD1, 2, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* equiv_node = dynamic_cast<ProtonEquiv*>(result.get());
    ASSERT_TRUE(equiv_node);
    EXPECT_EQ(1u, equiv_node->numFields());
    EXPECT_EQ(2u, equiv_node->getChildren().size());
}

TEST_F(FieldSplitterTest, equiv_with_multi_field_view_splits) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addEquiv(2, TERM_ID, TERM_WEIGHT);
    builder.addStringTerm("term1", VIEW, 1, TERM_WEIGHT);
    builder.addStringTerm("term2", VIEW, 2, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    // Should create OR with 3 Equiv nodes (one per field)
    auto* or_node = dynamic_cast<ProtonOr*>(result.get());
    ASSERT_TRUE(or_node);
    EXPECT_EQ(3u, or_node->getChildren().size());

    // Each child should be an Equiv node
    for (auto* child : or_node->getChildren()) {
        auto* equiv = dynamic_cast<ProtonEquiv*>(child);
        ASSERT_TRUE(equiv);
        EXPECT_EQ(1u, equiv->numFields());
    }
}

//==============================================================================
// SameElement Node Tests
//==============================================================================

TEST_F(FieldSplitterTest, same_element_with_single_field_not_split) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addSameElement(2, FIELD1, TERM_ID, TERM_WEIGHT);
    builder.addStringTerm("term1", FIELD1, 1, TERM_WEIGHT);
    builder.addStringTerm("term2", FIELD1, 2, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* same_elem_node = dynamic_cast<ProtonSameElement*>(result.get());
    ASSERT_TRUE(same_elem_node);
    EXPECT_EQ(1u, same_elem_node->numFields());
    EXPECT_EQ(2u, same_elem_node->getChildren().size());
}

TEST_F(FieldSplitterTest, same_element_with_multi_field_view_splits) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addSameElement(2, VIEW, TERM_ID, TERM_WEIGHT);
    builder.addStringTerm("term1", VIEW, 1, TERM_WEIGHT);
    builder.addStringTerm("term2", VIEW, 2, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* or_node = dynamic_cast<ProtonOr*>(result.get());
    ASSERT_TRUE(or_node);
    EXPECT_EQ(3u, or_node->getChildren().size());

    // Each child should be a SameElement
    for (auto* child : or_node->getChildren()) {
        auto* same_elem = dynamic_cast<ProtonSameElement*>(child);
        ASSERT_TRUE(same_elem);
        EXPECT_EQ(2u, same_elem->getChildren().size());
    }
}

//==============================================================================
// Multi-term Node Tests
//==============================================================================

TEST_F(FieldSplitterTest, weighted_set_term_with_multi_field_view_splits) {
    QueryBuilder<ProtonNodeTypes> builder;

    auto terms = std::make_unique<WeightedStringTermVector>(2);
    terms->addTerm("value1", Weight(10));
    terms->addTerm("value2", Weight(20));

    builder.addWeightedSetTerm(
        std::move(terms),
        search::query::MultiTerm::Type::WEIGHTED_STRING,
        VIEW, TERM_ID, TERM_WEIGHT);

    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* or_node = dynamic_cast<ProtonOr*>(result.get());
    ASSERT_TRUE(or_node);
    EXPECT_EQ(3u, or_node->getChildren().size());

    for (auto* child : or_node->getChildren()) {
        auto* wset_node = dynamic_cast<ProtonWeightedSetTerm*>(child);
        ASSERT_TRUE(wset_node);
        EXPECT_EQ(1u, wset_node->numFields());
        EXPECT_EQ(2u, wset_node->getNumTerms());
    }
}

TEST_F(FieldSplitterTest, dot_product_with_single_field_not_split) {
    QueryBuilder<ProtonNodeTypes> builder;

    auto terms = std::make_unique<WeightedStringTermVector>(1);
    terms->addTerm("value1", Weight(10));

    builder.addDotProduct(
        std::move(terms),
        search::query::MultiTerm::Type::WEIGHTED_STRING,
        FIELD1, TERM_ID, TERM_WEIGHT);

    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* dotprod_node = dynamic_cast<ProtonDotProduct*>(result.get());
    ASSERT_TRUE(dotprod_node);
    EXPECT_EQ(1u, dotprod_node->numFields());
}

//==============================================================================
// Complex Scenarios
//==============================================================================

TEST_F(FieldSplitterTest, complex_query_with_and_or_phrases) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addAnd(2);
    {
        // First child: multi-field term
        builder.addStringTerm("term1", VIEW, 1, TERM_WEIGHT);
    }
    {
        // Second child: phrase with multi-field view
        builder.addPhrase(2, VIEW, 2, TERM_WEIGHT);
        builder.addStringTerm("hello", VIEW, 3, TERM_WEIGHT);
        builder.addStringTerm("world", VIEW, 4, TERM_WEIGHT);
    }

    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* and_node = dynamic_cast<ProtonAnd*>(result.get());
    ASSERT_TRUE(and_node);
    EXPECT_EQ(2u, and_node->getChildren().size());

    // Both children should be OR nodes
    auto* or1 = dynamic_cast<ProtonOr*>(and_node->getChildren()[0]);
    ASSERT_TRUE(or1);
    EXPECT_EQ(3u, or1->getChildren().size());

    auto* or2 = dynamic_cast<ProtonOr*>(and_node->getChildren()[1]);
    ASSERT_TRUE(or2);
    EXPECT_EQ(3u, or2->getChildren().size());
}

//==============================================================================
// Edge Cases and Error Handling
//==============================================================================

TEST_F(FieldSplitterTest, term_with_no_fields_handled) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(TERM, "nonexistent_view", TERM_ID, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* term_node = dynamic_cast<ProtonStringTerm*>(result.get());
    ASSERT_TRUE(term_node);
    EXPECT_EQ(0u, term_node->numFields());
}

TEST_F(FieldSplitterTest, empty_and_node_preserved) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addAnd(0);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* and_node = dynamic_cast<ProtonAnd*>(result.get());
    ASSERT_TRUE(and_node);
    EXPECT_EQ(0u, and_node->getChildren().size());
}

TEST_F(FieldSplitterTest, true_and_false_nodes_preserved) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addOr(2);
    builder.add_true_node();
    builder.add_false_node();
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    auto* or_node = dynamic_cast<ProtonOr*>(result.get());
    ASSERT_TRUE(or_node);
    EXPECT_EQ(2u, or_node->getChildren().size());
}

//==============================================================================
// Regression Tests
//==============================================================================

TEST_F(FieldSplitterTest, deeply_nested_structure_handled) {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addAnd(1);
    builder.addOr(1);
    builder.addAnd(1);
    builder.addOr(1);
    builder.addStringTerm(TERM, VIEW, TERM_ID, TERM_WEIGHT);
    Node::UP root = builder.build();

    Node::UP result = buildAndSplit(std::move(root));

    ASSERT_TRUE(result);
    // Should successfully navigate the deep structure
    auto* and_node = dynamic_cast<ProtonAnd*>(result.get());
    ASSERT_TRUE(and_node);
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
