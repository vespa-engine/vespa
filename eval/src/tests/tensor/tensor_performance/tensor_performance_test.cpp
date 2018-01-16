// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/tensor_nodes.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/sparse/sparse_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/tensor_builder.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/eval/tensor/default_tensor_engine.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::tensor;

//-----------------------------------------------------------------------------

const vespalib::string dot_product_match_expr    = "reduce(query*document,sum)";
const vespalib::string dot_product_multiply_expr = "reduce(query*document,sum)";
const vespalib::string model_match_expr          = "reduce((query*document)*model,sum)";
const vespalib::string matrix_product_expr       = "reduce(reduce((query+document)*model,sum,x),sum)";

//-----------------------------------------------------------------------------

struct Params {
    std::map<vespalib::string, Value::UP> map;
    Params &add(const vespalib::string &name, Value::UP value) {
        map.emplace(name, std::move(value));
        return *this;
    }
};

SimpleObjectParams make_params(const Function &function, const Params &params)
{
    SimpleObjectParams fun_params({});
    EXPECT_EQUAL(params.map.size(), function.num_params());
    for (size_t i = 0; i < function.num_params(); ++i) {
        auto param = params.map.find(function.param_name(i));
        ASSERT_TRUE(param != params.map.end());
        fun_params.params.push_back(*param->second);
    }
    return fun_params;
}

std::vector<ValueType> extract_param_types(const Function &function, const Params &params) {
    std::vector<ValueType> result;
    EXPECT_EQUAL(params.map.size(), function.num_params());
    for (size_t i = 0; i < function.num_params(); ++i) {
        auto param = params.map.find(function.param_name(i));
        ASSERT_TRUE(param != params.map.end());
        result.push_back(param->second->type());
    }
    return result;
}

double calculate_expression(const vespalib::string &expression, const Params &params) {
    const Function function = Function::parse(expression);
    const NodeTypes types(function, extract_param_types(function, params));
    const InterpretedFunction interpreted(tensor::DefaultTensorEngine::ref(), function, types);
    InterpretedFunction::Context context(interpreted);
    auto fun_params = make_params(function, params);
    const Value &result = interpreted.eval(context, fun_params);
    EXPECT_TRUE(result.is_double());
    return result.as_double();
}

DoubleValue dummy_result(0.0);
const Value &dummy_ranking(InterpretedFunction::Context &, InterpretedFunction::LazyParams &) { return dummy_result; }

double benchmark_expression_us(const vespalib::string &expression, const Params &params) {
    const Function function = Function::parse(expression);
    const NodeTypes types(function, extract_param_types(function, params));
    const InterpretedFunction interpreted(tensor::DefaultTensorEngine::ref(), function, types);
    InterpretedFunction::Context context(interpreted);
    auto fun_params = make_params(function, params);
    auto ranking = [&](){ interpreted.eval(context, fun_params); };
    auto baseline = [&](){ dummy_ranking(context, fun_params); };
    return BenchmarkTimer::benchmark(ranking, baseline, 5.0) * 1000.0 * 1000.0;
}

//-----------------------------------------------------------------------------

Value::UP make_tensor(TensorSpec spec) {
    return DefaultTensorEngine::ref().from_spec(spec);
}

//-----------------------------------------------------------------------------

TEST("SMOKETEST - require that dot product benchmark expressions produce expected results") {
    Params params;
    params.add("query",    make_tensor(TensorSpec("tensor(x{})")
                                       .add({{"x","0"}}, 1.0)
                                       .add({{"x","1"}}, 2.0)
                                       .add({{"x","2"}}, 3.0)));
    params.add("document", make_tensor(TensorSpec("tensor(x{})")
                                       .add({{"x","0"}}, 2.0)
                                       .add({{"x","1"}}, 2.0)
                                       .add({{"x","2"}}, 2.0)));
    EXPECT_EQUAL(calculate_expression(dot_product_match_expr, params), 12.0);
    EXPECT_EQUAL(calculate_expression(dot_product_multiply_expr, params), 12.0);
}

TEST("SMOKETEST - require that model match benchmark expression produces expected result") {
    Params params;
    params.add("query",    make_tensor(TensorSpec("tensor(x{})")
                                       .add({{"x","0"}}, 1.0)
                                       .add({{"x","1"}}, 2.0)));
    params.add("document", make_tensor(TensorSpec("tensor(y{})")
                                       .add({{"y","0"}}, 3.0)
                                       .add({{"y","1"}}, 4.0)));
    params.add("model",    make_tensor(TensorSpec("tensor(x{},y{})")
                                       .add({{"x","0"},{"y","0"}}, 2.0)
                                       .add({{"x","0"},{"y","1"}}, 2.0)
                                       .add({{"x","1"},{"y","0"}}, 2.0)
                                       .add({{"x","1"},{"y","1"}}, 2.0)));
    EXPECT_EQUAL(calculate_expression(model_match_expr, params), 42.0);
}

