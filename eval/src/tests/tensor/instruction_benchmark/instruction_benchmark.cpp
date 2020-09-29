// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Microbenchmark exploring performance differences between
// interpreted function instructions.

// This benchmark was initially written to measure the difference in
// performance between (old) instructions using the TensorEngine
// immediate API and (new) instructions using the Value API
// directly. Note that all previous optimizations for dense tensors
// are trivially transformed to use the Value API, and thus only the
// generic cases need to be compared. Specifically; we want to make
// sure join performance for sparse tensors with full dimensional
// overlap does not suffer too much. Also, we want to showcase an
// improvement in generic dense join and possibly also in sparse join
// with partial dimensional overlap. Benchmarks are done using float
// cells since this is what gives best overall performance in
// production. Also, we use the multiply operation since it is the
// most optimized operations across all implementations. When
// benchmarking different implementations against each other, a smoke
// test is performed by verifying that all implementations produce the
// same result.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/default_value_builder_factory.h>
#include <vespa/eval/tensor/mixed/packed_mixed_tensor_builder_factory.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>
#include <algorithm>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::tensor;
using namespace vespalib::eval::instruction;
using vespalib::make_string_short::fmt;

using Instruction = InterpretedFunction::Instruction;
using EvalSingle = InterpretedFunction::EvalSingle;

template <typename T> using CREF = std::reference_wrapper<const T>;

//-----------------------------------------------------------------------------

struct Impl {
    size_t order;
    vespalib::string name;
    vespalib::string short_name;
    Impl(size_t order_in, const vespalib::string &name_in, const vespalib::string &short_name_in)
        : order(order_in), name(name_in), short_name(short_name_in) {}
    virtual Value::UP create_value(const TensorSpec &spec) const = 0;
    virtual TensorSpec create_spec(const Value &value) const = 0;
    virtual Instruction create_join(const ValueType &lhs, const ValueType &rhs, operation::op2_t function, Stash &stash) const = 0;
    virtual const TensorEngine &engine() const { return SimpleTensorEngine::ref(); } // engine used by EvalSingle
    virtual ~Impl() {}
};

struct ValueImpl : Impl {
    const ValueBuilderFactory &my_factory;
    ValueImpl(size_t order_in, const vespalib::string &name_in, const vespalib::string &short_name_in, const ValueBuilderFactory &factory)
        : Impl(order_in, name_in, short_name_in), my_factory(factory) {}
    Value::UP create_value(const TensorSpec &spec) const override { return value_from_spec(spec, my_factory); }
    TensorSpec create_spec(const Value &value) const override { return spec_from_value(value); }
    Instruction create_join(const ValueType &lhs, const ValueType &rhs, operation::op2_t function, Stash &stash) const override {
        return GenericJoin::make_instruction(lhs, rhs, function, my_factory, stash);
    }
};

struct EngineImpl : Impl {
    const TensorEngine &my_engine;
    EngineImpl(size_t order_in, const vespalib::string &name_in, const vespalib::string &short_name_in, const TensorEngine &engine_in)
        : Impl(order_in, name_in, short_name_in), my_engine(engine_in) {}
    Value::UP create_value(const TensorSpec &spec) const override { return my_engine.from_spec(spec); }
    TensorSpec create_spec(const Value &value) const override { return my_engine.to_spec(value); }
    Instruction create_join(const ValueType &lhs, const ValueType &rhs, operation::op2_t function, Stash &stash) const override {
        // create a complete tensor function joining two parameters, but only compile the join instruction itself
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &rhs_node = tensor_function::inject(rhs, 1, stash);
        const auto &join_node = tensor_function::join(lhs_node, rhs_node, function, stash); 
        return join_node.compile_self(my_engine, stash);
    }
    const TensorEngine &engine() const override { return my_engine; }
};

//-----------------------------------------------------------------------------

EngineImpl  simple_tensor_engine_impl(4, " SimpleTensorEngine", " SimpleT", SimpleTensorEngine::ref());
EngineImpl default_tensor_engine_impl(1, "DefaultTensorEngine", "OLD PROD", DefaultTensorEngine::ref());
ValueImpl           simple_value_impl(3, "        SimpleValue", " SimpleV", SimpleValueBuilderFactory::get());
ValueImpl    packed_mixed_tensor_impl(2, "  PackedMixedTensor", "  Packed", PackedMixedTensorBuilderFactory::get());
ValueImpl   default_tensor_value_impl(0, "       DefaultValue", "NEW PROD", DefaultValueBuilderFactory::get());
vespalib::string                                   short_header("--------");

