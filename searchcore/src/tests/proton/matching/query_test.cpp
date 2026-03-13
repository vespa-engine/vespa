// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for query.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchcore/proton/matching/fakesearchcontext.h>
#include <vespa/searchcore/proton/matching/matchdatareservevisitor.h>
#include <vespa/searchcore/proton/matching/blueprintbuilder.h>
#include <vespa/searchcore/proton/matching/query.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/resolveviewvisitor.h>
#include <vespa/searchcore/proton/matching/termdataextractor.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchcore/proton/matching/sameelementmodifier.h>
#include <vespa/searchlib/features/utils.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/tree/customtypetermvisitor.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_blueprint.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/termasstring.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/thread_bundle.h>
#include <vespa/searchlib/query/tree/querytreecreator.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::PositionDataType;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::ITermData;
using search::fef::ITermFieldData;
using search::fef::IllegalHandle;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::query::CustomTypeTermVisitor;
using search::query::Node;
using search::query::QueryBuilder;
using search::query::Range;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::SerializedQueryTree;
using search::queryeval::AndBlueprint;
using search::queryeval::AndNotBlueprint;
using search::queryeval::Blueprint;
using search::queryeval::ExecuteInfo;
using search::queryeval::FakeBlueprint;
using search::queryeval::FakeRequestContext;
using search::queryeval::FakeResult;
using search::queryeval::FakeSearchable;
using search::queryeval::FieldSpec;
using search::queryeval::FieldSpecList;
using search::queryeval::GlobalFilter;
using search::queryeval::IntermediateBlueprint;
using search::queryeval::ParallelWeakAndBlueprint;
using search::queryeval::RankBlueprint;
using search::queryeval::SearchIterator;
using search::queryeval::Searchable;
using search::queryeval::SimpleBlueprint;
using search::queryeval::SimpleResult;
using search::queryeval::SourceBlenderBlueprint;
using search::queryeval::termAsString;
using std::string;
using std::vector;
namespace fef_test = search::fef::test;
using CollectionType = FieldInfo::CollectionType;

namespace proton::matching {
namespace {

const string field = "field";
const string loc_field = "location";
const string resolved_field1 = "resolved1";
const string resolved_field2 = "resolved2";
const string unknown_field = "unknown_field";
const string float_term = "3.14";
const string int_term = "42";
const string prefix_term = "foo";
const string string_term = "bar";
const uint32_t string_id = 4;
const Weight string_weight(4);
const string substring_term = "baz";
const string suffix_term = "qux";
const string phrase_term = "quux";
const Range range_term = Range(32, 47);
const int doc_count = 100;
const int field_id = 154;
const uint32_t term_count = 8;

fef_test::IndexEnvironment plain_index_env;
fef_test::IndexEnvironment resolved_index_env;
fef_test::IndexEnvironment attribute_index_env;

void setupIndexEnvironments()
{
    plain_index_env.getFields().emplace_back(FieldType::INDEX, CollectionType::SINGLE, field, field_id);

    resolved_index_env.getFields().emplace_back(FieldType::INDEX, CollectionType::SINGLE, resolved_field1, field_id);
    resolved_index_env.getFields().emplace_back(FieldType::INDEX, CollectionType::SINGLE, resolved_field2, field_id + 1);

    attribute_index_env.getFields().emplace_back(FieldType::ATTRIBUTE, CollectionType::SINGLE, field, 0);
    FieldInfo loc_field_info = FieldInfo(FieldType::ATTRIBUTE, CollectionType::SINGLE,
                                         PositionDataType::getZCurveFieldName(loc_field), field_id + 1);
    plain_index_env.getFields().push_back(loc_field_info);
    attribute_index_env.getFields().push_back(loc_field_info);
}
struct InitializeGlobals {
    InitializeGlobals() { setupIndexEnvironments(); }
};

InitializeGlobals globals;

struct Fixture {
    Fixture();
    ~Fixture();
    SearchIterator::UP getIterator(Node &node, ISearchContext &context);
    MatchData::UP _match_data;
    Blueprint::UP _blueprint;
    FakeRequestContext _requestContext;
};

Fixture::Fixture()
    : _match_data(),
      _blueprint(),
      _requestContext()
{
}

Fixture::~Fixture() = default;

SearchIterator::UP
Fixture::getIterator(Node &node, ISearchContext &context) {
    MatchDataLayout mdl;
    MatchDataReserveVisitor mdr_visitor(mdl);
    node.accept(mdr_visitor);

    _blueprint = BlueprintBuilder::build(_requestContext, node, context, mdl);
    _blueprint->basic_plan(true, 1000);
    _blueprint->fetchPostings(ExecuteInfo::FULL);
    _match_data = mdl.createMatchData();
    SearchIterator::UP search(_blueprint->createSearch(*_match_data));
    search->initFullRange();
    return search;
}

vespalib::ThreadBundle &ttb() { return vespalib::ThreadBundle::trivial(); }

std::string
termAsString(const search::query::Range &term) {
    vespalib::asciistream os;
    return (os << term).str();
}

const std::string &
termAsString(const std::string & term) {
    return term;
}


Node::UP buildQueryTree(const ViewResolver &resolver,
                        const search::fef::IIndexEnvironment &idxEnv)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addOr(term_count);
    query_builder.addNumberTerm(float_term, field, 0, Weight(0));
    query_builder.addNumberTerm(int_term, field, 1, Weight(0));
    query_builder.addPrefixTerm(prefix_term, field, 2, Weight(0));
    query_builder.addRangeTerm(range_term, field, 3, Weight(0));
    query_builder.addStringTerm(string_term, field, string_id, string_weight);
    query_builder.addSubstringTerm(substring_term, field, 5, Weight(0));
    query_builder.addSuffixTerm(suffix_term, field, 6, Weight(0));
    query_builder.addPhrase(2, field, 7, Weight(0));
    query_builder.addStringTerm(phrase_term, field, 8, Weight(0));
    query_builder.addStringTerm(phrase_term, field, 9, Weight(0));

    Node::UP node = query_builder.build();

    ResolveViewVisitor visitor(resolver, idxEnv);
    node->accept(visitor);
    return node;
}

Node::UP buildSameElementQueryTree(const ViewResolver &resolver,
                                   const search::fef::IIndexEnvironment &idxEnv)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addSameElement(2, field, 2, Weight(0));
    query_builder.addStringTerm(string_term, field, 0, Weight(0));
    query_builder.addStringTerm(prefix_term, field, 1, Weight(0));
    Node::UP node = query_builder.build();
    ResolveViewVisitor visitor(resolver, idxEnv);
    node->accept(visitor);
    return node;
}

