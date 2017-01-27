// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include "tensor_conformance.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/interpreted_function.h>

namespace vespalib {
namespace eval {
namespace test {
namespace {

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

TensorSpec spec(const vespalib::string &type,
                const std::vector<std::pair<TensorSpec::Address, TensorSpec::Value>> &cells) {
    TensorSpec spec("tensor(" + type + ")");

    for (const auto &cell : cells) {
        spec.add(cell.first, cell.second);
    }
    return spec;
}

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
        Result(const Value &value) : _type(Type::ERROR), _number(error_value), _tensor("error") {
            if (value.is_double()) {
                _type = Type::NUMBER;
                _number = value.as_double();
                _tensor = TensorSpec("double").add({}, _number);
            } else if (value.is_tensor()) {
                _type = Type::TENSOR;
                _tensor = value.as_tensor()->engine().to_spec(*value.as_tensor());
                if (_tensor.type() == "double") {
                    _number = as_double(_tensor);
                }
            }
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
        return Result(ErrorValue());
    }
    virtual Result eval(const TensorEngine &, const TensorSpec &) const {
        TEST_ERROR("wrong signature");
        return Result(ErrorValue());
    }
    virtual Result eval(const TensorEngine &, const TensorSpec &, const TensorSpec &) const {
        TEST_ERROR("wrong signature");
        return Result(ErrorValue());
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
            return Result(ErrorValue());
        }
    }
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        try {
            return unsafe.eval(engine, a);
        } catch (std::exception &e) {
            TEST_ERROR(e.what());
            return Result(ErrorValue());
        }

    }
    Result eval(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &b) const override {
        try {
            return unsafe.eval(engine, a, b);
        } catch (std::exception &e) {
            TEST_ERROR(e.what());
            return Result(ErrorValue());
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
        InterpretedFunction::Context ctx;
        return Result(check_type(ifun.eval(ctx), types.get_type(fun.root())));
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
        InterpretedFunction::Context ctx;
        TensorValue va(engine.create(a));
        ctx.add_param(va);
        return Result(check_type(ifun.eval(ctx), types.get_type(fun.root())));
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
        InterpretedFunction::Context ctx;
        TensorValue va(engine.create(a));
        TensorValue vb(engine.create(b));
        ctx.add_param(va);
        ctx.add_param(vb);
        return Result(check_type(ifun.eval(ctx), types.get_type(fun.root())));
    }
};

const Value &make_value(const TensorEngine &engine, const TensorSpec &spec, Stash &stash) {
    if (spec.type() == "double") {
        double number = as_double(spec);
        return stash.create<DoubleValue>(number);
    }
    return stash.create<TensorValue>(engine.create(spec));
}

// evaluate tensor reduce operation using tensor engine immediate api
struct ImmediateReduce : Eval {
    const BinaryOperation &op;
    std::vector<vespalib::string> dimensions;
    ImmediateReduce(const BinaryOperation &op_in) : op(op_in), dimensions() {}
    ImmediateReduce(const BinaryOperation &op_in, const vespalib::string &dimension)
        : op(op_in), dimensions({dimension}) {}    
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        Stash stash;
        return Result(engine.reduce(*engine.create(a), op, dimensions, stash));
    }
};

// evaluate tensor map operation using tensor engine immediate api
struct ImmediateMap : Eval {
    const UnaryOperation &op;
    ImmediateMap(const UnaryOperation &op_in) : op(op_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        Stash stash;
        return Result(engine.map(op, *engine.create(a), stash));
    }
};

// evaluate tensor apply operation using tensor engine immediate api
struct ImmediateApply : Eval {
    const BinaryOperation &op;
    ImmediateApply(const BinaryOperation &op_in) : op(op_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &b) const override {
        Stash stash;
        return Result(engine.apply(op, *engine.create(a), *engine.create(b), stash));
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
        return Result(engine.concat(lhs, rhs, dimension, stash));
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
        return Result(engine.rename(lhs, from, to, stash));
    }
};

const size_t tensor_id_a = 11;
const size_t tensor_id_b = 12;
const size_t map_operation_id = 22;

// input used when evaluating in retained mode
struct Input : TensorFunction::Input {
    std::vector<TensorValue> tensors;
    const UnaryOperation *map_op;
    Input(std::unique_ptr<Tensor> a) : tensors(), map_op(nullptr) {
        tensors.emplace_back(std::move(a));
    }
    Input(std::unique_ptr<Tensor> a, const UnaryOperation &op) : tensors(), map_op(&op) {
        tensors.emplace_back(std::move(a));
    }
    Input(std::unique_ptr<Tensor> a, std::unique_ptr<Tensor> b) : tensors(), map_op(nullptr) {
        tensors.emplace_back(std::move(a));
        tensors.emplace_back(std::move(b));
    }
    const Value &get_tensor(size_t id) const override {
        size_t offset = (id - tensor_id_a);
        ASSERT_GREATER(tensors.size(), offset);
        return tensors[offset];
    }
    const UnaryOperation &get_map_operation(size_t id) const {
        ASSERT_TRUE(map_op != nullptr);
        ASSERT_EQUAL(id, map_operation_id);
        return *map_op;
    }
};

// evaluate tensor reduce operation using tensor engine retained api
struct RetainedReduce : Eval {
    const BinaryOperation &op;
    std::vector<vespalib::string> dimensions;
    RetainedReduce(const BinaryOperation &op_in) : op(op_in), dimensions() {}
    RetainedReduce(const BinaryOperation &op_in, const vespalib::string &dimension)
        : op(op_in), dimensions({dimension}) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        auto a_type = ValueType::from_spec(a.type());
        auto ir = tensor_function::reduce(tensor_function::inject(a_type, tensor_id_a), op, dimensions);
        ValueType expect_type = ir->result_type;
        auto fun = engine.compile(std::move(ir));
        Input input(engine.create(a));
        Stash stash;
        return Result(check_type(fun->eval(input, stash), expect_type));
    }
};

