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
// production. Also, we use the multiply operation for join and sum
// operation for reduce since those are the most optimized operations
// across all implementations. When benchmarking different
// implementations against each other, a smoke test is performed by
// verifying that all implementations produce the same result.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/instruction/generic_concat.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/instruction/generic_reduce.h>
#include <vespa/eval/instruction/generic_rename.h>
#include <vespa/eval/instruction/generic_map.h>
#include <vespa/eval/instruction/generic_merge.h>
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
    EngineOrFactory engine;
    Impl(size_t order_in, const vespalib::string &name_in, const vespalib::string &short_name_in, EngineOrFactory engine_in)
        : order(order_in), name(name_in), short_name(short_name_in), engine(engine_in) {}
    Value::UP create_value(const TensorSpec &spec) const { return engine.from_spec(spec); }
    TensorSpec create_spec(const Value &value) const { return engine.to_spec(value); }
    Instruction create_join(const ValueType &lhs, const ValueType &rhs, operation::op2_t function, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &rhs_node = tensor_function::inject(rhs, 1, stash);
        const auto &join_node = tensor_function::join(lhs_node, rhs_node, function, stash); 
        return join_node.compile_self(engine, stash);
    }
    Instruction create_reduce(const ValueType &lhs, Aggr aggr, const std::vector<vespalib::string> &dims, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &reduce_node = tensor_function::reduce(lhs_node, aggr, dims, stash); 
        return reduce_node.compile_self(engine, stash);
    }
    Instruction create_rename(const ValueType &lhs, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &rename_node = tensor_function::rename(lhs_node, from, to, stash);
        return rename_node.compile_self(engine, stash);
    }
    Instruction create_merge(const ValueType &lhs, const ValueType &rhs, operation::op2_t function, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &rhs_node = tensor_function::inject(rhs, 1, stash);
        const auto &merge_node = tensor_function::merge(lhs_node, rhs_node, function, stash); 
        return merge_node.compile_self(engine, stash);
    }
    Instruction create_concat(const ValueType &lhs, const ValueType &rhs, const std::string &dimension, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &rhs_node = tensor_function::inject(rhs, 1, stash);
        const auto &concat_node = tensor_function::concat(lhs_node, rhs_node, dimension, stash); 
        return concat_node.compile_self(engine, stash);
    }
    Instruction create_map(const ValueType &lhs, operation::op1_t function, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &map_node = tensor_function::map(lhs_node, function, stash); 
        return map_node.compile_self(engine, stash);
    }
};

//-----------------------------------------------------------------------------

Impl   simple_tensor_engine_impl(5, " SimpleTensorEngine", " SimpleT", SimpleTensorEngine::ref());
Impl  default_tensor_engine_impl(1, "DefaultTensorEngine", "OLD PROD", DefaultTensorEngine::ref());
Impl           simple_value_impl(2, "        SimpleValue", " SimpleV", SimpleValueBuilderFactory::get());
Impl             fast_value_impl(0, "          FastValue", "NEW PROD", FastValueBuilderFactory::get());
Impl    packed_mixed_tensor_impl(4, "  PackedMixedTensor", "  Packed", PackedMixedTensorBuilderFactory::get());
Impl   default_tensor_value_impl(3, "       DefaultValue", "DefaultV", DefaultValueBuilderFactory::get());
vespalib::string                              short_header("--------");

constexpr double budget = 5.0;
constexpr double best_limit = 0.95; // everything within 95% of best performance gets a star
constexpr double bad_limit = 0.90; // BAD: new prod has performance lower than 90% of old prod
constexpr double good_limit = 1.10; // GOOD: new prod has performance higher than 110% of old prod

std::vector<CREF<Impl>> impl_list = {simple_tensor_engine_impl,
                                     default_tensor_engine_impl,
                                     simple_value_impl,
                                     fast_value_impl,
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
    void print_header(const vespalib::string &desc) const {
        for (const auto &name: short_names) {
            fprintf(stderr, "|%s", name.c_str());
        }
        fprintf(stderr, "| %s Benchmark cases\n", desc.c_str());
    }
    void print_trailer() const {
        for (size_t i = 0; i < short_names.size(); ++i) {
            fprintf(stderr, "+%s", short_header.c_str());
        }
        fprintf(stderr, "+------------------------------------------------\n");
    }
};