TEST(QueryTest, requireThatMatchDataIsReserved)
{
    Node::UP node = buildQueryTree(ViewResolver(), plain_index_env);

    MatchDataLayout mdl;
    MatchDataReserveVisitor visitor(mdl);
    node->accept(visitor);
    MatchData::UP match_data = mdl.createMatchData();

    EXPECT_EQ(term_count, match_data->getNumTermFields());
}

ViewResolver getViewResolver() {
    ViewResolver resolver;
    resolver.add(field, resolved_field1);
    resolver.add(field, resolved_field2);
    return resolver;
}

TEST(QueryTest, requireThatMatchDataIsReservedForEachFieldInAView)
{
    Node::UP node = buildQueryTree(getViewResolver(), resolved_index_env);

    MatchDataLayout mdl;
    MatchDataReserveVisitor visitor(mdl);
    node->accept(visitor);
    MatchData::UP match_data = mdl.createMatchData();

    EXPECT_EQ(term_count * 2, match_data->getNumTermFields());
}

class LookupTestCheckerVisitor : public CustomTypeTermVisitor<ProtonNodeTypes>
{
public:
    template <class TermType>
    void checkNode(const TermType &n, int estimatedHitCount, bool empty) {
        EXPECT_EQ(empty, (estimatedHitCount == 0));
        EXPECT_EQ(static_cast<uint64_t>(estimatedHitCount), n.field(0).get_doc_freq().frequency);
        EXPECT_EQ(static_cast<uint64_t>(doc_count), n.field(0).get_doc_freq().count);
    }

    void visit(ProtonNumberTerm &n) override { checkNode(n, 1, false); }
    void visit(ProtonLocationTerm &n) override { checkNode(n, 0, true); }
    void visit(ProtonPrefixTerm &n) override { checkNode(n, 1, false); }
    void visit(ProtonRangeTerm &n) override { checkNode(n, 2, false); }
    void visit(ProtonStringTerm &n) override { checkNode(n, 2, false); }
    void visit(ProtonSubstringTerm &n) override { checkNode(n, 0, true); }
    void visit(ProtonSuffixTerm &n) override { checkNode(n, 2, false); }
    void visit(ProtonPhrase &n) override { checkNode(n, 0, true); }
    void visit(ProtonFuzzyTerm &n) override { checkNode(n, 1, false); }
    void visit(ProtonWeightedSetTerm &) override {}
    void visit(ProtonDotProduct &) override {}
    void visit(ProtonWandTerm &) override {}
    void visit(ProtonPredicateQuery &) override {}
    void visit(ProtonRegExpTerm &) override {}
    void visit(ProtonNearestNeighborTerm &) override {}
    void visit(ProtonInTerm&) override {}
    void visit(ProtonWordAlternatives&) override {}
};

TEST(QueryTest, requireThatTermsAreLookedUp)
{
    FakeRequestContext requestContext;
    Node::UP node = buildQueryTree(ViewResolver(), plain_index_env);

    FakeSearchContext context;
    context.addIdx(1).addIdx(2);
    context.idx(0).getFake()
        .addResult(field, prefix_term, FakeResult().doc(1).pos(2))
        .addResult(field, string_term,
                   FakeResult().doc(2).pos(3).doc(3).pos(4))
        .addResult(field, termAsString(int_term),
                   FakeResult().doc(4).pos(5));
    context.idx(1).getFake()
        .addResult(field, string_term, FakeResult().doc(6).pos(7))
        .addResult(field, suffix_term,
                   FakeResult().doc(7).pos(8).doc(8).pos(9))
        .addResult(field, termAsString(float_term),
                   FakeResult().doc(9).pos(10))
        .addResult(field, termAsString(int_term),
                   FakeResult().doc(10).pos(11))
        .addResult(field, termAsString(range_term),
                   FakeResult().doc(12).pos(13).doc(13).pos(14));
    context.setLimit(doc_count + 1);

    MatchDataLayout mdl;
    MatchDataReserveVisitor visitor(mdl);
    node->accept(visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, *node, context, mdl);

    LookupTestCheckerVisitor checker;
    node->accept(checker);
}

TEST(QueryTest, requireThatTermsAreLookedUpInMultipleFieldsFromAView)
{
    Node::UP node = buildQueryTree(getViewResolver(), resolved_index_env);

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.addIdx(1).addIdx(2);
    context.idx(0).getFake()
        .addResult(resolved_field1, prefix_term,
                   FakeResult().doc(1).pos(2))
        .addResult(resolved_field2, string_term,
                   FakeResult().doc(2).pos(3).doc(3).pos(4))
        .addResult(resolved_field1, termAsString(int_term),
                   FakeResult().doc(4).pos(5));
    context.idx(1).getFake()
        .addResult(resolved_field1, string_term,
                   FakeResult().doc(6).pos(7))
        .addResult(resolved_field2, suffix_term,
                   FakeResult().doc(7).pos(8).doc(8).pos(9))
        .addResult(resolved_field1, termAsString(float_term),
                   FakeResult().doc(9).pos(10))
        .addResult(resolved_field2, termAsString(int_term),
                   FakeResult().doc(10).pos(11))
        .addResult(resolved_field1, termAsString(range_term),
                   FakeResult().doc(12).pos(13).doc(13).pos(14));
    context.setLimit(doc_count + 1);

    MatchDataLayout mdl;
    MatchDataReserveVisitor visitor(mdl);
    node->accept(visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, *node, context, mdl);

    LookupTestCheckerVisitor checker;
    node->accept(checker);
}

TEST(QueryTest, requireThatAttributeTermsAreLookedUpInAttributeSource)
{
    const string term = "bar";
    ProtonStringTerm node(term, field, 1, Weight(2));
    node.resolve(ViewResolver(), attribute_index_env);

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.addIdx(1);
    context.attr().addResult(field, term, FakeResult().doc(1).pos(2));

    MatchDataLayout mdl;
    MatchDataReserveVisitor visitor(mdl);
    node.accept(visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, node, context, mdl);
    EXPECT_TRUE(!blueprint->getState().estimate().empty);
    EXPECT_EQ(1u, blueprint->getState().estimate().estHits);
}

