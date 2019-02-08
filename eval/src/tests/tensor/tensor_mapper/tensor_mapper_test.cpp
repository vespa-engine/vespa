// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/tensor_mapper.h>
#include <vespa/eval/tensor/test/test_utils.h>
#include <vespa/eval/tensor/wrapped_simple_tensor.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::eval::ValueType;
using vespalib::eval::Value;
using vespalib::eval::TensorSpec;
using vespalib::eval::SimpleTensor;
using vespalib::tensor::test::makeTensor;
using namespace vespalib::tensor;

void
verify_wrapped(const TensorSpec &source, const vespalib::string &type, const TensorSpec &expect)
{
    auto tensor = std::make_unique<WrappedSimpleTensor>(SimpleTensor::create(source));
    auto mapped = TensorMapper::mapToWrapped(*tensor, ValueType::from_spec(type));
    TensorSpec actual = mapped->toSpec();
    EXPECT_EQUAL(actual, expect);
}

void
verify(const TensorSpec &source, const vespalib::string &type, const TensorSpec &expect)
{
    auto tensor = makeTensor<Tensor>(source);
    TensorMapper mapper(ValueType::from_spec(type));
    auto mapped = mapper.map(*tensor);
    TensorSpec actual = mapped->toSpec();
    EXPECT_EQUAL(actual, expect);
    TEST_DO(verify_wrapped(source, type, expect));
}

TEST("require that sparse tensors can be mapped to sparse type") {
    TEST_DO(verify(TensorSpec("tensor(x{},y{})")
                   .add({{"x","1"},{"y","1"}}, 1)
                   .add({{"x","2"},{"y","1"}}, 3)
                   .add({{"x","1"},{"y","2"}}, 5)
                   .add({{"x","2"},{"y","2"}}, 7),
                   "tensor(y{})",
                   TensorSpec("tensor(y{})")
                   .add({{"y","1"}}, 4)
                   .add({{"y","2"}}, 12)));

    TEST_DO(verify(TensorSpec("tensor(x{},y{})")
                   .add({{"x","1"},{"y","1"}}, 1)
                   .add({{"x","2"},{"y","1"}}, 3)
                   .add({{"x","1"},{"y","2"}}, 5)
                   .add({{"x","2"},{"y","2"}}, 7),
                   "tensor(x{})",
                   TensorSpec("tensor(x{})")
                   .add({{"x","1"}}, 6)
                   .add({{"x","2"}}, 10)));
}

TEST("require that sparse tensors can be mapped to dense type") {
    TEST_DO(verify(TensorSpec("tensor(x{},y{})")
                   .add({{"x","1"},{"y","0"}}, 1)
                   .add({{"x","2"},{"y","0"}}, 3)
                   .add({{"x","1"},{"y","1"}}, 5)
                   .add({{"x","2"},{"y","1"}}, 7),
                   "tensor(y[3])",
                   TensorSpec("tensor(y[3])")
                   .add({{"y",0}}, 4)
                   .add({{"y",1}}, 12)
                   .add({{"y",2}}, 0)));

    TEST_DO(verify(TensorSpec("tensor(x{},y{})")
                   .add({{"x","1"},{"y","0x"}}, 1)
                   .add({{"x","2"},{"y",""}}, 3)
                   .add({{"x","1"},{"y","1"}}, 5)
                   .add({{"x","2"},{"y","10"}}, 7),
                   "tensor(y[3])",
                   TensorSpec("tensor(y[3])")
                   .add({{"y",0}}, 3)
                   .add({{"y",1}}, 5)
                   .add({{"y",2}}, 0)));

    TEST_DO(verify(TensorSpec("tensor(x{},y{})")
                   .add({{"x","0"},{"y","0"}}, 1)
                   .add({{"x","1"},{"y","0"}}, 3)
                   .add({{"x","0"},{"y","1"}}, 5)
                   .add({{"x","10"},{"y","1"}}, 7),
                   "tensor(x[2],y[3])",
                   TensorSpec("tensor(x[2],y[3])")
                   .add({{"x",0},{"y",0}}, 1)
                   .add({{"x",0},{"y",1}}, 5)
                   .add({{"x",0},{"y",2}}, 0)
                   .add({{"x",1},{"y",0}}, 3)
                   .add({{"x",1},{"y",1}}, 0)
                   .add({{"x",1},{"y",2}}, 0)));
}

