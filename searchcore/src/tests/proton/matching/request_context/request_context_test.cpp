// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/searchcore/proton/matching/requestcontext.h>
#include <vespa/searchlib/attribute/attribute_blueprint_params.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/vespalib/util/testclock.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>

using search::attribute::AttributeBlueprintParams;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeFunctor;
using search::attribute::IAttributeVector;
using search::fef::test::IndexEnvironment;
using search::fef::test::QueryEnvironment;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;

using namespace proton;
using namespace search::fef::indexproperties;

class MyAttributeContext : public search::attribute::IAttributeContext {
public:
    const IAttributeVector* getAttribute(const vespalib::string&) const override { abort(); }
    const IAttributeVector* getAttributeStableEnum(const vespalib::string&) const override { abort(); }
    void getAttributeList(std::vector<const IAttributeVector*>&) const override { abort(); }
    void asyncForAttribute(const vespalib::string&, std::unique_ptr<IAttributeFunctor>) const override { abort(); }
};

class RequestContextTest : public ::testing::Test {
private:
    vespalib::TestClock    _clock;
    vespalib::Doom         _doom;
    MyAttributeContext     _attr_ctx;
    IndexEnvironment       _index_env;
    QueryEnvironment       _query_env;
    RequestContext         _request_ctx;
    Value::UP              _query_tensor;

    void insert_tensor_in_properties(const vespalib::string& tensor_name, const Value& tensor_value) {
        vespalib::nbostream stream;
        encode_value(tensor_value, stream);
        _query_env.getProperties().add(tensor_name, vespalib::stringref(stream.data(), stream.size()));
    }

public:
    RequestContextTest()
        : _clock(),
          _doom(_clock.clock(), vespalib::steady_time(), vespalib::steady_time(), false),
          _attr_ctx(),
          _index_env(),
          _query_env(&_index_env),
          _request_ctx(_doom, _attr_ctx, _query_env, _query_env.getObjectStore(), AttributeBlueprintParams(), nullptr),
          _query_tensor(SimpleValue::from_spec(TensorSpec("tensor(x[2])")
                                               .add({{"x", 0}}, 3).add({{"x", 1}}, 5)))
    {
        type::QueryFeature::set(_index_env.getProperties(), "my_tensor", "tensor(x[2])");
        insert_tensor_in_properties("my_tensor", *_query_tensor);
        _query_env.getProperties().add("my_string", "foo bar");
    }
    TensorSpec expected_query_tensor() const {
        return spec_from_value(*_query_tensor);
    }
    const Value* get_query_tensor(const vespalib::string& tensor_name) const {
        return _request_ctx.get_query_tensor(tensor_name);
    }
};

TEST_F(RequestContextTest, query_tensor_can_be_retrieved)
{
    auto tensor = get_query_tensor("my_tensor");
    ASSERT_TRUE(tensor);
    EXPECT_TRUE(tensor->type().has_dimensions());
    EXPECT_EQ(expected_query_tensor(), spec_from_value(*tensor));
}

TEST_F(RequestContextTest, non_existing_query_tensor_returns_nullptr)
{
    auto tensor = get_query_tensor("non_existing");
    EXPECT_FALSE(tensor);
}

TEST_F(RequestContextTest, rank_property_of_non_tensor_type_returns_nullptr)
{
    auto tensor = get_query_tensor("my_string");
    EXPECT_FALSE(tensor);
}

GTEST_MAIN_RUN_ALL_TESTS()
