// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for PredicateTreeAnnotator.

#include <vespa/document/predicate/predicate.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/predicate/predicate_tree_annotator.h>
#include <vespa/searchlib/predicate/predicate_hash.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <sstream>

using document::Predicate;
using std::ostringstream;
using std::pair;
using std::string;
using std::vector;
using vespalib::Slime;
using vespalib::slime::Cursor;
using namespace search;
using namespace search::predicate;
using namespace document::predicate_slime_builder;

namespace {
Cursor &makeAndNode(Cursor &obj) {
    obj.setLong(Predicate::NODE_TYPE, Predicate::TYPE_CONJUNCTION);
    return obj.setArray(Predicate::CHILDREN);
}

Cursor &makeOrNode(Cursor &obj) {
    obj.setLong(Predicate::NODE_TYPE, Predicate::TYPE_DISJUNCTION);
    return obj.setArray(Predicate::CHILDREN);
}

void makeFeatureSet(Cursor &obj, const string &key, const string &value) {
    obj.setLong(Predicate::NODE_TYPE, Predicate::TYPE_FEATURE_SET);
    obj.setString(Predicate::KEY, key);
    Cursor &set = obj.setArray(Predicate::SET);
    set.addString(value);
}

void makeHashedFeatureRange(Cursor &obj, const string &key,
                            const vector<string> &partitions,
                            const vector<vector<int64_t> >& edge_partitions) {
    obj.setLong(Predicate::NODE_TYPE, Predicate::TYPE_FEATURE_RANGE);
    obj.setString(Predicate::KEY, key);
    Cursor &p = obj.setArray(Predicate::HASHED_PARTITIONS);
    for (auto partition : partitions) {
        p.addLong(PredicateHash::hash64(partition));
    }
    Cursor &e = obj.setArray(Predicate::HASHED_EDGE_PARTITIONS);
    for (auto edge_partition : edge_partitions) {
        ostringstream label;
        label << key << "=" << edge_partition[0];
        uint64_t hash = PredicateHash::hash64(label.str());
        int64_t value = edge_partition[1];
        int64_t payload = edge_partition[2];

        Cursor &o = e.addObject();
        o.setLong(Predicate::HASH, hash);
        o.setLong(Predicate::VALUE, value);
        o.setLong(Predicate::PAYLOAD, payload);
    }
}

void checkInterval(const PredicateTreeAnnotations &result,
                   const string &feature, vector<uint32_t> expected) {
    SCOPED_TRACE("Check interval: " + feature);
    uint64_t hash = PredicateHash::hash64(feature);
    auto it = result.interval_map.find(hash);
    ASSERT_TRUE(it != result.interval_map.end());
    const auto &intervals = it->second;
    ASSERT_EQ(expected.size(), intervals.size());
    for (size_t i = 0; i < expected.size(); ++i) {
        EXPECT_EQ(expected[i], intervals[i].interval);
    }
}

void checkBounds(const PredicateTreeAnnotations &result,
                 const string &feature,
                 vector<IntervalWithBounds> expected) {
    SCOPED_TRACE("Check bounds: " + feature);
    uint64_t hash = PredicateHash::hash64(feature);
    auto it = result.bounds_map.find(hash);
    ASSERT_TRUE(it != result.bounds_map.end());
    const auto &intervals = it->second;
    ASSERT_EQ(expected.size(), intervals.size());
    for (size_t i = 0; i < expected.size(); ++i) {
        EXPECT_EQ(expected[i].interval, intervals[i].interval);
        EXPECT_EQ(expected[i].bounds, intervals[i].bounds);
    }
}

TEST(PredicateTreeAnnotatorTest, require_that_OR_intervals_are_the_same) {
    Slime slime;
    Cursor &children = makeOrNode(slime.setObject());
    makeFeatureSet(children.addObject(), "key1", "value1");
    makeFeatureSet(children.addObject(), "key2", "value2");

    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime.get(), result);

    EXPECT_EQ(1u, result.min_feature);
    EXPECT_EQ(2u, result.interval_range);
    EXPECT_EQ(2u, result.interval_map.size());
    checkInterval(result, "key1=value1", {0x00010002});
    checkInterval(result, "key2=value2", {0x00010002});
}

TEST(PredicateTreeAnnotatorTest, require_that_ANDs_below_ORs_get_different_intervals) {
    auto slime = orNode({andNode({featureSet("key1", {"value1"}),
                                  featureSet("key1", {"value1"}),
                                  featureSet("key1", {"value1"})}),
                         andNode({featureSet("key2", {"value2"}),
                                  featureSet("key2", {"value2"}),
                                  featureSet("key2", {"value2"})})});
    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime->get(), result);

    EXPECT_EQ(1u, result.min_feature);
    EXPECT_EQ(6u, result.interval_range);
    EXPECT_EQ(2u, result.interval_map.size());
    checkInterval(result, "key1=value1", {0x00010001, 0x00020002, 0x00030006});
    checkInterval(result, "key2=value2", {0x00010004, 0x00050005, 0x00060006});
}

