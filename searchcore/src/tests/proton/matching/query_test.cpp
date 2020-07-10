// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for query.

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

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/log/log.h>
LOG_SETUP("query_test");
#include <vespa/searchlib/query/tree/querytreecreator.h>

using document::PositionDataType;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::ITermData;
using search::fef::ITermFieldData;
using search::fef::IllegalHandle;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldHandle;
using search::query::CustomTypeTermVisitor;
using search::query::Node;
using search::query::QueryBuilder;
using search::query::Range;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::queryeval::termAsString;
using search::queryeval::Blueprint;
using search::queryeval::FakeResult;
using search::queryeval::FakeSearchable;
using search::queryeval::FakeRequestContext;
using search::queryeval::FakeBlueprint;
using search::queryeval::FieldSpec;
using search::queryeval::FieldSpecList;
using search::queryeval::Searchable;
using search::queryeval::SearchIterator;
using search::queryeval::SimpleBlueprint;
using search::queryeval::SimpleResult;
using search::queryeval::ParallelWeakAndBlueprint;
using search::queryeval::RankBlueprint;
using search::queryeval::AndBlueprint;
using search::queryeval::IntermediateBlueprint;
using search::queryeval::AndNotBlueprint;
using search::queryeval::SourceBlenderBlueprint;
using search::queryeval::ExecuteInfo;

using std::string;
using std::vector;
namespace fef_test = search::fef::test;
using CollectionType = FieldInfo::CollectionType;

namespace proton::matching {
namespace {

class Test : public vespalib::TestApp {
    MatchData::UP _match_data;
    Blueprint::UP _blueprint;
    FakeRequestContext _requestContext;

    void setUp();
    void tearDown();

    void requireThatMatchDataIsReserved();
    void requireThatMatchDataIsReservedForEachFieldInAView();
    void requireThatTermsAreLookedUp();
    void requireThatTermsAreLookedUpInMultipleFieldsFromAView();
    void requireThatAttributeTermsAreLookedUpInAttributeSource();
    void requireThatAttributeTermDataHandlesAreAllocated();
    void requireThatTermDataIsFilledIn();

    SearchIterator::UP getIterator(Node &node, ISearchContext &context);

    void requireThatSingleIndexCanUseBlendingAsBlacklisting();
    void requireThatIteratorsAreBuiltWithBlending();
    void requireThatIteratorsAreBuiltForAllTermNodes();
    void requireThatNearIteratorsCanBeBuilt();
    void requireThatONearIteratorsCanBeBuilt();
    void requireThatPhraseIteratorsCanBeBuilt();

    void requireThatUnknownFieldActsEmpty();
    void requireThatIllegalFieldsAreIgnored();
    void requireThatQueryGluesEverythingTogether();
    void requireThatLocationIsAddedTheCorrectPlace();
    void requireThatQueryAddsLocation();
    void requireThatQueryAddsLocationCutoff();
    void requireThatFakeFieldSearchDumpsDiffer();
    void requireThatNoDocsGiveZeroDocFrequency();
    void requireThatWeakAndBlueprintsAreCreatedCorrectly();
    void requireThatParallelWandBlueprintsAreCreatedCorrectly();
    void requireThatWhiteListBlueprintCanBeUsed();
    void requireThatRankBlueprintStaysOnTopAfterWhiteListing();
    void requireThatAndNotBlueprintStaysOnTopAfterWhiteListing();
    void requireThatSameElementTermsAreProperlyPrefixed();
    void requireThatSameElementDoesNotAllocateMatchData();
    void requireThatSameElementIteratorsCanBeBuilt();

public:
    ~Test() override;
    int Main() override;
};

#define TEST_CALL(func) \
    TEST_DO(setUp()); \
    TEST_DO(func()); \
    TEST_DO(tearDown())

void Test::setUp() {
    _match_data.reset();
    _blueprint.reset();
}

void Test::tearDown() {
    _match_data.reset();
    _blueprint.reset();
}

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
    FieldInfo field_info(FieldType::INDEX, CollectionType::SINGLE, field, field_id);
    plain_index_env.getFields().push_back(field_info);

    FieldInfo field_info1(FieldType::INDEX, CollectionType::SINGLE, resolved_field1, field_id);
    resolved_index_env.getFields().push_back(field_info1);
    FieldInfo field_info2(FieldType::INDEX, CollectionType::SINGLE, resolved_field2, field_id + 1);
    resolved_index_env.getFields().push_back(field_info2);

