// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include "tensor_conformance.h"
#include <vespa/vespalib/eval/simple_tensor_engine.h>
#include <vespa/vespalib/eval/tensor_spec.h>
#include <vespa/vespalib/eval/function.h>
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
    virtual double get(size_t i) const = 0;
    virtual ~Sequence() {}
};

// Sequence of natural numbers (starting at 1)
struct N : Sequence {
    double get(size_t i) const override { return (1.0 + i); }
};

// Sequence of another sequence divided by 10
struct Div10 : Sequence {
    const Sequence &seq;
    Div10(const Sequence &seq_in) : seq(seq_in) {}
    double get(size_t i) const override { return (seq.get(i) / 10.0); }
};

// Sequence of a unary operator applied to a sequence
struct OpSeq : Sequence {
    const Sequence &seq;
    const UnaryOperation &op;
    OpSeq(const Sequence &seq_in, const UnaryOperation &op_in) : seq(seq_in), op(op_in) {}
    double get(size_t i) const override { return op.eval(seq.get(i)); }
};

// pre-defined sequence of numbers
struct Seq : Sequence {
    std::vector<double> seq;
    Seq(const std::vector<double> &seq_in) : seq(seq_in) {}
    double get(size_t i) const override {
        ASSERT_LESS(i, seq.size());
        return seq[i];
    }
};

// custom op1
struct MyOp : CustomUnaryOperation {
    double eval(double b) const override { return ((b + 1) * 2); }
};

// A collection of labels for a single dimension
struct Space {
    vespalib::string name;
    size_t size; // indexed
    std::vector<vespalib::string> keys; // mapped
    Space(const vespalib::string &name_in, size_t size_in)
        : name(name_in), size(size_in), keys() {}
    Space(const vespalib::string &name_in, const std::vector<vespalib::string> &keys_in)
        : name(name_in), size(0), keys(keys_in) {}
};