double budget = 5.0;
std::vector<CREF<Impl>> impl_list = {simple_tensor_engine_impl,
                                     default_tensor_engine_impl,
                                     simple_value_impl,
                                     packed_mixed_tensor_impl,
                                     default_tensor_value_impl};

//-----------------------------------------------------------------------------

struct BenchmarkHeader {
    std::vector<vespalib::string> short_names;
    BenchmarkHeader() : short_names() {
        short_names.resize(impl_list.size());
        for (const Impl &impl: impl_list) {
            short_names[impl.order] = impl.short_name;
        }
    }
    void print_trailer() const {
        for (size_t i = 0; i < short_names.size(); ++i) {
            fprintf(stderr, "+%s", short_header.c_str());
        }
        fprintf(stderr, "+------------------------------------------------\n");
    }
    void print() const {
        for (const auto &name: short_names) {
            fprintf(stderr, "|%s", name.c_str());
        }
        fprintf(stderr, "| Benchmark description\n");
        print_trailer();
    }
};

struct BenchmarkResult {
    vespalib::string desc;
    std::optional<double> ref_time;
    std::vector<double> relative_perf;
    BenchmarkResult(const vespalib::string &desc_in, size_t num_values)
        : desc(desc_in), ref_time(std::nullopt), relative_perf(num_values, 0.0) {}
    ~BenchmarkResult();
    void sample(size_t order, double time) {
        relative_perf[order] = time;
        if (order == 1) {
            if (ref_time.has_value()) {
                ref_time = std::min(ref_time.value(), time);
            } else {
                ref_time = time;
            }
        }
    }
    void normalize() {
        for (double &perf: relative_perf) {
            perf = ref_time.value() / perf;
        }
    }
    void print() const {
        for (double perf: relative_perf) {
            fprintf(stderr, "|%8.2f", perf);
        }
        fprintf(stderr, "| %s\n", desc.c_str());
    }
};
BenchmarkResult::~BenchmarkResult() = default;

std::vector<BenchmarkResult> benchmark_results;

//-----------------------------------------------------------------------------

struct EvalOp {
    using UP = std::unique_ptr<EvalOp>;
    const Impl              &impl;
    std::vector<Value::UP>   values;
    std::vector<Value::CREF> stack;
    EvalSingle               single;
    EvalOp(const EvalOp &) = delete;
    EvalOp &operator=(const EvalOp &) = delete;
    EvalOp(Instruction op, const std::vector<CREF<TensorSpec>> &stack_spec, const Impl &impl_in)
        : impl(impl_in), values(), stack(), single(impl.engine(), op)
    {
        for (const TensorSpec &spec: stack_spec) {
            values.push_back(impl.create_value(spec));
        }
        for (const auto &value: values) {
            stack.push_back(*value.get());
        }
    }
    TensorSpec result() { return impl.create_spec(single.eval(stack)); }
    double estimate_cost_us() {
        auto actual = [&](){ single.eval(stack); };
        return BenchmarkTimer::benchmark(actual, budget) * 1000.0 * 1000.0;
    }
};

//-----------------------------------------------------------------------------

void benchmark(const vespalib::string &desc, const std::vector<EvalOp::UP> &list) {
    fprintf(stderr, "--------------------------------------------------------\n");
    fprintf(stderr, "Benchmark Case: [%s]\n", desc.c_str());
    std::optional<TensorSpec> expect = std::nullopt;
    for (const auto &eval: list) {
        if (expect.has_value()) {
            ASSERT_EQ(eval->result(), expect.value());
        } else {
            expect = eval->result();
        }
    }
    BenchmarkResult result(desc, list.size());
    for (const auto &eval: list) {
        double time = eval->estimate_cost_us();
        result.sample(eval->impl.order, time);
        fprintf(stderr, "    %s(%s): %10.3f us\n", eval->impl.name.c_str(), eval->impl.short_name.c_str(), time);
    }
    result.normalize();
    benchmark_results.push_back(result);
    fprintf(stderr, "--------------------------------------------------------\n");
}