    FieldInfo attr_info(FieldType::ATTRIBUTE, CollectionType::SINGLE, field, 0);
    attribute_index_env.getFields().push_back(attr_info);
    FieldInfo loc_field_info = FieldInfo(FieldType::ATTRIBUTE, CollectionType::SINGLE,
                                     PositionDataType::getZCurveFieldName(loc_field), field_id + 1);
    plain_index_env.getFields().push_back(loc_field_info);
    attribute_index_env.getFields().push_back(loc_field_info);
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
    query_builder.addSameElement(2, field);
    query_builder.addStringTerm(string_term, field, 0, Weight(0));
    query_builder.addStringTerm(prefix_term, field, 1, Weight(0));
    Node::UP node = query_builder.build();
    ResolveViewVisitor visitor(resolver, idxEnv);
    node->accept(visitor);
    return node;
}

void Test::requireThatMatchDataIsReserved() {
    Node::UP node = buildQueryTree(ViewResolver(), plain_index_env);

    MatchDataLayout mdl;
    MatchDataReserveVisitor visitor(mdl);
    node->accept(visitor);
    MatchData::UP match_data = mdl.createMatchData();

    EXPECT_EQUAL(term_count, match_data->getNumTermFields());
}

ViewResolver getViewResolver() {
    ViewResolver resolver;
    resolver.add(field, resolved_field1);
    resolver.add(field, resolved_field2);
    return resolver;
}

void Test::requireThatMatchDataIsReservedForEachFieldInAView() {
    Node::UP node = buildQueryTree(getViewResolver(), resolved_index_env);

    MatchDataLayout mdl;
    MatchDataReserveVisitor visitor(mdl);
    node->accept(visitor);
    MatchData::UP match_data = mdl.createMatchData();

    EXPECT_EQUAL(term_count * 2, match_data->getNumTermFields());
}

class LookupTestCheckerVisitor : public CustomTypeTermVisitor<ProtonNodeTypes>
{
    int Main() { return 0; }

public:
    template <class TermType>
    void checkNode(const TermType &n, int estimatedHitCount, bool empty) {
        EXPECT_EQUAL(empty, (estimatedHitCount == 0));
        EXPECT_EQUAL((double)estimatedHitCount / doc_count, n.field(0).getDocFreq());
    }

    void visit(ProtonNumberTerm &n) override { checkNode(n, 1, false); }
    void visit(ProtonLocationTerm &n) override { checkNode(n, 0, true); }
    void visit(ProtonPrefixTerm &n) override { checkNode(n, 1, false); }
    void visit(ProtonRangeTerm &n) override { checkNode(n, 2, false); }
    void visit(ProtonStringTerm &n) override { checkNode(n, 2, false); }
    void visit(ProtonSubstringTerm &n) override { checkNode(n, 0, true); }
    void visit(ProtonSuffixTerm &n) override { checkNode(n, 2, false); }
    void visit(ProtonPhrase &n) override { checkNode(n, 0, true); }
    void visit(ProtonWeightedSetTerm &) override {}
    void visit(ProtonDotProduct &) override {}
    void visit(ProtonWandTerm &) override {}
    void visit(ProtonPredicateQuery &) override {}
    void visit(ProtonRegExpTerm &) override {}
    void visit(ProtonNearestNeighborTerm &) override {}
};

void Test::requireThatTermsAreLookedUp() {
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

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, *node, context);

    LookupTestCheckerVisitor checker;
    TEST_DO(node->accept(checker));
}

void Test::requireThatTermsAreLookedUpInMultipleFieldsFromAView() {
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

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, *node, context);

    LookupTestCheckerVisitor checker;
    TEST_DO(node->accept(checker));
}

void Test::requireThatAttributeTermsAreLookedUpInAttributeSource() {
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

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, node, context);

    EXPECT_TRUE(!blueprint->getState().estimate().empty);
    EXPECT_EQUAL(1u, blueprint->getState().estimate().estHits);
}

void Test::requireThatAttributeTermDataHandlesAreAllocated() {
    const string term = "bar";
    ProtonStringTerm node(term, field, 1, Weight(2));
    node.resolve(ViewResolver(), attribute_index_env);

    FakeSearchContext context;
    FakeRequestContext requestContext;

    MatchDataLayout mdl;
    MatchDataReserveVisitor reserve_visitor(mdl);
    node.accept(reserve_visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, node, context);

    MatchData::UP match_data = mdl.createMatchData();

    EXPECT_EQUAL(1u, match_data->getNumTermFields());
    EXPECT_TRUE(node.field(0).attribute_field);
}


