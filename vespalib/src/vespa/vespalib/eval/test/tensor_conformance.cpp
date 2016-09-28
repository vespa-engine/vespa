// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include "tensor_conformance.h"
#include <vespa/vespalib/eval/simple_tensor_engine.h>
#include <vespa/vespalib/eval/tensor_spec.h>
#include <vespa/vespalib/eval/function.h>
#include <vespa/vespalib/eval/tensor_function.h>
#include <vespa/vespalib/eval/interpreted_function.h>

namespace vespalib {
namespace eval {
namespace test {
namespace {

//    virtual ValueType type_of(const Tensor &tensor) const = 0;
//    virtual bool equal(const Tensor &a, const Tensor &b) const = 0;

//    virtual TensorFunction::UP compile(tensor_function::Node_UP expr) const { return std::move(expr); }

//    virtual std::unique_ptr<Tensor> create(const TensorSpec &spec) const = 0;

//    virtual const Value &reduce(const Tensor &tensor, const BinaryOperation &op, const std::vector<vespalib::string> &dimensions, Stash &stash) const = 0;
//    virtual const Value &map(const UnaryOperation &op, const Tensor &a, Stash &stash) const = 0;
//    virtual const Value &apply(const BinaryOperation &op, const Tensor &a, const Tensor &b, Stash &stash) const = 0;

// Random access sequence of numbers
struct Sequence {
    virtual double operator[](size_t i) const = 0;
    virtual ~Sequence() {}
};

// Sequence of natural numbers (starting at 1)
struct N : Sequence {
    double operator[](size_t i) const override { return (1.0 + i); }
};

// Sequence of another sequence divided by 10
struct Div10 : Sequence {
    const Sequence &seq;
    Div10(const Sequence &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override { return (seq[i] / 10.0); }
};

// Sequence of another sequence minus 2
struct Sub2 : Sequence {
    const Sequence &seq;
    Sub2(const Sequence &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override { return (seq[i] - 2.0); }
};

// Sequence of a unary operator applied to a sequence
struct OpSeq : Sequence {
    const Sequence &seq;
    const UnaryOperation &op;
    OpSeq(const Sequence &seq_in, const UnaryOperation &op_in) : seq(seq_in), op(op_in) {}
    double operator[](size_t i) const override { return op.eval(seq[i]); }
};

// Sequence of applying sigmoid to another sequence
struct Sigmoid : Sequence {
    const Sequence &seq;
    Sigmoid(const Sequence &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override { return operation::Sigmoid().eval(seq[i]); }
};

// pre-defined sequence of numbers
struct Seq : Sequence {
    std::vector<double> seq;
    Seq() : seq() {}
    Seq(const std::vector<double> &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override {
        ASSERT_LESS(i, seq.size());
        return seq[i];
    }
};

// Random access bit mask
struct Mask {
    virtual bool operator[](size_t i) const = 0;
    virtual ~Mask() {}
};

// Mask with all bits set
struct All : Mask {
    bool operator[](size_t) const override { return true; }
};

// Mask with no bits set
struct None : Mask {
    bool operator[](size_t) const override { return false; }
};

// Mask with false for each Nth index
struct SkipNth : Mask {
    size_t n;
    SkipNth(size_t n_in) : n(n_in) {}
    bool operator[](size_t i) const override { return (i % n) != 0; }
};

// pre-defined mask
struct Bits : Mask {
    std::vector<bool> bits;
    Bits(const std::vector<bool> &bits_in) : bits(bits_in) {}
    bool operator[](size_t i) const override {
        ASSERT_LESS(i, bits.size());
        return bits[i];
    }
};

// A mask converted to a sequence of two unique values (mapped from true and false)
struct Mask2Seq : Sequence {
    const Mask &mask;
    double true_value;
    double false_value;
    Mask2Seq(const Mask &mask_in, double true_value_in = 1.0, double false_value_in = 0.0)
        : mask(mask_in), true_value(true_value_in), false_value(false_value_in) {}
    double operator[](size_t i) const override { return mask[i] ? true_value : false_value; }
};

// custom op1
struct MyOp : CustomUnaryOperation {
    double eval(double a) const override { return ((a + 1) * 2); }
};

// A collection of labels for a single dimension
struct Domain {
    vespalib::string dimension;
    size_t size; // indexed
    std::vector<vespalib::string> keys; // mapped
    Domain(const vespalib::string &dimension_in, size_t size_in)
        : dimension(dimension_in), size(size_in), keys() {}
    Domain(const vespalib::string &dimension_in, const std::vector<vespalib::string> &keys_in)
        : dimension(dimension_in), size(0), keys(keys_in) {}
};
using Layout = std::vector<Domain>;

Domain x() { return Domain("x", {}); }
Domain x(size_t size) { return Domain("x", size); }
Domain x(const std::vector<vespalib::string> &keys) { return Domain("x", keys); }

Domain y() { return Domain("y", {}); }
Domain y(size_t size) { return Domain("y", size); }
Domain y(const std::vector<vespalib::string> &keys) { return Domain("y", keys); }

Domain z(size_t size) { return Domain("z", size); }
Domain z(const std::vector<vespalib::string> &keys) { return Domain("z", keys); }

// Infer the tensor type spanned by the given spaces
vespalib::string infer_type(const Layout &layout) {
    if (layout.empty()) {
        return "double";
    }
    std::vector<ValueType::Dimension> dimensions;
    for (const auto &domain: layout) {
        if (domain.size == 0) {
            dimensions.emplace_back(domain.dimension); // mapped
        } else {
            dimensions.emplace_back(domain.dimension, domain.size); // indexed
        }
    }
    return ValueType::tensor_type(dimensions).to_spec();
}

// Wrapper for the things needed to generate a tensor
struct Source {
    using Address = TensorSpec::Address;