//-----------------------------------------------------------------------------

void benchmark_join(const vespalib::string &desc, const TensorSpec &lhs,
                    const TensorSpec &rhs, operation::op2_t function)
{
    Stash stash;
    ValueType lhs_type = ValueType::from_spec(lhs.type());
    ValueType rhs_type = ValueType::from_spec(rhs.type());
    ValueType res_type = ValueType::join(lhs_type, rhs_type);
    ASSERT_FALSE(lhs_type.is_error());
    ASSERT_FALSE(rhs_type.is_error());
    ASSERT_FALSE(res_type.is_error());
    std::vector<EvalOp::UP> list;
    for (const Impl &impl: impl_list) {
        auto op = impl.create_join(lhs_type, rhs_type, function, stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs, rhs});
        list.push_back(std::make_unique<EvalOp>(op, stack_spec, impl));
    }
    benchmark(desc, list);
}

//-----------------------------------------------------------------------------

struct D {
    vespalib::string name;
    bool mapped;
    size_t size;
    size_t stride;
    static D map(const vespalib::string &name_in, size_t size_in, size_t stride_in) { return D{name_in, true, size_in, stride_in}; }
    static D idx(const vespalib::string &name_in, size_t size_in) { return D{name_in, false, size_in, 1}; }
    operator ValueType::Dimension() const {
        if (mapped) {
            return ValueType::Dimension(name);
        } else {
            return ValueType::Dimension(name, size);
        }
    }
    TensorSpec::Label operator()(size_t idx) const {
        if (mapped) {
            return TensorSpec::Label(fmt("label_%zu", idx));
        } else {
            return TensorSpec::Label(idx);
        }
    }
};

void add_cells(TensorSpec &spec, double &seq, TensorSpec::Address addr) {
    spec.add(addr, seq);
    seq += 1.0;
}

template <typename ...Ds> void add_cells(TensorSpec &spec, double &seq, TensorSpec::Address addr, const D &d, const Ds &...ds) {
    for (size_t i = 0, idx = 0; i < d.size; ++i, idx += d.stride) {
        addr.insert_or_assign(d.name, d(idx));
        add_cells(spec, seq, addr, ds...);
    }
}

template <typename ...Ds> TensorSpec make_spec(double seq, const Ds &...ds) {
    TensorSpec spec(ValueType::tensor_type({ds...}, ValueType::CellType::FLOAT).to_spec());
    add_cells(spec, seq, TensorSpec::Address(), ds...);
    return spec;
}

TensorSpec make_vector(const D &d1, double seq) { return make_spec(seq, d1); }
TensorSpec make_cube(const D &d1, const D &d2, const D &d3, double seq) { return make_spec(seq, d1, d2, d3); }

//-----------------------------------------------------------------------------

TEST(MakeInputTest, print_some_test_input) {
    auto number = make_spec(5.0);
    auto sparse = make_vector(D::map("x", 5, 3), 1.0);
    auto dense = make_vector(D::idx("x", 5), 10.0);
    auto mixed = make_cube(D::map("x", 3, 7), D::idx("y", 2), D::idx("z", 2), 100.0);
    fprintf(stderr, "--------------------------------------------------------\n");
    fprintf(stderr, "simple number: %s\n", number.to_string().c_str());
    fprintf(stderr, "sparse vector: %s\n", sparse.to_string().c_str());
    fprintf(stderr, "dense vector: %s\n", dense.to_string().c_str());
    fprintf(stderr, "mixed cube: %s\n", mixed.to_string().c_str());
    fprintf(stderr, "--------------------------------------------------------\n");
}

//-----------------------------------------------------------------------------

TEST(NumberJoin, plain_op2) {
    auto lhs = make_spec(2.0);
    auto rhs = make_spec(3.0);
    benchmark_join("simple numbers multiply", lhs, rhs, operation::Mul::f);
}

//-----------------------------------------------------------------------------

TEST(DenseJoin, small_vectors) {
    auto lhs = make_vector(D::idx("x", 10), 1.0);
    auto rhs = make_vector(D::idx("x", 10), 2.0);
    benchmark_join("small dense vector multiply", lhs, rhs, operation::Mul::f);
}