TEST(QueryTest, requireThatAttributeTermDataHandlesAreAllocated)
{
    const string term = "bar";
    ProtonStringTerm node(term, field, 1, Weight(2));
    node.resolve(ViewResolver(), attribute_index_env);

    FakeSearchContext context;
    FakeRequestContext requestContext;

    MatchDataLayout mdl;
    MatchDataReserveVisitor reserve_visitor(mdl);
    node.accept(reserve_visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, node, context, mdl);
    MatchData::UP match_data = mdl.createMatchData();
    EXPECT_EQ(1u, match_data->getNumTermFields());
    EXPECT_TRUE(node.field(0).attribute_field);
}


class SetUpTermDataTestCheckerVisitor
    : public CustomTypeTermVisitor<ProtonNodeTypes>
{
public:
    void visit(ProtonNumberTerm &) override {}
    void visit(ProtonLocationTerm &) override {}
    void visit(ProtonPrefixTerm &) override {}
    void visit(ProtonRangeTerm &) override {}

    void visit(ProtonStringTerm &n) override {
        const ITermData &term_data = n;
        EXPECT_EQ(string_weight.percent(), term_data.getWeight().percent());
        EXPECT_EQ(1u, term_data.getPhraseLength());
        EXPECT_EQ(string_id, term_data.getUniqueId());
        EXPECT_EQ(term_data.numFields(), n.numFields());
        for (size_t i = 0; i < term_data.numFields(); ++i) {
            const ITermFieldData &term_field_data = term_data.field(i);
            EXPECT_EQ(2u, term_field_data.get_doc_freq().frequency);
            EXPECT_EQ(static_cast<uint64_t>(doc_count), term_field_data.get_doc_freq().count);
            EXPECT_TRUE(!n.field(i).attribute_field);
            EXPECT_EQ(field_id + i, term_field_data.getFieldId());
        }
    }

    void visit(ProtonSubstringTerm &) override {}
    void visit(ProtonSuffixTerm &) override {}
    void visit(ProtonPhrase &n) override {
        const ITermData &term_data = n;
        EXPECT_EQ(2u, term_data.getPhraseLength());
    }
    void visit(ProtonWeightedSetTerm &) override {}
    void visit(ProtonDotProduct &) override {}
    void visit(ProtonWandTerm &) override {}
    void visit(ProtonPredicateQuery &) override {}
    void visit(ProtonRegExpTerm &) override {}
    void visit(ProtonNearestNeighborTerm &) override {}
    void visit(ProtonFuzzyTerm &) override {}
    void visit(ProtonInTerm&) override { }
    void visit(ProtonWordAlternatives&) override { }
};

TEST(QueryTest, requireThatTermDataIsFilledIn)
{
    Node::UP node = buildQueryTree(getViewResolver(), resolved_index_env);

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.addIdx(1);
    context.idx(0).getFake().addResult(resolved_field1, string_term,
                                       FakeResult().doc(1).pos(2).doc(5).pos(3));
    context.setLimit(doc_count + 1);

    MatchDataLayout mdl;
    MatchDataReserveVisitor reserve_visitor(mdl);
    node->accept(reserve_visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, *node, context, mdl);

    SetUpTermDataTestCheckerVisitor checker;
    node->accept(checker);
}

FakeIndexSearchable getFakeSearchable(const string &term, int doc1, int doc2) {
    FakeIndexSearchable source;
    source.getFake().addResult(field, term,
                               FakeResult().doc(doc1).pos(2).doc(doc2).pos(3));
    return source;
}

TEST(QueryTest, requireThatSingleIndexCanUseBlendingAsBlacklisting)
{
    Fixture f;
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(string_term, field, 1, Weight(2))
        .resolve(ViewResolver(), plain_index_env);
    Node::UP node = builder.build();
    ASSERT_TRUE(node);

    FakeSearchContext context;
    context.addIdx(1).idx(0) = getFakeSearchable(string_term, 2, 5);
    context.selector().setSource(5, 1);

    SearchIterator::UP iterator = f.getIterator(*node, context);
    ASSERT_TRUE(iterator);
    EXPECT_TRUE(!iterator->seek(1));
    EXPECT_TRUE(!iterator->seek(2));
    EXPECT_TRUE(iterator->seek(5));
    iterator->unpack(5);
}

TEST(QueryTest, requireThatIteratorsAreBuiltWithBlending)
{
    Fixture f;
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(string_term, field, 1, Weight(2))
        .resolve(ViewResolver(), plain_index_env);
    Node::UP node = builder.build();
    ASSERT_TRUE(node);

    FakeSearchContext context;
    context.addIdx(1).idx(0) = getFakeSearchable(string_term, 3, 7);
    context.addIdx(0).idx(1) = getFakeSearchable(string_term, 2, 6);
    context.selector().setSource(3, 1);
    context.selector().setSource(7, 1);

    SearchIterator::UP iterator = f.getIterator(*node, context);
    ASSERT_TRUE(iterator);

    EXPECT_TRUE(!iterator->seek(1));
    EXPECT_TRUE(iterator->seek(2));
    EXPECT_TRUE(iterator->seek(3));
    EXPECT_TRUE(iterator->seek(6));
    EXPECT_TRUE(iterator->seek(7));
}

TEST(QueryTest, requireThatIteratorsAreBuiltForAllTermNodes)
{
    Fixture f;
    Node::UP node = buildQueryTree(ViewResolver(), plain_index_env);
    ASSERT_TRUE(node);

    FakeSearchContext context(42);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, termAsString(float_term),
                   FakeResult().doc(2).pos(2))
        .addResult(field, termAsString(int_term),
                   FakeResult().doc(4).pos(2))
        .addResult(field, prefix_term, FakeResult().doc(8).pos(2))
        .addResult(field, termAsString(range_term),
                   FakeResult().doc(15).pos(2))
        .addResult(field, string_term, FakeResult().doc(16).pos(2))
        .addResult(field, substring_term, FakeResult().doc(23).pos(2))
        .addResult(field, suffix_term, FakeResult().doc(42).pos(2));

    SearchIterator::UP iterator = f.getIterator(*node, context);
    ASSERT_TRUE(iterator);

    EXPECT_TRUE(!iterator->seek(1));
    EXPECT_TRUE(iterator->seek(2));
    EXPECT_TRUE(iterator->seek(4));
    EXPECT_TRUE(iterator->seek(8));
    EXPECT_TRUE(iterator->seek(15));
    EXPECT_TRUE(iterator->seek(16));
    EXPECT_TRUE(iterator->seek(23));
    EXPECT_TRUE(iterator->seek(42));
}