    const Layout   &layout;
    const Sequence &seq;
    const Mask     &mask;
    Source(const Layout &layout_in, const Sequence &seq_in, const Mask &mask_in)
        : layout(layout_in), seq(seq_in), mask(mask_in) {}
};

// Mix layout with a number sequence to make a tensor spec
class TensorSpecBuilder
{
private:
    using Label = TensorSpec::Label;
    using Address = TensorSpec::Address;

    Source     _source;
    TensorSpec _spec;
    Address    _addr;
    size_t     _idx;

    void generate(size_t layout_idx) {
        if (layout_idx == _source.layout.size()) {
            if (_source.mask[_idx]) {
                _spec.add(_addr, _source.seq[_idx]);
            }
            ++_idx;
        } else {
            const Domain &domain = _source.layout[layout_idx];
            if (domain.size > 0) { // indexed
                for (size_t i = 0; i < domain.size; ++i) {
                    _addr.emplace(domain.dimension, Label(i)).first->second = Label(i);
                    generate(layout_idx + 1);
                }
            } else { // mapped
                for (const vespalib::string &key: domain.keys) {
                    _addr.emplace(domain.dimension, Label(key)).first->second = Label(key);
                    generate(layout_idx + 1);
                }
            }
        }
    }

public:
    TensorSpecBuilder(const Layout &layout, const Sequence &seq, const Mask &mask)
        : _source(layout, seq, mask), _spec(infer_type(layout)), _addr(), _idx(0) {}
    TensorSpec build() {
        generate(0);
        return _spec;
    }
};
TensorSpec spec(const Layout &layout, const Sequence &seq, const Mask &mask) {
    return TensorSpecBuilder(layout, seq, mask).build();
}
TensorSpec spec(const Layout &layout, const Sequence &seq) {
    return spec(layout, seq, All());
}
TensorSpec spec(const Layout &layout) {
    return spec(layout, Seq(), None());
}
TensorSpec spec(const Domain &domain, const Sequence &seq, const Mask &mask) {
    return spec(Layout({domain}), seq, mask);
}
TensorSpec spec(const Domain &domain, const Sequence &seq) {
    return spec(Layout({domain}), seq);
}
TensorSpec spec(const Domain &domain) {
    return spec(Layout({domain}));
}
TensorSpec spec(double value) {
    return spec(Layout({}), Seq({value}));
}
TensorSpec spec() {
    return spec(Layout({}));
}

// abstract evaluation verification wrapper
struct Eval {
    virtual void verify(const TensorEngine &engine, const TensorSpec &expect) const {
        (void) engine;
        (void) expect;
        TEST_ERROR("wrong signature");
    }
    virtual void verify(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &expect) const {
        (void) engine;
        (void) a;
        (void) expect;
        TEST_ERROR("wrong signature");
    }
    virtual ~Eval() {}
};

// expression(void) -> tensor
struct Expr_V_T : Eval {
    const vespalib::string &expr;
    Expr_V_T(const vespalib::string &expr_in) : expr(expr_in) {}
    void verify(const TensorEngine &engine, const TensorSpec &expect) const override {
        InterpretedFunction::Context ctx;
        InterpretedFunction ifun(engine, Function::parse(expr));
        const Value &result = ifun.eval(ctx);
        if (EXPECT_TRUE(result.is_tensor())) {
            TensorSpec actual = engine.to_spec(*result.as_tensor());
            EXPECT_EQUAL(actual, expect);
        }
    }
};

// expression(tensor) -> tensor
struct Expr_T_T : Eval {
    const vespalib::string &expr;
    Expr_T_T(const vespalib::string &expr_in) : expr(expr_in) {}
    void verify(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &expect) const override {
        TensorValue va(engine.create(a));
        InterpretedFunction::Context ctx;
        InterpretedFunction ifun(engine, Function::parse(expr));
        ctx.add_param(va);
        const Value &result = ifun.eval(ctx);
        if (EXPECT_TRUE(result.is_tensor())) {
            TensorSpec actual = engine.to_spec(*result.as_tensor());
            EXPECT_EQUAL(actual, expect);
        }
    }
};

// evaluate tensor map operation using tensor engine immediate api
struct ImmediateMap : Eval {
    const UnaryOperation &op;
    ImmediateMap(const UnaryOperation &op_in) : op(op_in) {}
    void verify(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &expect) const override {
        Stash stash;
        const Value &result = engine.map(op, *engine.create(a), stash);
        if (EXPECT_TRUE(result.is_tensor())) {
            TensorSpec actual = engine.to_spec(*result.as_tensor());
            EXPECT_EQUAL(actual, expect);
        }
    }
};

const size_t tensor_id = 11;
const size_t map_operation_id = 22;

// input needed to evaluate a map operation in retained mode
struct TensorMapInput : TensorFunction::Input {
    TensorValue tensor;
    const UnaryOperation &map_op;
    TensorMapInput(std::unique_ptr<Tensor> in, const UnaryOperation &op) : tensor(std::move(in)), map_op(op) {}
    const Value &get_tensor(size_t id) const override {
        ASSERT_EQUAL(id, tensor_id);
        return tensor;
    }
    const UnaryOperation &get_map_operation(size_t id) const {
        ASSERT_EQUAL(id, map_operation_id);
        return map_op;
    }
};

// evaluate tensor map operation using tensor engine retained api
struct RetainedMap : Eval {
    const UnaryOperation &op;
    RetainedMap(const UnaryOperation &op_in) : op(op_in) {}
    void verify(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &expect) const override {
        auto a_type = ValueType::from_spec(a.type());
        auto ir = tensor_function::map(map_operation_id, tensor_function::inject(a_type, tensor_id));
        auto fun = engine.compile(std::move(ir));
        TensorMapInput input(engine.create(a), op);
        Stash stash;
        const Value &result = fun->eval(input, stash);
        if (EXPECT_TRUE(result.is_tensor())) {
            TensorSpec actual = engine.to_spec(*result.as_tensor());
            EXPECT_EQUAL(actual, expect);
        }
    }
};

// placeholder used for unused values in a sequence
const double X = 31212.0;

// NaN value
const double my_nan = std::numeric_limits<double>::quiet_NaN();

// Test wrapper to avoid passing global test parameters around
struct TestContext {