class SetUpTermDataTestCheckerVisitor
    : public CustomTypeTermVisitor<ProtonNodeTypes>
{
    int Main() { return 0; }

public:
    void visit(ProtonNumberTerm &) override {}
    void visit(ProtonLocationTerm &) override {}
    void visit(ProtonPrefixTerm &) override {}
    void visit(ProtonRangeTerm &) override {}

    void visit(ProtonStringTerm &n) override {
        const ITermData &term_data = n;
        EXPECT_EQUAL(string_weight.percent(),
                   term_data.getWeight().percent());
        EXPECT_EQUAL(1u, term_data.getPhraseLength());
        EXPECT_EQUAL(string_id, term_data.getUniqueId());
        EXPECT_EQUAL(term_data.numFields(), n.numFields());
        for (size_t i = 0; i < term_data.numFields(); ++i) {
            const ITermFieldData &term_field_data = term_data.field(i);
            EXPECT_APPROX(2.0 / doc_count, term_field_data.getDocFreq(), 1.0e-6);
            EXPECT_TRUE(!n.field(i).attribute_field);
            EXPECT_EQUAL(field_id + i, term_field_data.getFieldId());
        }
    }

    void visit(ProtonSubstringTerm &) override {}
    void visit(ProtonSuffixTerm &) override {}
    void visit(ProtonPhrase &n) override {
        const ITermData &term_data = n;
        EXPECT_EQUAL(2u, term_data.getPhraseLength());
    }
    void visit(ProtonWeightedSetTerm &) override {}
    void visit(ProtonDotProduct &) override {}
    void visit(ProtonWandTerm &) override {}
    void visit(ProtonPredicateQuery &) override {}
    void visit(ProtonRegExpTerm &) override {}
    void visit(ProtonNearestNeighborTerm &) override {}
};

void Test::requireThatTermDataIsFilledIn() {
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

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, *node, context);

    TEST_DO(
            SetUpTermDataTestCheckerVisitor checker;
            node->accept(checker);
    );
}

SearchIterator::UP Test::getIterator(Node &node, ISearchContext &context) {
    MatchDataLayout mdl;
    MatchDataReserveVisitor mdr_visitor(mdl);
    node.accept(mdr_visitor);
    _match_data = mdl.createMatchData();

    _blueprint = BlueprintBuilder::build(_requestContext, node, context);

    _blueprint->fetchPostings(ExecuteInfo::TRUE);
    SearchIterator::UP search(_blueprint->createSearch(*_match_data, true));
    search->initFullRange();
    return search;
}

FakeIndexSearchable getFakeSearchable(const string &term, int doc1, int doc2) {
    FakeIndexSearchable source;
    source.getFake().addResult(field, term,
                               FakeResult().doc(doc1).pos(2).doc(doc2).pos(3));
    return source;
}

void Test::requireThatSingleIndexCanUseBlendingAsBlacklisting() {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(string_term, field, 1, Weight(2))
        .resolve(ViewResolver(), plain_index_env);
    Node::UP node = builder.build();
    ASSERT_TRUE(node.get());

    FakeSearchContext context;
    context.addIdx(1).idx(0) = getFakeSearchable(string_term, 2, 5);
    context.selector().setSource(5, 1);

    SearchIterator::UP iterator = getIterator(*node, context);
    ASSERT_TRUE(iterator.get());
    EXPECT_TRUE(!iterator->seek(1));
    EXPECT_TRUE(!iterator->seek(2));
    EXPECT_TRUE(iterator->seek(5));
    iterator->unpack(5);
}

void Test::requireThatIteratorsAreBuiltWithBlending() {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(string_term, field, 1, Weight(2))
        .resolve(ViewResolver(), plain_index_env);
    Node::UP node = builder.build();
    ASSERT_TRUE(node.get());

    FakeSearchContext context;
    context.addIdx(1).idx(0) = getFakeSearchable(string_term, 3, 7);
    context.addIdx(0).idx(1) = getFakeSearchable(string_term, 2, 6);
    context.selector().setSource(3, 1);
    context.selector().setSource(7, 1);

    SearchIterator::UP iterator = getIterator(*node, context);
    ASSERT_TRUE(iterator.get());

    EXPECT_TRUE(!iterator->seek(1));
    EXPECT_TRUE(iterator->seek(2));
    EXPECT_TRUE(iterator->seek(3));
    EXPECT_TRUE(iterator->seek(6));
    EXPECT_TRUE(iterator->seek(7));
}

void Test::requireThatIteratorsAreBuiltForAllTermNodes() {
    Node::UP node = buildQueryTree(ViewResolver(), plain_index_env);
    ASSERT_TRUE(node.get());

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

    SearchIterator::UP iterator = getIterator(*node, context);
    ASSERT_TRUE(iterator.get());

    EXPECT_TRUE(!iterator->seek(1));
    EXPECT_TRUE(iterator->seek(2));
    EXPECT_TRUE(iterator->seek(4));
    EXPECT_TRUE(iterator->seek(8));
    EXPECT_TRUE(iterator->seek(15));
    EXPECT_TRUE(iterator->seek(16));
    EXPECT_TRUE(iterator->seek(23));
    EXPECT_TRUE(iterator->seek(42));
}