TEST(QueryTest, requireThatNearIteratorsCanBeBuilt)
{
    Fixture f;
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addNear(3, 4, 1, 5);
    builder.addStringTerm(string_term, field, 1, Weight(2));
    builder.addStringTerm(prefix_term, field, 1, Weight(2));
    builder.addStringTerm(int_term, field, 1, Weight(2));
    Node::UP node = builder.build();
    ViewResolver resolver;
    ResolveViewVisitor visitor(resolver, plain_index_env);
    node->accept(visitor);
    ASSERT_TRUE(node);

    FakeSearchContext context(100);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, prefix_term, FakeResult()
                   .doc(4).pos(2).len(50).doc(8).pos(2).len(50).doc(12).pos(2).len(50))
        .addResult(field, string_term, FakeResult()
                   .doc(4).pos(40).len(50).doc(8).pos(5).len(50).doc(12).pos(5).len(50))
        .addResult(field, int_term, FakeResult()
                   .doc(8).pos(7).len(50));

    SearchIterator::UP iterator = f.getIterator(*node, context);
    ASSERT_TRUE(iterator);
    EXPECT_TRUE(!iterator->seek(4));
    EXPECT_TRUE(!iterator->seek(8));
    EXPECT_TRUE(iterator->seek(12));
}

TEST(QueryTest, requireThatONearIteratorsCanBeBuilt)
{
    Fixture f;
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addONear(3, 4, 1, 5);
    builder.addStringTerm(string_term, field, 1, Weight(2));
    builder.addStringTerm(prefix_term, field, 1, Weight(2));
    builder.addStringTerm(int_term, field, 1, Weight(2));
    Node::UP node = builder.build();
    ViewResolver resolver;
    ResolveViewVisitor visitor(resolver, plain_index_env);
    node->accept(visitor);
    ASSERT_TRUE(node);

    FakeSearchContext context(100);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, string_term, FakeResult()
                   .doc(4).pos(5).len(50).doc(8).pos(2).len(50).doc(12).pos(2).len(50))
        .addResult(field, prefix_term, FakeResult()
                   .doc(4).pos(2).len(50).doc(8).pos(5).len(50).doc(12).pos(5).len(50))
        .addResult(field, int_term, FakeResult()
                   .doc(8).pos(7).len(50));

    SearchIterator::UP iterator = f.getIterator(*node, context);
    ASSERT_TRUE(iterator);
    EXPECT_TRUE(!iterator->seek(4));
    EXPECT_TRUE(!iterator->seek(8));
    EXPECT_TRUE(iterator->seek(12));
}

TEST(QueryTest, requireThatPhraseIteratorsCanBeBuilt)
{
    Fixture f;
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addPhrase(3, field, 0, Weight(42));
    builder.addStringTerm(string_term, field, 1, Weight(2));
    builder.addStringTerm(prefix_term, field, 1, Weight(2));
    builder.addStringTerm(suffix_term, field, 1, Weight(2));
    Node::UP node = builder.build();
    ViewResolver resolver;
    ResolveViewVisitor visitor(resolver, plain_index_env);
    node->accept(visitor);
    ASSERT_TRUE(node);

    FakeSearchContext context(9);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, string_term, FakeResult()
                  .doc(4).pos(3).len(50)
                  .doc(5).pos(2).len(50)
                  .doc(8).pos(2).len(50)
                  .doc(9).pos(2).len(50))
        .addResult(field, prefix_term, FakeResult()
                   .doc(4).pos(2).len(50)
                   .doc(5).pos(4).len(50)
                   .doc(8).pos(3).len(50))
        .addResult(field, suffix_term, FakeResult()
                   .doc(4).pos(1).len(50)
                   .doc(5).pos(5).len(50)
                   .doc(8).pos(4).len(50));

    SearchIterator::UP iterator = f.getIterator(*node, context);
    ASSERT_TRUE(iterator);
    EXPECT_TRUE(!iterator->seek(4));
    EXPECT_TRUE(!iterator->seek(5));
    EXPECT_TRUE(iterator->seek(8));
    EXPECT_TRUE(!iterator->seek(9));
    EXPECT_TRUE(iterator->isAtEnd());
}

TEST(QueryTest, requireThatUnknownFieldActsEmpty)
{
    Fixture f;
    FakeSearchContext context;
    context.addIdx(0).idx(0).getFake()
        .addResult(unknown_field, string_term, FakeResult()
                   .doc(4).pos(3).len(50)
                   .doc(5).pos(2).len(50));

    ProtonNodeTypes::StringTerm node(string_term, unknown_field, string_id, string_weight);
    node.resolve(ViewResolver(), plain_index_env);

    std::vector<const ITermData *> terms;
    TermDataExtractor::extractTerms(node, terms);

    SearchIterator::UP iterator = f.getIterator(node, context);

    ASSERT_EQ(1u, terms.size());
    EXPECT_EQ(0u, terms[0]->numFields());

    ASSERT_TRUE(iterator);
    EXPECT_TRUE(!iterator->seek(1));
    EXPECT_TRUE(iterator->isAtEnd());
}

TEST(QueryTest, requireThatIllegalFieldsAreIgnored)
{
    ProtonNodeTypes::StringTerm node(string_term, unknown_field, string_id, string_weight);
    node.resolve(ViewResolver(), plain_index_env);

    FakeRequestContext requestContext;
    FakeSearchContext context;

    MatchDataLayout mdl;
    MatchDataReserveVisitor reserve_visitor(mdl);
    node.accept(reserve_visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, node, context, mdl);
    EXPECT_EQ(0u, node.numFields());
    MatchData::UP match_data = mdl.createMatchData();
    EXPECT_EQ(0u, match_data->getNumTermFields());
}

TEST(QueryTest, requireThatQueryGluesEverythingTogether)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(string_term, field, 1, Weight(2));
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*builder.build());
    Query query;
    query.buildTree(*serializedQueryTree, "", ViewResolver(), plain_index_env);
    vector<const ITermData *> term_data;
    query.extractTerms(term_data);
    EXPECT_EQ(1u, term_data.size());

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.setLimit(42);
    MatchDataLayout mdl;
    query.reserve_handles(mdl);
    query.make_blueprint(requestContext, context, mdl);
    MatchData::UP md = mdl.createMatchData();
    EXPECT_EQ(1u, md->getNumTermFields());

    query.optimize(true, true);
    query.fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = query.createSearch(*md);
    ASSERT_TRUE(search);
}

