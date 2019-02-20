// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/test/test_utils.h>
#include <vespa/vespalib/testkit/test_kit.h>

using vespalib::eval::Value;
using vespalib::eval::TensorSpec;
using vespalib::tensor::test::makeTensor;
using namespace vespalib::tensor;

void
assertRemove(const TensorSpec &source, const TensorSpec &arg, const TensorSpec &expected)
{
    auto sourceTensor = makeTensor<Tensor>(source);
    auto argTensor = makeTensor<SparseTensor>(arg);
    auto resultTensor = sourceTensor->remove(CellValues(*argTensor));
    auto actual = resultTensor->toSpec();
    EXPECT_EQUAL(actual, expected);
}

TEST("require that cells can be removed from a sparse tensor")
{
    assertRemove(TensorSpec("tensor(x{},y{})")
                         .add({{"x","a"},{"y","b"}}, 2)
                         .add({{"x","c"},{"y","d"}}, 3),
                 TensorSpec("tensor(x{},y{})")
                         .add({{"x","c"},{"y","d"}}, 1)
                         .add({{"x","e"},{"y","f"}}, 1),
                 TensorSpec("tensor(x{},y{})")
                         .add({{"x","a"},{"y","b"}}, 2));
}

TEST("require that all cells can be removed from a sparse tensor")
{
    assertRemove(TensorSpec("tensor(x{},y{})")
                         .add({{"x","a"},{"y","b"}}, 2),
                 TensorSpec("tensor(x{},y{})")
                         .add({{"x","a"},{"y","b"}}, 1),
                 TensorSpec("tensor(x{},y{})"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