// Infer the tensor type spanned by the given spaces
vespalib::string infer_type(const std::vector<Space> &spaces) {
    if (spaces.empty()) {
        return "double";
    }
    std::vector<ValueType::Dimension> dimensions;
    for (const auto &space: spaces) {
        if (space.size == 0) {
            dimensions.emplace_back(space.name); // mapped
        } else {
            dimensions.emplace_back(space.name, space.size); // indexed
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

    const std::vector<Space> &_spaces;
    const Sequence           &_seq;
    TensorSpec                _spec;
    Address                   _addr;
    size_t                    _gen_idx;

    void generate(size_t space_idx) {
        if (space_idx == _spaces.size()) {
            _spec.add(_addr, _seq.get(_gen_idx++));
        } else {
            const Space &space = _spaces[space_idx];
            if (space.size > 0) { // indexed
                for (size_t i = 0; i < space.size; ++i) {
                    _addr.emplace(space.name, Label(i)).first->second = Label(i);
                    generate(space_idx + 1);
                }
            } else { // mapped
                for (const vespalib::string &key: space.keys) {
                    _addr.emplace(space.name, Label(key)).first->second = Label(key);
                    generate(space_idx + 1);
                }
            }
        }
    }

public:
    TensorSpecBuilder(const std::vector<Space> &spaces, const Sequence &seq)
        : _spaces(spaces), _seq(seq), _spec(infer_type(spaces)), _addr(), _gen_idx(0) {}
    TensorSpec build() {
        generate(0);
        return _spec;
    }
};

// Test wrapper to avoid passing global test parameters around
struct TestContext {
    const TensorEngine &engine;
    TestContext(const TensorEngine &engine_in) : engine(engine_in) {}

    std::unique_ptr<Tensor> tensor(const std::vector<Space> &spaces, const Sequence &seq) {
        return engine.create(TensorSpecBuilder(spaces, seq).build());
    }

    void verify_create_type(const vespalib::string &type_spec) {
        auto tensor = engine.create(TensorSpec(type_spec));
        EXPECT_TRUE(&engine == &tensor->engine());
        EXPECT_EQUAL(type_spec, engine.type_of(*tensor).to_spec());
    }

    void verify_not_equal(const Tensor &a, const Tensor &b) {
        EXPECT_FALSE(a == b);
        EXPECT_FALSE(b == a);
    }

    void verify_verbatim_tensor(const vespalib::string &tensor_expr, const Tensor &expect) {
        InterpretedFunction::Context ctx;
        InterpretedFunction ifun(engine, Function::parse(tensor_expr));
        const Value &result = ifun.eval(ctx);
        if (EXPECT_TRUE(result.is_tensor())) {
            const Tensor *actual = result.as_tensor();
            EXPECT_EQUAL(*actual, expect);
        }
    }

    void test_tensor_create_type() {
        TEST_DO(verify_create_type("double"));
        TEST_DO(verify_create_type("tensor(x{})"));
        TEST_DO(verify_create_type("tensor(x{},y{})"));
        TEST_DO(verify_create_type("tensor(x[5])"));
        TEST_DO(verify_create_type("tensor(x[5],y[10])"));
        TEST_DO(verify_create_type("tensor(x{},y[10])"));
        TEST_DO(verify_create_type("tensor(x[5],y{})"));
    }

    void test_tensor_inequality() {
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("double")),
                                 *engine.create(TensorSpec("tensor(x{})"))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("double")),
                                 *engine.create(TensorSpec("tensor(x[1])"))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x{})")),
                                 *engine.create(TensorSpec("tensor(y{})"))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x[1])")),
                                 *engine.create(TensorSpec("tensor(x[2])"))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x[1])")),
                                 *engine.create(TensorSpec("tensor(y[1])"))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x{})")),
                                 *engine.create(TensorSpec("tensor(x[1])"))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("double").add({}, 1)),
                                 *engine.create(TensorSpec("double").add({}, 2))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x{})").add({{"x", "a"}}, 1)),
                                 *engine.create(TensorSpec("tensor(x{})").add({{"x", "a"}}, 2))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x{})").add({{"x", "a"}}, 1)),
                                 *engine.create(TensorSpec("tensor(x{})").add({{"x", "b"}}, 1))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x{})").add({{"x", "a"}}, 1)),
                                 *engine.create(TensorSpec("tensor(x{},y{})").add({{"x", "a"},{"y", "a"}}, 1))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x[1])").add({{"x", 0}}, 1)),
                                 *engine.create(TensorSpec("tensor(x[1])").add({{"x", 0}}, 2))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x[1])").add({{"x", 0}}, 1)),
                                 *engine.create(TensorSpec("tensor(x[2])").add({{"x", 0}}, 1))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x[2])").add({{"x", 0}}, 1)),
                                 *engine.create(TensorSpec("tensor(x[2])").add({{"x", 1}}, 1))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x[1])").add({{"x", 0}}, 1)),
                                 *engine.create(TensorSpec("tensor(x[1],y[1])").add({{"x", 0},{"y", 0}}, 1))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x{},y[1])").add({{"x", "a"},{"y", 0}}, 1)),
                                 *engine.create(TensorSpec("tensor(x{},y[1])").add({{"x", "a"},{"y", 0}}, 2))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x{},y[1])").add({{"x", "a"},{"y", 0}}, 1)),
                                 *engine.create(TensorSpec("tensor(x{},y[1])").add({{"x", "b"},{"y", 0}}, 1))));
        TEST_DO(verify_not_equal(*engine.create(TensorSpec("tensor(x[2],y{})").add({{"x", 0},{"y", "a"}}, 1)),
                                 *engine.create(TensorSpec("tensor(x[2],y{})").add({{"x", 1},{"y", "a"}}, 1))));
    }

    void test_verbatim_tensors() {
        TEST_DO(verify_verbatim_tensor("{}", *engine.create(TensorSpec("double"))));
        TEST_DO(verify_verbatim_tensor("{{}:5}", *engine.create(TensorSpec("double").add({}, 5))));
        TEST_DO(verify_verbatim_tensor("{{x:foo}:1,{x:bar}:2,{x:baz}:3}", *engine.create(TensorSpec("tensor(x{})")
                                .add({{"x", "foo"}}, 1)
                                .add({{"x", "bar"}}, 2)
                                .add({{"x", "baz"}}, 3))));
        TEST_DO(verify_verbatim_tensor("{{x:foo,y:a}:1,{y:b,x:bar}:2}", *engine.create(TensorSpec("tensor(x{},y{})")
                                .add({{"x", "foo"}, {"y", "a"}}, 1)
                                .add({{"x", "bar"}, {"y", "b"}}, 2))));
    }

    void verify_map_op(const UnaryOperation &op, const Tensor &input, const Tensor &expect) {
        Stash stash;
        const Value &result = engine.map(op, input, stash);
        if (EXPECT_TRUE(result.is_tensor())) {
            const Tensor &actual = *result.as_tensor();
            EXPECT_EQUAL(actual, expect);
        }
    }

    void test_map_op(const UnaryOperation &op, const Sequence &seq) {
        TEST_DO(verify_map_op(op,
                              *tensor({Space("x", 10)}, seq),
                              *tensor({Space("x", 10)}, OpSeq(seq, op))));
        TEST_DO(verify_map_op(op,
                              *tensor({Space("x", {"a", "b", "c"})}, seq),
                              *tensor({Space("x", {"a", "b", "c"})}, OpSeq(seq, op))));
    }

    void test_tensor_map() {
        TEST_DO(test_map_op(operation::Floor(), Div10(N())));
        TEST_DO(test_map_op(operation::Ceil(), Div10(N())));
        TEST_DO(test_map_op(operation::Sqrt(), Div10(N())));
        TEST_DO(test_map_op(MyOp(), Div10(N())));
    }

    void run_all_tests() {
        TEST_DO(test_tensor_create_type());
        TEST_DO(test_tensor_inequality());
        TEST_DO(test_verbatim_tensors());
        TEST_DO(test_tensor_map());
    }
};

} // namespace vespalib::eval::test::<unnamed>

void
TensorConformance::run_tests(const TensorEngine &engine)
{
    TestContext ctx(engine);
    ctx.run_all_tests();
}

} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
