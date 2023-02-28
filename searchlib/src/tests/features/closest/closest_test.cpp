// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/features/closest_feature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/labels.h>
#include <vespa/searchlib/test/features/distance_closeness_fixture.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>

using search::feature_t;
using search::features::test::BlueprintFactoryFixture;
using search::features::test::DistanceClosenessFixture;
using search::features::test::FeatureDumpFixture;
using search::features::test::IndexEnvironmentFixture;
using search::features::ClosestBlueprint;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::spec_from_value;
using vespalib::eval::value_from_spec;

const vespalib::string field_and_label_feature_name("closest(bar,nns)");
const vespalib::string field_feature_name("closest(bar)");

const vespalib::string dense_tensor_type("tensor(x[2])");
const vespalib::string mixed_tensor_type("tensor(a{},x[2])");
const vespalib::string sparse_tensor_type("tensor(a{})");

TensorSpec no_subspace(sparse_tensor_type);
TensorSpec subspace_a = TensorSpec::from_expr("tensor(a{}):{{a:\"a\"}:1}");
TensorSpec subspace_b = TensorSpec::from_expr("tensor(a{}):{{a:\"b\"}:1}");

TensorSpec doc_tensor = TensorSpec::from_expr("tensor(a{},x[2]):{{a:\"a\",x:0}:3,{a:\"a\",x:1}:10,{a:\"b\",x:0}:5,{a:\"b\",x:1}:10}");

using RankFixture = DistanceClosenessFixture;

TensorSpec get_spec(RankFixture& f, uint32_t docid) {
    return spec_from_value(f.getObject(docid).get());
}

struct TestParam
{
    vespalib::string _name;
    bool             _direct_tensor;
    TestParam(vespalib::string name, bool direct_tensor)
        : _name(std::move(name)),
          _direct_tensor(direct_tensor)
    {
    }
    ~TestParam();
};

TestParam::~TestParam() = default;

std::ostream& operator<<(std::ostream& os, const TestParam param)
{
    os << param._name;
    return os;
}

void
assert_setup(vespalib::string field_name,
             bool exp_setup_result,
             std::optional<vespalib::string> attr_type_spec,
             std::optional<vespalib::string> label)
{
    vespalib::asciistream feature_name;
    std::vector<vespalib::string> setup_args;
    ClosestBlueprint f1;
    IndexEnvironmentFixture f2;
    DummyDependencyHandler deps(f1);
    setup_args.emplace_back(field_name);
    feature_name << f1.getBaseName() << "(" << field_name;
    if (label.has_value()) {
        feature_name << "," << label.value();
        setup_args.emplace_back(label.value());
    }
    feature_name << ")";
    f1.setName(feature_name.str());
    if (attr_type_spec.has_value()) {
        search::fef::indexproperties::type::Attribute::set(f2.indexEnv.getProperties(), field_name, attr_type_spec.value());
    }
    EXPECT_EQ(exp_setup_result, static_cast<Blueprint&>(f1).setup(f2.indexEnv, setup_args));
}

class ClosestTest : public ::testing::TestWithParam<TestParam>
{
protected:
    ClosestTest();
    ~ClosestTest();
    bool direct_tensor() const noexcept { return GetParam()._direct_tensor; }
    void assert_closest(const Labels& labels, const vespalib::string& feature_name, const vespalib::string& query_tensor, const TensorSpec& exp_spec);
    void assert_closest(const Labels& labels, const vespalib::string& feature_name, const std::vector<TensorSpec>& exp_specs);
};

ClosestTest::ClosestTest()
    : testing::TestWithParam<TestParam>()
{
}

ClosestTest::~ClosestTest() = default;

void
ClosestTest::assert_closest(const Labels& labels, const vespalib::string& feature_name, const vespalib::string& query_tensor, const TensorSpec& exp_spec)
{
    RankFixture f(mixed_tensor_type, direct_tensor(), 0, 1, labels, feature_name,
                  dense_tensor_type + ":" + query_tensor);
    ASSERT_FALSE(f.failed());
    SCOPED_TRACE(query_tensor);
    f.set_attribute_tensor(9, doc_tensor);
    EXPECT_EQ(exp_spec, get_spec(f, 9));
}

void
ClosestTest::assert_closest(const Labels& labels, const vespalib::string& feature_name, const std::vector<TensorSpec>& exp_specs)
{
    assert_closest(labels, feature_name, "[9,10]", exp_specs[0]);
    assert_closest(labels, feature_name, "[1,10]", exp_specs[1]);
}

INSTANTIATE_TEST_SUITE_P(ClosestMultiTest,
                         ClosestTest,
                         testing::Values(TestParam("Serialized", false),
                                         TestParam("Direct", true)),
                         testing::PrintToStringParamName());

TEST(ClosestTest, require_that_blueprint_can_be_created_from_factory)
{
    BlueprintFactoryFixture f;
    auto bp = f.factory.createBlueprint("closest");
    EXPECT_TRUE(bp);
    EXPECT_TRUE(dynamic_cast<ClosestBlueprint*>(bp.get()) != 0);
}

TEST(ClosestTest, require_that_no_features_are_dumped)
{
    ClosestBlueprint f1;
    IndexEnvironmentFixture f2;
    FeatureDumpFixture f3;
    f1.visitDumpFeatures(f2.indexEnv, f3);
}

TEST(ClosestTest, require_that_setup_fails_for_unknown_field)
{
    assert_setup("random_field", false, mixed_tensor_type, std::nullopt);
}

TEST(ClosestTest, require_that_setup_fails_if_field_type_is_not_attribute)
{
    assert_setup("ibar", false, sparse_tensor_type, std::nullopt);
}

TEST(ClosestTest, require_that_setup_fails_if_field_data_type_is_not_tensor)
{
    assert_setup("foo", false, sparse_tensor_type, std::nullopt);
}

TEST(ClosestTest, require_that_setup_can_be_done_on_random_label)
{
    assert_setup("bar", true, mixed_tensor_type, "random_label");
}

TEST(ClosestTest, require_that_setup_fails_if_tensor_type_is_missing)
{
    assert_setup("bar", false, std::nullopt, std::nullopt);
}

TEST(ClosestTest, require_that_setup_fails_if_tensor_type_is_dense)
{
    assert_setup("bar", false, dense_tensor_type, std::nullopt);
}

TEST(ClosestTest, require_that_setup_fails_if_tensor_type_is_sparse)
{
    assert_setup("bar", false, sparse_tensor_type, std::nullopt);
}

TEST_P(ClosestTest, require_that_no_label_gives_empty_result)
{
    NoLabel f;
    assert_closest(f, field_and_label_feature_name, {no_subspace, no_subspace});
}

TEST_P(ClosestTest, require_that_unrelated_label_gives_empty_result)
{
    SingleLabel f("unrelated", 1);
    assert_closest(f, field_and_label_feature_name, {no_subspace, no_subspace});
}

TEST_P(ClosestTest, closest_using_field_setup)
{
    NoLabel f;
    assert_closest(f, field_feature_name, {subspace_b, subspace_a});
}

TEST_P(ClosestTest, closest_using_field_and_label_setup)
{
    SingleLabel f("nns", 1);
    assert_closest(f, field_and_label_feature_name, {subspace_b, subspace_a});
}

GTEST_MAIN_RUN_ALL_TESTS()
