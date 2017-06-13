// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/vespalib/util/stash.h>
#include <iostream>

using namespace vespalib::eval;

using Cell = SimpleTensor::Cell;
using Cells = SimpleTensor::Cells;
using Address = SimpleTensor::Address;
using Stash = vespalib::Stash;

// need to specify numbers explicitly as size_t to avoid ambiguous behavior for 0
constexpr size_t operator "" _z (unsigned long long int n) { return n; }

const Tensor &unwrap(const Value &value) {
    ASSERT_TRUE(value.is_tensor());
    return *value.as_tensor();
}

struct CellBuilder {
    Cells cells;
    CellBuilder &add(const Address &addr, double value) {
        cells.emplace_back(addr, value);
        return *this;
    }
    Cells build() { return cells; }
};

TEST("require that simple tensors can be built using tensor spec") {
    TensorSpec spec("tensor(w{},x[2],y{},z[2])");
    spec.add({{"w", "xxx"}, {"x", 0}, {"y", "xxx"}, {"z", 0}}, 1.0)
        .add({{"w", "xxx"}, {"x", 0}, {"y", "yyy"}, {"z", 1}}, 2.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "xxx"}, {"z", 0}}, 3.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "yyy"}, {"z", 1}}, 4.0);
    auto tensor = SimpleTensorEngine::ref().create(spec);
    TensorSpec full_spec("tensor(w{},x[2],y{},z[2])");
    full_spec
        .add({{"w", "xxx"}, {"x", 0}, {"y", "xxx"}, {"z", 0}}, 1.0)
        .add({{"w", "xxx"}, {"x", 0}, {"y", "xxx"}, {"z", 1}}, 0.0)
        .add({{"w", "xxx"}, {"x", 0}, {"y", "yyy"}, {"z", 0}}, 0.0)
        .add({{"w", "xxx"}, {"x", 0}, {"y", "yyy"}, {"z", 1}}, 2.0)
        .add({{"w", "xxx"}, {"x", 1}, {"y", "xxx"}, {"z", 0}}, 0.0)
        .add({{"w", "xxx"}, {"x", 1}, {"y", "xxx"}, {"z", 1}}, 0.0)
        .add({{"w", "xxx"}, {"x", 1}, {"y", "yyy"}, {"z", 0}}, 0.0)
        .add({{"w", "xxx"}, {"x", 1}, {"y", "yyy"}, {"z", 1}}, 0.0)
        .add({{"w", "yyy"}, {"x", 0}, {"y", "xxx"}, {"z", 0}}, 0.0)
        .add({{"w", "yyy"}, {"x", 0}, {"y", "xxx"}, {"z", 1}}, 0.0)
        .add({{"w", "yyy"}, {"x", 0}, {"y", "yyy"}, {"z", 0}}, 0.0)
        .add({{"w", "yyy"}, {"x", 0}, {"y", "yyy"}, {"z", 1}}, 0.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "xxx"}, {"z", 0}}, 3.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "xxx"}, {"z", 1}}, 0.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "yyy"}, {"z", 0}}, 0.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "yyy"}, {"z", 1}}, 4.0);
    auto full_tensor = SimpleTensorEngine::ref().create(full_spec);
    SimpleTensor expect_tensor(ValueType::from_spec("tensor(w{},x[2],y{},z[2])"),
                               CellBuilder()
                               .add({{"xxx"}, {0_z}, {"xxx"}, {0_z}}, 1.0)
                               .add({{"xxx"}, {0_z}, {"xxx"}, {1_z}}, 0.0)
                               .add({{"xxx"}, {0_z}, {"yyy"}, {0_z}}, 0.0)
                               .add({{"xxx"}, {0_z}, {"yyy"}, {1_z}}, 2.0)
                               .add({{"xxx"}, {1_z}, {"xxx"}, {0_z}}, 0.0)
                               .add({{"xxx"}, {1_z}, {"xxx"}, {1_z}}, 0.0)
                               .add({{"xxx"}, {1_z}, {"yyy"}, {0_z}}, 0.0)
                               .add({{"xxx"}, {1_z}, {"yyy"}, {1_z}}, 0.0)
                               .add({{"yyy"}, {0_z}, {"xxx"}, {0_z}}, 0.0)
                               .add({{"yyy"}, {0_z}, {"xxx"}, {1_z}}, 0.0)
                               .add({{"yyy"}, {0_z}, {"yyy"}, {0_z}}, 0.0)
                               .add({{"yyy"}, {0_z}, {"yyy"}, {1_z}}, 0.0)
                               .add({{"yyy"}, {1_z}, {"xxx"}, {0_z}}, 3.0)
                               .add({{"yyy"}, {1_z}, {"xxx"}, {1_z}}, 0.0)
                               .add({{"yyy"}, {1_z}, {"yyy"}, {0_z}}, 0.0)
                               .add({{"yyy"}, {1_z}, {"yyy"}, {1_z}}, 4.0)
                               .build());
    EXPECT_EQUAL(expect_tensor, *tensor);
    EXPECT_EQUAL(expect_tensor, *full_tensor);
    EXPECT_EQUAL(full_spec, tensor->engine().to_spec(*tensor));
};

