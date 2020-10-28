// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_peek.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
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

std::vector<Layout> peek_layouts = {
    {x(4)},
    {x(4),y(5)},
    {x(4),y(5),z(7)},
    float_cells({x(4),y(5),z(7)}),
    {x({"-1","0","2"})},
    {x({"-1","0","2"}),y({"-2","0","1"})},
    {x({"-1","0","2"}),y({"-2","0","1"}),z({"-2","-1","0","1","2"})},
    float_cells({x({"-1","0","2"}),y({"-2","0","1"}),z({"-2","-1","0","1","2"})}),
    {x(4),y({"-2","0","1"}),z(7)},
    {x({"-1","0","2"}),y(5),z({"-2","-1","0","1","2"})},
    float_cells({x({"-1","0","2"}),y(5),z({"-2","-1","0","1","2"})})
};

using PeekSpec = GenericPeek::SpecMap;

TensorSpec reference_peek(const TensorSpec &param, const vespalib::string &result_type, const PeekSpec &spec) {
    TensorSpec result(result_type);
    ValueType param_type = ValueType::from_spec(param.type());
    auto is_mapped_dim = [&](const vespalib::string &name) {
        size_t dim_idx = param_type.dimension_index(name);
        assert(dim_idx != ValueType::Dimension::npos);
        const auto &param_dim = param_type.dimensions()[dim_idx];
        return param_dim.is_mapped();
    };
    TensorSpec::Address addr;
    for (const auto & [dim_name, label_or_child] : spec) {
        std::visit(vespalib::overload
                   {
                       [&](const TensorSpec::Label &label) {
                           addr.emplace(dim_name, label);
                       },
                       [&](const size_t &index) {
                           if (is_mapped_dim(dim_name)) {
                               addr.emplace(dim_name, vespalib::make_string("%" PRId64, int64_t(index)));
                           } else {
                               addr.emplace(dim_name, size_t(index));
                           }
                       }
                   }, label_or_child);
    }
    for (const auto &cell: param.cells()) {
        bool keep = true;
        TensorSpec::Address my_addr;
        for (const auto &binding: cell.first) {
            auto pos = addr.find(binding.first);
            if (pos == addr.end()) {
                my_addr.emplace(binding.first, binding.second);
            } else {
                if (!(pos->second == binding.second)) {
                    keep = false;
                }
            }
        }
        if (keep) {
            result.add(my_addr, cell.second);
        }
    }
    return spec_from_value(*value_from_spec(result, SimpleValueBuilderFactory::get()));
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
    size_t child_idx = 0;
    for (auto & [dim_name, label_or_child] : spec) {
        if (std::holds_alternative<size_t>(label_or_child)) {
            ssize_t child_value = std::get<size_t>(label_or_child);
            my_stack.push_back(stash.create<DoubleValue>(child_value));
            label_or_child = child_idx++;
        }
    }
    auto my_op = GenericPeek::make_instruction(param->type(), result_type, spec, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(my_stack));
}

vespalib::string to_str(const GenericPeek::SpecMap &spec) {
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
    return os.str();
}

void check_peek_for_dims(const TensorSpec &input,
                         GenericPeek::SpecMap spec,
                         std::vector<ValueType::Dimension> dimensions,
                         const ValueBuilderFactory &factory)
{
    if (dimensions.empty()) {
        ValueType param_type = ValueType::from_spec(input.type());
        std::vector<vespalib::string> reduce_dims;
        for (const auto & [dim_name, ignored] : spec) {
            reduce_dims.push_back(dim_name);
        }
        // if (reduce_dims.empty()) return;
        // ValueType result_type = param_type.reduce(reduce_dims);
        ValueType result_type = reduce_dims.empty() ? param_type : param_type.reduce(reduce_dims);
        auto expect = reference_peek(input, result_type.to_spec(), spec);
        SCOPED_TRACE(fmt("peek input: %s\n  peek spec: %s\n  peek result %s\n",
                         input.to_string().c_str(),
                         to_str(spec).c_str(),
                         expect.to_string().c_str()));
        auto actual = perform_generic_peek(input, result_type, spec, factory);
        EXPECT_EQ(actual, expect);
        return;
    }
    auto dim = dimensions.back();
    dimensions.pop_back();
    check_peek_for_dims(input, spec, dimensions, factory);
    for (size_t label_value : {-2, -1, 0, 1, 2, 3}) {
        if (dim.is_indexed()) {
            if (label_value > dim.size) continue;
            TensorSpec::Label label(label_value);
            spec.insert_or_assign(dim.name, label);
        } else {
            TensorSpec::Label label(make_string("%" PRId64, int64_t(label_value)));
            spec.insert_or_assign(dim.name, label);
        }
        check_peek_for_dims(input, spec, dimensions, factory);
    }
    for (size_t child_value : {-2, -1, 0, 1, 2, 3}) {
        spec.insert_or_assign(dim.name, child_value);
        check_peek_for_dims(input, spec, dimensions, factory);
    }
}

void test_generic_peek_with(const ValueBuilderFactory &factory) {
    for (const auto & layout : peek_layouts) {
        TensorSpec input = spec(layout, N());
        ValueType input_type = ValueType::from_spec(input.type());
        const auto &dims = input_type.dimensions();
        GenericPeek::SpecMap spec;
        check_peek_for_dims(input, spec, dims, factory);
    }
}

TEST(GenericPeekTest, generic_peek_works_for_simple_values) {
    test_generic_peek_with(SimpleValueBuilderFactory::get());
}

TEST(GenericPeekTest, generic_peek_works_for_fast_values) {
    test_generic_peek_with(FastValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