    const TensorEngine &engine;
    bool test_mixed_cases;
    size_t skip_count;

    TestContext(const TensorEngine &engine_in, bool test_mixed_cases_in)
        : engine(engine_in), test_mixed_cases(test_mixed_cases_in), skip_count(0) {}

    std::unique_ptr<Tensor> tensor(const TensorSpec &spec) {
        auto result = engine.create(spec);
        EXPECT_EQUAL(spec.type(), engine.type_of(*result).to_spec());
        return result;
    }

    bool mixed(size_t n) {
        if (!test_mixed_cases) {
            skip_count += n;
        }
        return test_mixed_cases;
    }

    void verify_create_type(const vespalib::string &type_spec) {
        auto tensor = engine.create(TensorSpec(type_spec));
        EXPECT_TRUE(&engine == &tensor->engine());
        EXPECT_EQUAL(type_spec, engine.type_of(*tensor).to_spec());
    }

    void verify_equal(const TensorSpec &a, const TensorSpec &b) {
        auto ta = tensor(a);
        auto tb = tensor(b);
        EXPECT_EQUAL(a, b);
        EXPECT_EQUAL(*ta, *tb);
    }

    void verify_not_equal(const TensorSpec &a, const TensorSpec &b) {
        auto ta = tensor(a);
        auto tb = tensor(b);
        EXPECT_NOT_EQUAL(a, b);
        EXPECT_NOT_EQUAL(b, a);
        EXPECT_NOT_EQUAL(*ta, *tb);
        EXPECT_NOT_EQUAL(*tb, *ta);
    }