TEST(PredicateTreeAnnotatorTest, require_that_NOTs_get_correct_intervals) {
    auto slime = andNode({featureSet("key", {"value"}),
                          neg(featureSet("key", {"value"})),
                          featureSet("key", {"value"}),
                          neg(featureSet("key", {"value"}))});
    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime->get(), result);

    EXPECT_EQ(2u, result.min_feature);  // needs key=value and z-star
    EXPECT_EQ(6u, result.interval_range);
    EXPECT_EQ(2u, result.interval_map.size());
    checkInterval(result, "key=value",
                  {0x00010001, 0x00020002, 0x00040004, 0x00050005});
    checkInterval(result, Constants::z_star_compressed_attribute_name,
                  {0x00020001, 0x00050004});
}

TEST(PredicateTreeAnnotatorTest, require_that_NOT_inverts_ANDs_and_ORs) {
    auto slime = neg(andNode({featureSet("key", {"value"}),
                              neg(featureSet("key", {"value"}))}));
    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime->get(), result);

    EXPECT_EQ(1u, result.min_feature);  // needs key=value or z-star
    EXPECT_EQ(3u, result.interval_range);
    EXPECT_EQ(2u, result.interval_map.size());
    checkInterval(result, "key=value",
                  {0x00010002, 0x00010003});
    checkInterval(result, Constants::z_star_compressed_attribute_name,
                  {0x00020000});
}

TEST(PredicateTreeAnnotatorTest, require_that_final_first_NOT_interval_is_extended) {
    auto slime = neg(featureSet("key", {"A"}));
    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime->get(), result);
    EXPECT_EQ(1u, result.min_feature);
    EXPECT_EQ(2u, result.interval_range);
    EXPECT_EQ(2u, result.interval_map.size());
    checkInterval(result, "key=A", {0x00010001});
    checkInterval(result, Constants::z_star_compressed_attribute_name,
                  {0x00010000});
}

TEST(PredicateTreeAnnotatorTest, show_different_types_of_NOT_intervals) {
    auto slime = andNode({orNode({andNode({featureSet("key", {"A"}),
                                           neg(featureSet("key", {"B"}))}),
                                  andNode({neg(featureSet("key", {"C"})),
                                           featureSet("key", {"D"})})}),
                          featureSet("foo", {"bar"})});
    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime->get(), result);
    EXPECT_EQ(3u, result.min_feature);
    EXPECT_EQ(7u, result.interval_range);
    EXPECT_EQ(6u, result.interval_map.size());
    checkInterval(result, "foo=bar", {0x00070007});
    checkInterval(result, "key=A", {0x00010001});
    checkInterval(result, "key=B", {0x00020002});
    checkInterval(result, "key=C", {0x00010004});
    checkInterval(result, "key=D", {0x00060006});
    checkInterval(result, Constants::z_star_compressed_attribute_name,
                  {0x00020001, 0x00000006, 0x00040000});

    slime = orNode({neg(featureSet("key", {"A"})),
                    neg(featureSet("key", {"B"}))});
    result = PredicateTreeAnnotations();
    PredicateTreeAnnotator::annotate(slime->get(), result);
    EXPECT_EQ(1u, result.min_feature);
    EXPECT_EQ(4u, result.interval_range);
    EXPECT_EQ(3u, result.interval_map.size());
    checkInterval(result, "key=A", {0x00010003});
    checkInterval(result, "key=B", {0x00010003});
    checkInterval(result, Constants::z_star_compressed_attribute_name,
                  {0x00030000, 0x00030000});

    slime = orNode({andNode({neg(featureSet("key", {"A"})),
                             neg(featureSet("key", {"B"}))}),
                    andNode({neg(featureSet("key", {"C"})),
                             neg(featureSet("key", {"D"}))})});
    result = PredicateTreeAnnotations();
    PredicateTreeAnnotator::annotate(slime->get(), result);
    EXPECT_EQ(1u, result.min_feature);
    EXPECT_EQ(8u, result.interval_range);
    EXPECT_EQ(5u, result.interval_map.size());
    checkInterval(result, "key=A", {0x00010001});
    checkInterval(result, "key=B", {0x00030007});
    checkInterval(result, "key=C", {0x00010005});
    checkInterval(result, "key=D", {0x00070007});
    checkInterval(result, Constants::z_star_compressed_attribute_name,
                  {0x00010000, 0x00070002, 0x00050000,0x00070006});

}

