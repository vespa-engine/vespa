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

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/optimize_tensor_function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_concat.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/instruction/generic_map.h>
#include <vespa/eval/instruction/generic_merge.h>
#include <vespa/eval/instruction/generic_reduce.h>
#include <vespa/eval/instruction/generic_rename.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <optional>
#include <algorithm>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using vespalib::make_string_short::fmt;

using vespalib::slime::JsonFormat;

using Instruction = InterpretedFunction::Instruction;
using EvalSingle = InterpretedFunction::EvalSingle;

template <typename T> using CREF = std::reference_wrapper<const T>;

//-----------------------------------------------------------------------------

TensorSpec NUM(double value) { return test::GenSpec(value).gen(); }
test::GenSpec GS(double bias) { return test::GenSpec(bias).cells_float(); }

//-----------------------------------------------------------------------------

// helper class used to set up peek instructions
struct MyPeekSpec {
    bool is_dynamic;
    std::map<vespalib::string,size_t> spec;
    MyPeekSpec(bool is_dynamic_in) : is_dynamic(is_dynamic_in), spec() {}
    MyPeekSpec &add(const vespalib::string &dim, size_t index) {
        auto [ignore, was_inserted] = spec.emplace(dim, index);
        assert(was_inserted);
        return *this;
    }
};
MyPeekSpec dynamic_peek() { return MyPeekSpec(true); }
MyPeekSpec verbatim_peek() { return MyPeekSpec(false); }

//-----------------------------------------------------------------------------

struct MultiOpParam {
    std::vector<Instruction> list;
};

void my_multi_instruction_op(InterpretedFunction::State &state, uint64_t param_in) {
    const auto &param = *(MultiOpParam*)(param_in);
    for (const auto &item: param.list) {
        item.perform(state);
    }
}

void collect_op1_chain(const TensorFunction &node, const ValueBuilderFactory &factory, Stash &stash, std::vector<Instruction> &list) {
    if (auto op1 = as<tensor_function::Op1>(node)) {
        collect_op1_chain(op1->child(), factory, stash, list);
        list.push_back(node.compile_self(factory, stash));
    }
}

Instruction compile_op1_chain(const TensorFunction &node, const ValueBuilderFactory &factory, Stash &stash) {
    auto &param = stash.create<MultiOpParam>();
    collect_op1_chain(node, factory, stash, param.list);
    return {my_multi_instruction_op,(uint64_t)(&param)};
}

//-----------------------------------------------------------------------------

struct Impl {
    size_t order;
    vespalib::string name;
    vespalib::string short_name;
    const ValueBuilderFactory &factory;
    bool optimize;
    Impl(size_t order_in, const vespalib::string &name_in, const vespalib::string &short_name_in, const ValueBuilderFactory &factory_in, bool optimize_in)
        : order(order_in), name(name_in), short_name(short_name_in), factory(factory_in), optimize(optimize_in) {}
    Value::UP create_value(const TensorSpec &spec) const { return value_from_spec(spec, factory); }
    TensorSpec create_spec(const Value &value) const { return spec_from_value(value); }
    Instruction create_join(const ValueType &lhs, const ValueType &rhs, operation::op2_t function, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &rhs_node = tensor_function::inject(rhs, 1, stash);
        const auto &join_node = tensor_function::join(lhs_node, rhs_node, function, stash);
        const auto &node = optimize ? optimize_tensor_function(factory, join_node, stash) : join_node;
        return node.compile_self(factory, stash);
    }
    Instruction create_reduce(const ValueType &lhs, Aggr aggr, const std::vector<vespalib::string> &dims, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &reduce_node = tensor_function::reduce(lhs_node, aggr, dims, stash);
        const auto &node = optimize ? optimize_tensor_function(factory, reduce_node, stash) : reduce_node;
        // since reduce might be optimized into multiple chained
        // instructions, we need some extra magic to package these
        // instructions into a single compound instruction.
        return compile_op1_chain(node, factory, stash);
    }
    Instruction create_rename(const ValueType &lhs, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &rename_node = tensor_function::rename(lhs_node, from, to, stash);
        const auto &node = optimize ? optimize_tensor_function(factory, rename_node, stash) : rename_node;
        return node.compile_self(factory, stash);
    }
    Instruction create_merge(const ValueType &lhs, const ValueType &rhs, operation::op2_t function, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &rhs_node = tensor_function::inject(rhs, 1, stash);
        const auto &merge_node = tensor_function::merge(lhs_node, rhs_node, function, stash); 
        const auto &node = optimize ? optimize_tensor_function(factory, merge_node, stash) : merge_node;
        return node.compile_self(factory, stash);
    }
    Instruction create_concat(const ValueType &lhs, const ValueType &rhs, const std::string &dimension, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &rhs_node = tensor_function::inject(rhs, 1, stash);
        const auto &concat_node = tensor_function::concat(lhs_node, rhs_node, dimension, stash); 
        return concat_node.compile_self(factory, stash);
        const auto &node = optimize ? optimize_tensor_function(factory, concat_node, stash) : concat_node;
        return node.compile_self(factory, stash);
    }
    Instruction create_map(const ValueType &lhs, operation::op1_t function, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &lhs_node = tensor_function::inject(lhs, 0, stash);
        const auto &map_node = tensor_function::map(lhs_node, function, stash); 
        const auto &node = optimize ? optimize_tensor_function(factory, map_node, stash) : map_node;
        return node.compile_self(factory, stash);
    }
    Instruction create_tensor_create(const ValueType &proto_type, const TensorSpec &proto, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &my_double = tensor_function::inject(ValueType::double_type(), 0, stash);
        std::map<TensorSpec::Address,TensorFunction::CREF> spec;
        for (const auto &cell: proto.cells()) {
            spec.emplace(cell.first, my_double);
        }
        const auto &create_tensor_node = tensor_function::create(proto_type, spec, stash); 
        const auto &node = optimize ? optimize_tensor_function(factory, create_tensor_node, stash) : create_tensor_node;
        return node.compile_self(factory, stash);
    }
    Instruction create_tensor_lambda(const ValueType &type, const Function &function, const ValueType &p0_type, Stash &stash) const {
        std::vector<ValueType> arg_types(type.dimensions().size(), ValueType::double_type());
        arg_types.push_back(p0_type);
        NodeTypes types(function, arg_types);
        EXPECT_EQ(types.errors(), std::vector<vespalib::string>());
        const auto &tensor_lambda_node = tensor_function::lambda(type, {0}, function, std::move(types), stash);
        const auto &node = optimize ? optimize_tensor_function(factory, tensor_lambda_node, stash) : tensor_lambda_node;
        return node.compile_self(factory, stash);
    }
    Instruction create_tensor_peek(const ValueType &type, const MyPeekSpec &my_spec, Stash &stash) const {
        // create a complete tensor function, but only compile the relevant instruction
        const auto &my_param = tensor_function::inject(type, 0, stash);
        std::map<vespalib::string, std::variant<TensorSpec::Label, TensorFunction::CREF>> spec;
        if (my_spec.is_dynamic) {
            const auto &my_double = tensor_function::inject(ValueType::double_type(), 1, stash);
            for (const auto &entry: my_spec.spec) {
                spec.emplace(entry.first, my_double);
            }
        } else {
            for (const auto &entry: my_spec.spec) {
                size_t idx = type.dimension_index(entry.first);
                assert(idx != ValueType::Dimension::npos);
                if (type.dimensions()[idx].is_mapped()) {
                    spec.emplace(entry.first, TensorSpec::Label(fmt("%zu", entry.second)));
                } else {
                    spec.emplace(entry.first, TensorSpec::Label(entry.second));
                }
            }
        }
        const auto &peek_node = tensor_function::peek(my_param, spec, stash);
        const auto &node = optimize ? optimize_tensor_function(factory, peek_node, stash) : peek_node;
        return node.compile_self(factory, stash);
    }
};

