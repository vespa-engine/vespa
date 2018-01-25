// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_conformance.h"
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/aggr.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include "tensor_model.hpp"
#include "test_io.h"

namespace vespalib {
namespace eval {
namespace test {
namespace {

using slime::Cursor;
using slime::Inspector;
using slime::JsonFormat;

double as_double(const TensorSpec &spec) {
    return spec.cells().empty() ? 0.0 : spec.cells().begin()->second.value;
}

// abstract evaluation wrapper
struct Eval {
    // typed result wrapper
    class Result {
    private:
        enum class Type { ERROR, NUMBER, TENSOR };
        Type _type;
        double _number;
        TensorSpec _tensor;
    public:
        Result() : _type(Type::ERROR), _number(error_value), _tensor("error") {}
        Result(const TensorEngine &engine, const Value &value) : _type(Type::ERROR), _number(error_value), _tensor("error") {
            if (value.is_double()) {
                _type = Type::NUMBER;
            }
            if (value.is_tensor()) {
                EXPECT_TRUE(_type == Type::ERROR);
                _type = Type::TENSOR;
            }
            _number = value.as_double();
            _tensor = engine.to_spec(value);
        }
        bool is_error() const { return (_type == Type::ERROR); }
        bool is_number() const { return (_type == Type::NUMBER); }
        bool is_tensor() const { return (_type == Type::TENSOR); }
        double number() const {
            EXPECT_TRUE(is_number());
            return _number;
        }
        const TensorSpec &tensor() const {
            EXPECT_TRUE(is_tensor());
            return _tensor;
        }
    };
    virtual Result eval(const TensorEngine &) const {
        TEST_ERROR("wrong signature");
        return Result();
    }
    virtual Result eval(const TensorEngine &, const TensorSpec &) const {
        TEST_ERROR("wrong signature");
        return Result();
    }
    virtual Result eval(const TensorEngine &, const TensorSpec &, const TensorSpec &) const {
        TEST_ERROR("wrong signature");
        return Result();
    }
    virtual ~Eval() {}
};

// catches exceptions trying to keep the test itself safe from eval side-effects
struct SafeEval : Eval {
    const Eval &unsafe;
    SafeEval(const Eval &unsafe_in) : unsafe(unsafe_in) {}
    Result eval(const TensorEngine &engine) const override {
        try {
            return unsafe.eval(engine);
        } catch (std::exception &e) {
            TEST_ERROR(e.what());
            return Result();
        }
    }
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        try {
            return unsafe.eval(engine, a);
        } catch (std::exception &e) {
            TEST_ERROR(e.what());
            return Result();
        }

    }
    Result eval(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &b) const override {
        try {
            return unsafe.eval(engine, a, b);
        } catch (std::exception &e) {
            TEST_ERROR(e.what());
            return Result();
        }
    }
};
SafeEval safe(const Eval &eval) { return SafeEval(eval); }

const Value &check_type(const Value &value, const ValueType &expect_type) {
    EXPECT_EQUAL(value.type(), expect_type);
    return value;
}

// expression(void)
struct Expr_V : Eval {
    const vespalib::string &expr;
    Expr_V(const vespalib::string &expr_in) : expr(expr_in) {}
    Result eval(const TensorEngine &engine) const override {
        Function fun = Function::parse(expr);
        NodeTypes types(fun, {});
        InterpretedFunction ifun(engine, fun, types);
        InterpretedFunction::Context ctx(ifun);
        SimpleObjectParams params({});
        return Result(engine, check_type(ifun.eval(ctx, params), types.get_type(fun.root())));
    }
};

// expression(tensor)
struct Expr_T : Eval {
    const vespalib::string &expr;
    Expr_T(const vespalib::string &expr_in) : expr(expr_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        Function fun = Function::parse(expr);
        auto a_type = ValueType::from_spec(a.type());
        NodeTypes types(fun, {a_type});
        InterpretedFunction ifun(engine, fun, types);
        InterpretedFunction::Context ctx(ifun);
        Value::UP va = engine.from_spec(a);
        SimpleObjectParams params({*va});
        return Result(engine, check_type(ifun.eval(ctx, params), types.get_type(fun.root())));
    }
};

// expression(tensor,tensor)
struct Expr_TT : Eval {
    vespalib::string expr;
    Expr_TT(const vespalib::string &expr_in) : expr(expr_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &b) const override {
        Function fun = Function::parse(expr);
        auto a_type = ValueType::from_spec(a.type());
        auto b_type = ValueType::from_spec(b.type());
        NodeTypes types(fun, {a_type, b_type});
        InterpretedFunction ifun(engine, fun, types);
        InterpretedFunction::Context ctx(ifun);
        Value::UP va = engine.from_spec(a);
        Value::UP vb = engine.from_spec(b);
        SimpleObjectParams params({*va,*vb});
        return Result(engine, check_type(ifun.eval(ctx, params), types.get_type(fun.root())));
    }
};

const Value &make_value(const TensorEngine &engine, const TensorSpec &spec, Stash &stash) {
    return *stash.create<Value::UP>(engine.from_spec(spec));
}

//-----------------------------------------------------------------------------

// evaluate tensor reduce operation using tensor engine immediate api
struct ImmediateReduce : Eval {
    Aggr aggr;
    std::vector<vespalib::string> dimensions;
    ImmediateReduce(Aggr aggr_in) : aggr(aggr_in), dimensions() {}
    ImmediateReduce(Aggr aggr_in, const vespalib::string &dimension)
        : aggr(aggr_in), dimensions({dimension}) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        Stash stash;
        const auto &lhs = make_value(engine, a, stash);
        return Result(engine, engine.reduce(lhs, aggr, dimensions, stash));
    }
};

// evaluate tensor map operation using tensor engine immediate api
struct ImmediateMap : Eval {
    using fun_t = double (*)(double);
    fun_t function;
    ImmediateMap(fun_t function_in) : function(function_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        Stash stash;
        const auto &lhs = make_value(engine, a, stash);
        return Result(engine, engine.map(lhs, function, stash));
    }
};

// evaluate tensor join operation using tensor engine immediate api
struct ImmediateJoin : Eval {
    using fun_t = double (*)(double, double);
    fun_t function;
    ImmediateJoin(fun_t function_in) : function(function_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &b) const override {
        Stash stash;
        const auto &lhs = make_value(engine, a, stash);
        const auto &rhs = make_value(engine, b, stash);
        return Result(engine, engine.join(lhs, rhs, function, stash));
    }
};

// evaluate tensor concat operation using tensor engine immediate api
struct ImmediateConcat : Eval {
    vespalib::string dimension;
    ImmediateConcat(const vespalib::string &dimension_in) : dimension(dimension_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &b) const override {
        Stash stash;
        const auto &lhs = make_value(engine, a, stash);
        const auto &rhs = make_value(engine, b, stash);
        return Result(engine, engine.concat(lhs, rhs, dimension, stash));
    }
};

// evaluate tensor rename operation using tensor engine immediate api
struct ImmediateRename : Eval {
    std::vector<vespalib::string> from;
    std::vector<vespalib::string> to;
    ImmediateRename(const std::vector<vespalib::string> &from_in, const std::vector<vespalib::string> &to_in)
        : from(from_in), to(to_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        Stash stash;
        const auto &lhs = make_value(engine, a, stash);
        return Result(engine, engine.rename(lhs, from, to, stash));
    }
};

//-----------------------------------------------------------------------------

const size_t tensor_id_a = 11;
const size_t tensor_id_b = 12;

// input used when evaluating in retained mode
struct Input {
    std::vector<Value::UP> tensors;
    std::vector<Value::CREF> params;
    ~Input() {}
    void pad_params() {
        for (size_t i = 0; i < tensor_id_a; ++i) {
            params.push_back(ErrorValue::instance);
        }
    }
    Input(Value::UP a) : tensors() {
        pad_params();
        tensors.push_back(std::move(a));
        params.emplace_back(*tensors.back());
    }
    Input(Value::UP a, Value::UP b) : tensors() {
        pad_params();
        tensors.push_back(std::move(a));
        params.emplace_back(*tensors.back());
        tensors.push_back(std::move(b));
        params.emplace_back(*tensors.back());
    }
    SimpleObjectParams get() const {
        return SimpleObjectParams(params);
    }
};

// evaluate tensor reduce operation using tensor engine retained api
struct RetainedReduce : Eval {
    Aggr aggr;
    std::vector<vespalib::string> dimensions;
    RetainedReduce(Aggr aggr_in) : aggr(aggr_in), dimensions() {}
    RetainedReduce(Aggr aggr_in, const vespalib::string &dimension)
        : aggr(aggr_in), dimensions({dimension}) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        Stash stash;
        auto a_type = ValueType::from_spec(a.type());
        const auto &ir = tensor_function::reduce(tensor_function::inject(a_type, tensor_id_a, stash), aggr, dimensions, stash);
        ValueType expect_type = ir.result_type();
        const auto &fun = engine.optimize(ir, stash);
        Input input(engine.from_spec(a));
        return Result(engine, check_type(fun.eval(engine, input.get(), stash), expect_type));
    }
};

// evaluate tensor map operation using tensor engine retained api
struct RetainedMap : Eval {
    map_fun_t function;
    RetainedMap(map_fun_t function_in) : function(function_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        Stash stash;
        auto a_type = ValueType::from_spec(a.type());
        const auto &ir = tensor_function::map(tensor_function::inject(a_type, tensor_id_a, stash), function, stash);
        ValueType expect_type = ir.result_type();
        const auto &fun = engine.optimize(ir, stash);
        Input input(engine.from_spec(a));
        return Result(engine, check_type(fun.eval(engine, input.get(), stash), expect_type));
    }
};

// evaluate tensor join operation using tensor engine retained api
struct RetainedJoin : Eval {
    join_fun_t function;
    RetainedJoin(join_fun_t function_in) : function(function_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &b) const override {
        Stash stash;
        auto a_type = ValueType::from_spec(a.type());
        auto b_type = ValueType::from_spec(b.type());
        const auto &ir = tensor_function::join(tensor_function::inject(a_type, tensor_id_a, stash),
                                               tensor_function::inject(b_type, tensor_id_b, stash),
                                               function, stash);
        ValueType expect_type = ir.result_type();
        const auto &fun = engine.optimize(ir, stash);
        Input input(engine.from_spec(a), engine.from_spec(b));
        return Result(engine, check_type(fun.eval(engine, input.get(), stash), expect_type));
    }
};

// placeholder used for unused values in a sequence
const double X = error_value;

// NaN value
const double my_nan = std::numeric_limits<double>::quiet_NaN();

void verify_result(const Eval::Result &result, const Eval::Result &expect) {
    if (expect.is_number()) {
        EXPECT_EQUAL(result.number(), expect.number());
    } else if (expect.is_tensor()) {
        EXPECT_EQUAL(result.tensor(), expect.tensor());
    } else {
        TEST_FATAL("expected result should be valid");
    }
}

void verify_result(const Eval::Result &result, const TensorSpec &expect) {
    if (expect.type() == "double") {
        EXPECT_EQUAL(result.number(), as_double(expect));
    } else {
        EXPECT_EQUAL(result.tensor(), expect);
    }
}

uint8_t unhex(char c) {
    if (c >= '0' && c <= '9') {
        return (c - '0');
    }
    if (c >= 'A' && c <= 'F') {
        return ((c - 'A') + 10);
    }
    TEST_ERROR("bad hex char");
    return 0;
}

nbostream extract_data(const Memory &hex_dump) {
    nbostream data;
    if ((hex_dump.size > 2) && (hex_dump.data[0] == '0') && (hex_dump.data[1] == 'x')) {
        for (size_t i = 2; i < (hex_dump.size - 1); i += 2) {
            data << uint8_t((unhex(hex_dump.data[i]) << 4) | unhex(hex_dump.data[i + 1]));
        }
    }
    return data;
}

bool is_same(const nbostream &a, const nbostream &b) {
    return (Memory(a.peek(), a.size()) == Memory(b.peek(), b.size()));
}

// Test wrapper to avoid passing global test parameters around
struct TestContext {

