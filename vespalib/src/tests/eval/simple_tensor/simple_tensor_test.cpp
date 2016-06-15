// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/eval/simple_tensor.h>
#include <vespa/vespalib/eval/simple_tensor_engine.h>
#include <vespa/vespalib/eval/operation.h>
#include <vespa/vespalib/util/stash.h>
#include <iostream>

using namespace vespalib::eval;

using Cell = SimpleTensor::Cell;
using Cells = SimpleTensor::Cells;
using Address = SimpleTensor::Address;
using Stash = vespalib::Stash;

// need to specify numbers explicitly as size_t to avoid ambiguous behavior for 0
constexpr size_t operator "" _z (unsigned long long int n) { return n; }

void dump(const Cells &cells, std::ostream &out) {
    out << std::endl;
    for (const auto &cell: cells) {
        size_t n = 0;
        out << "  [";
        for (const auto &label: cell.address) {
            if (n++) {
                out << ",";
            }
            if (label.is_mapped()) {
                out << label.name;
            } else {
                out << label.index;
            }
        }
        out << "]: " << cell.value << std::endl;
    }
}

struct Check {
    Cells cells;
    Check() : cells() {}
    explicit Check(const SimpleTensor &tensor) : cells() {
        for (const auto &cell: tensor.cells()) {
            add(cell.address, cell.value);
        }
    }
    explicit Check(const TensorSpec &spec)
        : Check(*SimpleTensor::create(spec)) {}
    Check &add(const Address &address, double value) {
        cells.emplace_back(address, value);
        std::sort(cells.begin(), cells.end(),
                  [](const auto &a, const auto &b){ return (a.address < b.address); });
        return *this;
    }
    bool operator==(const Check &rhs) const {
        if (cells.size() != rhs.cells.size()) {
            return false;
        }
        for (size_t i = 0; i < cells.size(); ++i) {
            if ((cells[i].address != rhs.cells[i].address) ||
                (cells[i].value != rhs.cells[i].value))
            {
                return false;
            }
        }
        return true;
    }
};

std::ostream &operator<<(std::ostream &out, const Check &value) {
    dump(value.cells, out);
    return out;
}

const SimpleTensor &unwrap(const Tensor &tensor) {
    ASSERT_EQUAL(&tensor.engine(), &SimpleTensorEngine::ref());
    return static_cast<const SimpleTensor &>(tensor);
}

const SimpleTensor &unwrap(const Value &value) {
    ASSERT_TRUE(value.is_tensor());
    return unwrap(*value.as_tensor());
}

TEST("require that simple tensors can be built using tensor spec") {
    TensorSpec spec("tensor(w{},x[2],y{},z[2])");
    spec.add({{"w", "xxx"}, {"x", 0}, {"y", "xxx"}, {"z", 0}}, 1.0)
        .add({{"w", "xxx"}, {"x", 0}, {"y", "yyy"}, {"z", 1}}, 2.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "xxx"}, {"z", 0}}, 3.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "yyy"}, {"z", 1}}, 4.0);
    std::unique_ptr<SimpleTensor> tensor = SimpleTensor::create(spec);
    Check expect = Check()
                   .add({{"xxx"}, {0_z}, {"xxx"}, {0_z}}, 1.0)
                   .add({{"xxx"}, {0_z}, {"xxx"}, {1_z}}, 0.0)
                   .add({{"xxx"}, {1_z}, {"xxx"}, {0_z}}, 0.0)
                   .add({{"xxx"}, {1_z}, {"xxx"}, {1_z}}, 0.0)
                   //-----------------------------------------
                   .add({{"xxx"}, {0_z}, {"yyy"}, {0_z}}, 0.0)
                   .add({{"xxx"}, {0_z}, {"yyy"}, {1_z}}, 2.0)
                   .add({{"xxx"}, {1_z}, {"yyy"}, {0_z}}, 0.0)
                   .add({{"xxx"}, {1_z}, {"yyy"}, {1_z}}, 0.0)
                   //-----------------------------------------
                   .add({{"yyy"}, {0_z}, {"xxx"}, {0_z}}, 0.0)
                   .add({{"yyy"}, {0_z}, {"xxx"}, {1_z}}, 0.0)
                   .add({{"yyy"}, {1_z}, {"xxx"}, {0_z}}, 3.0)
                   .add({{"yyy"}, {1_z}, {"xxx"}, {1_z}}, 0.0)
                   //-----------------------------------------
                   .add({{"yyy"}, {0_z}, {"yyy"}, {0_z}}, 0.0)
                   .add({{"yyy"}, {0_z}, {"yyy"}, {1_z}}, 0.0)
                   .add({{"yyy"}, {1_z}, {"yyy"}, {0_z}}, 0.0)
                   .add({{"yyy"}, {1_z}, {"yyy"}, {1_z}}, 4.0);
    EXPECT_EQUAL(expect, Check(*tensor));
    std::unique_ptr<Tensor> tensor2 = SimpleTensorEngine::ref().create(spec);
    EXPECT_EQUAL(expect, Check(unwrap(*tensor2)));
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
    auto result = SimpleTensor::perform(operation::Neg(), *tensor);
    EXPECT_EQUAL(Check(*expect), Check(*result));
    Stash stash;
    const Value &result2 = SimpleTensorEngine::ref().perform(operation::Neg(), *tensor, stash);
    EXPECT_EQUAL(Check(*expect), Check(unwrap(result2)));    
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
    auto result = SimpleTensor::perform(operation::Mul(), *lhs, *rhs);
    EXPECT_EQUAL(Check(*expect), Check(*result));
    Stash stash;
    const Value &result2 = SimpleTensorEngine::ref().perform(operation::Mul(), *lhs, *rhs, stash);
    EXPECT_EQUAL(Check(*expect), Check(unwrap(result2)));
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
    auto result_sum_y = tensor->reduce(operation::Add(), {"y"});
    auto result_sum_x = tensor->reduce(operation::Add(), {"x"});
    auto result_sum_all = tensor->reduce(operation::Add(), {"x", "y"});
    EXPECT_EQUAL(Check(*expect_sum_y), Check(*result_sum_y));
    EXPECT_EQUAL(Check(*expect_sum_x), Check(*result_sum_x));
    EXPECT_EQUAL(Check(*expect_sum_all), Check(*result_sum_all));
    Stash stash;
    const Value &result_sum_y_2 = SimpleTensorEngine::ref().reduce(*tensor, operation::Add(), {"y"}, stash);
    const Value &result_sum_x_2 = SimpleTensorEngine::ref().reduce(*tensor, operation::Add(), {"x"}, stash);
    const Value &result_sum_all_2 = SimpleTensorEngine::ref().reduce(*tensor, operation::Add(), {"x", "y"}, stash); 
    const Value &result_sum_all_3 = SimpleTensorEngine::ref().reduce(*tensor, operation::Add(), stash);
    EXPECT_EQUAL(Check(*expect_sum_y), Check(unwrap(result_sum_y_2)));
    EXPECT_EQUAL(Check(*expect_sum_x), Check(unwrap(result_sum_x_2)));
    EXPECT_TRUE(result_sum_all_2.is_double());
    EXPECT_TRUE(result_sum_all_3.is_double());
    EXPECT_EQUAL(21, result_sum_all_2.as_double());
    EXPECT_EQUAL(21, result_sum_all_3.as_double());
    EXPECT_TRUE(SimpleTensorEngine::ref().equal(*result_sum_y, *result_sum_y));
    EXPECT_TRUE(!SimpleTensorEngine::ref().equal(*result_sum_y, *result_sum_x));
}

TEST_MAIN() { TEST_RUN_ALL(); }