//-----------------------------------------------------------------------------

Impl             optimized_fast_value_impl(0, "          Optimized FastValue", "NEW PROD", FastValueBuilderFactory::get(), true);
Impl                       fast_value_impl(1, "                    FastValue", "   FastV", FastValueBuilderFactory::get(), false);
Impl                     simple_value_impl(2, "                  SimpleValue", " SimpleV", SimpleValueBuilderFactory::get(), false);
vespalib::string                                                  short_header("--------");
vespalib::string                   ghost_name("       loaded from ghost.json");
vespalib::string                                              ghost_short_name("   ghost");

double budget = 5.0;
constexpr double best_limit = 0.95; // everything within 95% of best performance gets a star
constexpr double bad_limit = 0.90;  // BAD: optimized has performance lower than 90% of un-optimized
constexpr double good_limit = 1.10; // GOOD: optimized has performance higher than 110% of un-optimized

std::vector<CREF<Impl>> impl_list = {simple_value_impl,
                                     optimized_fast_value_impl,
                                     fast_value_impl};

Slime ghost; // loaded from 'ghost.json'
bool has_ghost = false;
Slime prod_result; // saved to 'result.json'

//-----------------------------------------------------------------------------

struct BenchmarkHeader {
    std::vector<vespalib::string> short_names;
    BenchmarkHeader() : short_names() {
        short_names.resize(impl_list.size());
        for (const Impl &impl: impl_list) {
            short_names[impl.order] = impl.short_name;
        }
        if (has_ghost) {
            short_names.push_back(ghost_short_name);
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
        if (order == 0) {
            prod_result.get().setDouble(desc, time);
            if (has_ghost && (relative_perf.size() == impl_list.size())) {
                double ghost_time = ghost.get()[desc].asDouble();
                size_t ghost_order = relative_perf.size();
                fprintf(stderr, "    %s(%s): %10.3f us\n", ghost_name.c_str(), ghost_short_name.c_str(), ghost_time);
                relative_perf.resize(ghost_order + 1);
                return sample(ghost_order, ghost_time);
            }
        } else if (order == 1) {
            ref_time = time;
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

void load_ghost(const vespalib::string &file_name) {
    MappedFileInput input(file_name);
    has_ghost = JsonFormat::decode(input, ghost);
}

void save_result(const vespalib::string &file_name) {
    SmartBuffer output(4_Ki);
    JsonFormat::encode(prod_result, output, false);
    Memory memory = output.obtain();
    File file(file_name);
    file.open(File::CREATE | File::TRUNC);
    file.write(memory.data, memory.size, 0);
    file.close();
}

//-----------------------------------------------------------------------------

struct MyParam : LazyParams {
    Value::UP my_value;
    MyParam() : my_value() {}
    MyParam(const TensorSpec &p0, const Impl &impl) : my_value(impl.create_value(p0)) {}
    const Value &resolve(size_t idx, Stash &) const override {
        assert(idx == 0);
        return *my_value;
    }
    ~MyParam() override;
};
MyParam::~MyParam() = default;

struct EvalOp {
    using UP = std::unique_ptr<EvalOp>;
    Stash                    my_stash;
    const Impl              &impl;
    MyParam                  my_param;
    std::vector<Value::UP>   values;
    std::vector<Value::CREF> stack;
    EvalSingle               single;
    EvalOp(const EvalOp &) = delete;
    EvalOp &operator=(const EvalOp &) = delete;
    EvalOp(Stash &&stash_in, Instruction op, const std::vector<CREF<TensorSpec>> &stack_spec, const Impl &impl_in)
        : my_stash(std::move(stash_in)), impl(impl_in), my_param(), values(), stack(), single(impl.factory, op)
    {
        for (const TensorSpec &spec: stack_spec) {
            values.push_back(impl.create_value(spec));
        }
        for (const auto &value: values) {
            stack.push_back(*value.get());
        }
    }
    EvalOp(Stash &&stash_in, Instruction op, const TensorSpec &p0, const Impl &impl_in)
        : my_stash(std::move(stash_in)), impl(impl_in), my_param(p0, impl), values(), stack(), single(impl.factory, op, my_param)
    {
    }
    TensorSpec result() { return impl.create_spec(single.eval(stack)); }
    size_t suggest_loop_cnt() {
        if (budget < 0.1) {
            return 1;
        }
        size_t loop_cnt = 1;
        auto my_loop = [&](){
            for (size_t i = 0; i < loop_cnt; ++i) {
                single.eval(stack);
            }
        };
        for (;;) {
            vespalib::BenchmarkTimer timer(0.0);
            for (size_t i = 0; i < 5; ++i) {
                timer.before();
                my_loop();
                timer.after();
            }
            double min_time = timer.min_time();
            if (min_time > 0.004) {
                break;
            } else {
                loop_cnt *= 2;
            }
        }
        return std::max(loop_cnt, size_t(8));
    }
    double estimate_cost_us(size_t self_loop_cnt, size_t ref_loop_cnt) {
        size_t loop_cnt = ((self_loop_cnt * 128) < ref_loop_cnt) ? self_loop_cnt : ref_loop_cnt;
        BenchmarkTimer timer(budget);
        if (loop_cnt == 1) {
            while (timer.has_budget()) {
                timer.before();
                single.eval(stack);
                timer.after();
            }
        } else {
            assert((loop_cnt % 8) == 0);
            auto my_loop = [&](){
                for (size_t i = 0; (i + 7) < loop_cnt; i += 8) {
                    for (size_t j = 0; j < 8; ++j) {
                        single.eval(stack);
                    }
                }
            };
            while (timer.has_budget()) {
                timer.before();
                my_loop();
                timer.after();
            }
        }
        return timer.min_time() * 1000.0 * 1000.0 / double(loop_cnt);
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
    std::vector<size_t> loop_cnt(list.size());
    for (const auto &eval: list) {
        loop_cnt[eval->impl.order] = eval->suggest_loop_cnt();
    }
    size_t ref_idx = (list.size() > 1 ? 1u : 0u);
    for (const auto &eval: list) {
        double time = eval->estimate_cost_us(loop_cnt[eval->impl.order], loop_cnt[ref_idx]);
        fprintf(stderr, "    %s(%s): %10.3f us\n", eval->impl.name.c_str(), eval->impl.short_name.c_str(), time);
        result.sample(eval->impl.order, time);
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
        Stash my_stash;
        auto op = impl.create_join(lhs_type, rhs_type, function, my_stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs, rhs});
        list.push_back(std::make_unique<EvalOp>(std::move(my_stash), op, stack_spec, impl));
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
        Stash my_stash;
        auto op = impl.create_reduce(lhs_type, aggr, dims, my_stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs});
        list.push_back(std::make_unique<EvalOp>(std::move(my_stash), op, stack_spec, impl));
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
        Stash my_stash;
        auto op = impl.create_rename(lhs_type, from, to, my_stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs});
        list.push_back(std::make_unique<EvalOp>(std::move(my_stash), op, stack_spec, impl));
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
        Stash my_stash;
        auto op = impl.create_merge(lhs_type, rhs_type, function, my_stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs, rhs});
        list.push_back(std::make_unique<EvalOp>(std::move(my_stash), op, stack_spec, impl));
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
        Stash my_stash;
        auto op = impl.create_map(lhs_type, function, my_stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs});
        list.push_back(std::make_unique<EvalOp>(std::move(my_stash), op, stack_spec, impl));
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
        Stash my_stash;
        auto op = impl.create_concat(lhs_type, rhs_type, dimension, my_stash);
        std::vector<CREF<TensorSpec>> stack_spec({lhs, rhs});
        list.push_back(std::make_unique<EvalOp>(std::move(my_stash), op, stack_spec, impl));
    }
    benchmark(desc, list);
}