TEST(PredicateTreeAnnotatorTest, require_short_edge_partitions_to_get_correct_intervals_and_features) {
    Slime slime;
    Cursor &children = makeAndNode(slime.setObject());
    makeHashedFeatureRange(children.addObject(), "key",{}, {{0, 5, -1}, {30, 0, 3}});
    makeHashedFeatureRange(children.addObject(), "foo",{}, {{0, 5, -1}, {30, 0, 3}});

    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime.get(), result);

    EXPECT_EQ(2u, result.min_feature);
    EXPECT_EQ(2u, result.interval_range);
    EXPECT_EQ(0u, result.interval_map.size());
    EXPECT_EQ(4u, result.bounds_map.size());
    EXPECT_EQ(4u, result.features.size());
    EXPECT_EQ(0u, result.range_features.size());

    EXPECT_EQ(0xdbc38b103b5d50a9ul, result.features[0]);
    EXPECT_EQ(0xbe6d86e3e2270b0aul, result.features[1]);
    EXPECT_EQ(0xb2b301e26efffdc2ul, result.features[2]);
    EXPECT_EQ(0x31afc4833c50e1d9ul, result.features[3]);
    checkBounds(result, "key=0", {{0x00010001, 0xffffffff}});
    checkBounds(result, "key=30", {{0x00010001, 3}});
    checkBounds(result, "foo=0", {{0x00020002, 0xffffffff}});
    checkBounds(result, "foo=30", {{0x00020002, 3}});
}

TEST(PredicateTreeAnnotatorTest, require_that_hashed_ranges_get_correct_intervals) {
    Slime slime;
    Cursor &children = makeAndNode(slime.setObject());
    makeHashedFeatureRange(
            children.addObject(), "key",
            {"key=10-19", "key=20-29"}, {{0, 5, -1}, {30, 0, 3}});
    makeHashedFeatureRange(
            children.addObject(), "foo",
            {"foo=10-19", "foo=20-29"}, {{0, 5, -1}, {30, 0, 3}});

    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime.get(), result);

    EXPECT_EQ(2u, result.min_feature);
    EXPECT_EQ(2u, result.interval_range);
    EXPECT_EQ(4u, result.interval_map.size());
    EXPECT_EQ(4u, result.bounds_map.size());
    EXPECT_EQ(0u, result.features.size());
    EXPECT_EQ(2u, result.range_features.size());

    checkInterval(result, "key=10-19", {0x00010001});
    checkInterval(result, "key=20-29", {0x00010001});
    checkBounds(result, "key=0", {{0x00010001, 0xffffffff}});
    checkBounds(result, "key=30", {{0x00010001, 3}});

    checkInterval(result, "foo=10-19", {0x00020002});
    checkInterval(result, "foo=20-29", {0x00020002});
    checkBounds(result, "foo=0", {{0x00020002, 0xffffffff}});
    checkBounds(result, "foo=30", {{0x00020002, 3}});
}

TEST(PredicateTreeAnnotatorTest, require_that_extreme_ranges_works) {
    Slime slime;
    Cursor &children = makeAndNode(slime.setObject());
    makeHashedFeatureRange(
            children.addObject(), "max range",
            {"max range=9223372036854775806-9223372036854775807"}, {});
    makeHashedFeatureRange(
            children.addObject(), "max edge",
            {}, {{9223372036854775807, 0, 0x40000001}});
    makeHashedFeatureRange(
            children.addObject(), "min range",
            {"min range=-9223372036854775807-9223372036854775806"}, {});
    makeHashedFeatureRange(
            children.addObject(), "min edge",
            {}, {{LLONG_MIN, 0, 0x40000001}});

    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime.get(), result);

    EXPECT_EQ(4u, result.min_feature);
    EXPECT_EQ(4u, result.interval_range);
    EXPECT_EQ(2u, result.interval_map.size());
    EXPECT_EQ(2u, result.bounds_map.size());
    checkInterval(result, "max range=9223372036854775806-9223372036854775807",
                  {0x00010001});
    checkBounds(result, "max edge=9223372036854775807",
                {{0x00020002, 0x40000001}});
    checkInterval(result, "min range=-9223372036854775807-9223372036854775806",
                  {0x00030003});
    checkBounds(result, "min edge=-9223372036854775808",
                {{0x00040004, 0x40000001}});
}