// evaluate tensor map operation using tensor engine retained api
struct RetainedMap : Eval {
    const UnaryOperation &op;
    RetainedMap(const UnaryOperation &op_in) : op(op_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a) const override {
        auto a_type = ValueType::from_spec(a.type());
        auto ir = tensor_function::map(map_operation_id, tensor_function::inject(a_type, tensor_id_a));
        ValueType expect_type = ir->result_type;
        auto fun = engine.compile(std::move(ir));
        Input input(engine.create(a), op);
        Stash stash;
        return Result(check_type(fun->eval(input, stash), expect_type));
    }
};

// evaluate tensor apply operation using tensor engine retained api
struct RetainedApply : Eval {
    const BinaryOperation &op;
    RetainedApply(const BinaryOperation &op_in) : op(op_in) {}
    Result eval(const TensorEngine &engine, const TensorSpec &a, const TensorSpec &b) const override {
        auto a_type = ValueType::from_spec(a.type());
        auto b_type = ValueType::from_spec(b.type());
        auto ir = tensor_function::apply(op, tensor_function::inject(a_type, tensor_id_a),
                                         tensor_function::inject(b_type, tensor_id_b));
        ValueType expect_type = ir->result_type;
        auto fun = engine.compile(std::move(ir));
        Input input(engine.create(a), engine.create(b));
        Stash stash;
        return Result(check_type(fun->eval(input, stash), expect_type));
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

// Test wrapper to avoid passing global test parameters around
struct TestContext {

    const TensorEngine &ref_engine;
    const TensorEngine &engine;
    bool test_mixed_cases;
    size_t skip_count;

    TestContext(const TensorEngine &engine_in, bool test_mixed_cases_in)
        : ref_engine(SimpleTensorEngine::ref()), engine(engine_in),
          test_mixed_cases(test_mixed_cases_in), skip_count(0) {}

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

    //-------------------------------------------------------------------------

    void verify_create_type(const vespalib::string &type_spec) {
        auto tensor = engine.create(TensorSpec(type_spec));
        EXPECT_TRUE(&engine == &tensor->engine());
        EXPECT_EQUAL(type_spec, engine.type_of(*tensor).to_spec());
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

    //-------------------------------------------------------------------------

    void verify_equal(const TensorSpec &a, const TensorSpec &b) {
        auto ta = tensor(a);
        auto tb = tensor(b);
        EXPECT_EQUAL(a, b);
        EXPECT_EQUAL(*ta, *tb);
        TensorSpec spec = engine.to_spec(*ta);
        TensorSpec ref_spec = ref_engine.to_spec(*ref_engine.create(a));
        EXPECT_EQUAL(spec, ref_spec);
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

    //-------------------------------------------------------------------------

    void verify_not_equal(const TensorSpec &a, const TensorSpec &b) {
        auto ta = tensor(a);
        auto tb = tensor(b);
        EXPECT_NOT_EQUAL(a, b);
        EXPECT_NOT_EQUAL(b, a);
        EXPECT_NOT_EQUAL(*ta, *tb);
        EXPECT_NOT_EQUAL(*tb, *ta);
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

    //-------------------------------------------------------------------------

    void verify_reduce_result(const Eval &eval, const TensorSpec &a, const Eval::Result &expect) {
        TEST_DO(verify_result(eval.eval(engine, a), expect));
    }

    void test_reduce_op(const vespalib::string &name, const BinaryOperation &op, const Sequence &seq) {
        std::vector<Layout> layouts = {
            {x(3)},
            {x(3),y(5)},
            {x(3),y(5),z(7)},
            {x({"a","b","c"})},
            {x({"a","b","c"}),y({"foo","bar"})},
            {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}
        };
        if (mixed(2 * 4)) {
            layouts.push_back({x(3),y({"foo", "bar"}),z(7)});
            layouts.push_back({x({"a","b","c"}),y(5),z({"i","j","k","l"})});
        }
        for (const Layout &layout: layouts) {
            TensorSpec input = spec(layout, seq);
            for (const Domain &domain: layout) {
                Eval::Result expect = ImmediateReduce(op, domain.dimension).eval(ref_engine, input);
                TEST_STATE(make_string("shape: %s, reduce dimension: %s",
                                       infer_type(layout).c_str(), domain.dimension.c_str()).c_str());
                if (!name.empty()) {
                    vespalib::string expr = make_string("%s(a,%s)", name.c_str(), domain.dimension.c_str());
                    TEST_DO(verify_reduce_result(Expr_T(expr), input, expect));
                }
                TEST_DO(verify_reduce_result(ImmediateReduce(op, domain.dimension), input, expect));
                TEST_DO(verify_reduce_result(RetainedReduce(op, domain.dimension), input, expect));
            }
            {
                Eval::Result expect = ImmediateReduce(op).eval(ref_engine, input);
                TEST_STATE(make_string("shape: %s, reduce all dimensions",
                                       infer_type(layout).c_str()).c_str());
                if (!name.empty()) {
                    vespalib::string expr = make_string("%s(a)", name.c_str());
                    TEST_DO(verify_reduce_result(Expr_T(expr), input, expect));
                }
                TEST_DO(verify_reduce_result(ImmediateReduce(op), input, expect));
                TEST_DO(verify_reduce_result(RetainedReduce(op), input, expect));
            }
        }
    }

    void test_tensor_reduce() {
        TEST_DO(test_reduce_op("sum", operation::Add(), N()));
        TEST_DO(test_reduce_op("", operation::Mul(), Sigmoid(N())));
        TEST_DO(test_reduce_op("", operation::Min(), N()));
        TEST_DO(test_reduce_op("", operation::Max(), N()));
    }

    //-------------------------------------------------------------------------

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
            TEST_DO(verify_result(eval.eval(engine, spec(layout, seq)), spec(layout, OpSeq(seq, ref_op))));
        }
    }

    void test_map_op(const vespalib::string &expr, const UnaryOperation &op, const Sequence &seq) {
        TEST_DO(test_map_op(ImmediateMap(op), op, seq));
        TEST_DO(test_map_op(RetainedMap(op), op, seq));
        TEST_DO(test_map_op(Expr_T(expr), op, seq));
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

    //-------------------------------------------------------------------------

    void test_apply_op(const Eval &eval,
                       const TensorSpec &expect,
                       const TensorSpec &lhs,
                       const TensorSpec &rhs) {
        TEST_DO(verify_result(safe(eval).eval(engine, lhs, rhs), expect));
    }

    void test_fixed_sparse_cases_apply_op(const Eval &eval,
                                          const BinaryOperation &op)
    {
        TEST_DO(test_apply_op(eval,
                              spec("x{}", {}),
                              spec("x{}", { { {{"x","1"}}, 3 } }),
                              spec("x{}", { { {{"x","2"}}, 5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{}", { { {{"x","1"}}, op.eval(3,5) } }),
                              spec("x{}", { { {{"x","1"}}, 3 } }),
                              spec("x{}", { { {{"x","1"}}, 5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{}", { { {{"x","1"}}, op.eval(3,-5) } }),
                              spec("x{}", { { {{"x","1"}},  3 } }),
                              spec("x{}", { { {{"x","1"}}, -5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","-"},{"y","2"},{"z","-"}},
                                               op.eval(5,7) },
                                       { {{"x","1"},{"y","-"},{"z","3"}},
                                               op.eval(3,11) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},  5 },
                                       { {{"x","1"},{"y","-"}},  3 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","-"},{"y","2"},{"z","-"}},
                                               op.eval(7,5) },
                                       { {{"x","1"},{"y","-"},{"z","3"}},
                                               op.eval(11,3) } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},  5 },
                                       { {{"x","1"},{"y","-"}},  3 } })));
        TEST_DO(test_apply_op(eval,
                              spec("y{},z{}",
                                   {   { {{"y","2"},{"z","-"}},
                                               op.eval(5,7) } }),
                              spec("y{}", { { {{"y","2"}}, 5 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("y{},z{}",
                                   {   { {{"y","2"},{"z","-"}},
                                               op.eval(7,5) } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } }),
                              spec("y{}", { { {{"y","2"}}, 5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},
                                               op.eval(5,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}}, 5 },
                                       { {{"x","1"},{"y","-"}}, 3 } }),
                              spec("y{}", { { {{"y","2"}}, 7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},
                                               op.eval(7,5) } }),
                              spec("y{}", { { {{"y","2"}}, 7 } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}}, 5 },
                                       { {{"x","1"},{"y","-"}}, 3 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},z{}",
                                   {   { {{"x","1"},{"z","3"}},
                                               op.eval(3,11) } }),
                              spec("x{}", { { {{"x","1"}},  3 } }),
                              spec("z{}", { { {{"z","3"}}, 11 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},z{}",
                                   {   { {{"x","1"},{"z","3"}},
                                               op.eval(11,3) } }),
                              spec("z{}",{ { {{"z","3"}}, 11 } }),
                              spec("x{}",{ { {{"x","1"}},  3 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","1"}},
                                               op.eval(3,5) },
                                       { {{"x","2"},{"y","1"}},
                                               op.eval(7,5) } }),
                              spec("x{}",
                                   {   { {{"x","1"}}, 3 },
                                       { {{"x","2"}}, 7 } }),
                              spec("y{}",
                                   {   { {{"y","1"}}, 5 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","1"},{"z","1"}},
                                               op.eval(1,7) },
                                       { {{"x","1"},{"y","1"},{"z","2"}},
                                               op.eval(1,13) },
                                       { {{"x","1"},{"y","2"},{"z","1"}},
                                               op.eval(5,11) },
                                       { {{"x","2"},{"y","1"},{"z","1"}},
                                               op.eval(3,7) },
                                       { {{"x","2"},{"y","1"},{"z","2"}},
                                               op.eval(3,13) } }),
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
                                               op.eval(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","1"},{"z","1"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","-"},{"z","1"}},
                                               op.eval(5,11) },
                                       { {{"x","1"},{"y","1"},{"z","1"}},
                                               op.eval(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","1"}}, 11 },
                                       { {{"y","1"},{"z","1"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","1"},{"z","1"}},
                                               op.eval(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","1"},{"z","1"}},  7 } })));
        TEST_DO(test_apply_op(eval,
                              spec("x{},y{},z{}",
                                   {   { {{"x","-"},{"y","-"},{"z", "-"}},
                                               op.eval(5,11) },
                                       { {{"x","1"},{"y","1"},{"z","1"}},
                                               op.eval(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","-"}}, 11 },
                                       { {{"y","1"},{"z","1"}},  7 } })));
    }

    void test_fixed_dense_cases_apply_op(const Eval &eval,
                                         const BinaryOperation &op)
    {
        TEST_DO(test_apply_op(eval,
                              spec(op.eval(0.1,0.2)), spec(0.1), spec(0.2)));
        TEST_DO(test_apply_op(eval,
                              spec(x(1), Seq({ op.eval(3,5) })),
                              spec(x(1), Seq({ 3 })),
                              spec(x(1), Seq({ 5 }))));
        TEST_DO(test_apply_op(eval,
                              spec(x(1), Seq({ op.eval(3,-5) })),
                              spec(x(1), Seq({ 3 })),
                              spec(x(1), Seq({ -5 }))));
        TEST_DO(test_apply_op(eval,
                              spec(x(2), Seq({ op.eval(3,7), op.eval(5,11) })),
                              spec(x(2), Seq({ 3, 5 })),
                              spec(x(2), Seq({ 7, 11 }))));
        TEST_DO(test_apply_op(eval,
                              spec({x(1),y(1)}, Seq({ op.eval(3,5) })),
                              spec({x(1),y(1)}, Seq({ 3 })),
                              spec({x(1),y(1)}, Seq({ 5 }))));
        TEST_DO(test_apply_op(eval,
                              spec(x(1), Seq({ op.eval(3, 0) })),
                              spec(x(1), Seq({ 3 })),
                              spec(x(2), Seq({ 0, 7 }))));
        TEST_DO(test_apply_op(eval,
                              spec(x(1), Seq({ op.eval(0, 5) })),
                              spec(x(2), Seq({ 0, 3 })),
                              spec(x(1), Seq({ 5 }))));
        TEST_DO(test_apply_op(eval,
                              spec({x(2),y(2),z(2)},
                                   Seq({        op.eval(1,  7), op.eval(1, 11),
                                                op.eval(2, 13), op.eval(2, 17),
                                                op.eval(3,  7), op.eval(3, 11),
                                                op.eval(5, 13), op.eval(5, 17)
                                                })),
                              spec({x(2),y(2)},
                                   Seq({         1,  2,
                                                 3,  5 })),
                              spec({y(2),z(2)},
                                   Seq({         7, 11,
                                                13, 17 }))));
    }

    void test_apply_op(const Eval &eval, const BinaryOperation &op, const Sequence &seq) {
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
            {x({"a","b"}),y({"foo","bar","baz"})}, {y({"foo","bar"}),z({"i","j","k","l"})}
        };
        if (mixed(2)) {
            layouts.push_back({x(3),y({"foo", "bar"})});
            layouts.push_back({y({"foo", "bar"}),z(7)});
            layouts.push_back({x({"a","b","c"}),y(5)});
            layouts.push_back({y(5),z({"i","j","k","l"})});
        }
        ASSERT_TRUE((layouts.size() % 2) == 0);
        for (size_t i = 0; i < layouts.size(); i += 2) {
            TensorSpec lhs_input = spec(layouts[i], seq);
            TensorSpec rhs_input = spec(layouts[i + 1], seq);
            TEST_STATE(make_string("lhs shape: %s, rhs shape: %s",
                                   lhs_input.type().c_str(),
                                   rhs_input.type().c_str()).c_str());
            Eval::Result expect = ImmediateApply(op).eval(ref_engine, lhs_input, rhs_input); 
            TEST_DO(verify_result(safe(eval).eval(engine, lhs_input, rhs_input), expect));
        }
        TEST_DO(test_fixed_sparse_cases_apply_op(eval, op));
        TEST_DO(test_fixed_dense_cases_apply_op(eval, op));
    }

    void test_apply_op(const vespalib::string &expr, const BinaryOperation &op, const Sequence &seq) {
        TEST_DO(test_apply_op(ImmediateApply(op), op, seq));
        TEST_DO(test_apply_op(RetainedApply(op), op, seq));
        TEST_DO(test_apply_op(Expr_TT(expr), op, seq));
    }

    void test_tensor_apply() {
        TEST_DO(test_apply_op("a+b", operation::Add(), Div10(N())));
        TEST_DO(test_apply_op("a-b", operation::Sub(), Div10(N())));
        TEST_DO(test_apply_op("a*b", operation::Mul(), Div10(N())));
        TEST_DO(test_apply_op("a/b", operation::Div(), Div10(N())));
        TEST_DO(test_apply_op("a^b", operation::Pow(), Div10(N())));
        TEST_DO(test_apply_op("pow(a,b)", operation::Pow(), Div10(N())));
        TEST_DO(test_apply_op("a==b", operation::Equal(), Div10(N())));
        TEST_DO(test_apply_op("a!=b", operation::NotEqual(), Div10(N())));
        TEST_DO(test_apply_op("a~=b", operation::Approx(), Div10(N())));
        TEST_DO(test_apply_op("a<b", operation::Less(), Div10(N())));
        TEST_DO(test_apply_op("a<=b", operation::LessEqual(), Div10(N())));
        TEST_DO(test_apply_op("a>b", operation::Greater(), Div10(N())));
        TEST_DO(test_apply_op("a>=b", operation::GreaterEqual(), Div10(N())));
        TEST_DO(test_apply_op("a&&b", operation::And(), Mask2Seq(SkipNth(3))));
        TEST_DO(test_apply_op("a||b", operation::Or(), Mask2Seq(SkipNth(3))));
        TEST_DO(test_apply_op("atan2(a,b)", operation::Atan2(), Div10(N())));
        TEST_DO(test_apply_op("ldexp(a,b)", operation::Ldexp(), Div10(N())));
        TEST_DO(test_apply_op("fmod(a,b)", operation::Fmod(), Div10(N())));
        TEST_DO(test_apply_op("min(a,b)", operation::Min(), Div10(N())));
        TEST_DO(test_apply_op("max(a,b)", operation::Max(), Div10(N())));
    }

    //-------------------------------------------------------------------------

    void test_dot_product(double expect,
                          const TensorSpec &lhs,
                          const TensorSpec &rhs)
    {
        Expr_TT eval("sum(a*b)");
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

    void test_concat(const TensorSpec &expect,
                     const TensorSpec &a,
                     const TensorSpec &b,
                     const vespalib::string &dimension)
    {
        ImmediateConcat eval(dimension);
        TEST_DO(verify_result(eval.eval(engine, a, b), expect));
    }

    void test_concat() {
        TEST_DO(test_concat(spec(x(2), Seq({10.0, 20.0})), spec(10.0), spec(20.0), "x"));
        TEST_DO(test_concat(spec(x(2), Seq({10.0, 20.0})), spec(x(1), Seq({10.0})), spec(20.0), "x"));
        TEST_DO(test_concat(spec(x(2), Seq({10.0, 20.0})), spec(10.0), spec(x(1), Seq({20.0})), "x"));
        TEST_DO(test_concat(spec(x(5), Seq({1.0, 2.0, 3.0, 4.0, 5.0})),
                            spec(x(3), Seq({1.0, 2.0, 3.0})),
                            spec(x(2), Seq({4.0, 5.0})), "x"));
        TEST_DO(test_concat(spec({x(2),y(4)}, Seq({1.0, 2.0, 5.0, 6.0, 3.0, 4.0, 5.0, 6.0})),
                            spec({x(2),y(2)}, Seq({1.0, 2.0, 3.0, 4.0})),
                            spec(y(2), Seq({5.0, 6.0})), "y"));
        TEST_DO(test_concat(spec({x(4),y(2)}, Seq({1.0, 2.0, 3.0, 4.0, 5.0, 5.0, 6.0, 6.0})),
                            spec({x(2),y(2)}, Seq({1.0, 2.0, 3.0, 4.0})),
                            spec(x(2), Seq({5.0, 6.0})), "x"));
        TEST_DO(test_concat(spec({x(2),y(2),z(3)}, Seq({1.0, 2.0, 3.0, 1.0, 2.0, 3.0, 4.0, 4.0, 4.0, 5.0, 5.0, 5.0})),
                            spec(z(3), Seq({1.0, 2.0, 3.0})),
                            spec(y(2), Seq({4.0, 5.0})), "x"));
        TEST_DO(test_concat(spec({x(2), y(2)}, Seq({1.0, 2.0, 4.0, 5.0})),
                            spec(y(3), Seq({1.0, 2.0, 3.0})),
                            spec(y(2), Seq({4.0, 5.0})), "x"));
    }

    //-------------------------------------------------------------------------

    void test_rename(const TensorSpec &expect,
                     const TensorSpec &input,
                     const std::vector<vespalib::string> &from,
                     const std::vector<vespalib::string> &to)
    {
        ImmediateRename eval(from, to);
        TEST_DO(verify_result(eval.eval(engine, input), expect));
    }

    void test_rename() {
        TEST_DO(test_rename(spec(y(5), N()), spec(x(5), N()), {"x"}, {"y"}));
        TEST_DO(test_rename(spec({x(5),z(5)}, N()), spec({y(5),z(5)}, N()), {"y"}, {"x"}));
        TEST_DO(test_rename(spec({y(5),x(5)}, N()), spec({y(5),z(5)}, N()), {"z"}, {"x"}));
        TEST_DO(test_rename(spec({z(5),y(5)}, N()), spec({x(5),y(5)}, N()), {"x"}, {"z"}));
        TEST_DO(test_rename(spec({x(5),z(5)}, N()), spec({x(5),y(5)}, N()), {"y"}, {"z"}));
        TEST_DO(test_rename(spec({y(5),x(5)}, N()), spec({x(5),y(5)}, N()), {"x","y"}, {"y","x"}));
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

    void run_tests() {
        TEST_DO(test_tensor_create_type());
        TEST_DO(test_tensor_equality());
        TEST_DO(test_tensor_inequality());
        TEST_DO(test_tensor_reduce());
        TEST_DO(test_tensor_map());
        TEST_DO(test_tensor_apply());
        TEST_DO(test_dot_product());
        TEST_DO(test_concat());
        TEST_DO(test_rename());
        TEST_DO(test_tensor_lambda());
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