void
checkQueryAddsLocation(const string &loc_in, const string &loc_out) {
    fef_test::IndexEnvironment index_environment;
    index_environment.getFields().emplace_back(FieldType::INDEX, CollectionType::SINGLE, field, 0);
    index_environment.getFields().emplace_back(FieldType::ATTRIBUTE, CollectionType::SINGLE,
                                               PositionDataType::getZCurveFieldName(loc_field), 1);

    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(string_term, field, 1, Weight(2));
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*builder.build());
    Query query;
    query.buildTree(*serializedQueryTree,
                    loc_field + ":" + loc_in,
                    ViewResolver(), index_environment);
    vector<const ITermData *> term_data;
    query.extractTerms(term_data);
    EXPECT_EQ(2u, term_data.size());

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.addIdx(0).setLimit(42);
    MatchDataLayout mdl;
    query.reserve_handles(mdl);
    query.make_blueprint(requestContext, context, mdl);
    MatchData::UP md = mdl.createMatchData();
    EXPECT_EQ(2u, md->getNumTermFields());

    // query.optimize(true, true);
    query.fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = query.createSearch(*md);
    ASSERT_TRUE(search);
    EXPECT_NE(string::npos, search->asString().find(loc_out)) << "search (missing loc_out '" << loc_out << "'): " <<
        search->asString();
}