    vespalib::string module_path;
    const TensorEngine &ref_engine;
    const TensorEngine &engine;

    TestContext(const vespalib::string &module_path_in, const TensorEngine &engine_in)
        : module_path(module_path_in), ref_engine(SimpleTensorEngine::ref()), engine(engine_in) {}

    //-------------------------------------------------------------------------

    void verify_create_type(const vespalib::string &type_spec) {
        Value::UP value = engine.from_spec(TensorSpec(type_spec));
        EXPECT_EQUAL(type_spec, value->type().to_spec());
    }

    void test_tensor_create_type() {
        TEST_DO(verify_create_type("error"));
        TEST_DO(verify_create_type("double"));
        TEST_DO(verify_create_type("tensor(x{})"));
        TEST_DO(verify_create_type("tensor(x{},y{})"));
        TEST_DO(verify_create_type("tensor(x[5])"));
        TEST_DO(verify_create_type("tensor(x[5],y[10])"));
        TEST_DO(verify_create_type("tensor(x{},y[10])"));
        TEST_DO(verify_create_type("tensor(x[5],y{})"));
    }

    //-------------------------------------------------------------------------

    void verify_reduce_result(const Eval &eval, const TensorSpec &a, const Eval::Result &expect) {
        TEST_DO(verify_result(eval.eval(engine, a), expect));
    }

