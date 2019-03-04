// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/test/test_utils.h>
#include <vespa/vespalib/gtest/gtest.h>

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
    EXPECT_EQ(actual, expected);
}

TEST(TensorRemoveTest, cells_can_be_removed_from_a_sparse_tensor)
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

TEST(TensorRemoveTest, all_cells_can_be_removed_from_a_sparse_tensor)
{
    assertRemove(TensorSpec("tensor(x{},y{})")
                         .add({{"x","a"},{"y","b"}}, 2),
                 TensorSpec("tensor(x{},y{})")
                         .add({{"x","a"},{"y","b"}}, 1),
                 TensorSpec("tensor(x{},y{})"));
}

TEST(TensorRemoveTest, cells_can_be_removed_from_a_mixed_tensor)
{
    assertRemove(TensorSpec("tensor(x{},y[2])")
                         .add({{"x","a"},{"y",0}}, 2)
                         .add({{"x","a"},{"y",1}}, 3)
                         .add({{"x","b"},{"y",0}}, 4)
                         .add({{"x","b"},{"y",1}}, 5),
                 TensorSpec("tensor(x{})")
                         .add({{"x","b"}}, 1)
                         .add({{"x","c"}}, 1),
                 TensorSpec("tensor(x{},y[2])")
                         .add({{"x","a"},{"y",0}}, 2)
                         .add({{"x","a"},{"y",1}}, 3));

    assertRemove(TensorSpec("tensor(x{},y{},z[2])")
                         .add({{"x","a"},{"y","c"},{"z",0}}, 2)
                         .add({{"x","a"},{"y","c"},{"z",1}}, 3)
                         .add({{"x","b"},{"y","c"},{"z",0}}, 4)
                         .add({{"x","b"},{"y","c"},{"z",1}}, 5),
                 TensorSpec("tensor(x{},y{})")
                         .add({{"x","b"},{"y","c"}}, 1)
                         .add({{"x","c"},{"y","c"}}, 1),
                 TensorSpec("tensor(x{},y{},z[2])")
                         .add({{"x","a"},{"y","c"},{"z",0}}, 2)
                         .add({{"x","a"},{"y","c"},{"z",1}}, 3));

     assertRemove(TensorSpec("tensor(x{},y[1],z[2])")
                         .add({{"x","a"},{"y",0},{"z",0}}, 2)
                         .add({{"x","a"},{"y",0},{"z",1}}, 3)
                         .add({{"x","b"},{"y",0},{"z",0}}, 4)
                         .add({{"x","b"},{"y",0},{"z",1}}, 5),
                 TensorSpec("tensor(x{})")
                         .add({{"x","b"}}, 1)
                         .add({{"x","c"}}, 1),
                 TensorSpec("tensor(x{},y[1],z[2])")
                         .add({{"x","a"},{"y",0},{"z",0}}, 2)
                         .add({{"x","a"},{"y",0},{"z",1}}, 3));
}

TEST(TensorRemoveTest, all_cells_can_be_removed_from_a_mixed_tensor)
{
    assertRemove(TensorSpec("tensor(x{},y[2])")
                         .add({{"x","a"},{"y",0}}, 2)
                         .add({{"x","a"},{"y",1}}, 3),
                 TensorSpec("tensor(x{})")
                         .add({{"x","a"}}, 1),
                 TensorSpec("tensor(x{},y[2])"));
}

GTEST_MAIN_RUN_ALL_TESTS()