void Test::requireThatNearIteratorsCanBeBuilt() {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addNear(2, 4);
    builder.addStringTerm(string_term, field, 1, Weight(2));
    builder.addStringTerm(prefix_term, field, 1, Weight(2));
    Node::UP node = builder.build();
    ViewResolver resolver;
    ResolveViewVisitor visitor(resolver, plain_index_env);
    node->accept(visitor);
    ASSERT_TRUE(node.get());

    FakeSearchContext context(8);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, prefix_term, FakeResult()
                   .doc(4).pos(2).len(50).doc(8).pos(2).len(50))
        .addResult(field, string_term, FakeResult()
                   .doc(4).pos(40).len(50).doc(8).pos(5).len(50));

    SearchIterator::UP iterator = getIterator(*node, context);
    ASSERT_TRUE(iterator.get());
    EXPECT_TRUE(!iterator->seek(4));
    EXPECT_TRUE(iterator->seek(8));
}

void Test::requireThatONearIteratorsCanBeBuilt() {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addONear(2, 4);
    builder.addStringTerm(string_term, field, 1, Weight(2));
    builder.addStringTerm(prefix_term, field, 1, Weight(2));
    Node::UP node = builder.build();
    ViewResolver resolver;
    ResolveViewVisitor visitor(resolver, plain_index_env);
    node->accept(visitor);
    ASSERT_TRUE(node.get());

    FakeSearchContext context(8);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, string_term, FakeResult()
                   .doc(4).pos(5).len(50).doc(8).pos(2).len(50))
        .addResult(field, prefix_term, FakeResult()
                   .doc(4).pos(2).len(50).doc(8).pos(5).len(50));

    SearchIterator::UP iterator = getIterator(*node, context);
    ASSERT_TRUE(iterator.get());
    EXPECT_TRUE(!iterator->seek(4));
    EXPECT_TRUE(iterator->seek(8));
}

void Test::requireThatPhraseIteratorsCanBeBuilt() {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addPhrase(3, field, 0, Weight(42));
    builder.addStringTerm(string_term, field, 1, Weight(2));
    builder.addStringTerm(prefix_term, field, 1, Weight(2));
    builder.addStringTerm(suffix_term, field, 1, Weight(2));
    Node::UP node = builder.build();
    ViewResolver resolver;
    ResolveViewVisitor visitor(resolver, plain_index_env);
    node->accept(visitor);
    ASSERT_TRUE(node.get());

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

    SearchIterator::UP iterator = getIterator(*node, context);
    ASSERT_TRUE(iterator.get());
    EXPECT_TRUE(!iterator->seek(4));
    EXPECT_TRUE(!iterator->seek(5));
    EXPECT_TRUE(iterator->seek(8));
    EXPECT_TRUE(!iterator->seek(9));
    EXPECT_TRUE(iterator->isAtEnd());
}

void
Test::requireThatUnknownFieldActsEmpty()
{
    FakeSearchContext context;
    context.addIdx(0).idx(0).getFake()
        .addResult(unknown_field, string_term, FakeResult()
                   .doc(4).pos(3).len(50)
                   .doc(5).pos(2).len(50));

    ProtonNodeTypes::StringTerm
        node(string_term, unknown_field, string_id, string_weight);
    node.resolve(ViewResolver(), plain_index_env);

    std::vector<const ITermData *> terms;
    TermDataExtractor::extractTerms(node, terms);

    SearchIterator::UP iterator = getIterator(node, context);

    ASSERT_TRUE(EXPECT_EQUAL(1u, terms.size()));
    EXPECT_EQUAL(0u, terms[0]->numFields());

    ASSERT_TRUE(iterator.get());
    EXPECT_TRUE(!iterator->seek(1));
    EXPECT_TRUE(iterator->isAtEnd());
}

void
Test::requireThatIllegalFieldsAreIgnored()
{
    ProtonNodeTypes::StringTerm
        node(string_term, unknown_field, string_id, string_weight);
    node.resolve(ViewResolver(), plain_index_env);

    FakeRequestContext requestContext;
    FakeSearchContext context;

    MatchDataLayout mdl;
    MatchDataReserveVisitor reserve_visitor(mdl);
    node.accept(reserve_visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, node, context);

    EXPECT_EQUAL(0u, node.numFields());

    MatchData::UP match_data = mdl.createMatchData();
    EXPECT_EQUAL(0u, match_data->getNumTermFields());
}

void Test::requireThatQueryGluesEverythingTogether() {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(string_term, field, 1, Weight(2));
    string stack_dump = StackDumpCreator::create(*builder.build());

    Query query;
    query.buildTree(stack_dump, "", ViewResolver(), plain_index_env);
    vector<const ITermData *> term_data;
    query.extractTerms(term_data);
    EXPECT_EQUAL(1u, term_data.size());

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.setLimit(42);
    MatchDataLayout mdl;
    query.reserveHandles(requestContext, context, mdl);
    MatchData::UP md = mdl.createMatchData();
    EXPECT_EQUAL(1u, md->getNumTermFields());

    query.optimize();
    query.fetchPostings();
    SearchIterator::UP search = query.createSearch(*md);
    ASSERT_TRUE(search.get());
}

