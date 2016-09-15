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

// Sequence of a unary operator applied to a sequence
struct OpSeq : Sequence {
    const Sequence &seq;
    const UnaryOperation &op;
    OpSeq(const Sequence &seq_in, const UnaryOperation &op_in) : seq(seq_in), op(op_in) {}
    double operator[](size_t i) const override { return op.eval(seq[i]); }
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

// pre-defined mask
struct Bits : Mask {
    std::vector<bool> bits;
    Bits(const std::vector<bool> &bits_in) : bits(bits_in) {}
    bool operator[](size_t i) const override {
        ASSERT_LESS(i, bits.size());
        return bits[i];
    }
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

Domain z() { return Domain("z", {}); }
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

// Mix spaces with a number sequence to make a tensor spec
class TensorSpecBuilder
{
private:
    using Label = TensorSpec::Label;
    using Address = TensorSpec::Address;

    const Layout   &_layout;
    const Sequence &_seq;
    const Mask     &_mask;
    TensorSpec      _spec;
    Address         _addr;
    size_t          _idx;

    void generate(size_t layout_idx) {
        if (layout_idx == _layout.size()) {
            if (_mask[_idx]) {
                _spec.add(_addr, _seq[_idx]);
            }
            ++_idx;
        } else {
            const Domain &domain = _layout[layout_idx];
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
        : _layout(layout), _seq(seq), _mask(mask), _spec(infer_type(layout)), _addr(), _idx(0) {}
    TensorSpec build() {
        generate(0);
        return _spec;
    }
};

using Tensor_UP = std::unique_ptr<Tensor>;

// small utility used to capture passed tensor references for uniform handling
struct TensorRef {
    const Tensor &ref;
    TensorRef(const Tensor &ref_in) : ref(ref_in) {}
    TensorRef(const Tensor_UP &up_ref) : ref(*(up_ref.get())) {}
};

// abstract evaluation verification wrapper
struct Eval {
    virtual void verify(const TensorEngine &engine, TensorRef expect) const {
        (void) engine;
        (void) expect;
        TEST_ERROR("wrong signature");
    }
    virtual void verify(const TensorEngine &engine, TensorRef a, TensorRef expect) const {
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
    void verify(const TensorEngine &engine, TensorRef expect) const override {
        InterpretedFunction::Context ctx;
        InterpretedFunction ifun(engine, Function::parse(expr));
        const Value &result = ifun.eval(ctx);
        if (EXPECT_TRUE(result.is_tensor())) {
            const Tensor *actual = result.as_tensor();
            EXPECT_EQUAL(*actual, expect.ref);
        }
    }
};

// expression(tensor) -> tensor
struct Expr_T_T : Eval {
    const vespalib::string &expr;
    Expr_T_T(const vespalib::string &expr_in) : expr(expr_in) {}
    void verify(const TensorEngine &engine, TensorRef a, TensorRef expect) const override {
        TensorValue va(a.ref);
        InterpretedFunction::Context ctx;
        InterpretedFunction ifun(engine, Function::parse(expr));
        ctx.add_param(va);
        const Value &result = ifun.eval(ctx);
        if (EXPECT_TRUE(result.is_tensor())) {
            const Tensor *actual = result.as_tensor();
            EXPECT_EQUAL(*actual, expect.ref);
        }
    }
};

// evaluate tensor map operation using tensor engine immediate api
struct ImmediateMap : Eval {
    const UnaryOperation &op;
    ImmediateMap(const UnaryOperation &op_in) : op(op_in) {}
    void verify(const TensorEngine &engine, TensorRef a, TensorRef expect) const override {
        Stash stash;
        const Value &result = engine.map(op, a.ref, stash);
        if (EXPECT_TRUE(result.is_tensor())) {
            const Tensor *actual = result.as_tensor();
            EXPECT_EQUAL(*actual, expect.ref);
        }
    }
};

// input needed to evaluate a map operation in retained mode
struct TensorMapInput : TensorFunction::Input {
    TensorValue tensor;
    const UnaryOperation &map_op;
    TensorMapInput(TensorRef in, const UnaryOperation &op) : tensor(in.ref), map_op(op) {}
    const Value &get_tensor(size_t id) const override {
        ASSERT_EQUAL(id, 11u);
        return tensor;
    }
    const UnaryOperation &get_map_operation(size_t id) const {
        ASSERT_EQUAL(id, 22u);
        return map_op;
    }
};

// evaluate tensor map operation using tensor engine retained api
struct RetainedMap : Eval {
    const UnaryOperation &op;
    RetainedMap(const UnaryOperation &op_in) : op(op_in) {}
    void verify(const TensorEngine &engine, TensorRef a, TensorRef expect) const override {
        auto a_type = a.ref.engine().type_of(a.ref);
        auto ir = tensor_function::map(22, tensor_function::inject(a_type, 11));
        auto fun = engine.compile(std::move(ir));
        TensorMapInput input(a, op);
        Stash stash;
        const Value &result = fun->eval(input, stash);
        if (EXPECT_TRUE(result.is_tensor())) {
            const Tensor *actual = result.as_tensor();
            EXPECT_EQUAL(*actual, expect.ref);
        }
    }
};

// placeholder used for unused values in a sequence
const double X = 31212.0;

// Test wrapper to avoid passing global test parameters around
struct TestContext {

    const TensorEngine &engine;
    bool test_mixed_cases;
    TestContext(const TensorEngine &engine_in, bool test_mixed_cases_in)
        : engine(engine_in), test_mixed_cases(test_mixed_cases_in) {}

    bool mixed() {
        if (!test_mixed_cases) {
            fprintf(stderr, "skipping some tests since mixed testing is disabled\n");
        }
        return test_mixed_cases;
    }

    Tensor_UP tensor(const Layout &layout, const Sequence &seq, const Mask &mask) {
        TensorSpec spec = TensorSpecBuilder(layout, seq, mask).build();
        Tensor_UP result = engine.create(spec);
        EXPECT_EQUAL(spec.type(), engine.type_of(*result).to_spec());
        return result;
    }
    Tensor_UP tensor(const Layout &layout, const Sequence &seq) {
        return tensor(layout, seq, All());
    }
    Tensor_UP tensor(const Layout &layout) {
        return tensor(layout, Seq(), None());
    }
    Tensor_UP tensor(const Domain &domain, const Sequence &seq, const Mask &mask) {
        return tensor(Layout({domain}), seq, mask);
    }
    Tensor_UP tensor(const Domain &domain, const Sequence &seq) {
        return tensor(Layout({domain}), seq);
    }
    Tensor_UP tensor(const Domain &domain) {
        return tensor(Layout({domain}));
    }
    Tensor_UP tensor(double value) {
        return tensor(Layout({}), Seq({value}));
    }
    Tensor_UP tensor() {
        return tensor(Layout({}));
    }

    void verify_create_type(const vespalib::string &type_spec) {
        auto tensor = engine.create(TensorSpec(type_spec));
        EXPECT_TRUE(&engine == &tensor->engine());
        EXPECT_EQUAL(type_spec, engine.type_of(*tensor).to_spec());
    }

    void verify_not_equal(TensorRef a, TensorRef b) {
        EXPECT_FALSE(a.ref == b.ref);
        EXPECT_FALSE(b.ref == a.ref);
    }

    void verify_verbatim_tensor(const vespalib::string &tensor_expr, TensorRef expect) {
        Expr_V_T(tensor_expr).verify(engine, expect);
    }

    void test_tensor_create_type() {
        TEST_DO(verify_create_type("double"));
        TEST_DO(verify_create_type("tensor(x{})"));
        TEST_DO(verify_create_type("tensor(x{},y{})"));
        TEST_DO(verify_create_type("tensor(x[5])"));
        TEST_DO(verify_create_type("tensor(x[5],y[10])"));
        if (mixed()) {
            TEST_DO(verify_create_type("tensor(x{},y[10])"));
            TEST_DO(verify_create_type("tensor(x[5],y{})"));
        }
    }

    void test_tensor_inequality() {
        TEST_DO(verify_not_equal(tensor(1.0), tensor(2.0)));
        TEST_DO(verify_not_equal(tensor(), tensor(x())));
        TEST_DO(verify_not_equal(tensor(), tensor(x(1))));
        TEST_DO(verify_not_equal(tensor(x()), tensor(x(1))));
        TEST_DO(verify_not_equal(tensor(x()), tensor(y())));
        TEST_DO(verify_not_equal(tensor(x(1)), tensor(x(2))));
        TEST_DO(verify_not_equal(tensor(x(1)), tensor(y(1))));
        TEST_DO(verify_not_equal(tensor(x({"a"}), Seq({1})), tensor(x({"a"}), Seq({2}))));
        TEST_DO(verify_not_equal(tensor(x({"a"}), Seq({1})), tensor(x({"b"}), Seq({1}))));
        TEST_DO(verify_not_equal(tensor(x({"a"}), Seq({1})), tensor({x({"a"}),y({"a"})}, Seq({1}))));
        TEST_DO(verify_not_equal(tensor(x(1), Seq({1})), tensor(x(1), Seq({2}))));
        TEST_DO(verify_not_equal(tensor(x(1), Seq({1})), tensor(x(2), Seq({1}), Bits({1,0}))));
        TEST_DO(verify_not_equal(tensor(x(2), Seq({1,1}), Bits({1,0})),
                                 tensor(x(2), Seq({1,1}), Bits({0,1}))));
        TEST_DO(verify_not_equal(tensor(x(1), Seq({1})), tensor({x(1),y(1)}, Seq({1}))));
        if (mixed()) {
            TEST_DO(verify_not_equal(tensor({x({"a"}),y(1)}, Seq({1})), tensor({x({"a"}),y(1)}, Seq({2}))));
            TEST_DO(verify_not_equal(tensor({x({"a"}),y(1)}, Seq({1})), tensor({x({"b"}),y(1)}, Seq({1}))));
            TEST_DO(verify_not_equal(tensor({x(2),y({"a"})}, Seq({1}), Bits({1,0})),
                                     tensor({x(2),y({"a"})}, Seq({X,1}), Bits({0,1}))));
        }
    }

    void test_verbatim_tensors() {
        TEST_DO(verify_verbatim_tensor("{}", tensor()));
        TEST_DO(verify_verbatim_tensor("{{}:5}", tensor(5.0)));
        TEST_DO(verify_verbatim_tensor("{{x:foo}:1,{x:bar}:2,{x:baz}:3}", tensor(x({"foo","bar","baz"}), Seq({1,2,3}))));
        TEST_DO(verify_verbatim_tensor("{{x:foo,y:a}:1,{y:b,x:bar}:2}",
                                       tensor({x({"foo","bar"}),y({"a","b"})}, Seq({1,X,X,2}), Bits({1,0,0,1}))));
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
        if (mixed()) {
            layouts.push_back({x(3),y({"foo", "bar"}),z(7)});
            layouts.push_back({x({"a","b","c"}),y(5),z({"i","j","k","l"})});
        }
        for (const Layout &layout: layouts) {
            TEST_DO(eval.verify(engine, tensor(layout, seq), tensor(layout, OpSeq(seq, ref_op))));
        }
    }

    void test_tensor_map() {
        TEST_DO(test_map_op(ImmediateMap(operation::Floor()), operation::Floor(), Div10(N())));
        TEST_DO(test_map_op(RetainedMap(operation::Floor()), operation::Floor(), Div10(N())));
        TEST_DO(test_map_op(Expr_T_T("floor(a)"), operation::Floor(), Div10(N())));
        //---------------------------------------------------------------------
        TEST_DO(test_map_op(ImmediateMap(operation::Ceil()), operation::Ceil(), Div10(N())));
        TEST_DO(test_map_op(RetainedMap(operation::Ceil()), operation::Ceil(), Div10(N())));
        TEST_DO(test_map_op(Expr_T_T("ceil(a)"), operation::Ceil(), Div10(N())));
        //---------------------------------------------------------------------
        TEST_DO(test_map_op(ImmediateMap(operation::Sqrt()), operation::Sqrt(), Div10(N())));
        TEST_DO(test_map_op(RetainedMap(operation::Sqrt()), operation::Sqrt(), Div10(N())));
        TEST_DO(test_map_op(Expr_T_T("sqrt(a)"), operation::Sqrt(), Div10(N())));
        //---------------------------------------------------------------------
        TEST_DO(test_map_op(ImmediateMap(MyOp()), MyOp(), Div10(N())));
        TEST_DO(test_map_op(RetainedMap(MyOp()), MyOp(), Div10(N())));
        TEST_DO(test_map_op(Expr_T_T("(a+1)*2"), MyOp(), Div10(N())));
    }

    void run_tests() {
        TEST_DO(test_tensor_create_type());
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
}

} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