template<typename T1, typename T2>
void verifyThatRankBlueprintAndAndNotStaysOnTopAfterLocation(QueryBuilder<ProtonNodeTypes> & builder) {
    const string loc_string = "(2,10,10,3,0,1,0,0)";
    builder.addStringTerm("foo", field, field_id, string_weight);
    builder.addStringTerm("bar", field, field_id, string_weight);
    builder.addStringTerm("baz", field, field_id, string_weight);
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*builder.build());

    Query query;
    query.buildTree(*serializedQueryTree, loc_field + ":" + loc_string, ViewResolver(), attribute_index_env);
    FakeSearchContext context(42);
    context.addIdx(0).idx(0).getFake()
            .addResult(field, "foo", FakeResult().doc(1));
    context.setLimit(42);

    query.setWhiteListBlueprint(std::make_unique<SimpleBlueprint>(SimpleResult()));

    FakeRequestContext requestContext;
    MatchDataLayout mdl;
    query.reserve_handles(mdl);
    query.make_blueprint(requestContext, context, mdl);
    const IntermediateBlueprint * root = dynamic_cast<const T1 *>(query.peekRoot());
    ASSERT_TRUE(root != nullptr);
    EXPECT_EQ(2u, root->childCnt());
    const IntermediateBlueprint * second = dynamic_cast<const T2 *>(&root->getChild(0));
    ASSERT_TRUE(second != nullptr);
    EXPECT_EQ(2u, second->childCnt());
    auto first = dynamic_cast<const AndBlueprint *>(&second->getChild(0));
    ASSERT_TRUE(first != nullptr);
    EXPECT_EQ(2u, first->childCnt());
    EXPECT_TRUE(dynamic_cast<const AndBlueprint *>(&first->getChild(0)));
    auto bottom = dynamic_cast<const AndBlueprint *>(&first->getChild(0));
    EXPECT_EQ(2u, bottom->childCnt());
    EXPECT_TRUE(dynamic_cast<const FakeBlueprint *>(&bottom->getChild(0)));
    EXPECT_TRUE(dynamic_cast<const FakeBlueprint *>(&bottom->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const SimpleBlueprint *>(&first->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const FakeBlueprint *>(&second->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const FakeBlueprint *>(&root->getChild(1)));
}

TEST(QueryTest, requireThatLocationIsAddedTheCorrectPlace)
{
    {
        QueryBuilder<ProtonNodeTypes> builder;
        builder.addRank(2);
        builder.addAndNot(2);
        verifyThatRankBlueprintAndAndNotStaysOnTopAfterLocation<RankBlueprint, AndNotBlueprint>(builder);
    }
    {
        QueryBuilder<ProtonNodeTypes> builder;
        builder.addAndNot(2);
        builder.addRank(2);
        verifyThatRankBlueprintAndAndNotStaysOnTopAfterLocation<AndNotBlueprint, RankBlueprint>(builder);
    }
}

TEST(QueryTest, requireThatQueryAddsLocation)
{
    checkQueryAddsLocation("(2,10,10,3,0,1,0,0)", "{p:{x:10,y:10},r:3,b:{x:[7,13],y:[7,13]}}");
    checkQueryAddsLocation("{p:{x:10,y:10},r:3}", "{p:{x:10,y:10},r:3,b:{x:[7,13],y:[7,13]}}");
    checkQueryAddsLocation("{b:{x:[6,11],y:[8,15]},p:{x:10,y:10},r:3}", "{p:{x:10,y:10},r:3,b:{x:[7,11],y:[8,13]}}");
    checkQueryAddsLocation("{a:12345,b:{x:[8,10],y:[8,10]},p:{x:10,y:10},r:3}", "{p:{x:10,y:10},r:3,a:12345,b:{x:[8,10],y:[8,10]}}");
}

TEST(QueryTest, requireThatQueryAddsLocationCutoff)
{
    checkQueryAddsLocation("[2,10,11,23,24]", "{b:{x:[10,23],y:[11,24]}}");
    checkQueryAddsLocation("{b:{y:[11,24],x:[10,23]}}", "{b:{x:[10,23],y:[11,24]}}");
}

TEST(QueryTest, requireThatFakeFieldSearchDumpsDiffer)
{
    FakeRequestContext requestContext;
    uint32_t fieldId = 0;
    MatchDataLayout mdl;
    TermFieldHandle handle = mdl.allocTermField(fieldId);
    MatchData::UP match_data = mdl.createMatchData();

    FakeSearchable a;
    FakeSearchable b;
    a.tag("a");
    b.tag("b");
    std::string term1="term1";
    std::string term2="term2";
    ProtonStringTerm n1(term1, "field1", string_id, string_weight);
    ProtonStringTerm n2(term2, "field1", string_id, string_weight);
    ProtonStringTerm n3(term1, "field2", string_id, string_weight);

    FieldSpecList fields1;
    FieldSpecList fields2;
    fields1.add(FieldSpec("field1", fieldId, handle));
    fields2.add(FieldSpec("field2", fieldId, handle));

    Blueprint::UP l1(a.createBlueprint(requestContext, fields1, n1, mdl)); // reference
    Blueprint::UP l2(a.createBlueprint(requestContext, fields1, n2, mdl)); // term
    Blueprint::UP l3(a.createBlueprint(requestContext, fields2, n3, mdl)); // field
    Blueprint::UP l4(b.createBlueprint(requestContext, fields1, n1, mdl)); // tag

    l1->basic_plan(true, 1000);
    l2->basic_plan(true, 1000);
    l3->basic_plan(true, 1000);
    l4->basic_plan(true, 1000);

    l1->fetchPostings(ExecuteInfo::FULL);
    l2->fetchPostings(ExecuteInfo::FULL);
    l3->fetchPostings(ExecuteInfo::FULL);
    l4->fetchPostings(ExecuteInfo::FULL);

    SearchIterator::UP s1(l1->createSearch(*match_data));
    SearchIterator::UP s2(l2->createSearch(*match_data));
    SearchIterator::UP s3(l3->createSearch(*match_data));
    SearchIterator::UP s4(l4->createSearch(*match_data));

    EXPECT_NE(s1->asString(), s2->asString());
    EXPECT_NE(s1->asString(), s3->asString());
    EXPECT_NE(s1->asString(), s4->asString());
}

TEST(QueryTest, requireThatNoDocsGiveZeroDocFrequency)
{
    ProtonStringTerm node(string_term, field, string_id, string_weight);
    node.resolve(ViewResolver(), plain_index_env);
    FakeSearchContext context;
    FakeRequestContext requestContext;
    context.setLimit(0);

    MatchDataLayout mdl;
    MatchDataReserveVisitor reserve_visitor(mdl);
    node.accept(reserve_visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, node, context, mdl);

    EXPECT_EQ(1u, node.numFields());
    EXPECT_EQ(0u, node.field(0).get_doc_freq().frequency);
    EXPECT_EQ(1u, node.field( 0).get_doc_freq().count);
}

TEST(QueryTest, requireThatWeakAndBlueprintsAreCreatedCorrectly)
{
    using search::queryeval::WeakAndBlueprint;

    ProtonWeakAnd wand(123, "view");
    wand.append(Node::UP(new ProtonStringTerm("foo", field, 0, Weight(3))));
    wand.append(Node::UP(new ProtonStringTerm("bar", field, 0, Weight(7))));

    ViewResolver viewResolver;
    ResolveViewVisitor resolve_visitor(viewResolver, plain_index_env);
    wand.accept(resolve_visitor);

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.addIdx(0).idx(0).getFake()
        .addResult(field, "foo", FakeResult().doc(1).doc(3))
        .addResult(field, "bar", FakeResult().doc(2).doc(3).doc(4));

    MatchDataLayout mdl;
    MatchDataReserveVisitor reserve_visitor(mdl);
    wand.accept(reserve_visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, wand, context, mdl);
    auto *wbp = dynamic_cast<WeakAndBlueprint*>(blueprint.get());
    ASSERT_TRUE(wbp != nullptr);
    ASSERT_EQ(2u, wbp->getWeights().size());
    ASSERT_EQ(2u, wbp->childCnt());
    EXPECT_EQ(123u, wbp->getN());
    EXPECT_EQ(3u, wbp->getWeights()[0]);
    EXPECT_EQ(7u, wbp->getWeights()[1]);
    EXPECT_EQ(2u, wbp->getChild(0).getState().estimate().estHits);
    EXPECT_EQ(3u, wbp->getChild(1).getState().estimate().estHits);
}

TEST(QueryTest, requireThatParallelWandBlueprintsAreCreatedCorrectly)
{
    using search::queryeval::WeakAndBlueprint;

    ProtonWandTerm wand(2, field, 42, Weight(100), 123, 9000, 1.25);
    wand.addTerm("foo", Weight(3));
    wand.addTerm("bar", Weight(7));

    ViewResolver viewResolver;
    ResolveViewVisitor resolve_visitor(viewResolver, attribute_index_env);
    wand.accept(resolve_visitor);

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.setLimit(1000);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, "foo", FakeResult().doc(1).doc(3))
        .addResult(field, "bar", FakeResult().doc(2).doc(3).doc(4));

    MatchDataLayout mdl;
    MatchDataReserveVisitor reserve_visitor(mdl);
    wand.accept(reserve_visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, wand, context, mdl);
    auto *wbp = dynamic_cast<ParallelWeakAndBlueprint*>(blueprint.get());
    ASSERT_TRUE(wbp != nullptr);
    EXPECT_EQ(9000, wbp->getScoreThreshold());
    EXPECT_EQ(1.25, wbp->getThresholdBoostFactor());
    EXPECT_EQ(1000u, wbp->get_docid_limit());
}

TEST(QueryTest, requireThatWhiteListBlueprintCanBeUsed)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm("foo", field, field_id, string_weight);
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*builder.build());
    Query query;
    query.buildTree(*serializedQueryTree, "", ViewResolver(), plain_index_env);

    FakeSearchContext context(42);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, "foo", FakeResult().doc(1).doc(3).doc(5).doc(7).doc(9).doc(11));
    context.setLimit(42);

    query.setWhiteListBlueprint(std::make_unique<SimpleBlueprint>(SimpleResult().addHit(1).addHit(2).addHit(4).addHit(5).addHit(6).addHit(7).addHit(8).addHit(10).addHit(11).addHit(12)));

    FakeRequestContext requestContext;
    MatchDataLayout mdl;
    query.reserve_handles(mdl);
    query.make_blueprint(requestContext, context, mdl);
    MatchData::UP md = mdl.createMatchData();

    query.optimize(true, true);
    query.fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = query.createSearch(*md);
    SimpleResult exp = SimpleResult().addHit(1).addHit(5).addHit(7).addHit(11);
    SimpleResult act;
    act.search(*search, 42);
    EXPECT_EQ(exp, act);
}