void checkQueryAddsLocation(Test &test, const string &loc_string) {
    fef_test::IndexEnvironment index_environment;
    FieldInfo field_info(FieldType::INDEX, CollectionType::SINGLE, field, 0);
    index_environment.getFields().push_back(field_info);
    field_info = FieldInfo(FieldType::ATTRIBUTE, CollectionType::SINGLE,
                           PositionDataType::getZCurveFieldName(loc_field), 1);
    index_environment.getFields().push_back(field_info);

    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(string_term, field, 1, Weight(2));
    string stack_dump = StackDumpCreator::create(*builder.build());

    Query query;
    query.buildTree(stack_dump,
                    loc_field + ":" + loc_string,
                    ViewResolver(), index_environment);
    vector<const ITermData *> term_data;
    query.extractTerms(term_data);
    test.EXPECT_EQUAL(1u, term_data.size());

    FakeRequestContext requestContext;
    FakeSearchContext context;
    context.addIdx(0).setLimit(42);
    MatchDataLayout mdl;
    query.reserveHandles(requestContext, context, mdl);
    MatchData::UP md = mdl.createMatchData();
    test.EXPECT_EQUAL(2u, md->getNumTermFields());

    query.fetchPostings();
    SearchIterator::UP search = query.createSearch(*md);
    test.ASSERT_TRUE(search.get());
    if (!test.EXPECT_NOT_EQUAL(string::npos, search->asString().find(loc_string))) {
        fprintf(stderr, "search (missing loc_string '%s'): %s",
                loc_string.c_str(), search->asString().c_str());
    }
}