//-----------------------------------------------------------------------------

void benchmark_tensor_create(const vespalib::string &desc, const TensorSpec &proto) {
    Stash stash;
    ValueType proto_type = ValueType::from_spec(proto.type());
    ASSERT_FALSE(proto_type.is_error());
    std::vector<CREF<TensorSpec>> stack_spec;
    for (const auto &cell: proto.cells()) {
        stack_spec.emplace_back(stash.create<TensorSpec>(NUM(cell.second)));
    }
    std::vector<EvalOp::UP> list;
    for (const Impl &impl: impl_list) {
        Stash my_stash;
        auto op = impl.create_tensor_create(proto_type, proto, my_stash);
        list.push_back(std::make_unique<EvalOp>(std::move(my_stash), op, stack_spec, impl));
    }
    benchmark(desc, list);
}

//-----------------------------------------------------------------------------

void benchmark_tensor_lambda(const vespalib::string &desc, const ValueType &type, const TensorSpec &p0, const Function &function) {
    Stash stash;
    ValueType p0_type = ValueType::from_spec(p0.type());
    ASSERT_FALSE(p0_type.is_error());
    std::vector<EvalOp::UP> list;
    for (const Impl &impl: impl_list) {
        Stash my_stash;
        auto op = impl.create_tensor_lambda(type, function, p0_type, my_stash);
        list.push_back(std::make_unique<EvalOp>(std::move(my_stash), op, p0, impl));
    }
    benchmark(desc, list);
}