TEST("SMOKETEST - require that matrix product benchmark expression produces expected result") {
    Params params;
    params.add("query",    make_tensor(TensorSpec("tensor(x{})")
                                       .add({{"x","0"}}, 1.0)
                                       .add({{"x","1"}}, 0.0)));
    params.add("document", make_tensor(TensorSpec("tensor(x{})")
                                       .add({{"x","0"}}, 0.0)
                                       .add({{"x","1"}}, 2.0)));
    params.add("model",    make_tensor(TensorSpec("tensor(x{},y{})")
                                       .add({{"x","0"},{"y","0"}}, 1.0)
                                       .add({{"x","0"},{"y","1"}}, 2.0)
                                       .add({{"x","1"},{"y","0"}}, 3.0)
                                       .add({{"x","1"},{"y","1"}}, 4.0)));
    EXPECT_EQUAL(calculate_expression(matrix_product_expr, params), 17.0);
}

//-----------------------------------------------------------------------------

struct DummyBuilder : TensorBuilder {
    Dimension define_dimension(const vespalib::string &) override { return 0; }
    TensorBuilder &add_label(Dimension, const vespalib::string &) override { return *this; }
    TensorBuilder &add_cell(double) override { return *this; }
    tensor::Tensor::UP build() override { return tensor::Tensor::UP(); }
};


struct DummyDenseTensorBuilder
{
    using Dimension = TensorBuilder::Dimension;
    Dimension defineDimension(const vespalib::string &, size_t) { return 0; }
    DummyDenseTensorBuilder &addLabel(Dimension, size_t) { return *this; }
    DummyDenseTensorBuilder &addCell(double) { return *this; }
    tensor::Tensor::UP build() { return tensor::Tensor::UP(); }
};

struct DimensionSpec {
    vespalib::string name;
    size_t count;
    size_t offset;
    DimensionSpec(const vespalib::string &name_in, size_t count_in, size_t offset_in = 0)
        : name(name_in), count(count_in), offset(offset_in) {}
};

struct StringBinding {
    TensorBuilder::Dimension dimension;
    vespalib::string label;
    StringBinding(TensorBuilder &builder, const DimensionSpec &dimension_in)
        : dimension(builder.define_dimension(dimension_in.name)),
          label()
    {
    }
    void set_label(size_t id) {
        label = vespalib::make_string("%zu", id);
    }
    static void add_cell(TensorBuilder &builder, double value) {
        builder.add_cell(value);
    }
    void add_label(TensorBuilder &builder) const {
        builder.add_label(dimension, label);
    }
};

struct NumberBinding {
    TensorBuilder::Dimension dimension;
    size_t label;
    template <typename Builder>
    NumberBinding(Builder &builder, const DimensionSpec &dimension_in)
        : dimension(builder.defineDimension(dimension_in.name,
                                            dimension_in.offset +
                                            dimension_in.count)),
          label()
    {
    }
    void set_label(size_t id) {
        label = id;
    }
    template <typename Builder>
    static void add_cell(Builder &builder, double value) {
        builder.addCell(value);
    }
    template <typename Builder>
    void add_label(Builder &builder) const {
        builder.addLabel(dimension, label);
    }
};


template <typename Builder, typename Binding>
void build_tensor(Builder &builder, const std::vector<DimensionSpec> &dimensions,
                  std::vector<Binding> &bindings)
{
    if (bindings.size() == dimensions.size()) {
        for (const auto &bound: bindings) {
            bound.add_label(builder);
        }
        Binding::add_cell(builder, 42);
    } else {
        const auto &spec = dimensions[bindings.size()];
        bindings.emplace_back(builder, spec);
        for (size_t i = 0; i < spec.count; ++i) {
            bindings.back().set_label(spec.offset + i);
            build_tensor(builder, dimensions, bindings);
        }
        bindings.pop_back();
    }
}

template <typename Builder, typename IBuilder, typename Binding>
tensor::Tensor::UP make_tensor_impl(const std::vector<DimensionSpec> &dimensions) {
    Builder builder;
    std::vector<Binding> bindings;
    bindings.reserve(dimensions.size());
    build_tensor<IBuilder, Binding>(builder, dimensions, bindings);
    return builder.build();
}

//-----------------------------------------------------------------------------

enum class BuilderType { DUMMY, SPARSE, NUMBERDUMMY,
        DENSE };

const BuilderType DUMMY = BuilderType::DUMMY;
const BuilderType SPARSE = BuilderType::SPARSE;
const BuilderType NUMBERDUMMY = BuilderType::NUMBERDUMMY;
const BuilderType DENSE = BuilderType::DENSE;

const char *name(BuilderType type) {
    switch (type) {
    case BuilderType::DUMMY:   return "  dummy";
    case BuilderType::SPARSE: return "sparse";
    case BuilderType::NUMBERDUMMY: return "numberdummy";
    case BuilderType::DENSE: return "dense";
    }
    abort();
}

