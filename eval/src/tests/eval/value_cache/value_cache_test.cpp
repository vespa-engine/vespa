// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value_cache/constant_value_cache.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;

struct MyValue : ConstantValue {
    DoubleValue my_value;
    ValueType my_type;
    MyValue(double val) : my_value(val), my_type(ValueType::double_type()) {}
    const ValueType &type() const override { return my_type; }
    const Value &value() const override { return my_value; }
};

struct MyFactory : ConstantValueFactory {
    mutable size_t create_cnt = 0;
    ConstantValue::UP create(const std::string &path, const std::string &) const override {
        ++create_cnt;
        return std::make_unique<MyValue>(double(atoi(path.c_str())));
    }
    ~MyFactory() override;
};

MyFactory::~MyFactory() = default;

class ValueCacheTest : public ::testing::Test {
protected:
    MyFactory f1;
    ConstantValueCache f2;
    ValueCacheTest();
    ~ValueCacheTest() override;
};

ValueCacheTest::ValueCacheTest()
    : ::testing::Test(),
      f1(),
      f2(f1)
{
}

ValueCacheTest::~ValueCacheTest() = default;

TEST_F(ValueCacheTest, require_that_values_can_be_created)
{
    ConstantValue::UP res = f2.create("1", "type");
    EXPECT_TRUE(res->type().is_double());
    EXPECT_EQ(1.0, res->value().as_double());
    EXPECT_EQ(2.0, f2.create("2", "type")->value().as_double());
    EXPECT_EQ(3.0, f2.create("3", "type")->value().as_double());
    EXPECT_EQ(3u, f1.create_cnt);
}

TEST_F(ValueCacheTest, require_that_underlying_values_can_be_shared)
{
    auto res1 = f2.create("1", "type");
    auto res2 = f2.create("2", "type");
    auto res3 = f2.create("2", "type");
    auto res4 = f2.create("2", "type");
    EXPECT_EQ(1.0, res1->value().as_double());
    EXPECT_EQ(2.0, res2->value().as_double());
    EXPECT_EQ(2.0, res2->value().as_double());
    EXPECT_EQ(2.0, res2->value().as_double());
    EXPECT_EQ(2u, f1.create_cnt);
}

TEST_F(ValueCacheTest, require_that_unused_values_are_evicted)
{
    EXPECT_EQ(1.0, f2.create("1", "type")->value().as_double());
    EXPECT_EQ(2.0, f2.create("2", "type")->value().as_double());
    EXPECT_EQ(2.0, f2.create("2", "type")->value().as_double());
    EXPECT_EQ(2.0, f2.create("2", "type")->value().as_double());
    EXPECT_EQ(4u, f1.create_cnt);
}

TEST_F(ValueCacheTest, require_that_type_spec_is_part_of_cache_key)
{
    auto res1 = f2.create("1", "type");
    auto res2 = f2.create("2", "type_a");
    auto res3 = f2.create("2", "type_b");
    auto res4 = f2.create("2", "type_b");
    EXPECT_EQ(1.0, res1->value().as_double());
    EXPECT_EQ(2.0, res2->value().as_double());
    EXPECT_EQ(2.0, res2->value().as_double());
    EXPECT_EQ(2.0, res2->value().as_double());
    EXPECT_EQ(3u, f1.create_cnt);
}

GTEST_MAIN_RUN_ALL_TESTS()