template<typename T1, typename T2>
void verifyThatRankBlueprintAndAndNotStaysOnTopAfterWhiteListing(QueryBuilder<ProtonNodeTypes> & builder) {
    builder.addStringTerm("foo", field, field_id, string_weight);
    builder.addStringTerm("bar", field, field_id, string_weight);
    builder.addStringTerm("baz", field, field_id, string_weight);
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*builder.build());
    Query query;
    query.buildTree(*serializedQueryTree, "", ViewResolver(), plain_index_env);
    FakeSearchContext context(42);
    context.addIdx(0).idx(0).getFake()
            .addResult(field, "foo", FakeResult().doc(1));
    context.setLimit(42);

    query.setWhiteListBlueprint(std::make_unique<SimpleBlueprint>(SimpleResult()));

    FakeRequestContext requestContext;
    MatchDataLayout mdl;
    query.reserve_handles(mdl);
    query.make_blueprint(requestContext, context, mdl);
    const IntermediateBlueprint * root = dynamic_cast<const T1 *>(query.peekRoot());
    ASSERT_TRUE(root != nullptr);
    EXPECT_EQ(2u, root->childCnt());
    const IntermediateBlueprint * second = dynamic_cast<const T2 *>(&root->getChild(0));
    ASSERT_TRUE(second != nullptr);
    EXPECT_EQ(2u, second->childCnt());
    auto first = dynamic_cast<const AndBlueprint *>(&second->getChild(0));
    ASSERT_TRUE(first != nullptr);
    EXPECT_EQ(2u, first->childCnt());
    EXPECT_TRUE(dynamic_cast<const SourceBlenderBlueprint *>(&first->getChild(0)));
    EXPECT_TRUE(dynamic_cast<const SimpleBlueprint *>(&first->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const SourceBlenderBlueprint *>(&second->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const SourceBlenderBlueprint *>(&root->getChild(1)));
}

TEST(QueryTest, requireThatRankBlueprintStaysOnTopAfterWhiteListing)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addRank(2);
    builder.addAndNot(2);
    verifyThatRankBlueprintAndAndNotStaysOnTopAfterWhiteListing<RankBlueprint, AndNotBlueprint>(builder);
}

TEST(QueryTest, requireThatAndNotBlueprintStaysOnTopAfterWhiteListing)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addAndNot(2);
    builder.addRank(2);
    verifyThatRankBlueprintAndAndNotStaysOnTopAfterWhiteListing<AndNotBlueprint, RankBlueprint>(builder);
}


search::query::Node::UP
make_same_element_stack_dump(const std::string &prefix, const std::string &term_prefix)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addSameElement(2, prefix, 0, Weight(1));
    builder.addStringTerm("xyz", term_prefix + "f1", 1, Weight(1));
    builder.addStringTerm("abc", term_prefix + "f2", 2, Weight(1));
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*builder.build());
    auto stack_dump_iterator = serializedQueryTree->makeIterator();
    SameElementModifier sem;
    search::query::Node::UP query = search::query::QueryTreeCreator<ProtonNodeTypes>::create(*stack_dump_iterator);
    query->accept(sem);
    return query;
}

TEST(QueryTest, requireThatSameElementTermsAreProperlyPrefixed)
{
    search::query::Node::UP query = make_same_element_stack_dump("", "");
    auto * root = dynamic_cast<search::query::SameElement *>(query.get());
    EXPECT_EQ(root->getView(), "");
    EXPECT_EQ(root->getChildren().size(), 2u);
    EXPECT_EQ(dynamic_cast<ProtonStringTerm *>(root->getChildren()[0])->getView(), "f1");
    EXPECT_EQ(dynamic_cast<ProtonStringTerm *>(root->getChildren()[1])->getView(), "f2");

    query = make_same_element_stack_dump("abc", "");
    root = dynamic_cast<search::query::SameElement *>(query.get());
    EXPECT_EQ(root->getView(), "abc");
    EXPECT_EQ(root->getChildren().size(), 2u);
    EXPECT_EQ(dynamic_cast<ProtonStringTerm *>(root->getChildren()[0])->getView(), "abc.f1");
    EXPECT_EQ(dynamic_cast<ProtonStringTerm *>(root->getChildren()[1])->getView(), "abc.f2");

    query = make_same_element_stack_dump("abc", "xyz.");
    root = dynamic_cast<search::query::SameElement *>(query.get());
    EXPECT_EQ(root->getView(), "abc");
    EXPECT_EQ(root->getChildren().size(), 2u);
    EXPECT_EQ(dynamic_cast<ProtonStringTerm *>(root->getChildren()[0])->getView(), "abc.xyz.f1");
    EXPECT_EQ(dynamic_cast<ProtonStringTerm *>(root->getChildren()[1])->getView(), "abc.xyz.f2");

    query = make_same_element_stack_dump("abc", "abc.");
    root = dynamic_cast<search::query::SameElement *>(query.get());
    EXPECT_EQ(root->getView(), "abc");
    EXPECT_EQ(root->getChildren().size(), 2u);
    EXPECT_EQ(dynamic_cast<ProtonStringTerm *>(root->getChildren()[0])->getView(), "abc.abc.f1");
    EXPECT_EQ(dynamic_cast<ProtonStringTerm *>(root->getChildren()[1])->getView(), "abc.abc.f2");
}

TEST(QueryTest, requireThatSameElementAllocatesMatchData)
{
    Node::UP node = buildSameElementQueryTree(ViewResolver(), plain_index_env);
    MatchDataLayout mdl;
    MatchDataReserveVisitor visitor(mdl);
    node->accept(visitor);
    MatchData::UP match_data = mdl.createMatchData();
    EXPECT_EQ(3u, match_data->getNumTermFields());
}

TEST(QueryTest, requireThatSameElementIteratorsCanBeBuilt)
{
    Fixture f;
    Node::UP node = buildSameElementQueryTree(ViewResolver(), plain_index_env);
    FakeSearchContext context(10);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, string_term, FakeResult()
                   .doc(4).elem(1).pos(0).doc(8).elem(1).pos(0))
        .addResult(field, prefix_term, FakeResult()
                   .doc(4).elem(2).pos(0).doc(8).elem(1).pos(1));
    SearchIterator::UP iterator = f.getIterator(*node, context);
    ASSERT_TRUE(iterator);
    EXPECT_TRUE(!iterator->seek(4));
    EXPECT_TRUE(iterator->seek(8));
}