TEST(PredicateTreeAnnotatorTest, require_that_unique_features_and_all_ranges_are_collected) {
    auto slime = andNode({featureSet("key1", {"value1"}),
                          featureSet("key1", {"value1"}),
                          featureRange("key2", 9, 40),
                          featureRange("key2", 9, 40)});
    Cursor &c1 = slime->get()[Predicate::CHILDREN][2]
                 .setArray(Predicate::HASHED_PARTITIONS);
    c1.addLong(PredicateHash::hash64("key2=10-19"));
    c1.addLong(PredicateHash::hash64("key2=20-29"));
    c1.addLong(PredicateHash::hash64("key2=30-39"));
    c1.addLong(PredicateHash::hash64("key2=0"));
    c1.addLong(PredicateHash::hash64("key2=40"));
    Cursor &c2 = slime->get()[Predicate::CHILDREN][3]
                 .setArray(Predicate::HASHED_PARTITIONS);
    c2.addLong(PredicateHash::hash64("key2=10-19"));
    c2.addLong(PredicateHash::hash64("key2=20-29"));
    c2.addLong(PredicateHash::hash64("key2=30-39"));
    c2.addLong(PredicateHash::hash64("key2=0"));
    c2.addLong(PredicateHash::hash64("key2=40"));

    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime->get(), result);

    EXPECT_EQ(4u, result.interval_range);
    ASSERT_EQ(1u, result.features.size());
    EXPECT_EQ(static_cast<uint64_t>(PredicateHash::hash64("key1=value1")),
                 result.features[0]);
    ASSERT_EQ(2u, result.range_features.size());
    EXPECT_EQ("key2", result.range_features[0].label.make_string());
    EXPECT_EQ(9, result.range_features[0].from);
    EXPECT_EQ(40, result.range_features[0].to);
    EXPECT_EQ("key2", result.range_features[1].label.make_string());
    EXPECT_EQ(9, result.range_features[1].from);
    EXPECT_EQ(40, result.range_features[1].to);
}

TEST(PredicateTreeAnnotatorTest, require_that_z_star_feature_is_only_registered_once) {
    auto slime = andNode({neg(featureSet("key1", {"value1"})),
                          neg(featureRange("key2", 10, 19))});
    Cursor &c = slime->get()[Predicate::CHILDREN][1][Predicate::CHILDREN][0]
                .setArray(Predicate::HASHED_PARTITIONS);
    c.addLong(PredicateHash::hash64("key2=10-19"));

    // simple range will be stored as a feature.
    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime->get(), result);

    EXPECT_EQ(4u, result.interval_range);
    ASSERT_EQ(3u, result.features.size());
    EXPECT_EQ(PredicateHash::hash64("key1=value1"), result.features[0]);
    EXPECT_EQ(Constants::z_star_compressed_hash, result.features[1]);
    EXPECT_EQ(PredicateHash::hash64("key2=10-19"), result.features[2]);
    ASSERT_EQ(0u, result.range_features.size());
}

TEST(PredicateTreeAnnotatorTest, require_that_default_open_range_works) {
    auto slime = lessEqual("foo", 39);
    Cursor &c = slime->get().setArray(Predicate::HASHED_PARTITIONS);
    c.addLong(PredicateHash::hash64("foo=-9223372036854775808"));
    c.addLong(PredicateHash::hash64("foo=-9223372036854775807-0"));
    c.addLong(PredicateHash::hash64("foo=0-31"));
    c.addLong(PredicateHash::hash64("foo=32-39"));

    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime->get(), result);

    EXPECT_EQ(1u, result.interval_range);
    EXPECT_EQ(0u, result.features.size());
    ASSERT_EQ(1u, result.range_features.size());
    EXPECT_EQ("foo", result.range_features[0].label.make_string());
    EXPECT_EQ(LONG_MIN, result.range_features[0].from);
    EXPECT_EQ(39, result.range_features[0].to);
}

TEST(PredicateTreeAnnotatorTest, require_that_open_range_works) {
    auto slime = lessEqual("foo", 39);
    Cursor &c = slime->get().setArray(Predicate::HASHED_PARTITIONS);
    c.addLong(PredicateHash::hash64("foo=8-15"));
    c.addLong(PredicateHash::hash64("foo=16-31"));
    c.addLong(PredicateHash::hash64("foo=32-39"));

    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(slime->get(), result, 8, 200);

    EXPECT_EQ(1u, result.interval_range);
    EXPECT_EQ(0u, result.features.size());
    ASSERT_EQ(1u, result.range_features.size());
    EXPECT_EQ("foo", result.range_features[0].label.make_string());
    EXPECT_EQ(8, result.range_features[0].from);
    EXPECT_EQ(39, result.range_features[0].to);
}

}  // namespace
