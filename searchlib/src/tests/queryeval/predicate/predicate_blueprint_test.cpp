// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_blueprint.

#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/predicate/predicate_hash.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/predicate/predicate_tree_annotator.h>
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/predicate_blueprint.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <algorithm>
#include <iterator>

#include <vespa/log/log.h>
LOG_SETUP("predicate_blueprint_test");

using namespace search;
using namespace search::predicate;
using search::fef::TermFieldMatchDataArray;
using search::query::PredicateQueryTerm;
using search::query::SimplePredicateQuery;
using search::query::Weight;
using search::queryeval::ExecuteInfo;
using search::queryeval::FieldSpecBase;
using search::queryeval::PredicateBlueprint;
using search::queryeval::SearchIterator;

namespace {

struct PredicateBlueprintTest : public ::testing::Test {
    FieldSpecBase        field;
    AttributeVector::SP  attribute;
    SimplePredicateQuery query;

    using IntervalRange = PredicateAttribute::IntervalRange;

    PredicateBlueprintTest();
    ~PredicateBlueprintTest() override;
    PredicateAttribute& guard() { return dynamic_cast<PredicateAttribute&>(*attribute); }
    PredicateIndex& index() { return predicate().getIndex(); }
    PredicateAttribute& predicate() { return static_cast<PredicateAttribute&>(*attribute); }
    void resize(uint32_t doc_id) {
        while (predicate().getNumDocs() <= doc_id) {
            uint32_t tmp;
            predicate().addDoc(tmp);
            PredicateAttribute::MinFeatureHandle mfh = predicate().getMinFeatureVector();
            const_cast<uint8_t*>(mfh.first)[tmp] = 0;
        }
    }
    void setIntervalRange(uint32_t doc_id, IntervalRange interval_range) {
        const_cast<IntervalRange*>(predicate().getIntervalRangeVector())[doc_id] = interval_range;
    }
    void indexEmptyDocument(uint32_t doc_id, IntervalRange ir = 0x1) {
        resize(doc_id);
        index().indexEmptyDocument(doc_id);
        setIntervalRange(doc_id, ir);
        predicate().updateMaxIntervalRange(ir);
        predicate().commit(CommitParam::UpdateStats::SKIP);
    }
    void indexDocument(uint32_t doc_id, const PredicateTreeAnnotations& annotations, IntervalRange ir = 0xffff) {
        resize(doc_id);
        index().indexDocument(doc_id, annotations);
        setIntervalRange(doc_id, ir);
        predicate().updateMaxIntervalRange(ir);
        predicate().commit(CommitParam::UpdateStats::SKIP);
    }
};

PredicateBlueprintTest::PredicateBlueprintTest()
    : ::testing::Test(),
      field(42, 0),
      attribute(std::make_shared<PredicateAttribute>("f")),
      query(std::make_unique<PredicateQueryTerm>(), "view", 0, Weight(1)) {
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

const int      min_feature = 1;
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

void runQuery(PredicateBlueprintTest& f, std::vector<uint32_t> expected, uint32_t expectCachedSize,
              uint32_t expectedKV) {
    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    EXPECT_EQ(expectCachedSize, blueprint.getCachedFeatures().size());
    for (uint32_t docId : expected) {
        EXPECT_EQ(expectedKV, uint32_t(blueprint.getKV()[docId]));
    }
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP      it = blueprint.createLeafSearch(tfmda);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_EQ(SearchIterator::beginId(), it->getDocId());
    std::vector<uint32_t> actual;
    for (it->seek(1); !it->isAtEnd(); it->seek(it->getDocId() + 1)) {
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
    runQuery(*this, {2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 1);
    indexDocument(doc_id + 9, annotations);
    runQuery(*this, {2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 0, 1);
    index().requireCachePopulation();
    indexDocument(doc_id + 10, annotations);
    runQuery(*this, {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 1, 1);
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_can_create_more_advanced_search) {
    PredicateTreeAnnotations annotations(2);
    annotations.interval_map[PredicateHash::hash64("key=value")] = std::vector<Interval>{{0x00010001}};
    annotations.bounds_map[PredicateHash::hash64("range_key=40")] =
        std::vector<IntervalWithBounds>{{0x00020010, 0x40000005}}; // [40..44]
    indexDocument(doc_id, annotations, 0x10);
    indexEmptyDocument(doc_id + 2);

    PredicateBlueprint blueprint(field, guard(), query);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP      it = blueprint.createLeafSearch(tfmda);
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
    annotations.interval_map[Constants::z_star_hash] = std::vector<Interval>{{0x00010000}, {0xffff0001}};
    indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(field, guard(), query);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP      it = blueprint.createLeafSearch(tfmda);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_TRUE(it->seek(doc_id));
    EXPECT_EQ(doc_id, it->getDocId());
    EXPECT_FALSE(it->seek(doc_id + 1));
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_can_create_compressed_NOT_search) {
    PredicateTreeAnnotations annotations(1);
    annotations.interval_map[Constants::z_star_compressed_hash] = std::vector<Interval>{{0xfffe0000}};
    indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(field, guard(), query);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP      it = blueprint.createLeafSearch(tfmda);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_TRUE(it->seek(doc_id));
    EXPECT_EQ(doc_id, it->getDocId());
    EXPECT_FALSE(it->seek(doc_id + 1));
}

TEST_F(PredicateBlueprintTest, require_that_blueprint_can_set_up_search_with_subqueries) {
    PredicateTreeAnnotations annotations(2);
    annotations.interval_map[PredicateHash::hash64("key=value")] = std::vector<Interval>{{0x00010001}};
    annotations.interval_map[PredicateHash::hash64("key2=value")] = std::vector<Interval>{{0x0002ffff}};
    indexDocument(doc_id, annotations);

    SimplePredicateQuery pquery(std::make_unique<PredicateQueryTerm>(), "view", 0, Weight(1));
    pquery.getTerm()->addFeature("key", "value", 1);
    pquery.getTerm()->addFeature("key2", "value", 2);

    PredicateBlueprint blueprint(field, guard(), pquery);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP      it = blueprint.createLeafSearch(tfmda);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_FALSE(it->seek(doc_id));
}

TEST_F(PredicateBlueprintTest, require_that_search_can_rewind_to_an_earlier_range) {
    // One kind of posting list per doc: interval, bounds, z-star compressed and zero constraint.
    PredicateTreeAnnotations interval_annotations(1);
    interval_annotations.interval_map[PredicateHash::hash64("key=value")] = std::vector<Interval>{{interval}};
    PredicateTreeAnnotations bounds_annotations(1);
    bounds_annotations.bounds_map[PredicateHash::hash64("range_key=40")] =
        std::vector<IntervalWithBounds>{{interval, 0x40000005}}; // [40..44]
    PredicateTreeAnnotations zstar_annotations(1);
    zstar_annotations.interval_map[Constants::z_star_compressed_hash] = std::vector<Interval>{{0xfffe0000}};
    std::vector<uint32_t> expected;
    for (uint32_t id = 2; id < 100; id += 3) {
        switch (id % 4) {
        case 0:
            indexDocument(id, interval_annotations);
            break;
        case 1:
            indexDocument(id, bounds_annotations);
            break;
        case 2:
            indexDocument(id, zstar_annotations);
            break;
        default:
            indexEmptyDocument(id);
        }
        expected.push_back(id);
    }

    PredicateBlueprint blueprint(field, guard(), query);
    blueprint.basic_plan(true, 100);
    blueprint.fetchPostings(ExecuteInfo::FULL);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP      it = blueprint.createLeafSearch(tfmda);
    auto                    hits_in_range = [&](uint32_t begin, uint32_t end) {
        std::vector<uint32_t> hits;
        it->initRange(begin, end);
        for (uint32_t id = it->seekFirst(begin); id < end; id = it->seekNext(id + 1)) {
            hits.push_back(id);
        }
        return hits;
    };
    auto expected_in_range = [&](uint32_t begin, uint32_t end) {
        std::vector<uint32_t> hits;
        std::copy_if(expected.begin(), expected.end(), std::back_inserter(hits),
                     [&](uint32_t id) { return begin <= id && id < end; });
        return hits;
    };
    EXPECT_EQ(expected, hits_in_range(1, 100));
    for (uint32_t begin = 90; begin > 1; begin -= 10) { // every range rewinds
        EXPECT_EQ(expected_in_range(begin, begin + 10), hits_in_range(begin, begin + 10));
    }
    EXPECT_EQ(expected_in_range(40, 70), hits_in_range(40, 70)); // forward
    EXPECT_EQ(expected_in_range(70, 100), hits_in_range(70, 100));
}

} // namespace

GTEST_MAIN_RUN_ALL_TESTS()