//-----------------------------------------------------------------------------

void benchmark_tensor_peek(const vespalib::string &desc, const TensorSpec &lhs, const MyPeekSpec &peek_spec) {
    Stash stash;
    ValueType type = ValueType::from_spec(lhs.type());
    ASSERT_FALSE(type.is_error());
    std::vector<CREF<TensorSpec>> stack_spec;
    stack_spec.emplace_back(lhs);
    if (peek_spec.is_dynamic) {
        for (const auto &entry: peek_spec.spec) {
            stack_spec.emplace_back(stash.create<TensorSpec>(NUM(double(entry.second))));
        }
    }
    std::vector<EvalOp::UP> list;
    for (const Impl &impl: impl_list) {
        Stash my_stash;
        auto op = impl.create_tensor_peek(type, peek_spec, my_stash);
        list.push_back(std::make_unique<EvalOp>(std::move(my_stash), op, stack_spec, impl));
    }
    benchmark(desc, list);
}

//-----------------------------------------------------------------------------

TEST(MakeInputTest, print_some_test_input) {
    auto number = NUM(5.0);
    auto sparse = GS(1.0).map("x", 5, 3);
    auto dense = GS(10.0).idx("x", 5);
    auto mixed = GS(100.0).map("x", 3, 7).idx("y", 2).idx("z", 2);
    fprintf(stderr, "--------------------------------------------------------\n");
    fprintf(stderr, "simple number: %s\n", number.to_string().c_str());
    fprintf(stderr, "sparse vector: %s\n", sparse.gen().to_string().c_str());
    fprintf(stderr, "dense vector: %s\n", dense.gen().to_string().c_str());
    fprintf(stderr, "mixed cube: %s\n", mixed.gen().to_string().c_str());
    fprintf(stderr, "--------------------------------------------------------\n");
}

//-----------------------------------------------------------------------------

void benchmark_encode_decode(const vespalib::string &desc, const TensorSpec &proto) {
    ValueType proto_type = ValueType::from_spec(proto.type());
    ASSERT_FALSE(proto_type.is_error());
    for (const Impl &impl: impl_list) {
        vespalib::nbostream data;
        auto value = impl.create_value(proto);
        encode_value(*value, data);
        auto new_value = decode_value(data, impl.factory);
        ASSERT_EQ(data.size(), 0);
        ASSERT_EQ(proto, spec_from_value(*new_value));
    }
    fprintf(stderr, "--------------------------------------------------------\n");
    fprintf(stderr, "Benchmarking encode/decode for: [%s]\n", desc.c_str());
    BenchmarkResult encode_result(desc + " <encode>", impl_list.size());
    BenchmarkResult decode_result(desc + " <decode>", impl_list.size());
    for (const Impl &impl: impl_list) {
        constexpr size_t loop_cnt = 32;
        auto value = impl.create_value(proto);
        BenchmarkTimer encode_timer(2 * budget);
        BenchmarkTimer decode_timer(2 * budget);
        while (encode_timer.has_budget()) {
            std::array<vespalib::nbostream, loop_cnt> data;
            std::array<Value::UP, loop_cnt> object;
            encode_timer.before();
            for (size_t i = 0; i < loop_cnt; ++i) {
                encode_value(*value, data[i]);
            }
            encode_timer.after();
            decode_timer.before();
            for (size_t i = 0; i < loop_cnt; ++i) {
                object[i] = decode_value(data[i], impl.factory);
            }
            decode_timer.after();
        }
        double encode_us = encode_timer.min_time() * 1000.0 * 1000.0 / double(loop_cnt);
        double decode_us = decode_timer.min_time() * 1000.0 * 1000.0 / double(loop_cnt);
        fprintf(stderr, "    %s(%s): %10.3f us <encode>\n", impl.name.c_str(), impl.short_name.c_str(), encode_us);
        encode_result.sample(impl.order, encode_us);
        fprintf(stderr, "    %s(%s): %10.3f us <decode>\n", impl.name.c_str(), impl.short_name.c_str(), decode_us);
        decode_result.sample(impl.order, decode_us);
    }
    encode_result.normalize();
    decode_result.normalize();
    benchmark_results.push_back(encode_result);
    benchmark_results.push_back(decode_result);
    fprintf(stderr, "--------------------------------------------------------\n");
}