TEST("require that sparse tensors can be mapped to abstract dense type") {
    TEST_DO(verify(TensorSpec("tensor(x{},y{})")
                   .add({{"x","0"},{"y","0"}}, 1)
                   .add({{"x","1"},{"y","0"}}, 3)
                   .add({{"x","0"},{"y","1"}}, 5)
                   .add({{"x","10"},{"y","1"}}, 7),
                   "tensor(x[2],y[])",
                   TensorSpec("tensor(x[2],y[2])")
                   .add({{"x",0},{"y",0}}, 1)
                   .add({{"x",0},{"y",1}}, 5)
                   .add({{"x",1},{"y",0}}, 3)
                   .add({{"x",1},{"y",1}}, 0)));

    TEST_DO(verify(TensorSpec("tensor(x{},y{})")
                   .add({{"x","0"},{"y","0"}}, 1)
                   .add({{"x","1"},{"y","0"}}, 3)
                   .add({{"x","0"},{"y","1"}}, 5)
                   .add({{"x","2"},{"y","0"}}, 7),
                   "tensor(x[],y[])",
                   TensorSpec("tensor(x[3],y[2])")
                   .add({{"x",0},{"y",0}}, 1)
                   .add({{"x",0},{"y",1}}, 5)
                   .add({{"x",1},{"y",0}}, 3)
                   .add({{"x",1},{"y",1}}, 0)
                   .add({{"x",2},{"y",0}}, 7)
                   .add({{"x",2},{"y",1}}, 0)));

    TEST_DO(verify(TensorSpec("tensor(x{},y{})")
                   .add({{"x","0"},{"y","0"}}, 1)
                   .add({{"x","1"},{"y","0"}}, 3)
                   .add({{"x","0"},{"y","1"}}, 5)
                   .add({{"x","10"},{"y","3"}}, 7),
                   "tensor(x[],y[3])",
                   TensorSpec("tensor(x[2],y[3])")
                   .add({{"x",0},{"y",0}}, 1)
                   .add({{"x",0},{"y",1}}, 5)
                   .add({{"x",0},{"y",2}}, 0)
                   .add({{"x",1},{"y",0}}, 3)
                   .add({{"x",1},{"y",1}}, 0)
                   .add({{"x",1},{"y",2}}, 0)));
}

TEST("require that dense tensors can be mapped to sparse type") {
    TEST_DO(verify(TensorSpec("tensor(x[2],y[2])")
                   .add({{"x",0},{"y",0}}, 1)
                   .add({{"x",0},{"y",1}}, 3)
                   .add({{"x",1},{"y",0}}, 5)
                   .add({{"x",1},{"y",1}}, 7),
                   "tensor(x{})",
                   TensorSpec("tensor(x{})")
                   .add({{"x","0"}}, 4)
                   .add({{"x","1"}}, 12)));
}

TEST("require that mixed tensors can be mapped to sparse type") {
    TEST_DO(verify(TensorSpec("tensor(x[2],y{})")
                   .add({{"x",0},{"y","0"}}, 1)
                   .add({{"x",0},{"y","1"}}, 3)
                   .add({{"x",1},{"y","0"}}, 5)
                   .add({{"x",1},{"y","1"}}, 7),
                   "tensor(x{})",
                   TensorSpec("tensor(x{})")
                   .add({{"x","0"}}, 4)
                   .add({{"x","1"}}, 12)));
}