TEST("require that simple tensors can have their values negated") {
    auto tensor = SimpleTensor::create(
            TensorSpec("tensor(x{},y{})")
            .add({{"x","1"},{"y","1"}}, 1)
            .add({{"x","2"},{"y","1"}}, -3)
            .add({{"x","1"},{"y","2"}}, 5));
    auto expect = SimpleTensor::create(
            TensorSpec("tensor(x{},y{})")
            .add({{"x","1"},{"y","1"}}, -1)
            .add({{"x","2"},{"y","1"}}, 3)
            .add({{"x","1"},{"y","2"}}, -5));
    auto result = tensor->map([](double a){ return -a; });
    EXPECT_EQUAL(*expect, *result);
    Stash stash;
    const Value &result2 = SimpleTensorEngine::ref().map(operation::Neg(), *tensor, stash);
    EXPECT_EQUAL(*expect, unwrap(result2));    
}

TEST("require that simple tensors can be multiplied with each other") {
    auto lhs = SimpleTensor::create(
            TensorSpec("tensor(x{},y{})")
            .add({{"x","1"},{"y","1"}}, 1)
            .add({{"x","2"},{"y","1"}}, 3)
            .add({{"x","1"},{"y","2"}}, 5));
    auto rhs = SimpleTensor::create(
            TensorSpec("tensor(y{},z{})")
            .add({{"y","1"},{"z","1"}}, 7)
            .add({{"y","2"},{"z","1"}}, 11)
            .add({{"y","1"},{"z","2"}}, 13));
    auto expect = SimpleTensor::create(
            TensorSpec("tensor(x{},y{},z{})")
            .add({{"x","1"},{"y","1"},{"z","1"}}, 7)
            .add({{"x","1"},{"y","1"},{"z","2"}}, 13)
            .add({{"x","2"},{"y","1"},{"z","1"}}, 21)
            .add({{"x","2"},{"y","1"},{"z","2"}}, 39)
            .add({{"x","1"},{"y","2"},{"z","1"}}, 55));
    auto result = SimpleTensor::join(*lhs, *rhs, [](double a, double b){ return (a * b); });
    EXPECT_EQUAL(*expect, *result);
    Stash stash;
    const Value &result2 = SimpleTensorEngine::ref().apply(operation::Mul(), *lhs, *rhs, stash);
    EXPECT_EQUAL(*expect, unwrap(result2));
}

TEST("require that simple tensors support dimension reduction") {
    auto tensor = SimpleTensor::create(
            TensorSpec("tensor(x[3],y[2])")
            .add({{"x",0},{"y",0}}, 1)
            .add({{"x",1},{"y",0}}, 2)
            .add({{"x",2},{"y",0}}, 3)
            .add({{"x",0},{"y",1}}, 4)
            .add({{"x",1},{"y",1}}, 5)
            .add({{"x",2},{"y",1}}, 6));
    auto expect_sum_y = SimpleTensor::create(
            TensorSpec("tensor(x[3])")
            .add({{"x",0}}, 5)
            .add({{"x",1}}, 7)
            .add({{"x",2}}, 9));
    auto expect_sum_x = SimpleTensor::create(
            TensorSpec("tensor(y[2])")
            .add({{"y",0}}, 6)
            .add({{"y",1}}, 15));
    auto expect_sum_all = SimpleTensor::create(TensorSpec("double").add({}, 21));
    Stash stash;
    Aggregator &aggr_sum = Aggregator::create(Aggr::SUM, stash);
    auto result_sum_y = tensor->reduce(aggr_sum, {"y"});
    auto result_sum_x = tensor->reduce(aggr_sum, {"x"});
    auto result_sum_all = tensor->reduce(aggr_sum, {"x", "y"});
    EXPECT_EQUAL(*expect_sum_y, *result_sum_y);
    EXPECT_EQUAL(*expect_sum_x, *result_sum_x);
    EXPECT_EQUAL(*expect_sum_all, *result_sum_all);
    const Value &result_sum_y_2 = SimpleTensorEngine::ref().reduce(*tensor, operation::Add(), {"y"}, stash);
    const Value &result_sum_x_2 = SimpleTensorEngine::ref().reduce(*tensor, operation::Add(), {"x"}, stash);
    const Value &result_sum_all_2 = SimpleTensorEngine::ref().reduce(*tensor, operation::Add(), {"x", "y"}, stash); 
    const Value &result_sum_all_3 = SimpleTensorEngine::ref().reduce(*tensor, operation::Add(), {}, stash);
    EXPECT_EQUAL(*expect_sum_y, unwrap(result_sum_y_2));
    EXPECT_EQUAL(*expect_sum_x, unwrap(result_sum_x_2));
    EXPECT_TRUE(result_sum_all_2.is_double());
    EXPECT_TRUE(result_sum_all_3.is_double());
    EXPECT_EQUAL(21, result_sum_all_2.as_double());
    EXPECT_EQUAL(21, result_sum_all_3.as_double());
    EXPECT_EQUAL(*result_sum_y, *result_sum_y);
    EXPECT_NOT_EQUAL(*result_sum_y, *result_sum_x);
}

TEST_MAIN() { TEST_RUN_ALL(); }