TEST(QueryTest, andnot_below_same_element_is_elementwise)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addSameElement(1, "top", 0, Weight(1));
    builder.addAndNot(2);
    builder.addStringTerm("xyz", "f1", 1, Weight(1));
    builder.addStringTerm("abc", "f2", 2, Weight(1));
    auto query = builder.build();
    auto root = dynamic_cast<search::query::SameElement *>(query.get());
    ASSERT_NE(nullptr, root);
    EXPECT_EQ(root->getChildren().size(), 1u);
    auto child = dynamic_cast<ProtonAndNot*>(root->getChildren()[0]);
    ASSERT_NE(nullptr, child);
    EXPECT_FALSE(child->elementwise);
    SameElementModifier sem;
    query->accept(sem);
    EXPECT_TRUE(child->elementwise);
}

TEST(QueryTest, requireThatConstBoolBlueprintsAreCreatedCorrectly)
{
    using search::queryeval::AlwaysTrueBlueprint;
    using search::queryeval::EmptyBlueprint;

    ProtonTrue true_node;
    ProtonFalse false_node;

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.setLimit(1000);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, "foo", FakeResult().doc(1).doc(3));

    MatchDataLayout mdl;
    Blueprint::UP t_blueprint = BlueprintBuilder::build(requestContext, true_node, context, mdl);
    auto *tbp = dynamic_cast<AlwaysTrueBlueprint*>(t_blueprint.get());
    EXPECT_TRUE(tbp != nullptr);

    Blueprint::UP f_blueprint = BlueprintBuilder::build(requestContext, false_node, context, mdl);
    auto *fbp = dynamic_cast<EmptyBlueprint*>(f_blueprint.get());
    EXPECT_TRUE(fbp != nullptr);
}

class GlobalFilterBlueprint : public SimpleBlueprint {
private:
    bool _want_global_filter;
public:
    std::shared_ptr<const GlobalFilter> filter;
    double estimated_hit_ratio;
    GlobalFilterBlueprint(const SimpleResult& result, bool want_global_filter)
        : search::queryeval::SimpleBlueprint(result),
          _want_global_filter(want_global_filter),
          filter(),
          estimated_hit_ratio(-1.0)
    {
    }
    ~GlobalFilterBlueprint() override;
    bool want_global_filter(Blueprint::GlobalFilterLimits&) const override {
        return _want_global_filter;
    }
    void set_global_filter(const GlobalFilter& filter_, double estimated_hit_ratio_) override {
        filter = filter_.shared_from_this();
        estimated_hit_ratio = estimated_hit_ratio_;
    }
};

GlobalFilterBlueprint::~GlobalFilterBlueprint() = default;

TEST(QueryTest, global_filter_is_calculated_and_handled)
{
    // estimated hits = 3, estimated hit ratio = 0.3
    auto result = SimpleResult().addHit(3).addHit(5).addHit(7);
    uint32_t docid_limit = 10;
    { // global filter is not wanted
        GlobalFilterBlueprint bp(result, false);
        auto res = Query::handle_global_filter(bp, docid_limit, 0, 1, ttb(), nullptr);
        EXPECT_FALSE(res);
        EXPECT_FALSE(bp.filter);
        EXPECT_EQ(-1.0, bp.estimated_hit_ratio);
    }
    { // estimated_hit_ratio < global_filter_lower_limit
        GlobalFilterBlueprint bp(result, true);
        auto res = Query::handle_global_filter(bp, docid_limit, 0.31, 1, ttb(), nullptr);
        EXPECT_FALSE(res);
        EXPECT_FALSE(bp.filter);
        EXPECT_EQ(-1.0, bp.estimated_hit_ratio);
    }
    { // estimated_hit_ratio <= global_filter_upper_limit
        GlobalFilterBlueprint bp(result, true);
        auto res = Query::handle_global_filter(bp, docid_limit, 0, 0.3, ttb(), nullptr);
        EXPECT_TRUE(res);
        EXPECT_TRUE(bp.filter);
        EXPECT_TRUE(bp.filter->is_active());
        EXPECT_EQ(0.3, bp.estimated_hit_ratio);

        EXPECT_EQ(3u, bp.filter->count());
        EXPECT_TRUE(bp.filter->check(3));
        EXPECT_TRUE(bp.filter->check(5));
        EXPECT_TRUE(bp.filter->check(7));
    }
    { // estimated_hit_ratio > global_filter_upper_limit
        GlobalFilterBlueprint bp(result, true);
        auto res = Query::handle_global_filter(bp, docid_limit, 0, 0.29, ttb(), nullptr);
        EXPECT_TRUE(res);
        EXPECT_TRUE(bp.filter);
        EXPECT_FALSE(bp.filter->is_active());
        EXPECT_EQ(0.3, bp.estimated_hit_ratio);
    }
}

bool query_needs_ranking(const std::string& stack_dump)
{
    Query query;
    auto serializedQueryTree = SerializedQueryTree::fromStackDump(stack_dump);
    query.buildTree(*serializedQueryTree, "", ViewResolver(), plain_index_env);
    return query.needs_ranking();

}

TEST(QueryTest, normal_term_doesnt_need_ranking)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm("xyz", "f1", 1, Weight(1));
    EXPECT_FALSE(query_needs_ranking(StackDumpCreator::create(*builder.build())));
}

TEST(QueryTest, weak_and_term_needs_ranking)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addWeakAnd(1, 10, "f1");
    builder.addStringTerm("xyz", "f1", 1, Weight(1));
    EXPECT_TRUE(query_needs_ranking(StackDumpCreator::create(*builder.build())));
}

TEST(QueryTest, wand_term_needs_ranking)
{
    QueryBuilder<ProtonNodeTypes> builder;
    auto& wand = builder.addWandTerm(1, "f1", 1, Weight(1), 10, 0, 1.0);
    wand.addTerm("xyz", Weight(1));
    EXPECT_TRUE(query_needs_ranking(StackDumpCreator::create(*builder.build())));
}

TEST(QueryTest, nearest_neighbor_term_needs_ranking)
{
    QueryBuilder<ProtonNodeTypes> builder;
    search::query::NearestNeighborTerm::HnswParams hnsw_params;
    hnsw_params.distance_threshold = 1.5;
    hnsw_params.explore_additional_hits = 100;
    builder.add_nearest_neighbor_term("qtensor", "f1", 1, Weight(1), 10, true, hnsw_params);
    EXPECT_TRUE(query_needs_ranking(StackDumpCreator::create(*builder.build())));
}

}  // namespace
}  // namespace proton::matching

GTEST_MAIN_RUN_ALL_TESTS()