struct BenchmarkResult {
    vespalib::string desc;
    std::optional<double> ref_time;
    std::vector<double> relative_perf;
    double star_rating;
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
        star_rating = 0.0;
        for (double &perf: relative_perf) {
            perf = ref_time.value() / perf;
            star_rating = std::max(star_rating, perf);
        }
        star_rating *= best_limit;
    }
    void print() const {
        for (double perf: relative_perf) {
            if (perf > star_rating) {
                fprintf(stderr, "|*%7.2f", perf);
            } else {
                fprintf(stderr, "| %7.2f", perf);
            }
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
        : impl(impl_in), values(), stack(), single(impl.engine, op)
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

void benchmark_reduce(const vespalib::string &desc, const TensorSpec &lhs,
                      Aggr aggr, const std::vector<vespalib::string> &dims)
{
    Stash stash;
    ValueType lhs_type = ValueType::from_spec(lhs.type());
    ValueType res_type = lhs_type.reduce(dims);
    ASSERT_FALSE(lhs_type.is_error());
    ASSERT_FALSE(res_type.is_error());
    std::vector<EvalOp::UP> list;
    for (const Impl &impl: impl_list) {
        auto op = impl.create_reduce(lhs_type, aggr, dims, stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs});
        list.push_back(std::make_unique<EvalOp>(op, stack_spec, impl));
    }
    benchmark(desc, list);
}

//-----------------------------------------------------------------------------

void benchmark_rename(const vespalib::string &desc, const TensorSpec &lhs,
                      const std::vector<vespalib::string> &from,
                      const std::vector<vespalib::string> &to)
{
    Stash stash;
    ValueType lhs_type = ValueType::from_spec(lhs.type());
    ValueType res_type = lhs_type.rename(from, to);
    ASSERT_FALSE(lhs_type.is_error());
    ASSERT_FALSE(res_type.is_error());
    std::vector<EvalOp::UP> list;
    for (const Impl &impl: impl_list) {
        auto op = impl.create_rename(lhs_type, from, to, stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs});
        list.push_back(std::make_unique<EvalOp>(op, stack_spec, impl));
    }
    benchmark(desc, list);
}

//-----------------------------------------------------------------------------

void benchmark_merge(const vespalib::string &desc, const TensorSpec &lhs,
                     const TensorSpec &rhs, operation::op2_t function)
{
    Stash stash;
    ValueType lhs_type = ValueType::from_spec(lhs.type());
    ValueType rhs_type = ValueType::from_spec(rhs.type());
    ValueType res_type = ValueType::merge(lhs_type, rhs_type);
    ASSERT_FALSE(lhs_type.is_error());
    ASSERT_FALSE(rhs_type.is_error());
    ASSERT_FALSE(res_type.is_error());
    std::vector<EvalOp::UP> list;
    for (const Impl &impl: impl_list) {
        auto op = impl.create_merge(lhs_type, rhs_type, function, stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs, rhs});
        list.push_back(std::make_unique<EvalOp>(op, stack_spec, impl));
    }
    benchmark(desc, list);
}

//-----------------------------------------------------------------------------

void benchmark_map(const vespalib::string &desc, const TensorSpec &lhs, operation::op1_t function)
{
    Stash stash;
    ValueType lhs_type = ValueType::from_spec(lhs.type());
    ASSERT_FALSE(lhs_type.is_error());
    std::vector<EvalOp::UP> list;
    for (const Impl &impl: impl_list) {
        auto op = impl.create_map(lhs_type, function, stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs});
        list.push_back(std::make_unique<EvalOp>(op, stack_spec, impl));
    }
    benchmark(desc, list);
}

//-----------------------------------------------------------------------------

