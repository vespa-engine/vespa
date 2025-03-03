// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_blueprint.

#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/predicate/predicate_tree_annotator.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/predicate_blueprint.h>
#include <vespa/searchlib/predicate/predicate_hash.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("predicate_blueprint_test");

using namespace search;
using namespace search::predicate;
using search::fef::TermFieldMatchDataArray;
using search::query::PredicateQueryTerm;
using search::query::SimplePredicateQuery;
using search::query::Weight;
using search::queryeval::FieldSpecBase;
using search::queryeval::PredicateBlueprint;
using search::queryeval::SearchIterator;
using search::queryeval::ExecuteInfo;

namespace {

struct PredicateBlueprintTest : public ::testing::Test {
    FieldSpecBase field;
    AttributeVector::SP attribute;
    SimplePredicateQuery query;

    using IntervalRange = PredicateAttribute::IntervalRange;

    PredicateBlueprintTest();
    ~PredicateBlueprintTest() override;
    PredicateAttribute & guard() {
        return dynamic_cast<PredicateAttribute &>(*attribute);
    }
    PredicateIndex & index() {
        return predicate().getIndex();
    }
    PredicateAttribute & predicate() { return static_cast<PredicateAttribute &>(*attribute); }
    void resize(uint32_t doc_id) {
        while (predicate().getNumDocs() <= doc_id) {
            uint32_t tmp;
            predicate().addDoc(tmp);
            PredicateAttribute::MinFeatureHandle mfh = predicate().getMinFeatureVector();
            const_cast<uint8_t *>(mfh.first)[tmp] = 0;
        }
    }
    void setIntervalRange(uint32_t doc_id, IntervalRange interval_range) {
        const_cast<IntervalRange *>(predicate().getIntervalRangeVector())[doc_id] = interval_range;
    }
    void indexEmptyDocument(uint32_t doc_id, IntervalRange ir = 0x1) {
        resize(doc_id);
        index().indexEmptyDocument(doc_id);
        setIntervalRange(doc_id, ir);
        predicate().updateMaxIntervalRange(ir);
        predicate().commit(false);
    }
    void indexDocument(uint32_t doc_id, const PredicateTreeAnnotations &annotations, IntervalRange ir = 0xffff) {
        resize(doc_id);
        index().indexDocument(doc_id, annotations);
        setIntervalRange(doc_id, ir);
        predicate().updateMaxIntervalRange(ir);
        predicate().commit(false);
    }
};

PredicateBlueprintTest::PredicateBlueprintTest()
    : ::testing::Test(),
      field(42, 0),
      attribute(std::make_shared<PredicateAttribute>("f")),
      query(std::make_unique<PredicateQueryTerm>(),"view", 0, Weight(1))
{
    query.getTerm()->addFeature("key", "value");
    query.getTerm()->addRangeFeature("range_key", 42);
}

PredicateBlueprintTest::~PredicateBlueprintTest() = default;

TEST_F(PredicateBlueprintTest, require_that_blueprint_with_empty_index_estimates_empty) {
    PredicateBlueprint blueprint(field, guard(), query);
    EXPECT_TRUE(blueprint.getState().estimate().empty);
    EXPECT_EQ(0u, blueprint.getState().estimate().estHits);
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_with_zero_constraint_doc_estimates_non_empty) {
    indexEmptyDocument(42);
    PredicateBlueprint blueprint(field, guard(), query);
    EXPECT_FALSE(blueprint.getState().estimate().empty);
    EXPECT_EQ(1u, blueprint.getState().estimate().estHits);
}

const int min_feature = 1;
const uint32_t doc_id = 2;
const uint32_t interval = 0x0001ffff;

TEST_F(PredicateBlueprintTest, require_that_blueprint_with_posting_list_entry_estimates_non_empty) {
    PredicateTreeAnnotations annotations(min_feature);
    annotations.interval_map[PredicateHash::hash64("key=value")] = std::vector<Interval>{{interval}};
    indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(field, guard(), query);
    EXPECT_FALSE(blueprint.getState().estimate().empty);
    EXPECT_EQ(0u, blueprint.getState().estimate().estHits);
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_with_bounds_posting_list_entry_estimates_non_empty) {
    PredicateTreeAnnotations annotations(min_feature);
    annotations.bounds_map[PredicateHash::hash64("range_key=40")] =
        std::vector<IntervalWithBounds>{{interval, 0x80000003}};
    indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(field, guard(), query);
    EXPECT_FALSE(blueprint.getState().estimate().empty);
    EXPECT_EQ(0u, blueprint.getState().estimate().estHits);
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_with_zstar_compressed_estimates_non_empty) {
    PredicateTreeAnnotations annotations(1);
    annotations.interval_map[Constants::z_star_compressed_hash] = std::vector<Interval>{{0xfffe0000}};
    indexDocument(doc_id, annotations);
    PredicateBlueprint blueprint(field, guard(), query);
    EXPECT_FALSE(blueprint.getState().estimate().empty);
    EXPECT_EQ(0u, blueprint.getState().estimate().estHits);
}

void
runQuery(PredicateBlueprintTest & f, std::vector<uint32_t> expected, uint32_t expectCachedSize, uint32_t expectedKV) {
    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    EXPECT_EQ(expectCachedSize, blueprint.getCachedFeatures().size());
    for (uint32_t docId : expected) {
        EXPECT_EQ(expectedKV, uint32_t(blueprint.getKV()[docId]));
    }
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_EQ(SearchIterator::beginId(), it->getDocId());
    std::vector<uint32_t> actual;
    for (it->seek(1); ! it->isAtEnd(); it->seek(it->getDocId()+1)) {
        actual.push_back(it->getDocId());
    }
    EXPECT_EQ(expected.size(), actual.size());
    for (size_t i(0); i < expected.size(); i++) {
        EXPECT_EQ(expected[i], actual[i]);
    }
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_can_create_search) {
    PredicateTreeAnnotations annotations(1);
    annotations.interval_map[PredicateHash::hash64("key=value")] = std::vector<Interval>{{interval}};
    for (size_t i(0); i < 9; i++) {
        indexDocument(doc_id + i, annotations);
    }
    runQuery(*this, {2,3,4,5,6,7,8,9,10}, 0, 1);
    indexDocument(doc_id+9, annotations);
    runQuery(*this, {2, 3,4,5,6,7,8,9,10,11}, 0, 1);
    index().requireCachePopulation();
    indexDocument(doc_id+10, annotations);
    runQuery(*this, {2,3,4,5,6,7,8,9,10,11,12}, 1, 1);
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_can_create_more_advanced_search) {
    PredicateTreeAnnotations annotations(2);
    annotations.interval_map[PredicateHash::hash64("key=value")] =
        std::vector<Interval>{{0x00010001}};
    annotations.bounds_map[PredicateHash::hash64("range_key=40")] =
        std::vector<IntervalWithBounds>{{0x00020010, 0x40000005}};  // [40..44]
    indexDocument(doc_id, annotations, 0x10);
    indexEmptyDocument(doc_id + 2);

    PredicateBlueprint blueprint(field, guard(), query);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_EQ(SearchIterator::beginId(), it->getDocId());
    EXPECT_FALSE(it->seek(doc_id - 1));
    EXPECT_EQ(doc_id, it->getDocId());
    EXPECT_TRUE(it->seek(doc_id));
    EXPECT_EQ(doc_id, it->getDocId());
    EXPECT_FALSE(it->seek(doc_id + 1));
    EXPECT_EQ(doc_id + 2, it->getDocId());
    EXPECT_TRUE(it->seek(doc_id + 2));
    EXPECT_FALSE(it->seek(doc_id + 3));
    EXPECT_TRUE(it->isAtEnd());
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_can_create_NOT_search) {
    PredicateTreeAnnotations annotations(1);
    annotations.interval_map[Constants::z_star_hash] =std::vector<Interval>{{0x00010000}, {0xffff0001}};
    indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(field, guard(), query);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_TRUE(it->seek(doc_id));
    EXPECT_EQ(doc_id, it->getDocId());
    EXPECT_FALSE(it->seek(doc_id + 1));
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_can_create_compressed_NOT_search) {
    PredicateTreeAnnotations annotations(1);
    annotations.interval_map[Constants::z_star_compressed_hash] =std::vector<Interval>{{0xfffe0000}};
    indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(field, guard(), query);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_TRUE(it->seek(doc_id));
    EXPECT_EQ(doc_id, it->getDocId());
    EXPECT_FALSE(it->seek(doc_id + 1));
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_can_set_up_search_with_subqueries) {
    PredicateTreeAnnotations annotations(2);
    annotations.interval_map[PredicateHash::hash64("key=value")] =
        std::vector<Interval>{{0x00010001}};
    annotations.interval_map[PredicateHash::hash64("key2=value")] =
        std::vector<Interval>{{0x0002ffff}};
    indexDocument(doc_id, annotations);

    SimplePredicateQuery pquery(std::make_unique<PredicateQueryTerm>(),
                               "view", 0, Weight(1));
    pquery.getTerm()->addFeature("key", "value", 1);
    pquery.getTerm()->addFeature("key2", "value", 2);

    PredicateBlueprint blueprint(field, guard(), pquery);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_FALSE(it->seek(doc_id));
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
