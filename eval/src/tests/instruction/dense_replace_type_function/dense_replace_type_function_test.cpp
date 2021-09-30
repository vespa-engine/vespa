// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/instruction/replace_type_function.h>
#include <vespa/eval/eval/test/gen_spec.h>

using namespace vespalib::eval::tensor_function;
using namespace vespalib::eval::test;
using namespace vespalib::eval;
using namespace vespalib;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

TypedCells getCellsRef(const Value &value) {
    return value.cells();
}

struct ChildMock : Leaf {
    bool is_mutable;
    ChildMock(const ValueType &type) : Leaf(type), is_mutable(true) {}
    bool result_is_mutable() const override { return is_mutable; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &, Stash &) const override { abort(); }
};

struct Fixture {
    Value::UP                                my_value;
    ValueType                                new_type;
    ChildMock                                mock_child;
    ReplaceTypeFunction                      my_fun;
    std::vector<TensorFunction::Child::CREF> children;
    InterpretedFunction::State               state;
    Fixture()
        : my_value(value_from_spec(GenSpec().idx("x", 10), prod_factory)),
          new_type(ValueType::from_spec("tensor(x[5],y[2])")),
          mock_child(my_value->type()),
          my_fun(new_type, mock_child),
          children(),
          state(prod_factory)
    {
        my_fun.push_children(children);
        state.stack.push_back(*my_value);
        my_fun.compile_self(prod_factory, state.stash).perform(state);
        ASSERT_EQUAL(children.size(), 1u);
        ASSERT_EQUAL(state.stack.size(), 1u);
        ASSERT_TRUE(!new_type.is_error());
    }
};

TEST_F("require that ReplaceTypeFunction works as expected", Fixture()) {
    EXPECT_EQUAL(f1.my_fun.result_type(), f1.new_type);
    EXPECT_EQUAL(f1.my_fun.result_is_mutable(), true);
    f1.mock_child.is_mutable = false;
    EXPECT_EQUAL(f1.my_fun.result_is_mutable(), false);
    EXPECT_EQUAL(&f1.children[0].get().get(), &f1.mock_child);
    EXPECT_EQUAL(getCellsRef(f1.state.stack[0]).data, getCellsRef(*f1.my_value).data);
    EXPECT_EQUAL(getCellsRef(f1.state.stack[0]).size, getCellsRef(*f1.my_value).size);
    EXPECT_EQUAL(f1.state.stack[0].get().type(), f1.new_type);
    fprintf(stderr, "%s\n", f1.my_fun.as_string().c_str());
}

TEST("require that create_compact will collapse duplicate replace operations") {
    Stash stash;
    ValueType type = ValueType::double_type();
    ChildMock leaf(type);
    const ReplaceTypeFunction &a = ReplaceTypeFunction::create_compact(type, leaf, stash);
    const ReplaceTypeFunction &b = ReplaceTypeFunction::create_compact(type, a, stash);
    EXPECT_EQUAL(a.result_type(), type);
    EXPECT_EQUAL(&a.child(), &leaf);
    EXPECT_EQUAL(b.result_type(), type);
    EXPECT_EQUAL(&b.child(), &leaf);
}

TEST_MAIN() { TEST_RUN_ALL(); }