    void test_reduce_op(Aggr aggr, const Sequence &seq) {
        std::vector<Layout> layouts = {
            {x(3)},
            {x(3),y(5)},
            {x(3),y(5),z(7)},
            {x({"a","b","c"})},
            {x({"a","b","c"}),y({"foo","bar"})},
            {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})},
            {x(3),y({"foo", "bar"}),z(7)},
            {x({"a","b","c"}),y(5),z({"i","j","k","l"})}
        };
        for (const Layout &layout: layouts) {
            TensorSpec input = spec(layout, seq);
            for (const Domain &domain: layout) {
                Eval::Result expect = ImmediateReduce(aggr, domain.dimension).eval(ref_engine, input);
                TEST_STATE(make_string("shape: %s, reduce dimension: %s",
                                       infer_type(layout).c_str(), domain.dimension.c_str()).c_str());
                vespalib::string expr = make_string("reduce(a,%s,%s)",
                        AggrNames::name_of(aggr)->c_str(), domain.dimension.c_str());
                TEST_DO(verify_reduce_result(Expr_T(expr), input, expect));
                TEST_DO(verify_reduce_result(ImmediateReduce(aggr, domain.dimension), input, expect));
                TEST_DO(verify_reduce_result(RetainedReduce(aggr, domain.dimension), input, expect));
            }
            {
                Eval::Result expect = ImmediateReduce(aggr).eval(ref_engine, input);
                TEST_STATE(make_string("shape: %s, reduce all dimensions",
                                       infer_type(layout).c_str()).c_str());
                vespalib::string expr = make_string("reduce(a,%s)", AggrNames::name_of(aggr)->c_str());
                TEST_DO(verify_reduce_result(Expr_T(expr), input, expect));
                TEST_DO(verify_reduce_result(ImmediateReduce(aggr), input, expect));
                TEST_DO(verify_reduce_result(RetainedReduce(aggr), input, expect));
            }
        }
    }

    void test_tensor_reduce() {
        TEST_DO(test_reduce_op(Aggr::AVG, N()));
        TEST_DO(test_reduce_op(Aggr::COUNT, N()));
        TEST_DO(test_reduce_op(Aggr::PROD, Sigmoid(N())));
        TEST_DO(test_reduce_op(Aggr::SUM, N()));
        TEST_DO(test_reduce_op(Aggr::MAX, N()));
        TEST_DO(test_reduce_op(Aggr::MIN, N()));
    }

    //-------------------------------------------------------------------------

    void test_map_op(const Eval &eval, map_fun_t ref_op, const Sequence &seq) {
        std::vector<Layout> layouts = {
            {},
            {x(3)},
            {x(3),y(5)},
            {x(3),y(5),z(7)},
            {x({"a","b","c"})},
            {x({"a","b","c"}),y({"foo","bar"})},
            {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})},
            {x(3),y({"foo", "bar"}),z(7)},
            {x({"a","b","c"}),y(5),z({"i","j","k","l"})}
        };
        for (const Layout &layout: layouts) {
            TEST_DO(verify_result(eval.eval(engine, spec(layout, seq)), spec(layout, OpSeq(seq, ref_op))));
        }
    }

    void test_map_op(const vespalib::string &expr, map_fun_t op, const Sequence &seq) {
        TEST_DO(test_map_op(ImmediateMap(op), op, seq));
        TEST_DO(test_map_op(RetainedMap(op), op, seq));
        TEST_DO(test_map_op(Expr_T(expr), op, seq));
        TEST_DO(test_map_op(Expr_T(make_string("map(x,f(a)(%s))", expr.c_str())), op, seq));
    }

    void test_tensor_map() {
        TEST_DO(test_map_op("-a", operation::Neg::f, Sub2(Div10(N()))));
        TEST_DO(test_map_op("!a", operation::Not::f, Mask2Seq(SkipNth(3))));
        TEST_DO(test_map_op("cos(a)", operation::Cos::f, Div10(N())));
        TEST_DO(test_map_op("sin(a)", operation::Sin::f, Div10(N())));
        TEST_DO(test_map_op("tan(a)", operation::Tan::f, Div10(N())));
        TEST_DO(test_map_op("cosh(a)", operation::Cosh::f, Div10(N())));
        TEST_DO(test_map_op("sinh(a)", operation::Sinh::f, Div10(N())));
        TEST_DO(test_map_op("tanh(a)", operation::Tanh::f, Div10(N())));
        TEST_DO(test_map_op("acos(a)", operation::Acos::f, Sigmoid(Div10(N()))));
        TEST_DO(test_map_op("asin(a)", operation::Asin::f, Sigmoid(Div10(N()))));
        TEST_DO(test_map_op("atan(a)", operation::Atan::f, Div10(N())));
        TEST_DO(test_map_op("exp(a)", operation::Exp::f, Div10(N())));
        TEST_DO(test_map_op("log10(a)", operation::Log10::f, Div10(N())));
        TEST_DO(test_map_op("log(a)", operation::Log::f, Div10(N())));
        TEST_DO(test_map_op("sqrt(a)", operation::Sqrt::f, Div10(N())));
        TEST_DO(test_map_op("ceil(a)", operation::Ceil::f, Div10(N())));
        TEST_DO(test_map_op("fabs(a)", operation::Fabs::f, Div10(N())));
        TEST_DO(test_map_op("floor(a)", operation::Floor::f, Div10(N())));
        TEST_DO(test_map_op("isNan(a)", operation::IsNan::f, Mask2Seq(SkipNth(3), 1.0, my_nan)));
        TEST_DO(test_map_op("relu(a)", operation::Relu::f, Sub2(Div10(N()))));
        TEST_DO(test_map_op("sigmoid(a)", operation::Sigmoid::f, Sub2(Div10(N()))));
        TEST_DO(test_map_op("elu(a)", operation::Elu::f, Sub2(Div10(N()))));
        TEST_DO(test_map_op("a in [1,5,7,13,42]", MyIn::f, N()));
        TEST_DO(test_map_op("(a+1)*2", MyOp::f, Div10(N())));
    }

    //-------------------------------------------------------------------------

    void test_apply_op(const Eval &eval,
                       const TensorSpec &expect,
                       const TensorSpec &lhs,
                       const TensorSpec &rhs) {
        TEST_DO(verify_result(safe(eval).eval(engine, lhs, rhs), expect));
    }

    void test_fixed_sparse_cases_apply_op(const Eval &eval,
                                          join_fun_t op)
    {
        TEST_DO(test_apply_op(eval,
                              spec("x{}", {}),
                              spec("x{}", { { {{"x","1"}}, 3 } }),
                              spec("x{}", { { {{"x","2"}}, 5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{}", { { {{"x","1"}}, op(3,5) } }),
                              spec("x{}", { { {{"x","1"}}, 3 } }),
                              spec("x{}", { { {{"x","1"}}, 5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{}", { { {{"x","1"}}, op(3,-5) } }),
                              spec("x{}", { { {{"x","1"}},  3 } }),
                              spec("x{}", { { {{"x","1"}}, -5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","-"},{"y","2"},{"z","-"}},
                                               op(5,7) },
                                       { {{"x","1"},{"y","-"},{"z","3"}},
                                               op(3,11) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},  5 },
                                       { {{"x","1"},{"y","-"}},  3 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","-"},{"y","2"},{"z","-"}},
                                               op(7,5) },
                                       { {{"x","1"},{"y","-"},{"z","3"}},
                                               op(11,3) } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},  5 },
                                       { {{"x","1"},{"y","-"}},  3 } })));
        TEST_DO(test_apply_op(eval,
                              spec("y{},z{}",
                                   {   { {{"y","2"},{"z","-"}},
                                               op(5,7) } }),
                              spec("y{}", { { {{"y","2"}}, 5 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("y{},z{}",
                                   {   { {{"y","2"},{"z","-"}},
                                               op(7,5) } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } }),
                              spec("y{}", { { {{"y","2"}}, 5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},
                                               op(5,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}}, 5 },
                                       { {{"x","1"},{"y","-"}}, 3 } }),
                              spec("y{}", { { {{"y","2"}}, 7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},
                                               op(7,5) } }),
                              spec("y{}", { { {{"y","2"}}, 7 } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}}, 5 },
                                       { {{"x","1"},{"y","-"}}, 3 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},z{}",
                                   {   { {{"x","1"},{"z","3"}},
                                               op(3,11) } }),
                              spec("x{}", { { {{"x","1"}},  3 } }),
                              spec("z{}", { { {{"z","3"}}, 11 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},z{}",
                                   {   { {{"x","1"},{"z","3"}},
                                               op(11,3) } }),
                              spec("z{}",{ { {{"z","3"}}, 11 } }),
                              spec("x{}",{ { {{"x","1"}},  3 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","1"}},
                                               op(3,5) },
                                       { {{"x","2"},{"y","1"}},
                                               op(7,5) } }),
                              spec("x{}",
                                   {   { {{"x","1"}}, 3 },
                                       { {{"x","2"}}, 7 } }),
                              spec("y{}",
                                   {   { {{"y","1"}}, 5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) },
                                       { {{"x","1"},{"y","1"},{"z","2"}},
                                               op(1,13) },
                                       { {{"x","1"},{"y","2"},{"z","1"}},
                                               op(5,11) },
                                       { {{"x","2"},{"y","1"},{"z","1"}},
                                               op(3,7) },
                                       { {{"x","2"},{"y","1"},{"z","2"}},
                                               op(3,13) } }),
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","1"}},  1 },
                                       { {{"x","1"},{"y","2"}},  5 },
                                       { {{"x","2"},{"y","1"}},  3 } }),
                              spec("y{},z{}",
                                   {   { {{"y","1"},{"z","1"}},  7 },
                                       { {{"y","1"},{"z","2"}}, 13 },
                                       { {{"y","2"},{"z","1"}}, 11 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","1"},{"z","1"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","-"},{"z","1"}},
                                               op(5,11) },
                                       { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","1"}}, 11 },
                                       { {{"y","1"},{"z","1"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","1"},{"z","1"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","-"},{"y","-"},{"z", "-"}},
                                               op(5,11) },
                                       { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","-"}}, 11 },
                                       { {{"y","1"},{"z","1"}},  7 } })));
    }

    void test_fixed_dense_cases_apply_op(const Eval &eval,
                                         join_fun_t op)
    {
        TEST_DO(test_apply_op(eval,
                              spec(op(0.1,0.2)), spec(0.1), spec(0.2)));
        TEST_DO(test_apply_op(eval,
                              spec(x(1), Seq({ op(3,5) })),
                              spec(x(1), Seq({ 3 })),
                              spec(x(1), Seq({ 5 }))));
        TEST_DO(test_apply_op(eval,
                              spec(x(1), Seq({ op(3,-5) })),
                              spec(x(1), Seq({ 3 })),
                              spec(x(1), Seq({ -5 }))));
        TEST_DO(test_apply_op(eval,
                              spec(x(2), Seq({ op(3,7), op(5,11) })),
                              spec(x(2), Seq({ 3, 5 })),
                              spec(x(2), Seq({ 7, 11 }))));
        TEST_DO(test_apply_op(eval,
                              spec({x(1),y(1)}, Seq({ op(3,5) })),
                              spec({x(1),y(1)}, Seq({ 3 })),
                              spec({x(1),y(1)}, Seq({ 5 }))));
        TEST_DO(test_apply_op(eval,
                              spec(x(1), Seq({ op(3, 0) })),
                              spec(x(1), Seq({ 3 })),
                              spec(x(2), Seq({ 0, 7 }))));
        TEST_DO(test_apply_op(eval,
                              spec(x(1), Seq({ op(0, 5) })),
                              spec(x(2), Seq({ 0, 3 })),
                              spec(x(1), Seq({ 5 }))));
        TEST_DO(test_apply_op(eval,
                              spec({x(2),y(2),z(2)},
                                   Seq({        op(1,  7), op(1, 11),
                                                op(2, 13), op(2, 17),
                                                op(3,  7), op(3, 11),
                                                op(5, 13), op(5, 17)
                                                })),
                              spec({x(2),y(2)},
                                   Seq({         1,  2,
                                                 3,  5 })),
                              spec({y(2),z(2)},
                                   Seq({         7, 11,
                                                13, 17 }))));
    }

    void test_apply_op(const Eval &eval, join_fun_t op, const Sequence &seq) {
        std::vector<Layout> layouts = {
            {},                                    {},
            {x(5)},                                {x(5)},
            {x(5)},                                {x(3)},
            {x(5)},                                {y(5)},
            {x(5)},                                {x(5),y(5)},
            {x(3),y(5)},                           {x(4),y(4)},
            {x(3),y(5)},                           {y(5),z(7)},
            {x({"a","b","c"})},                    {x({"a","b","c"})},
            {x({"a","b","c"})},                    {x({"a","b"})},
            {x({"a","b","c"})},                    {y({"foo","bar","baz"})},
            {x({"a","b","c"})},                    {x({"a","b","c"}),y({"foo","bar","baz"})},
            {x({"a","b"}),y({"foo","bar","baz"})}, {x({"a","b","c"}),y({"foo","bar"})},
            {x({"a","b"}),y({"foo","bar","baz"})}, {y({"foo","bar"}),z({"i","j","k","l"})},
            {x(3),y({"foo", "bar"})},              {y({"foo", "bar"}),z(7)},
            {x({"a","b","c"}),y(5)},               {y(5),z({"i","j","k","l"})}
        };
        ASSERT_TRUE((layouts.size() % 2) == 0);
        for (size_t i = 0; i < layouts.size(); i += 2) {
            TensorSpec lhs_input = spec(layouts[i], seq);
            TensorSpec rhs_input = spec(layouts[i + 1], seq);
            TEST_STATE(make_string("lhs shape: %s, rhs shape: %s",
                                   lhs_input.type().c_str(),
                                   rhs_input.type().c_str()).c_str());
            Eval::Result expect = ImmediateJoin(op).eval(ref_engine, lhs_input, rhs_input);
            TEST_DO(verify_result(safe(eval).eval(engine, lhs_input, rhs_input), expect));
        }
        TEST_DO(test_fixed_sparse_cases_apply_op(eval, op));
        TEST_DO(test_fixed_dense_cases_apply_op(eval, op));
    }

    void test_apply_op(const vespalib::string &expr, join_fun_t op, const Sequence &seq) {
        TEST_DO(test_apply_op(ImmediateJoin(op), op, seq));
        TEST_DO(test_apply_op(RetainedJoin(op), op, seq));
        TEST_DO(test_apply_op(Expr_TT(expr), op, seq));
        TEST_DO(test_apply_op(Expr_TT(make_string("join(x,y,f(a,b)(%s))", expr.c_str())), op, seq));
    }

    void test_tensor_apply() {
        TEST_DO(test_apply_op("a+b", operation::Add::f, Div10(N())));
        TEST_DO(test_apply_op("a-b", operation::Sub::f, Div10(N())));
        TEST_DO(test_apply_op("a*b", operation::Mul::f, Div10(N())));
        TEST_DO(test_apply_op("a/b", operation::Div::f, Div10(N())));
        TEST_DO(test_apply_op("a%b", operation::Mod::f, Div10(N())));
        TEST_DO(test_apply_op("a^b", operation::Pow::f, Div10(N())));
        TEST_DO(test_apply_op("pow(a,b)", operation::Pow::f, Div10(N())));
        TEST_DO(test_apply_op("a==b", operation::Equal::f, Div10(N())));
        TEST_DO(test_apply_op("a!=b", operation::NotEqual::f, Div10(N())));
        TEST_DO(test_apply_op("a~=b", operation::Approx::f, Div10(N())));
        TEST_DO(test_apply_op("a<b", operation::Less::f, Div10(N())));
        TEST_DO(test_apply_op("a<=b", operation::LessEqual::f, Div10(N())));
        TEST_DO(test_apply_op("a>b", operation::Greater::f, Div10(N())));
        TEST_DO(test_apply_op("a>=b", operation::GreaterEqual::f, Div10(N())));
        TEST_DO(test_apply_op("a&&b", operation::And::f, Mask2Seq(SkipNth(3))));
        TEST_DO(test_apply_op("a||b", operation::Or::f, Mask2Seq(SkipNth(3))));
        TEST_DO(test_apply_op("atan2(a,b)", operation::Atan2::f, Div10(N())));
        TEST_DO(test_apply_op("ldexp(a,b)", operation::Ldexp::f, Div10(N())));
        TEST_DO(test_apply_op("fmod(a,b)", operation::Mod::f, Div10(N())));
        TEST_DO(test_apply_op("min(a,b)", operation::Min::f, Div10(N())));
        TEST_DO(test_apply_op("max(a,b)", operation::Max::f, Div10(N())));
    }

    //-------------------------------------------------------------------------

    void test_dot_product(double expect,
                          const TensorSpec &lhs,
                          const TensorSpec &rhs)
    {
        Expr_TT eval("reduce(a*b,sum)");
        TEST_DO(verify_result(safe(eval).eval(engine, lhs, rhs), spec(expect)));
    }

    void test_dot_product() {
        TEST_DO(test_dot_product(((2 * 7) + (3 * 11) + (5 * 13)),
                                 spec(x(3), Seq({ 2, 3, 5 })),
                                 spec(x(3), Seq({ 7, 11, 13 }))));
        TEST_DO(test_dot_product(((2 * 7) + (3 * 11)),
                                 spec(x(2), Seq({ 2, 3 })),
                                 spec(x(3), Seq({ 7, 11, 13 }))));
        TEST_DO(test_dot_product(((2 * 7) + (3 * 11)),
                                 spec(x(3), Seq({ 2, 3, 5 })),
                                 spec(x(2), Seq({ 7, 11 }))));
    }

    //-------------------------------------------------------------------------

    void test_concat(const TensorSpec &a,
                     const TensorSpec &b,
                     const vespalib::string &dimension,
                     const TensorSpec &expect)
    {
        ImmediateConcat eval(dimension);
        vespalib::string expr = make_string("concat(a,b,%s)", dimension.c_str());
        TEST_DO(verify_result(eval.eval(engine, a, b), expect));
        TEST_DO(verify_result(Expr_TT(expr).eval(engine, a, b), expect));
    }

    void test_concat() {
        TEST_DO(test_concat(spec(10.0), spec(20.0), "x", spec(x(2), Seq({10.0, 20.0}))));
        TEST_DO(test_concat(spec(x(1), Seq({10.0})), spec(20.0), "x", spec(x(2), Seq({10.0, 20.0}))));
        TEST_DO(test_concat(spec(10.0), spec(x(1), Seq({20.0})), "x", spec(x(2), Seq({10.0, 20.0}))));
        TEST_DO(test_concat(spec(x(3), Seq({1.0, 2.0, 3.0})), spec(x(2), Seq({4.0, 5.0})), "x",
                            spec(x(5), Seq({1.0, 2.0, 3.0, 4.0, 5.0}))));
        TEST_DO(test_concat(spec({x(2),y(2)}, Seq({1.0, 2.0, 3.0, 4.0})), spec(y(2), Seq({5.0, 6.0})), "y",
                            spec({x(2),y(4)}, Seq({1.0, 2.0, 5.0, 6.0, 3.0, 4.0, 5.0, 6.0}))));
        TEST_DO(test_concat(spec({x(2),y(2)}, Seq({1.0, 2.0, 3.0, 4.0})), spec(x(2), Seq({5.0, 6.0})), "x",
                            spec({x(4),y(2)}, Seq({1.0, 2.0, 3.0, 4.0, 5.0, 5.0, 6.0, 6.0}))));
        TEST_DO(test_concat(spec(z(3), Seq({1.0, 2.0, 3.0})), spec(y(2), Seq({4.0, 5.0})), "x",
                            spec({x(2),y(2),z(3)}, Seq({1.0, 2.0, 3.0, 1.0, 2.0, 3.0, 4.0, 4.0, 4.0, 5.0, 5.0, 5.0}))));
        TEST_DO(test_concat(spec(y(3), Seq({1.0, 2.0, 3.0})), spec(y(2), Seq({4.0, 5.0})), "x",
                            spec({x(2), y(2)}, Seq({1.0, 2.0, 4.0, 5.0}))));
    }

    //-------------------------------------------------------------------------

    void test_rename(const vespalib::string &expr,
                     const TensorSpec &input,
                     const std::vector<vespalib::string> &from,
                     const std::vector<vespalib::string> &to,
                     const TensorSpec &expect)
    {
        ImmediateRename eval(from, to);
        TEST_DO(verify_result(eval.eval(engine, input), expect));
        TEST_DO(verify_result(Expr_T(expr).eval(engine, input), expect));
    }

    void test_rename() {
        TEST_DO(test_rename("rename(a,x,y)", spec(x(5), N()), {"x"}, {"y"}, spec(y(5), N())));
        TEST_DO(test_rename("rename(a,y,x)", spec({y(5),z(5)}, N()), {"y"}, {"x"}, spec({x(5),z(5)}, N())));
        TEST_DO(test_rename("rename(a,z,x)", spec({y(5),z(5)}, N()), {"z"}, {"x"}, spec({y(5),x(5)}, N())));
        TEST_DO(test_rename("rename(a,x,z)", spec({x(5),y(5)}, N()), {"x"}, {"z"}, spec({z(5),y(5)}, N())));
        TEST_DO(test_rename("rename(a,y,z)", spec({x(5),y(5)}, N()), {"y"}, {"z"}, spec({x(5),z(5)}, N())));
        TEST_DO(test_rename("rename(a,(x,y),(y,x))", spec({x(5),y(5)}, N()), {"x","y"}, {"y","x"}, spec({y(5),x(5)}, N())));
    }

    //-------------------------------------------------------------------------

    void test_tensor_lambda(const vespalib::string &expr, const TensorSpec &expect) {
        TEST_DO(verify_result(Expr_V(expr).eval(engine), expect));
    }

    void test_tensor_lambda() {
        TEST_DO(test_tensor_lambda("tensor(x[10])(x+1)", spec(x(10), N())));
        TEST_DO(test_tensor_lambda("tensor(x[5],y[4])(x*4+(y+1))", spec({x(5),y(4)}, N())));
        TEST_DO(test_tensor_lambda("tensor(x[5],y[4])(x==y)", spec({x(5),y(4)},
                                Seq({           1.0, 0.0, 0.0, 0.0,
                                                0.0, 1.0, 0.0, 0.0,
                                                0.0, 0.0, 1.0, 0.0,
                                                0.0, 0.0, 0.0, 1.0,
                                                0.0, 0.0, 0.0, 0.0}))));
    }

    //-------------------------------------------------------------------------

    void verify_encode_decode(const TensorSpec &spec,
                              const TensorEngine &encode_engine,
                              const TensorEngine &decode_engine)
    {
        Stash stash;
        nbostream data;
        encode_engine.encode(make_value(encode_engine, spec, stash), data);
        TEST_DO(verify_result(Eval::Result(decode_engine, *decode_engine.decode(data)), spec));
    }

    void verify_encode_decode(const TensorSpec &spec) {
        TEST_DO(verify_encode_decode(spec, engine, ref_engine));
        if (&engine != &ref_engine) {
            TEST_DO(verify_encode_decode(spec, ref_engine, engine));
        }
    }

    void test_binary_format_spec(Cursor &test) {
        Stash stash;
        TensorSpec spec = TensorSpec::from_slime(test["tensor"]);
        const Inspector &binary = test["binary"];
        EXPECT_GREATER(binary.entries(), 0u);
        nbostream encoded;
        engine.encode(make_value(engine, spec, stash), encoded);
        test.setData("encoded", Memory(encoded.peek(), encoded.size()));
        bool matched_encode = false;
        for (size_t i = 0; i < binary.entries(); ++i) {
            nbostream data = extract_data(binary[i].asString());
            matched_encode = (matched_encode || is_same(encoded, data));
            TEST_DO(verify_result(Eval::Result(engine, *engine.decode(data)), spec));
            EXPECT_EQUAL(data.size(), 0u);
        }
        EXPECT_TRUE(matched_encode);
    }

    void test_binary_format_spec() {
        vespalib::string path = module_path;
        path.append("src/apps/make_tensor_binary_format_test_spec/test_spec.json");
        MappedFileInput file(path);
        EXPECT_TRUE(file.valid());
        auto handle_test = [this](Slime &slime)
                           {
                               size_t fail_cnt = TEST_MASTER.getProgress().failCnt;
                               TEST_DO(test_binary_format_spec(slime.get()));
                               if (TEST_MASTER.getProgress().failCnt > fail_cnt) {
                                   fprintf(stderr, "failed:\n%s", slime.get().toString().c_str());
                               }
                           };
        auto handle_summary = [](Slime &slime)
                              {
                                  EXPECT_GREATER(slime["num_tests"].asLong(), 0);
                              };
        for_each_test(file, handle_test, handle_summary);
    }

    void test_binary_format() {
        TEST_DO(test_binary_format_spec());
        TEST_DO(verify_encode_decode(spec(42)));
        TEST_DO(verify_encode_decode(spec({x(3)}, N())));
        TEST_DO(verify_encode_decode(spec({x(3),y(5)}, N())));
        TEST_DO(verify_encode_decode(spec({x(3),y(5),z(7)}, N())));
        TEST_DO(verify_encode_decode(spec({x({"a","b","c"})}, N())));
        TEST_DO(verify_encode_decode(spec({x({"a","b","c"}),y({"foo","bar"})}, N())));
        TEST_DO(verify_encode_decode(spec({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}, N())));
        TEST_DO(verify_encode_decode(spec({x(3),y({"foo", "bar"}),z(7)}, N())));
        TEST_DO(verify_encode_decode(spec({x({"a","b","c"}),y(5),z({"i","j","k","l"})}, N())));
    }

    //-------------------------------------------------------------------------

    void run_tests() {
        TEST_DO(test_tensor_create_type());
        TEST_DO(test_tensor_reduce());
        TEST_DO(test_tensor_map());
        TEST_DO(test_tensor_apply());
        TEST_DO(test_dot_product());
        TEST_DO(test_concat());
        TEST_DO(test_rename());
        TEST_DO(test_tensor_lambda());
        TEST_DO(test_binary_format());
    }
};

} // namespace vespalib::eval::test::<unnamed>

void
TensorConformance::run_tests(const vespalib::string &module_path, const TensorEngine &engine)
{
    TestContext ctx(module_path, engine);
    fprintf(stderr, "module path: '%s'\n", ctx.module_path.c_str());
    ctx.run_tests();
}

} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
