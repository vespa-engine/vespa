// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_blueprint.

#include <vespa/searchlib/attribute/attributeguard.h>
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
#include <vespa/vespalib/testkit/testapp.h>

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

namespace {

struct Fixture {
    FieldSpecBase field;
    AttributeVector::SP attribute;
    vespalib::GenerationHandler generation_handler;
    SimplePredicateQuery query;

    using IntervalRange = PredicateAttribute::IntervalRange;

    Fixture()
        : field(42, 0),
          attribute(new PredicateAttribute("f", attribute::Config(attribute::BasicType::PREDICATE))),
          query(PredicateQueryTerm::UP(new PredicateQueryTerm),
                "view", 0, Weight(1)) {
        query.getTerm()->addFeature("key", "value");
        query.getTerm()->addRangeFeature("range_key", 42);
    }
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

TEST_F("require that blueprint with empty index estimates empty.", Fixture) {
    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    EXPECT_TRUE(blueprint.getState().estimate().empty);
    EXPECT_EQUAL(0u, blueprint.getState().estimate().estHits);
}

TEST_F("require that blueprint with zero-constraint doc estimates non-empty.",
       Fixture) {
    f.indexEmptyDocument(42);
    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    EXPECT_FALSE(blueprint.getState().estimate().empty);
    EXPECT_EQUAL(1u, blueprint.getState().estimate().estHits);
}

const int min_feature = 1;
const uint32_t doc_id = 2;
const uint32_t interval = 0x0001ffff;

TEST_F("require that blueprint with posting list entry estimates non-empty.",
       Fixture) {
    PredicateTreeAnnotations annotations(min_feature);
    annotations.interval_map[PredicateHash::hash64("key=value")] =
        std::vector<Interval>{{interval}};
    f.indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    EXPECT_FALSE(blueprint.getState().estimate().empty);
    EXPECT_EQUAL(0u, blueprint.getState().estimate().estHits);
}

TEST_F("require that blueprint with 'bounds' posting list entry estimates "
       "non-empty.", Fixture) {
    PredicateTreeAnnotations annotations(min_feature);
    annotations.bounds_map[PredicateHash::hash64("range_key=40")] =
        std::vector<IntervalWithBounds>{{interval, 0x80000003}};
    f.indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    EXPECT_FALSE(blueprint.getState().estimate().empty);
    EXPECT_EQUAL(0u, blueprint.getState().estimate().estHits);
}

TEST_F("require that blueprint with zstar-compressed estimates non-empty.",
       Fixture) {
    PredicateTreeAnnotations annotations(1);
    annotations.interval_map[Constants::z_star_compressed_hash] =std::vector<Interval>{{0xfffe0000}};
    f.indexDocument(doc_id, annotations);
    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    EXPECT_FALSE(blueprint.getState().estimate().empty);
    EXPECT_EQUAL(0u, blueprint.getState().estimate().estHits);
}

TEST_F("require that blueprint can create search", Fixture) {
    PredicateTreeAnnotations annotations(1);
    annotations.interval_map[PredicateHash::hash64("key=value")] =std::vector<Interval>{{interval}};
    f.indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    blueprint.fetchPostings(true);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda, true);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_EQUAL(SearchIterator::beginId(), it->getDocId());
    EXPECT_FALSE(it->seek(doc_id - 1));
    EXPECT_EQUAL(doc_id, it->getDocId());
    EXPECT_TRUE(it->seek(doc_id));
    EXPECT_EQUAL(doc_id, it->getDocId());
    EXPECT_FALSE(it->seek(doc_id + 1));
    EXPECT_TRUE(it->isAtEnd());
}

TEST_F("require that blueprint can create more advanced search", Fixture) {
    PredicateTreeAnnotations annotations(2);
    annotations.interval_map[PredicateHash::hash64("key=value")] =
        std::vector<Interval>{{0x00010001}};
    annotations.bounds_map[PredicateHash::hash64("range_key=40")] =
        std::vector<IntervalWithBounds>{{0x00020010, 0x40000005}};  // [40..44]
    f.indexDocument(doc_id, annotations, 0x10);
    f.indexEmptyDocument(doc_id + 2);

    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    blueprint.fetchPostings(true);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda, true);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_EQUAL(SearchIterator::beginId(), it->getDocId());
    EXPECT_FALSE(it->seek(doc_id - 1));
    EXPECT_EQUAL(doc_id, it->getDocId());
    EXPECT_TRUE(it->seek(doc_id));
    EXPECT_EQUAL(doc_id, it->getDocId());
    EXPECT_FALSE(it->seek(doc_id + 1));
    EXPECT_EQUAL(doc_id + 2, it->getDocId());
    EXPECT_TRUE(it->seek(doc_id + 2));
    EXPECT_FALSE(it->seek(doc_id + 3));
    EXPECT_TRUE(it->isAtEnd());
}

TEST_F("require that blueprint can create NOT search", Fixture) {
    PredicateTreeAnnotations annotations(1);
    annotations.interval_map[Constants::z_star_hash] =std::vector<Interval>{{0x00010000}, {0xffff0001}};
    f.indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    blueprint.fetchPostings(true);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda, true);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_TRUE(it->seek(doc_id));
    EXPECT_EQUAL(doc_id, it->getDocId());
    EXPECT_FALSE(it->seek(doc_id + 1));
}

TEST_F("require that blueprint can create compressed NOT search", Fixture) {
    PredicateTreeAnnotations annotations(1);
    annotations.interval_map[Constants::z_star_compressed_hash] =std::vector<Interval>{{0xfffe0000}};
    f.indexDocument(doc_id, annotations);

    PredicateBlueprint blueprint(f.field, f.guard(), f.query);
    blueprint.fetchPostings(true);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda, true);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_TRUE(it->seek(doc_id));
    EXPECT_EQUAL(doc_id, it->getDocId());
    EXPECT_FALSE(it->seek(doc_id + 1));
}

TEST_F("require that blueprint can set up search with subqueries", Fixture) {
    PredicateTreeAnnotations annotations(2);
    annotations.interval_map[PredicateHash::hash64("key=value")] =
        std::vector<Interval>{{0x00010001}};
    annotations.interval_map[PredicateHash::hash64("key2=value")] =
        std::vector<Interval>{{0x0002ffff}};
    f.indexDocument(doc_id, annotations);

    SimplePredicateQuery query(PredicateQueryTerm::UP(new PredicateQueryTerm),
                               "view", 0, Weight(1));
    query.getTerm()->addFeature("key", "value", 1);
    query.getTerm()->addFeature("key2", "value", 2);

    PredicateBlueprint blueprint(f.field, f.guard(), query);
    blueprint.fetchPostings(true);
    TermFieldMatchDataArray tfmda;
    SearchIterator::UP it = blueprint.createLeafSearch(tfmda, true);
    ASSERT_TRUE(it.get());
    it->initFullRange();
    EXPECT_FALSE(it->seek(doc_id));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
