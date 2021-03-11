// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_create.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <stdlib.h>
#include <algorithm>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

GenSpec G() { return GenSpec(); }

const std::vector<GenSpec> create_layouts = {
    G().idx("x", 3),
    G().idx("x", 3).idx("y", 5),
    G().idx("x", 3).idx("y", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {"foo", "bar"}).idx("z", 7),
    G().map("x", {"a","b","c"}).idx("y", 5).map("z", {"i","j","k","l"})
};

TensorSpec remove_each(const TensorSpec &a, size_t n) {
    TensorSpec b(a.type());
    for (const auto & kv : a.cells()) {
        size_t v = kv.second;
        if ((v % n) != 0) {
            b.add(kv.first, kv.second);
        }
    }
    return b;
}

struct NumberedCellSpec {
    long int num;
    TensorSpec::Address addr;
    double value;
};

bool operator< (const NumberedCellSpec &a, const NumberedCellSpec &b) {
    return a.num < b.num;
}

TensorSpec reference_create(const TensorSpec &a) {
    std::vector<TensorSpec> children;
    ReferenceOperations::CreateSpec spec;
    for (const auto & [addr, value] : a.cells()) {
        size_t child_idx = children.size();
        spec.emplace(addr, child_idx);
        TensorSpec child("double");
        child.add({}, value);
        children.push_back(child);
    }
    return ReferenceOperations::create(a.type(), spec, children);
}

TensorSpec perform_generic_create(const TensorSpec &a, const ValueBuilderFactory &factory)
{
    ValueType res_type = ValueType::from_spec(a.type());
    EXPECT_FALSE(res_type.is_error());
    Stash stash;
    std::vector<NumberedCellSpec> scramble;
    for (const auto & kv : a.cells()) {
        NumberedCellSpec cell{random(), kv.first, kv.second};
        scramble.push_back(cell);
    }
    std::sort(scramble.begin(), scramble.end());
    std::vector<Value::CREF> my_stack;
    std::map<TensorSpec::Address,size_t> create_spec;
    for (size_t child_idx = 0; child_idx < scramble.size(); ++child_idx) {
        auto cell = scramble[child_idx];
        create_spec.emplace(cell.addr, child_idx);
        my_stack.push_back(stash.create<DoubleValue>(cell.value));
    }
    auto my_op = GenericCreate::make_instruction(res_type, create_spec, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(my_stack));
}

void test_generic_create_with(const ValueBuilderFactory &factory) {
    for (const auto &layout : create_layouts) {
        for (CellType ct : CellTypeUtils::list_types()) {
            auto full = layout.cpy().cells(ct);
            auto actual = perform_generic_create(full, factory);
            auto expect = reference_create(full);
            EXPECT_EQ(actual, expect);
            for (size_t n : {2, 3, 4, 5}) {
                TensorSpec partial = remove_each(full, n);
                actual = perform_generic_create(partial, factory);
                expect = reference_create(partial);
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

TEST(GenericCreateTest, generic_create_works_for_simple_values) {
    test_generic_create_with(SimpleValueBuilderFactory::get());
}

TEST(GenericCreateTest, generic_create_works_for_fast_values) {
    test_generic_create_with(FastValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
