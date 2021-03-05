// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_peek.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <stdlib.h>
#include <variant>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

GenSpec G() { return GenSpec(); }

const std::vector<GenSpec> peek_layouts = {
    G().idx("x", 4),
    G().idx("x", 4).idx("y", 5),
    G().idx("x", 4).idx("y", 5).idx("z", 3),
    G().map("x", {"-1","0","2"}),
    G().map("x", {"-1","0","2"}).map("y", {"-2","0","1"}).map("z", {"-2","-1","0","1","2"}),
    G().idx("x", 4).map("y", {"-2","0","1"}).idx("z", 3),
    G().map("x", {"-1","0","2"}).idx("y", 5).map("z", {"-2","-1","0","1","2"})
};

using PeekSpec = GenericPeek::SpecMap;

TensorSpec reference_peek(const TensorSpec &param, const PeekSpec &spec) {
    std::vector<TensorSpec> children;
    children.push_back(param);
    PeekSpec with_indexes;
    for (const auto & [dim_name, label_or_child] : spec) {
        const vespalib::string &dim = dim_name;
        std::visit(vespalib::overload
                   {
                       [&](const TensorSpec::Label &label) {
                           with_indexes.emplace(dim, label);
                       },
                       [&](const size_t &child_value) {
                           // here, label_or_child is a size_t specifying the value
                           // we pretend a child produced
                           size_t child_idx = children.size();
                           TensorSpec child("double");
                           // (but cast to signed first, to allow labels like the string "-2")
                           child.add({}, ssize_t(child_value));
                           children.push_back(child);
                           with_indexes.emplace(dim, child_idx);
                       }
                   }, label_or_child);
    }
    return ReferenceOperations::peek(with_indexes, children);
}

TensorSpec perform_generic_peek(const TensorSpec &a, const ValueType &result_type,
                                PeekSpec spec, const ValueBuilderFactory &factory)
{
    auto param = value_from_spec(a, factory);
    EXPECT_FALSE(param->type().is_error());
    EXPECT_FALSE(result_type.is_error());
    Stash stash;
    std::vector<Value::CREF> my_stack;
    my_stack.push_back(*param);
    size_t child_idx = 1;
    for (auto & [dim_name, label_or_child] : spec) {
        if (std::holds_alternative<size_t>(label_or_child)) {
            // here, label_or_child is a size_t specifying the value
            // this child should produce (but cast to signed first,
            // to allow negative values)
            ssize_t child_value = std::get<size_t>(label_or_child);
            my_stack.push_back(stash.create<DoubleValue>(child_value));
            // overwrite label_or_child, now it should be the index of
            // the child for make_instruction
            label_or_child = child_idx++;
        }
    }
    auto my_op = GenericPeek::make_instruction(result_type, param->type(), spec, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(my_stack));
}

TensorSpec tensor_function_peek(const TensorSpec &a, const ValueType &result_type,
                                PeekSpec spec, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto param = value_from_spec(a, factory);
    EXPECT_FALSE(param->type().is_error());
    EXPECT_FALSE(result_type.is_error());
    std::vector<Value::CREF> my_stack;
    my_stack.push_back(*param);
    const auto &func_double = tensor_function::inject(ValueType::double_type(), 1, stash);
    std::map<vespalib::string, std::variant<TensorSpec::Label, TensorFunction::CREF>> func_spec;
    for (auto & [dim_name, label_or_child] : spec) {
        if (std::holds_alternative<size_t>(label_or_child)) {
            // here, label_or_child is a size_t specifying the value
            // this child should produce (but cast to signed first,
            // to allow negative values)
            ssize_t child_value = std::get<size_t>(label_or_child);
            my_stack.push_back(stash.create<DoubleValue>(double(child_value)));
            func_spec.emplace(dim_name, func_double);
        } else {
            auto label = std::get<TensorSpec::Label>(label_or_child);
            func_spec.emplace(dim_name, label);
        }
    }
    const auto &func_param = tensor_function::inject(param->type(), 0, stash);
    const auto &peek_node = tensor_function::peek(func_param, func_spec, stash);
    auto my_op = peek_node.compile_self(factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(my_stack));
}

vespalib::string to_str(const PeekSpec &spec) {
    vespalib::asciistream os;
    os << "{ ";
    for (const auto & [dim, label_or_index] : spec) {
        os << dim << " : ";
        if (std::holds_alternative<size_t>(label_or_index)) {
            os << "[" << ssize_t(std::get<size_t>(label_or_index)) << "] ";
        } else {
            auto label = std::get<TensorSpec::Label>(label_or_index);
            if (label.is_mapped()) {
                os << "'" << label.name << "' ";
            } else {
                os << "(" << ssize_t(label.index) << ") ";
            }
        }
    }
    os << "}";
    return os.str();
}

void verify_peek_equal(const TensorSpec &input,
                       const PeekSpec &spec,
                       const ValueBuilderFactory &factory)
{
    ValueType param_type = ValueType::from_spec(input.type());
    std::vector<vespalib::string> reduce_dims;
    for (const auto & [dim_name, ignored] : spec) {
        reduce_dims.push_back(dim_name);
    }
    if (reduce_dims.empty()) return;
    ValueType result_type = param_type.peek(reduce_dims);
    auto expect = reference_peek(input, spec);
    SCOPED_TRACE(fmt("peek input: %s\n  peek spec: %s\n  peek result %s\n",
                     input.to_string().c_str(),
                     to_str(spec).c_str(),
                     expect.to_string().c_str()));
    auto actual = perform_generic_peek(input, result_type, spec, factory);
    EXPECT_EQ(actual, expect);
    auto from_func = tensor_function_peek(input, result_type, spec, factory);
    EXPECT_EQ(from_func, expect);
}

void fill_dims_and_check(const TensorSpec &input,
                         PeekSpec spec,
                         std::vector<ValueType::Dimension> dimensions,
                         const ValueBuilderFactory &factory)
{
    if (dimensions.empty()) {
        verify_peek_equal(input, spec, factory);
        return;
    }
    auto dim = dimensions.back();
    dimensions.pop_back();
    fill_dims_and_check(input, spec, dimensions, factory);
    for (int64_t label_value : {-2, -1, 0, 1, 3}) {
        if (dim.is_indexed()) {
            size_t index = label_value;
            if (index >= dim.size) continue;
            TensorSpec::Label label(index);
            spec.insert_or_assign(dim.name, label);
        } else {
            TensorSpec::Label label(make_string("%" PRId64, label_value));
            spec.insert_or_assign(dim.name, label);
        }
        fill_dims_and_check(input, spec, dimensions, factory);
    }
    for (int64_t child_value : {-2, -1, 0, 1, 3}) {
        spec.insert_or_assign(dim.name, size_t(child_value));
        fill_dims_and_check(input, spec, dimensions, factory);
    }
}

void test_generic_peek_with(const ValueBuilderFactory &factory) {
    for (const auto &layout : peek_layouts) {
        for (CellType ct : CellTypeUtils::list_types()) {
            auto input = layout.cpy().cells(ct);
            ValueType input_type = input.type();
            const auto &dims = input_type.dimensions();
            PeekSpec spec;
            fill_dims_and_check(input, spec, dims, factory);
        }
    }
}

TEST(GenericPeekTest, generic_peek_works_for_simple_values) {
    test_generic_peek_with(SimpleValueBuilderFactory::get());
}

TEST(GenericPeekTest, generic_peek_works_for_fast_values) {
    test_generic_peek_with(FastValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