TEST("require that mixed tensors can be mapped to dense type") {
    TEST_DO(verify(TensorSpec("tensor(x[2],y{})")
                   .add({{"x",0},{"y","0"}}, 1)
                   .add({{"x",0},{"y","1"}}, 3)
                   .add({{"x",1},{"y","0"}}, 5)
                   .add({{"x",1},{"y","1"}}, 7),
                   "tensor(y[])",
                   TensorSpec("tensor(y[2])")
                   .add({{"y",0}}, 6)
                   .add({{"y",1}}, 10)));
}

TEST("require that mixed tensors can be mapped to mixed type") {
    TEST_DO(verify(TensorSpec("tensor(x[2],y{})")
                   .add({{"x",0},{"y","0"}}, 1)
                   .add({{"x",0},{"y","1"}}, 3)
                   .add({{"x",1},{"y","0"}}, 5)
                   .add({{"x",1},{"y","1"}}, 7),
                   "tensor(x{},y[])",
                   TensorSpec("tensor(x{},y[2])")
                   .add({{"x","0"},{"y",0}}, 1)
                   .add({{"x","0"},{"y",1}}, 3)
                   .add({{"x","1"},{"y",0}}, 5)
                   .add({{"x","1"},{"y",1}}, 7)));
}

TEST("require that dense tensors can be mapped to mixed type") {
    TEST_DO(verify(TensorSpec("tensor(x[2],y[2])")
                   .add({{"x",0},{"y",0}}, 1)
                   .add({{"x",0},{"y",1}}, 3)
                   .add({{"x",1},{"y",0}}, 5)
                   .add({{"x",1},{"y",1}}, 7),
                   "tensor(x{},y[])",
                   TensorSpec("tensor(x{},y[2])")
                   .add({{"x","0"},{"y",0}}, 1)
                   .add({{"x","0"},{"y",1}}, 3)
                   .add({{"x","1"},{"y",0}}, 5)
                   .add({{"x","1"},{"y",1}}, 7)));
}

TEST("require that sparse tensors can be mapped to mixed type") {
    TEST_DO(verify(TensorSpec("tensor(x{},y{})")
                   .add({{"x","0"},{"y","0"}}, 1)
                   .add({{"x","0"},{"y","1"}}, 3)
                   .add({{"x","1"},{"y","0"}}, 5)
                   .add({{"x","1"},{"y","1"}}, 7),
                   "tensor(x[],y{})",
                   TensorSpec("tensor(x[2],y{})")
                   .add({{"x",0},{"y","0"}}, 1)
                   .add({{"x",0},{"y","1"}}, 3)
                   .add({{"x",1},{"y","0"}}, 5)
                   .add({{"x",1},{"y","1"}}, 7)));
}

TEST("require that missing dimensions are added appropriately") {
    TEST_DO(verify(TensorSpec("tensor(x{})")
                   .add({{"x","foo"}}, 42),
                   "tensor(x{},y{})",
                   TensorSpec("tensor(x{},y{})")
                   .add({{"x","foo"},{"y",""}}, 42)));

    TEST_DO(verify(TensorSpec("tensor(x[1])")
                   .add({{"x",0}}, 42),
                   "tensor(x[1],y[],z[2])",
                   TensorSpec("tensor(x[1],y[1],z[2])")
                   .add({{"x",0},{"y",0},{"z",0}}, 42)
                   .add({{"x",0},{"y",0},{"z",1}}, 0)));

    TEST_DO(verify(TensorSpec("tensor(a{})")
                   .add({{"a","foo"}}, 42),
                   "tensor(a{},b[],c{},d[2])",
                   TensorSpec("tensor(a{},b[1],c{},d[2])")
                   .add({{"a","foo"},{"b",0},{"c",""},{"d",0}}, 42)
                   .add({{"a","foo"},{"b",0},{"c",""},{"d",1}}, 0)));
}

TEST_MAIN() { TEST_RUN_ALL(); }
