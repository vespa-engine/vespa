// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/value_cache/constant_value_cache.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>

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
    ConstantValue::UP create(const vespalib::string &path, const vespalib::string &) const override {
        ++create_cnt;
        return std::make_unique<MyValue>(double(atoi(path.c_str())));
    }
    ~MyFactory();
};

MyFactory::~MyFactory() = default;

TEST_FF("require that values can be created", MyFactory(), ConstantValueCache(f1)) {
    ConstantValue::UP res = f2.create("1", "type");
    EXPECT_TRUE(res->type().is_double());
    EXPECT_EQUAL(1.0, res->value().as_double());
    EXPECT_EQUAL(2.0, f2.create("2", "type")->value().as_double());
    EXPECT_EQUAL(3.0, f2.create("3", "type")->value().as_double());
    EXPECT_EQUAL(3u, f1.create_cnt);
}

TEST_FF("require that underlying values can be shared", MyFactory(), ConstantValueCache(f1)) {
    auto res1 = f2.create("1", "type");
    auto res2 = f2.create("2", "type");
    auto res3 = f2.create("2", "type");
    auto res4 = f2.create("2", "type");
    EXPECT_EQUAL(1.0, res1->value().as_double());
    EXPECT_EQUAL(2.0, res2->value().as_double());
    EXPECT_EQUAL(2.0, res2->value().as_double());
    EXPECT_EQUAL(2.0, res2->value().as_double());
    EXPECT_EQUAL(2u, f1.create_cnt);
}

TEST_FF("require that unused values are evicted", MyFactory(), ConstantValueCache(f1)) {
    EXPECT_EQUAL(1.0, f2.create("1", "type")->value().as_double());
    EXPECT_EQUAL(2.0, f2.create("2", "type")->value().as_double());
    EXPECT_EQUAL(2.0, f2.create("2", "type")->value().as_double());
    EXPECT_EQUAL(2.0, f2.create("2", "type")->value().as_double());
    EXPECT_EQUAL(4u, f1.create_cnt);
}

TEST_FF("require that type spec is part of cache key", MyFactory(), ConstantValueCache(f1)) {
    auto res1 = f2.create("1", "type");
    auto res2 = f2.create("2", "type_a");
    auto res3 = f2.create("2", "type_b");
    auto res4 = f2.create("2", "type_b");
    EXPECT_EQUAL(1.0, res1->value().as_double());
    EXPECT_EQUAL(2.0, res2->value().as_double());
    EXPECT_EQUAL(2.0, res2->value().as_double());
    EXPECT_EQUAL(2.0, res2->value().as_double());
    EXPECT_EQUAL(3u, f1.create_cnt);
}

TEST_MAIN() { TEST_RUN_ALL(); }