//-----------------------------------------------------------------------------

// encode/decode operations are not actual instructions, but still
// relevant for the overall performance of the tensor implementation.

TEST(EncodeDecodeBench, encode_decode_dense) {
    auto proto = GS(1.0).idx("a", 64).idx("b", 64);
    benchmark_encode_decode("dense tensor", proto);
}

TEST(EncodeDecodeBench, encode_decode_sparse) {
    auto proto = GS(1.0).map("a", 64, 1).map("b", 64, 1);
    benchmark_encode_decode("sparse tensor", proto);
}

TEST(EncodeDecodeBench, encode_decode_mixed) {
    auto proto = GS(1.0).map("a", 64, 1).idx("b", 64);
    benchmark_encode_decode("mixed tensor", proto);
}

//-----------------------------------------------------------------------------

TEST(DenseConcat, small_vectors) {
    auto lhs = GS(1.0).idx("x", 10);
    auto rhs = GS(2.0).idx("x", 10);
    benchmark_concat("small dense vector append concat", lhs, rhs, "x");
}

TEST(DenseConcat, cross_vectors) {
    auto lhs = GS(1.0).idx("x", 10);
    auto rhs = GS(2.0).idx("x", 10);
    benchmark_concat("small dense vector cross concat", lhs, rhs, "y");
}

TEST(DenseConcat, cube_and_vector) {
    auto lhs = GS(1.0).idx("a", 16).idx("b", 16).idx("c", 16);
    auto rhs = GS(42.0).idx("a", 16);
    benchmark_concat("cube vs vector concat", lhs, rhs, "a");
}

TEST(SparseConcat, small_vectors) {
    auto lhs = GS(1.0).map("x", 10, 1);
    auto rhs = GS(2.0).map("x", 10, 2);
    benchmark_concat("small sparse concat", lhs, rhs, "y");
}

TEST(MixedConcat, mixed_vs_dense) {
    auto lhs = GS(1.0).idx("a", 16).idx("b", 16).map("c", 16, 1);
    auto rhs = GS(2.0).idx("a", 16).idx("b", 16);
    benchmark_concat("mixed dense concat a", lhs, rhs, "a");
}

TEST(MixedConcat, large_mixed_a) {
    auto lhs = GS(1.0).idx("a", 16).idx("b", 16).map("c", 16, 1);
    auto rhs = GS(2.0).idx("a", 16).idx("b", 16).map("c", 16, 2);
    benchmark_concat("mixed append concat a", lhs, rhs, "a");
}

TEST(MixedConcat, large_mixed_b) {
    auto lhs = GS(1.0).idx("a", 16).idx("b", 16).map("c", 16, 1);
    auto rhs = GS(2.0).idx("a", 16).idx("b", 16).map("c", 16, 2);
    benchmark_concat("mixed append concat b", lhs, rhs, "b");
}

//-----------------------------------------------------------------------------

TEST(NumberJoin, plain_op2) {
    auto lhs = NUM(2.0);
    auto rhs = NUM(3.0);
    benchmark_join("simple numbers multiply", lhs, rhs, operation::Mul::f);
}

//-----------------------------------------------------------------------------

TEST(DenseJoin, small_vectors) {
    auto lhs = GS(1.0).idx("x", 10);
    auto rhs = GS(2.0).idx("x", 10);
    benchmark_join("small dense vector multiply", lhs, rhs, operation::Mul::f);
}