template<typename T1, typename T2>
void verifyThatRankBlueprintAndAndNotStaysOnTopAfterLocation(QueryBuilder<ProtonNodeTypes> & builder) {
    const string loc_string = "(2,10,10,3,0,1,0,0)";
    builder.addStringTerm("foo", field, field_id, string_weight);
    builder.addStringTerm("bar", field, field_id, string_weight);
    builder.addStringTerm("baz", field, field_id, string_weight);
    std::string stackDump = StackDumpCreator::create(*builder.build());

    Query query;
    query.buildTree(stackDump, loc_field + ":" + loc_string, ViewResolver(), attribute_index_env);
    FakeSearchContext context(42);
    context.addIdx(0).idx(0).getFake()
            .addResult(field, "foo", FakeResult().doc(1));
    context.setLimit(42);

    query.setWhiteListBlueprint(std::make_unique<SimpleBlueprint>(SimpleResult()));

    FakeRequestContext requestContext;
    MatchDataLayout mdl;
    query.reserveHandles(requestContext, context, mdl);
    const IntermediateBlueprint * root = dynamic_cast<const T1 *>(query.peekRoot());
    ASSERT_TRUE(root != nullptr);
    EXPECT_EQUAL(2u, root->childCnt());
    const IntermediateBlueprint * second = dynamic_cast<const T2 *>(&root->getChild(0));
    ASSERT_TRUE(second != nullptr);
    EXPECT_EQUAL(2u, second->childCnt());
    auto first = dynamic_cast<const AndBlueprint *>(&second->getChild(0));
    ASSERT_TRUE(first != nullptr);
    EXPECT_EQUAL(2u, first->childCnt());
    EXPECT_TRUE(dynamic_cast<const AndBlueprint *>(&first->getChild(0)));
    auto bottom = dynamic_cast<const AndBlueprint *>(&first->getChild(0));
    EXPECT_EQUAL(2u, bottom->childCnt());
    EXPECT_TRUE(dynamic_cast<const FakeBlueprint *>(&bottom->getChild(0)));
    EXPECT_TRUE(dynamic_cast<const FakeBlueprint *>(&bottom->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const SimpleBlueprint *>(&first->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const FakeBlueprint *>(&second->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const FakeBlueprint *>(&root->getChild(1)));
}

void Test::requireThatLocationIsAddedTheCorrectPlace() {
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

void Test::requireThatQueryAddsLocation() {
    checkQueryAddsLocation(*this, "(2,10,10,3,0,1,0,0)");
}

void Test::requireThatQueryAddsLocationCutoff() {
    checkQueryAddsLocation(*this, "[2,10,10,20,20]");
}

void
Test::requireThatFakeFieldSearchDumpsDiffer()
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
    vespalib::string term1="term1";
    vespalib::string term2="term2";
    ProtonStringTerm n1(term1, "field1", string_id, string_weight);
    ProtonStringTerm n2(term2, "field1", string_id, string_weight);
    ProtonStringTerm n3(term1, "field2", string_id, string_weight);

    FieldSpecList fields1;
    FieldSpecList fields2;
    fields1.add(FieldSpec("field1", fieldId, handle));
    fields2.add(FieldSpec("field2", fieldId, handle));

    Blueprint::UP l1(a.createBlueprint(requestContext, fields1, n1)); // reference
    Blueprint::UP l2(a.createBlueprint(requestContext, fields1, n2)); // term
    Blueprint::UP l3(a.createBlueprint(requestContext, fields2, n3)); // field
    Blueprint::UP l4(b.createBlueprint(requestContext, fields1, n1)); // tag

    l1->fetchPostings(ExecuteInfo::TRUE);
    l2->fetchPostings(ExecuteInfo::TRUE);
    l3->fetchPostings(ExecuteInfo::TRUE);
    l4->fetchPostings(ExecuteInfo::TRUE);

    SearchIterator::UP s1(l1->createSearch(*match_data, true));
    SearchIterator::UP s2(l2->createSearch(*match_data, true));
    SearchIterator::UP s3(l3->createSearch(*match_data, true));
    SearchIterator::UP s4(l4->createSearch(*match_data, true));

    EXPECT_NOT_EQUAL(s1->asString(), s2->asString());
    EXPECT_NOT_EQUAL(s1->asString(), s3->asString());
    EXPECT_NOT_EQUAL(s1->asString(), s4->asString());
}

void Test::requireThatNoDocsGiveZeroDocFrequency() {
    ProtonStringTerm node(string_term, field, string_id, string_weight);
    node.resolve(ViewResolver(), plain_index_env);
    FakeSearchContext context;
    FakeRequestContext requestContext;
    context.setLimit(0);

    MatchDataLayout mdl;
    MatchDataReserveVisitor reserve_visitor(mdl);
    node.accept(reserve_visitor);

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, node, context);

    EXPECT_EQUAL(1u, node.numFields());
    EXPECT_EQUAL(0.0, node.field(0).getDocFreq());
}

void Test::requireThatWeakAndBlueprintsAreCreatedCorrectly() {
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

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, wand, context);
    auto *wbp = dynamic_cast<WeakAndBlueprint*>(blueprint.get());
    ASSERT_TRUE(wbp != nullptr);
    ASSERT_EQUAL(2u, wbp->getWeights().size());
    ASSERT_EQUAL(2u, wbp->childCnt());
    EXPECT_EQUAL(123u, wbp->getN());
    EXPECT_EQUAL(3u, wbp->getWeights()[0]);
    EXPECT_EQUAL(7u, wbp->getWeights()[1]);
    EXPECT_EQUAL(2u, wbp->getChild(0).getState().estimate().estHits);
    EXPECT_EQUAL(3u, wbp->getChild(1).getState().estimate().estHits);
}

void Test::requireThatParallelWandBlueprintsAreCreatedCorrectly() {
    using search::queryeval::WeakAndBlueprint;

    ProtonWandTerm wand(field, 42, Weight(100), 123, 9000, 1.25);
    wand.append(Node::UP(new ProtonStringTerm("foo", field, 0, Weight(3))));
    wand.append(Node::UP(new ProtonStringTerm("bar", field, 0, Weight(7))));

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

    Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, wand, context);
    auto *wbp = dynamic_cast<ParallelWeakAndBlueprint*>(blueprint.get());
    ASSERT_TRUE(wbp != nullptr);
    EXPECT_EQUAL(9000, wbp->getScoreThreshold());
    EXPECT_EQUAL(1.25, wbp->getThresholdBoostFactor());
    EXPECT_EQUAL(1000u, wbp->get_docid_limit());
}

void
Test::requireThatWhiteListBlueprintCanBeUsed()
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm("foo", field, field_id, string_weight);
    std::string stackDump = StackDumpCreator::create(*builder.build());

    Query query;
    query.buildTree(stackDump, "", ViewResolver(), plain_index_env);

    FakeSearchContext context(42);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, "foo", FakeResult().doc(1).doc(3).doc(5).doc(7).doc(9).doc(11));
    context.setLimit(42);

    query.setWhiteListBlueprint(SimpleBlueprint::UP(new SimpleBlueprint(SimpleResult().addHit(1).addHit(2).addHit(4).addHit(5).addHit(6).addHit(7).addHit(8).addHit(10).addHit(11).addHit(12))));

    FakeRequestContext requestContext;
    MatchDataLayout mdl;
    query.reserveHandles(requestContext, context, mdl);
    MatchData::UP md = mdl.createMatchData();

    query.optimize();
    query.fetchPostings();
    SearchIterator::UP search = query.createSearch(*md);
    SimpleResult exp = SimpleResult().addHit(1).addHit(5).addHit(7).addHit(11);
    SimpleResult act;
    act.search(*search);
    EXPECT_EQUAL(exp, act);
}