    void verify_verbatim_tensor(const vespalib::string &tensor_expr, const TensorSpec &expect) {
        Expr_V_T(tensor_expr).verify(engine, expect);
    }

    void test_tensor_create_type() {
        TEST_DO(verify_create_type("double"));
        TEST_DO(verify_create_type("tensor(x{})"));
        TEST_DO(verify_create_type("tensor(x{},y{})"));
        TEST_DO(verify_create_type("tensor(x[5])"));
        TEST_DO(verify_create_type("tensor(x[5],y[10])"));
        if (mixed(2)) {
            TEST_DO(verify_create_type("tensor(x{},y[10])"));
            TEST_DO(verify_create_type("tensor(x[5],y{})"));
        }
    }

    void test_tensor_equality() {
        TEST_DO(verify_equal(spec(), spec()));
        TEST_DO(verify_equal(spec(10.0), spec(10.0)));
        TEST_DO(verify_equal(spec(x()), spec(x())));
        TEST_DO(verify_equal(spec(x({"a"}), Seq({1})), spec(x({"a"}), Seq({1}))));
        TEST_DO(verify_equal(spec({x({"a"}),y({"a"})}, Seq({1})), spec({y({"a"}),x({"a"})}, Seq({1}))));
        TEST_DO(verify_equal(spec(x(3)), spec(x(3))));
        TEST_DO(verify_equal(spec({x(1),y(1)}, Seq({1})), spec({y(1),x(1)}, Seq({1}))));
        if (mixed(2)) {
            TEST_DO(verify_equal(spec({x({"a"}),y(1)}, Seq({1})), spec({y(1),x({"a"})}, Seq({1}))));
            TEST_DO(verify_equal(spec({y({"a"}),x(1)}, Seq({1})), spec({x(1),y({"a"})}, Seq({1}))));
        }
    }

    void test_tensor_inequality() {
        TEST_DO(verify_not_equal(spec(1.0), spec(2.0)));
        TEST_DO(verify_not_equal(spec(), spec(x())));
        TEST_DO(verify_not_equal(spec(), spec(x(1))));
        TEST_DO(verify_not_equal(spec(x()), spec(x(1))));
        TEST_DO(verify_not_equal(spec(x()), spec(y())));
        TEST_DO(verify_not_equal(spec(x(1)), spec(x(2))));
        TEST_DO(verify_not_equal(spec(x(1)), spec(y(1))));
        TEST_DO(verify_not_equal(spec(x({"a"}), Seq({1})), spec(x({"a"}), Seq({2}))));
        TEST_DO(verify_not_equal(spec(x({"a"}), Seq({1})), spec(x({"b"}), Seq({1}))));
        TEST_DO(verify_not_equal(spec(x({"a"}), Seq({1})), spec({x({"a"}),y({"a"})}, Seq({1}))));
        TEST_DO(verify_not_equal(spec(x(1), Seq({1})), spec(x(1), Seq({2}))));
        TEST_DO(verify_not_equal(spec(x(1), Seq({1})), spec(x(2), Seq({1}), Bits({1,0}))));
        TEST_DO(verify_not_equal(spec(x(2), Seq({1,1}), Bits({1,0})),
                                 spec(x(2), Seq({1,1}), Bits({0,1}))));
        TEST_DO(verify_not_equal(spec(x(1), Seq({1})), spec({x(1),y(1)}, Seq({1}))));
        if (mixed(3)) {
            TEST_DO(verify_not_equal(spec({x({"a"}),y(1)}, Seq({1})), spec({x({"a"}),y(1)}, Seq({2}))));
            TEST_DO(verify_not_equal(spec({x({"a"}),y(1)}, Seq({1})), spec({x({"b"}),y(1)}, Seq({1}))));
            TEST_DO(verify_not_equal(spec({x(2),y({"a"})}, Seq({1}), Bits({1,0})),
                                     spec({x(2),y({"a"})}, Seq({X,1}), Bits({0,1}))));
        }
    }