void benchmark_concat(const vespalib::string &desc, const TensorSpec &lhs,
                      const TensorSpec &rhs, const std::string &dimension)
{
    Stash stash;
    ValueType lhs_type = ValueType::from_spec(lhs.type());
    ValueType rhs_type = ValueType::from_spec(rhs.type());
    ValueType res_type = ValueType::concat(lhs_type, rhs_type, dimension);
    ASSERT_FALSE(lhs_type.is_error());
    ASSERT_FALSE(rhs_type.is_error());
    ASSERT_FALSE(res_type.is_error());
    std::vector<EvalOp::UP> list;
    for (const Impl &impl: impl_list) {
        auto op = impl.create_concat(lhs_type, rhs_type, dimension, stash);
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
TensorSpec make_matrix(const D &d1, const D &d2, double seq) { return make_spec(seq, d1, d2); }
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

TEST(DenseConcat, small_vectors) {
    auto lhs = make_vector(D::idx("x", 10), 1.0);
    auto rhs = make_vector(D::idx("x", 10), 2.0);
    benchmark_concat("small dense vector append concat", lhs, rhs, "x");
}

TEST(DenseConcat, cross_vectors) {
    auto lhs = make_vector(D::idx("x", 10), 1.0);
    auto rhs = make_vector(D::idx("x", 10), 2.0);
    benchmark_concat("small dense vector cross concat", lhs, rhs, "y");
}

TEST(DenseConcat, cube_and_vector) {
    auto lhs = make_cube(D::idx("a", 16), D::idx("b", 16), D::idx("c", 16), 1.0);
    auto rhs = make_vector(D::idx("a", 16), 42.0);
    benchmark_concat("cube vs vector concat", lhs, rhs, "a");
}

TEST(SparseConcat, small_vectors) {
    auto lhs = make_vector(D::map("x", 10, 1), 1.0);
    auto rhs = make_vector(D::map("x", 10, 2), 2.0);
    benchmark_concat("small sparse concat", lhs, rhs, "y");
}

TEST(MixedConcat, large_mixed_a) {
    auto lhs = make_cube(D::idx("a", 16), D::idx("b", 16), D::map("c", 16, 1), 1.0);
    auto rhs = make_cube(D::idx("a", 16), D::idx("b", 16), D::map("c", 16, 2), 2.0);
    benchmark_concat("mixed append concat a", lhs, rhs, "a");
}

TEST(MixedConcat, large_mixed_b) {
    auto lhs = make_cube(D::idx("a", 16), D::idx("b", 16), D::map("c", 16, 1), 1.0);
    auto rhs = make_cube(D::idx("a", 16), D::idx("b", 16), D::map("c", 16, 2), 2.0);
    benchmark_concat("mixed append concat b", lhs, rhs, "b");
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

TEST(SparseJoin, large_vectors) {
    auto lhs = make_vector(D::map("x", 1800, 1), 1.0);
    auto rhs = make_vector(D::map("x", 1000, 2), 2.0);
    benchmark_join("large sparse vector multiply", lhs, rhs, operation::Mul::f);
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

TEST(ReduceBench, dense_reduce) {
    auto lhs = make_cube(D::idx("a", 16), D::idx("b", 16), D::idx("c", 16), 1.0);
    benchmark_reduce("dense reduce inner", lhs, Aggr::SUM, {"c"});
    benchmark_reduce("dense reduce middle", lhs, Aggr::SUM, {"b"});
    benchmark_reduce("dense reduce outer", lhs, Aggr::SUM, {"a"});
    benchmark_reduce("dense multi-reduce inner", lhs, Aggr::SUM, {"b", "c"});
    benchmark_reduce("dense multi-reduce outer", lhs, Aggr::SUM, {"a", "b"});
    benchmark_reduce("dense multi-reduce outer-inner", lhs, Aggr::SUM, {"a", "c"});
    benchmark_reduce("dense reduce all", lhs, Aggr::SUM, {});
}

TEST(ReduceBench, sparse_reduce) {
    auto lhs = make_cube(D::map("a", 16, 1), D::map("b", 16, 1), D::map("c", 16, 1), 1.0);
    benchmark_reduce("sparse reduce inner", lhs, Aggr::SUM, {"c"});
    benchmark_reduce("sparse reduce middle", lhs, Aggr::SUM, {"b"});
    benchmark_reduce("sparse reduce outer", lhs, Aggr::SUM, {"a"});
    benchmark_reduce("sparse multi-reduce inner", lhs, Aggr::SUM, {"b", "c"});
    benchmark_reduce("sparse multi-reduce outer", lhs, Aggr::SUM, {"a", "b"});
    benchmark_reduce("sparse multi-reduce outer-inner", lhs, Aggr::SUM, {"a", "c"});
    benchmark_reduce("sparse reduce all", lhs, Aggr::SUM, {});
}

TEST(ReduceBench, mixed_reduce) {
    auto lhs = make_spec(1.0, D::map("a", 4, 1), D::map("b", 4, 1), D::map("c", 4, 1),
                         D::idx("d", 4), D::idx("e", 4), D::idx("f", 4));
    benchmark_reduce("mixed reduce middle dense", lhs, Aggr::SUM, {"e"});
    benchmark_reduce("mixed reduce middle sparse", lhs, Aggr::SUM, {"b"});
    benchmark_reduce("mixed reduce middle sparse/dense", lhs, Aggr::SUM, {"b", "e"});
    benchmark_reduce("mixed reduce all dense", lhs, Aggr::SUM, {"d", "e", "f"});
    benchmark_reduce("mixed reduce all sparse", lhs, Aggr::SUM, {"a", "b", "c"});
    benchmark_reduce("mixed reduce all", lhs, Aggr::SUM, {});
}

//-----------------------------------------------------------------------------

TEST(RenameBench, dense_rename) {
    auto lhs = make_matrix(D::idx("a", 64), D::idx("b", 64), 1.0);
    benchmark_rename("dense transpose", lhs, {"a", "b"}, {"b", "a"});
}

TEST(RenameBench, sparse_rename) {
    auto lhs = make_matrix(D::map("a", 64, 1), D::map("b", 64, 1), 1.0);
    benchmark_rename("sparse transpose", lhs, {"a", "b"}, {"b", "a"});
}

TEST(RenameBench, mixed_rename) {
    auto lhs = make_spec(1.0, D::map("a", 8, 1), D::map("b", 8, 1), D::idx("c", 8), D::idx("d", 8));
    benchmark_rename("mixed multi-transpose", lhs, {"a", "b", "c", "d"}, {"b", "a", "d", "c"});
}

//-----------------------------------------------------------------------------

TEST(MergeBench, dense_merge) {
    auto lhs = make_matrix(D::idx("a", 64), D::idx("b", 64), 1.0);
    auto rhs = make_matrix(D::idx("a", 64), D::idx("b", 64), 2.0);
    benchmark_merge("dense merge", lhs, rhs, operation::Max::f);
}

TEST(MergeBench, sparse_merge_big_small) {
    auto lhs = make_matrix(D::map("a", 64, 1), D::map("b", 64, 1), 1.0);
    auto rhs = make_matrix(D::map("a", 8, 1), D::map("b", 8, 1), 2.0);
    benchmark_merge("sparse merge big vs small", lhs, rhs, operation::Max::f);
}

TEST(MergeBench, sparse_merge_minimal_overlap) {
    auto lhs = make_matrix(D::map("a", 64, 11), D::map("b", 32, 11), 1.0);
    auto rhs = make_matrix(D::map("a", 32, 13), D::map("b", 64, 13), 2.0);
    benchmark_merge("sparse merge minimal overlap", lhs, rhs, operation::Max::f);
}

TEST(MergeBench, mixed_merge) {
    auto lhs = make_matrix(D::map("a", 64, 1), D::idx("b", 64), 1.0);
    auto rhs = make_matrix(D::map("a", 64, 2), D::idx("b", 64), 2.0);
    benchmark_merge("mixed merge", lhs, rhs, operation::Max::f);
}

//-----------------------------------------------------------------------------

TEST(MapBench, dense_map) {
    auto lhs = make_matrix(D::idx("a", 64), D::idx("b", 64), 1.75);
    benchmark_map("dense map", lhs, operation::Floor::f);
}

TEST(MapBench, sparse_map_small) {
    auto lhs = make_matrix(D::map("a", 4, 1), D::map("b", 4, 1), 1.75);
    benchmark_map("sparse map small", lhs, operation::Floor::f);
}

TEST(MapBench, sparse_map_big_small) {
    auto lhs = make_matrix(D::map("a", 64, 1), D::map("b", 64, 1), 1.75);
    benchmark_map("sparse map big", lhs, operation::Floor::f);
}

TEST(MapBench, mixed_map) {
    auto lhs = make_matrix(D::map("a", 64, 1), D::idx("b", 64), 1.75);
    benchmark_map("mixed map", lhs, operation::Floor::f);
}

//-----------------------------------------------------------------------------

void print_results(const vespalib::string &desc, const std::vector<BenchmarkResult> &results) {
    if (results.empty()) {
        return;
    }
    BenchmarkHeader header;
    header.print_trailer();
    header.print_header(desc);
    header.print_trailer();
    for (const auto &result: results) {
        result.print();
    }
    header.print_trailer();
}

void print_summary() {
    std::vector<BenchmarkResult> bad_results;
    std::vector<BenchmarkResult> neutral_results;
    std::vector<BenchmarkResult> good_results;
    std::sort(benchmark_results.begin(), benchmark_results.end(),
              [](const auto &a, const auto &b){ return (a.relative_perf[0] < b.relative_perf[0]); });
    for (const auto &result: benchmark_results) {
        double perf = result.relative_perf[0];
        if (perf < bad_limit) {
            bad_results.push_back(result);
        } else if (perf > good_limit) {
            good_results.push_back(result);
        } else {
            neutral_results.push_back(result);
        }
    }
    print_results("BAD", bad_results);
    print_results("NEUTRAL", neutral_results);
    print_results("GOOD", good_results);
}

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    int result = RUN_ALL_TESTS();
    print_summary();
    return result;
}