template<typename T1, typename T2>
void verifyThatRankBlueprintAndAndNotStaysOnTopAfterWhiteListing(QueryBuilder<ProtonNodeTypes> & builder) {
    builder.addStringTerm("foo", field, field_id, string_weight);
    builder.addStringTerm("bar", field, field_id, string_weight);
    builder.addStringTerm("baz", field, field_id, string_weight);
    std::string stackDump = StackDumpCreator::create(*builder.build());
    Query query;
    query.buildTree(stackDump, "", ViewResolver(), plain_index_env);
    FakeSearchContext context(42);
    context.addIdx(0).idx(0).getFake()
            .addResult(field, "foo", FakeResult().doc(1));
    context.setLimit(42);

    query.setWhiteListBlueprint(std::make_unique<SimpleBlueprint>(SimpleResult()));

    FakeRequestContext requestContext;
    MatchDataLayout mdl;
    query.reserveHandles(requestContext, context, mdl);
    const IntermediateBlueprint * root = dynamic_cast<const T1 *>(query.peekRoot());
    ASSERT_TRUE(root != nullptr);
    EXPECT_EQUAL(2u, root->childCnt());
    const IntermediateBlueprint * second = dynamic_cast<const T2 *>(&root->getChild(0));
    ASSERT_TRUE(second != nullptr);
    EXPECT_EQUAL(2u, second->childCnt());
    auto first = dynamic_cast<const AndBlueprint *>(&second->getChild(0));
    ASSERT_TRUE(first != nullptr);
    EXPECT_EQUAL(2u, first->childCnt());
    EXPECT_TRUE(dynamic_cast<const SourceBlenderBlueprint *>(&first->getChild(0)));
    EXPECT_TRUE(dynamic_cast<const SimpleBlueprint *>(&first->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const SourceBlenderBlueprint *>(&second->getChild(1)));
    EXPECT_TRUE(dynamic_cast<const SourceBlenderBlueprint *>(&root->getChild(1)));
}

void Test::requireThatRankBlueprintStaysOnTopAfterWhiteListing() {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addRank(2);
    builder.addAndNot(2);
    verifyThatRankBlueprintAndAndNotStaysOnTopAfterWhiteListing<RankBlueprint, AndNotBlueprint>(builder);
}

void Test::requireThatAndNotBlueprintStaysOnTopAfterWhiteListing() {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addAndNot(2);
    builder.addRank(2);
    verifyThatRankBlueprintAndAndNotStaysOnTopAfterWhiteListing<AndNotBlueprint, RankBlueprint>(builder);
}


search::query::Node::UP
make_same_element_stack_dump(const vespalib::string &prefix, const vespalib::string &term_prefix)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addSameElement(2, prefix);
    builder.addStringTerm("xyz", term_prefix + "f1", 1, search::query::Weight(1));
    builder.addStringTerm("abc", term_prefix + "f2", 2, search::query::Weight(1));
    vespalib::string stack = StackDumpCreator::create(*builder.build());
    search::SimpleQueryStackDumpIterator stack_dump_iterator(stack);
    SameElementModifier sem;
    search::query::Node::UP query = search::query::QueryTreeCreator<ProtonNodeTypes>::create(stack_dump_iterator);
    query->accept(sem);
    return query;
}

void
Test::requireThatSameElementTermsAreProperlyPrefixed()
{
    search::query::Node::UP query = make_same_element_stack_dump("", "");
    auto * root = dynamic_cast<search::query::SameElement *>(query.get());
    EXPECT_EQUAL(root->getView(), "");
    EXPECT_EQUAL(root->getChildren().size(), 2u);
    EXPECT_EQUAL(dynamic_cast<ProtonStringTerm *>(root->getChildren()[0])->getView(), "f1");
    EXPECT_EQUAL(dynamic_cast<ProtonStringTerm *>(root->getChildren()[1])->getView(), "f2");

    query = make_same_element_stack_dump("abc", "");
    root = dynamic_cast<search::query::SameElement *>(query.get());
    EXPECT_EQUAL(root->getView(), "abc");
    EXPECT_EQUAL(root->getChildren().size(), 2u);
    EXPECT_EQUAL(dynamic_cast<ProtonStringTerm *>(root->getChildren()[0])->getView(), "abc.f1");
    EXPECT_EQUAL(dynamic_cast<ProtonStringTerm *>(root->getChildren()[1])->getView(), "abc.f2");

    query = make_same_element_stack_dump("abc", "xyz.");
    root = dynamic_cast<search::query::SameElement *>(query.get());
    EXPECT_EQUAL(root->getView(), "abc");
    EXPECT_EQUAL(root->getChildren().size(), 2u);
    EXPECT_EQUAL(dynamic_cast<ProtonStringTerm *>(root->getChildren()[0])->getView(), "abc.xyz.f1");
    EXPECT_EQUAL(dynamic_cast<ProtonStringTerm *>(root->getChildren()[1])->getView(), "abc.xyz.f2");

    query = make_same_element_stack_dump("abc", "abc.");
    root = dynamic_cast<search::query::SameElement *>(query.get());
    EXPECT_EQUAL(root->getView(), "abc");
    EXPECT_EQUAL(root->getChildren().size(), 2u);
    EXPECT_EQUAL(dynamic_cast<ProtonStringTerm *>(root->getChildren()[0])->getView(), "abc.abc.f1");
    EXPECT_EQUAL(dynamic_cast<ProtonStringTerm *>(root->getChildren()[1])->getView(), "abc.abc.f2");
}