tensor::Tensor::UP make_tensor(BuilderType type, const std::vector<DimensionSpec> &dimensions) {
    switch (type) {
    case BuilderType::DUMMY:
        return make_tensor_impl<DummyBuilder, TensorBuilder, StringBinding>
            (dimensions);
    case BuilderType::SPARSE:
        return make_tensor_impl<SparseTensorBuilder, TensorBuilder,
            StringBinding>(dimensions);
    case BuilderType::NUMBERDUMMY:
        return make_tensor_impl<DummyDenseTensorBuilder,
            DummyDenseTensorBuilder, NumberBinding>(dimensions);
    case BuilderType::DENSE:
        return make_tensor_impl<DenseTensorBuilder, DenseTensorBuilder,
            NumberBinding>(dimensions);
    }
    abort();
}

//-----------------------------------------------------------------------------

struct BuildTask {
    BuilderType type;
    std::vector<DimensionSpec> spec;
    BuildTask(BuilderType type_in, const std::vector<DimensionSpec> &spec_in) : type(type_in), spec(spec_in) {}
    void operator()() { tensor::Tensor::UP tensor = make_tensor(type, spec); }
};

double benchmark_build_us(BuilderType type, const std::vector<DimensionSpec> &spec) {
    BuildTask build_task(type, spec);
    BuildTask dummy_task((type == DENSE) ? NUMBERDUMMY : DUMMY, spec);
    return BenchmarkTimer::benchmark(build_task, dummy_task, 5.0) * 1000.0 * 1000.0;
}

TEST("benchmark create/destroy time for 1d tensors") {
    for (size_t size: {5, 10, 25, 50, 100, 250, 500}) {
        for (auto type: {SPARSE, DENSE}) {
            double time_us = benchmark_build_us(type, {DimensionSpec("x", size)});
            fprintf(stderr, "-- 1d tensor create/destroy (%s) with size %zu: %g us\n", name(type), size, time_us);
        }
    }
}

TEST("benchmark create/destroy time for 2d tensors") {
    for (size_t size: {5, 10, 25, 50, 100}) {
        for (auto type: {SPARSE, DENSE}) {
            double time_us = benchmark_build_us(type, {DimensionSpec("x", size), DimensionSpec("y", size)});
            fprintf(stderr, "-- 2d tensor create/destroy (%s) with size %zux%zu: %g us\n", name(type), size, size, time_us);
        }
    }
}

//-----------------------------------------------------------------------------

TEST("benchmark dot product using match") {
    for (size_t size: {10, 25, 50, 100, 250}) {
        for (auto type: {SPARSE, DENSE}) {
            Params params;
            params.add("query",    make_tensor(type, {DimensionSpec("x", size)}));
            params.add("document", make_tensor(type, {DimensionSpec("x", size)}));
            double time_us = benchmark_expression_us(dot_product_match_expr, params);
            fprintf(stderr, "-- dot product (%s) using match %zu vs %zu: %g us\n", name(type), size, size, time_us);
        }
    }
}

TEST("benchmark dot product using multiply") {
    for (size_t size: {10, 25, 50, 100, 250}) {
        for (auto type: {SPARSE, DENSE}) {
            Params params;
            params.add("query",    make_tensor(type, {DimensionSpec("x", size)}));
            params.add("document", make_tensor(type, {DimensionSpec("x", size)}));
            double time_us = benchmark_expression_us(dot_product_multiply_expr, params);
            fprintf(stderr, "-- dot product (%s) using multiply %zu vs %zu: %g us\n", name(type), size, size, time_us);
        }
    }
}

TEST("benchmark model match") {
    for (size_t model_size: {25, 50, 100}) {
        for (size_t vector_size: {5, 10, 25, 50, 100}) {
            if (vector_size <= model_size) {
                for (auto type: {SPARSE}) {
                    Params params;
                    params.add("query",    make_tensor(type, {DimensionSpec("x", vector_size)}));
                    params.add("document", make_tensor(type, {DimensionSpec("y", vector_size)}));
                    params.add("model",    make_tensor(type, {DimensionSpec("x", model_size), DimensionSpec("y", model_size)}));
                    double time_us = benchmark_expression_us(model_match_expr, params);
                    fprintf(stderr, "-- model match (%s) %zu * %zu vs %zux%zu: %g us\n", name(type), vector_size, vector_size, model_size, model_size, time_us);
                }
            }
        }
    }
}

TEST("benchmark matrix product") {
    for (size_t vector_size: {5, 10, 25, 50}) {
        size_t matrix_size = vector_size * 2;
        for (auto type: {SPARSE, DENSE}) {
            Params params;
            params.add("query",    make_tensor(type, {DimensionSpec("x", matrix_size)}));
            params.add("document", make_tensor(type, {DimensionSpec("x", matrix_size)}));
            params.add("model",    make_tensor(type, {DimensionSpec("x", matrix_size), DimensionSpec("y", matrix_size)}));
            double time_us = benchmark_expression_us(matrix_product_expr, params);
            fprintf(stderr, "-- matrix product (%s) %zu + %zu vs %zux%zu: %g us\n", name(type), vector_size, vector_size, matrix_size, matrix_size, time_us);
        }
    }
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