TEST(DenseJoin, full_overlap) {
    auto lhs = GS(1.0).idx("a", 16).idx("b", 16).idx("c", 16);
    auto rhs = GS(2.0).idx("a", 16).idx("b", 16).idx("c", 16);
    benchmark_join("dense full overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(DenseJoin, partial_overlap) {
    auto lhs = GS(1.0).idx("a", 8).idx("c", 8).idx("d", 8);
    auto rhs = GS(2.0).idx("b", 8).idx("c", 8).idx("d", 8);
    benchmark_join("dense partial overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(DenseJoin, subset_overlap) {
    auto lhs = GS(1.0).idx("a", 16).idx("b", 16).idx("c", 16);
    auto rhs_inner = GS(2.0).idx("b", 16).idx("c", 16);
    auto rhs_outer = GS(3.0).idx("a", 16).idx("b", 16);
    benchmark_join("dense subset overlap inner multiply", lhs, rhs_inner, operation::Mul::f);
    benchmark_join("dense subset overlap outer multiply", lhs, rhs_outer, operation::Mul::f);
}

TEST(DenseJoin, no_overlap) {
    auto lhs = GS(1.0).idx("a", 4).idx("e", 4).idx("f", 4);
    auto rhs = GS(2.0).idx("b", 4).idx("c", 4).idx("d", 4);
    benchmark_join("dense no overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(DenseJoin, simple_expand) {
    auto lhs = GS(1.0).idx("a", 5).idx("b", 4).idx("c", 4);
    auto rhs = GS(2.0).idx("d", 4).idx("e", 4).idx("f", 5);
    benchmark_join("dense simple expand multiply", lhs, rhs, operation::Mul::f);
}

TEST(DenseJoin, multiply_by_number) {
    auto lhs = NUM(3.0);
    auto rhs = GS(2.0).idx("a", 16).idx("b", 16).idx("c", 16);
    benchmark_join("dense cube multiply by number", lhs, rhs, operation::Mul::f);
}

//-----------------------------------------------------------------------------

TEST(SparseJoin, small_vectors) {
    auto lhs = GS(1.0).map("x", 10, 1);
    auto rhs = GS(2.0).map("x", 10, 2);
    benchmark_join("small sparse vector multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, large_vectors) {
    auto lhs = GS(1.0).map("x", 1800, 1);
    auto rhs = GS(2.0).map("x", 1000, 2);
    benchmark_join("large sparse vector multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, full_overlap) {
    auto lhs = GS(1.0).map("a", 16, 1).map("b", 16, 1).map("c", 16, 1);
    auto rhs = GS(2.0).map("a", 16, 2).map("b", 16, 2).map("c", 16, 2);
    benchmark_join("sparse full overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, full_overlap_big_vs_small) {
    auto lhs = GS(1.0).map("a", 16, 1).map("b", 16, 1).map("c", 16, 1);
    auto rhs = GS(2.0).map("a", 2, 1).map("b", 2, 1).map("c", 2, 1);
    benchmark_join("sparse full overlap big vs small multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, partial_overlap) {
    auto lhs = GS(1.0).map("a", 8, 1).map("c", 8, 1).map("d", 8, 1);
    auto rhs = GS(2.0).map("b", 8, 2).map("c", 8, 2).map("d", 8, 2);
    benchmark_join("sparse partial overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, no_overlap) {
    auto lhs = GS(1.0).map("a", 4, 1).map("e", 4, 1).map("f", 4, 1);
    auto rhs = GS(2.0).map("b", 4, 1).map("c", 4, 1).map("d", 4, 1);
    benchmark_join("sparse no overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(SparseJoin, multiply_by_number) {
    auto lhs = NUM(3.0);
    auto rhs = GS(2.0).map("a", 16, 2).map("b", 16, 2).map("c", 16, 2);
    benchmark_join("sparse multiply by number", lhs, rhs, operation::Mul::f);
}

//-----------------------------------------------------------------------------

TEST(MixedJoin, full_overlap) {
    auto lhs = GS(1.0).map("a", 16, 1).map("b", 16, 1).idx("c", 16);
    auto rhs = GS(2.0).map("a", 16, 2).map("b", 16, 2).idx("c", 16);
    benchmark_join("mixed full overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(MixedJoin, partial_sparse_overlap) {
    auto lhs = GS(1.0).map("a", 8, 1).map("c", 8, 1).idx("d", 8);
    auto rhs = GS(2.0).map("b", 8, 2).map("c", 8, 2).idx("d", 8);
    benchmark_join("mixed partial sparse overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(MixedJoin, no_overlap) {
    auto lhs = GS(1.0).map("a", 4, 1).map("e", 4, 1).idx("f", 4);
    auto rhs = GS(2.0).map("b", 4, 1).map("c", 4, 1).idx("d", 4);
    benchmark_join("mixed no overlap multiply", lhs, rhs, operation::Mul::f);
}

TEST(MixedJoin, multiply_by_number) {
    auto lhs = NUM(3.0);
    auto rhs = GS(2.0).map("a", 16, 2).map("b", 16, 2).idx("c", 16);
    benchmark_join("mixed multiply by number", lhs, rhs, operation::Mul::f);
}

//-----------------------------------------------------------------------------

TEST(ReduceBench, number_reduce) {
    auto lhs = NUM(1.0);
    benchmark_reduce("number reduce", lhs, Aggr::SUM, {});
}

TEST(ReduceBench, dense_reduce) {
    auto lhs = GS(1.0).idx("a", 16).idx("b", 16).idx("c", 16);
    benchmark_reduce("dense reduce inner", lhs, Aggr::SUM, {"c"});
    benchmark_reduce("dense reduce middle", lhs, Aggr::SUM, {"b"});
    benchmark_reduce("dense reduce outer", lhs, Aggr::SUM, {"a"});
    benchmark_reduce("dense multi-reduce inner", lhs, Aggr::SUM, {"b", "c"});
    benchmark_reduce("dense multi-reduce outer", lhs, Aggr::SUM, {"a", "b"});
    benchmark_reduce("dense multi-reduce outer-inner", lhs, Aggr::SUM, {"a", "c"});
    benchmark_reduce("dense reduce all", lhs, Aggr::SUM, {});
}

TEST(ReduceBench, sparse_reduce) {
    auto lhs = GS(1.0).map("a", 16, 1).map("b", 16, 1).map("c", 16, 1);
    benchmark_reduce("sparse reduce inner", lhs, Aggr::SUM, {"c"});
    benchmark_reduce("sparse reduce middle", lhs, Aggr::SUM, {"b"});
    benchmark_reduce("sparse reduce outer", lhs, Aggr::SUM, {"a"});
    benchmark_reduce("sparse multi-reduce inner", lhs, Aggr::SUM, {"b", "c"});
    benchmark_reduce("sparse multi-reduce outer", lhs, Aggr::SUM, {"a", "b"});
    benchmark_reduce("sparse multi-reduce outer-inner", lhs, Aggr::SUM, {"a", "c"});
    benchmark_reduce("sparse reduce all", lhs, Aggr::SUM, {});
}

TEST(ReduceBench, mixed_reduce) {
    auto lhs = GS(1.0).map("a", 4, 1).map("b", 4, 1).map("c", 4, 1)
                      .idx("d", 4).idx("e", 4).idx("f", 4);
    benchmark_reduce("mixed reduce middle dense", lhs, Aggr::SUM, {"e"});
    benchmark_reduce("mixed reduce middle sparse", lhs, Aggr::SUM, {"b"});
    benchmark_reduce("mixed reduce middle sparse/dense", lhs, Aggr::SUM, {"b", "e"});
    benchmark_reduce("mixed reduce all dense", lhs, Aggr::SUM, {"d", "e", "f"});
    benchmark_reduce("mixed reduce all sparse", lhs, Aggr::SUM, {"a", "b", "c"});
    benchmark_reduce("mixed reduce all", lhs, Aggr::SUM, {});
}

//-----------------------------------------------------------------------------

TEST(RenameBench, dense_rename) {
    auto lhs = GS(1.0).idx("a", 64).idx("b", 64);
    benchmark_rename("dense transpose", lhs, {"a", "b"}, {"b", "a"});
}

TEST(RenameBench, sparse_rename) {
    auto lhs = GS(1.0).map("a", 64, 1).map("b", 64, 1);
    benchmark_rename("sparse transpose", lhs, {"a", "b"}, {"b", "a"});
}

TEST(RenameBench, mixed_rename) {
    auto lhs = GS(1.0).map("a", 8, 1).map("b", 8, 1).idx("c", 8).idx("d", 8);
    benchmark_rename("mixed multi-transpose", lhs, {"a", "b", "c", "d"}, {"b", "a", "d", "c"});
}

//-----------------------------------------------------------------------------

TEST(MergeBench, dense_merge) {
    auto lhs = GS(1.0).idx("a", 64).idx("b", 64);
    auto rhs = GS(2.0).idx("a", 64).idx("b", 64);
    benchmark_merge("dense merge", lhs, rhs, operation::Max::f);
}

TEST(MergeBench, sparse_merge_big_small) {
    auto lhs = GS(1.0).map("a", 64, 1).map("b", 64, 1);
    auto rhs = GS(2.0).map("a", 8, 1).map("b", 8, 1);
    benchmark_merge("sparse merge big vs small", lhs, rhs, operation::Max::f);
}

TEST(MergeBench, sparse_merge_minimal_overlap) {
    auto lhs = GS(1.0).map("a", 64, 11).map("b", 32, 11);
    auto rhs = GS(2.0).map("a", 32, 13).map("b", 64, 13);
    benchmark_merge("sparse merge minimal overlap", lhs, rhs, operation::Max::f);
}

TEST(MergeBench, mixed_merge) {
    auto lhs = GS(1.0).map("a", 64, 1).idx("b", 64);
    auto rhs = GS(2.0).map("a", 64, 2).idx("b", 64);
    benchmark_merge("mixed merge", lhs, rhs, operation::Max::f);
}

//-----------------------------------------------------------------------------

TEST(MapBench, number_map) {
    auto lhs = NUM(1.75);
    benchmark_map("number map", lhs, operation::Floor::f);
}

TEST(MapBench, dense_map) {
    auto lhs = GS(1.75).idx("a", 64).idx("b", 64);
    benchmark_map("dense map", lhs, operation::Floor::f);
}

TEST(MapBench, sparse_map_small) {
    auto lhs = GS(1.75).map("a", 4, 1).map("b", 4, 1);
    benchmark_map("sparse map small", lhs, operation::Floor::f);
}

TEST(MapBench, sparse_map_big) {
    auto lhs = GS(1.75).map("a", 64, 1).map("b", 64, 1);
    benchmark_map("sparse map big", lhs, operation::Floor::f);
}

TEST(MapBench, mixed_map) {
    auto lhs = GS(1.75).map("a", 64, 1).idx("b", 64);
    benchmark_map("mixed map", lhs, operation::Floor::f);
}

//-----------------------------------------------------------------------------

TEST(TensorCreateBench, create_dense) {
    auto proto = GS(1.0).idx("a", 32).idx("b", 32);
    benchmark_tensor_create("dense tensor create", proto);
}

TEST(TensorCreateBench, create_sparse) {
    auto proto = GS(1.0).map("a", 32, 1).map("b", 32, 1);
    benchmark_tensor_create("sparse tensor create", proto);
}

TEST(TensorCreateBench, create_mixed) {
    auto proto = GS(1.0).map("a", 32, 1).idx("b", 32);
    benchmark_tensor_create("mixed tensor create", proto);
}

//-----------------------------------------------------------------------------

TEST(TensorLambdaBench, simple_lambda) {
    auto type = ValueType::from_spec("tensor<float>(a[64],b[64])");
    auto p0 = NUM(3.5);
    auto function = Function::parse({"a", "b", "p0"}, "(a*64+b)*p0");
    ASSERT_FALSE(function->has_error());
    benchmark_tensor_lambda("simple tensor lambda", type, p0, *function);
}

TEST(TensorLambdaBench, complex_lambda) {
    auto type = ValueType::from_spec("tensor<float>(a[64],b[64])");
    auto p0 = GS(1.0).idx("x", 3);
    auto function = Function::parse({"a", "b", "p0"}, "(a*64+b)*reduce(p0,sum)");
    ASSERT_FALSE(function->has_error());
    benchmark_tensor_lambda("complex tensor lambda", type, p0, *function);
}

//-----------------------------------------------------------------------------

TEST(TensorPeekBench, dense_peek) {
    auto lhs = GS(1.0).idx("a", 64).idx("b", 64);
    benchmark_tensor_peek("dense peek cell verbatim", lhs, verbatim_peek().add("a", 1).add("b", 2));
    benchmark_tensor_peek("dense peek cell dynamic", lhs, dynamic_peek().add("a", 1).add("b", 2));
    benchmark_tensor_peek("dense peek vector verbatim", lhs, verbatim_peek().add("a", 1));
    benchmark_tensor_peek("dense peek vector dynamic", lhs, dynamic_peek().add("a", 1));
}

TEST(TensorPeekBench, sparse_peek) {
    auto lhs = GS(1.0).map("a", 64, 1).map("b", 64, 1);
    benchmark_tensor_peek("sparse peek cell verbatim", lhs, verbatim_peek().add("a", 1).add("b", 2));
    benchmark_tensor_peek("sparse peek cell dynamic", lhs, dynamic_peek().add("a", 1).add("b", 2));
    benchmark_tensor_peek("sparse peek vector verbatim", lhs, verbatim_peek().add("a", 1));
    benchmark_tensor_peek("sparse peek vector dynamic", lhs, dynamic_peek().add("a", 1));
}

TEST(TensorPeekBench, mixed_peek) {
    auto lhs = GS(1.0).map("a", 8, 1).map("b", 8, 1).idx("c", 8).idx("d", 8);
    benchmark_tensor_peek("mixed peek cell verbatim", lhs, verbatim_peek().add("a", 1).add("b", 2).add("c", 3).add("d", 4));
    benchmark_tensor_peek("mixed peek cell dynamic", lhs, dynamic_peek().add("a", 1).add("b", 2).add("c", 3).add("d", 4));
    benchmark_tensor_peek("mixed peek dense verbatim", lhs, verbatim_peek().add("a", 1).add("b", 2));
    benchmark_tensor_peek("mixed peek dense dynamic", lhs, dynamic_peek().add("a", 1).add("b", 2));
    benchmark_tensor_peek("mixed peek sparse verbatim", lhs, verbatim_peek().add("c", 3).add("d", 4));
    benchmark_tensor_peek("mixed peek sparse dynamic", lhs, dynamic_peek().add("c", 3).add("d", 4));
    benchmark_tensor_peek("mixed peek partial dense verbatim", lhs, verbatim_peek().add("a", 1).add("b", 2).add("c", 3));
    benchmark_tensor_peek("mixed peek partial dense dynamic", lhs, dynamic_peek().add("a", 1).add("b", 2).add("c", 3));
    benchmark_tensor_peek("mixed peek partial sparse verbatim", lhs, verbatim_peek().add("a", 1).add("c", 3).add("d", 4));
    benchmark_tensor_peek("mixed peek partial sparse dynamic", lhs, dynamic_peek().add("a", 1).add("c", 3).add("d", 4));
    benchmark_tensor_peek("mixed peek partial mixed verbatim", lhs, verbatim_peek().add("a", 1).add("c", 4));
    benchmark_tensor_peek("mixed peek partial mixed dynamic", lhs, dynamic_peek().add("a", 1).add("c", 4));
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
    prod_result.setObject();
    load_ghost("ghost.json");
    const std::string run_only_prod_option = "--limit-implementations";
    const std::string ghost_mode_option = "--ghost-mode";
    const std::string smoke_test_option = "--smoke-test";
    if ((argc > 1) && (argv[1] == run_only_prod_option)) {
        impl_list.clear();
        impl_list.push_back(optimized_fast_value_impl);
        impl_list.push_back(fast_value_impl);
        ++argv;
        --argc;
    } else if ((argc > 1) && (argv[1] == ghost_mode_option)) {
        impl_list.clear();
        impl_list.push_back(optimized_fast_value_impl);
        has_ghost = true;
        ++argv;
        --argc;
    } else if ((argc > 1) && (argv[1] == smoke_test_option)) {
        budget = 0.001;
        impl_list.clear();
        impl_list.push_back(optimized_fast_value_impl);
        has_ghost = true;
        ++argv;
        --argc;
    }
    ::testing::InitGoogleTest(&argc, argv);
    int result = RUN_ALL_TESTS();
    save_result("result.json");
    print_summary();
    return result;
}