TEST(DenseJoin, full_overlap) {
    auto lhs = make_cube(D::idx("a", 16), D::idx("b", 16), D::idx("c", 16), 1.0);
    auto rhs = make_cube(D::idx("a", 16), D::idx("b", 16), D::idx("c", 16), 2.0);
    benchmark_join("dense full overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(DenseJoin, partial_overlap) {
    auto lhs = make_cube(D::idx("a", 8), D::idx("c", 8), D::idx("d", 8), 1.0);
    auto rhs = make_cube(D::idx("b", 8), D::idx("c", 8), D::idx("d", 8), 2.0);
    benchmark_join("dense partial overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(DenseJoin, no_overlap) {
    auto lhs = make_cube(D::idx("a", 4), D::idx("e", 4), D::idx("f", 4), 1.0);
    auto rhs = make_cube(D::idx("b", 4), D::idx("c", 4), D::idx("d", 4), 2.0);
    benchmark_join("dense no overlap multiply", lhs, rhs, operation::Mul::f);
}

//-----------------------------------------------------------------------------

TEST(SparseJoin, small_vectors) {
    auto lhs = make_vector(D::map("x", 10, 1), 1.0);
    auto rhs = make_vector(D::map("x", 10, 2), 2.0);
    benchmark_join("small sparse vector multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, full_overlap) {
    auto lhs = make_cube(D::map("a", 16, 1), D::map("b", 16, 1), D::map("c", 16, 1), 1.0);
    auto rhs = make_cube(D::map("a", 16, 2), D::map("b", 16, 2), D::map("c", 16, 2), 2.0);
    benchmark_join("sparse full overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, full_overlap_big_vs_small) {
    auto lhs = make_cube(D::map("a", 16, 1), D::map("b", 16, 1), D::map("c", 16, 1), 1.0);
    auto rhs = make_cube(D::map("a", 2, 1), D::map("b", 2, 1), D::map("c", 2, 1), 2.0);
    benchmark_join("sparse full overlap big vs small multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, partial_overlap) {
    auto lhs = make_cube(D::map("a", 8, 1), D::map("c", 8, 1), D::map("d", 8, 1), 1.0);
    auto rhs = make_cube(D::map("b", 8, 2), D::map("c", 8, 2), D::map("d", 8, 2), 2.0);
    benchmark_join("sparse partial overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, no_overlap) {
    auto lhs = make_cube(D::map("a", 4, 1), D::map("e", 4, 1), D::map("f", 4, 1), 1.0);
    auto rhs = make_cube(D::map("b", 4, 1), D::map("c", 4, 1), D::map("d", 4, 1), 2.0);
    benchmark_join("sparse no overlap multiply", lhs, rhs, operation::Mul::f);
}

//-----------------------------------------------------------------------------

TEST(MixedJoin, full_overlap) {
    auto lhs = make_cube(D::map("a", 16, 1), D::map("b", 16, 1), D::idx("c", 16), 1.0);
    auto rhs = make_cube(D::map("a", 16, 2), D::map("b", 16, 2), D::idx("c", 16), 2.0);
    benchmark_join("mixed full overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(MixedJoin, partial_sparse_overlap) {
    auto lhs = make_cube(D::map("a", 8, 1), D::map("c", 8, 1), D::idx("d", 8), 1.0);
    auto rhs = make_cube(D::map("b", 8, 2), D::map("c", 8, 2), D::idx("d", 8), 2.0);
    benchmark_join("mixed partial sparse overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(MixedJoin, no_overlap) {
    auto lhs = make_cube(D::map("a", 4, 1), D::map("e", 4, 1), D::idx("f", 4), 1.0);
    auto rhs = make_cube(D::map("b", 4, 1), D::map("c", 4, 1), D::idx("d", 4), 2.0);
    benchmark_join("mixed no overlap multiply", lhs, rhs, operation::Mul::f);
}

//-----------------------------------------------------------------------------

TEST(PrintResults, print_results) {
    BenchmarkHeader header;
    std::sort(benchmark_results.begin(), benchmark_results.end(),
              [](const auto &a, const auto &b){ return (a.relative_perf[0] < b.relative_perf[0]); });
    header.print();
    for (const auto &result: benchmark_results) {
        result.print();
    }
    header.print_trailer();
}

GTEST_MAIN_RUN_ALL_TESTS()