    void test_verbatim_tensors() {
        TEST_DO(verify_verbatim_tensor("{}", spec(0.0)));
        TEST_DO(verify_verbatim_tensor("{{}:5}", spec(5.0)));
        TEST_DO(verify_verbatim_tensor("{{x:foo}:1,{x:bar}:2,{x:baz}:3}", spec(x({"foo","bar","baz"}), Seq({1,2,3}))));
        TEST_DO(verify_verbatim_tensor("{{x:foo,y:a}:1,{y:b,x:bar}:2}",
                                       spec({x({"foo","bar"}),y({"a","b"})}, Seq({1,X,X,2}), Bits({1,0,0,1}))));
    }

    void test_map_op(const Eval &eval, const UnaryOperation &ref_op, const Sequence &seq) {
        std::vector<Layout> layouts = {
            {},
            {x(3)},
            {x(3),y(5)},
            {x(3),y(5),z(7)},
            {x({"a","b","c"})},
            {x({"a","b","c"}),y({"foo","bar"})},
            {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}
        };
        if (mixed(2)) {
            layouts.push_back({x(3),y({"foo", "bar"}),z(7)});
            layouts.push_back({x({"a","b","c"}),y(5),z({"i","j","k","l"})});
        }
        for (const Layout &layout: layouts) {
            TEST_DO(eval.verify(engine, spec(layout, seq), spec(layout, OpSeq(seq, ref_op))));
        }
    }

    void test_map_op(const vespalib::string &expr, const UnaryOperation &op, const Sequence &seq) {
        TEST_DO(test_map_op(ImmediateMap(op), op, seq));
        TEST_DO(test_map_op(RetainedMap(op), op, seq));
        TEST_DO(test_map_op(Expr_T_T(expr), op, seq));
    }

    void test_tensor_map() {
        TEST_DO(test_map_op("-a", operation::Neg(), Sub2(Div10(N()))));
        TEST_DO(test_map_op("!a", operation::Not(), Mask2Seq(SkipNth(3))));
        TEST_DO(test_map_op("cos(a)", operation::Cos(), Div10(N())));
        TEST_DO(test_map_op("sin(a)", operation::Sin(), Div10(N())));
        TEST_DO(test_map_op("tan(a)", operation::Tan(), Div10(N())));
        TEST_DO(test_map_op("cosh(a)", operation::Cosh(), Div10(N())));
        TEST_DO(test_map_op("sinh(a)", operation::Sinh(), Div10(N())));
        TEST_DO(test_map_op("tanh(a)", operation::Tanh(), Div10(N())));
        TEST_DO(test_map_op("acos(a)", operation::Acos(), Sigmoid(Div10(N()))));
        TEST_DO(test_map_op("asin(a)", operation::Asin(), Sigmoid(Div10(N()))));
        TEST_DO(test_map_op("atan(a)", operation::Atan(), Div10(N())));
        TEST_DO(test_map_op("exp(a)", operation::Exp(), Div10(N())));
        TEST_DO(test_map_op("log10(a)", operation::Log10(), Div10(N())));
        TEST_DO(test_map_op("log(a)", operation::Log(), Div10(N())));
        TEST_DO(test_map_op("sqrt(a)", operation::Sqrt(), Div10(N())));
        TEST_DO(test_map_op("ceil(a)", operation::Ceil(), Div10(N())));
        TEST_DO(test_map_op("fabs(a)", operation::Fabs(), Div10(N())));
        TEST_DO(test_map_op("floor(a)", operation::Floor(), Div10(N())));
        TEST_DO(test_map_op("isNan(a)", operation::IsNan(), Mask2Seq(SkipNth(3), 1.0, my_nan)));
        TEST_DO(test_map_op("relu(a)", operation::Relu(), Sub2(Div10(N()))));
        TEST_DO(test_map_op("sigmoid(a)", operation::Sigmoid(), Sub2(Div10(N()))));
        TEST_DO(test_map_op("(a+1)*2", MyOp(), Div10(N())));
    }

    void run_tests() {
        TEST_DO(test_tensor_create_type());
        TEST_DO(test_tensor_equality());
        TEST_DO(test_tensor_inequality());
        TEST_DO(test_verbatim_tensors());
        TEST_DO(test_tensor_map());
    }
};

} // namespace vespalib::eval::test::<unnamed>

void
TensorConformance::run_tests(const TensorEngine &engine, bool test_mixed_cases)
{
    TestContext ctx(engine, test_mixed_cases);
    ctx.run_tests();
    if (ctx.skip_count > 0) {
        fprintf(stderr, "WARNING: skipped %zu mixed test cases\n", ctx.skip_count);
    }
}

} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