void
Test::requireThatSameElementDoesNotAllocateMatchData()
{
    Node::UP node = buildSameElementQueryTree(ViewResolver(), plain_index_env);
    MatchDataLayout mdl;
    MatchDataReserveVisitor visitor(mdl);
    node->accept(visitor);
    MatchData::UP match_data = mdl.createMatchData();
    EXPECT_EQUAL(0u, match_data->getNumTermFields());
}

void
Test::requireThatSameElementIteratorsCanBeBuilt() {
    Node::UP node = buildSameElementQueryTree(ViewResolver(), plain_index_env);
    FakeSearchContext context(10);
    context.addIdx(0).idx(0).getFake()
        .addResult(field, string_term, FakeResult()
                   .doc(4).elem(1).pos(0).doc(8).elem(1).pos(0))
        .addResult(field, prefix_term, FakeResult()
                   .doc(4).elem(2).pos(0).doc(8).elem(1).pos(1));
    SearchIterator::UP iterator = getIterator(*node, context);
    ASSERT_TRUE(iterator.get());
    EXPECT_TRUE(!iterator->seek(4));
    EXPECT_TRUE(iterator->seek(8));
}

Test::~Test() = default;

int
Test::Main()
{
    setupIndexEnvironments();

    TEST_INIT("query_test");

    TEST_CALL(requireThatMatchDataIsReserved);
    TEST_CALL(requireThatMatchDataIsReservedForEachFieldInAView);
    TEST_CALL(requireThatTermsAreLookedUp);
    TEST_CALL(requireThatTermsAreLookedUpInMultipleFieldsFromAView);
    TEST_CALL(requireThatAttributeTermsAreLookedUpInAttributeSource);
    TEST_CALL(requireThatAttributeTermDataHandlesAreAllocated);
    TEST_CALL(requireThatTermDataIsFilledIn);
    TEST_CALL(requireThatSingleIndexCanUseBlendingAsBlacklisting);
    TEST_CALL(requireThatIteratorsAreBuiltWithBlending);
    TEST_CALL(requireThatIteratorsAreBuiltForAllTermNodes);
    TEST_CALL(requireThatNearIteratorsCanBeBuilt);
    TEST_CALL(requireThatONearIteratorsCanBeBuilt);
    TEST_CALL(requireThatPhraseIteratorsCanBeBuilt);
    TEST_CALL(requireThatUnknownFieldActsEmpty);
    TEST_CALL(requireThatIllegalFieldsAreIgnored);
    TEST_CALL(requireThatQueryGluesEverythingTogether);
    TEST_CALL(requireThatLocationIsAddedTheCorrectPlace);
    TEST_CALL(requireThatQueryAddsLocation);
    TEST_CALL(requireThatQueryAddsLocationCutoff);
    TEST_CALL(requireThatFakeFieldSearchDumpsDiffer);
    TEST_CALL(requireThatNoDocsGiveZeroDocFrequency);
    TEST_CALL(requireThatWeakAndBlueprintsAreCreatedCorrectly);
    TEST_CALL(requireThatParallelWandBlueprintsAreCreatedCorrectly);
    TEST_CALL(requireThatWhiteListBlueprintCanBeUsed);
    TEST_CALL(requireThatRankBlueprintStaysOnTopAfterWhiteListing);
    TEST_CALL(requireThatAndNotBlueprintStaysOnTopAfterWhiteListing);
    TEST_CALL(requireThatSameElementTermsAreProperlyPrefixed);
    TEST_CALL(requireThatSameElementDoesNotAllocateMatchData);
    TEST_CALL(requireThatSameElementIteratorsCanBeBuilt);

    TEST_DONE();
}


}  // namespace
}  // namespace proton::matching

TEST_APPHOOK(proton::matching::Test);
