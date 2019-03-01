// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/test/test_utils.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::eval::Value;
using vespalib::eval::TensorSpec;
using vespalib::tensor::test::makeTensor;
using namespace vespalib::tensor;

void
checkUpdate(const TensorSpec &source, const TensorSpec &update, const TensorSpec &expect)
{
    auto sourceTensor = makeTensor<Tensor>(source);
    auto updateTensor = makeTensor<SparseTensor>(update);
    const CellValues cellValues(*updateTensor);

    auto actualTensor = sourceTensor->modify(vespalib::eval::operation::Add::f, cellValues);
    auto actual = actualTensor->toSpec();
    auto expectTensor = makeTensor<Tensor>(expect);
    auto expectPadded = expectTensor->toSpec();
    EXPECT_EQ(actual, expectPadded);
}

TEST(TensorModifyTest, sparse_tensors_can_be_modified)
{
    checkUpdate(TensorSpec("tensor(x{},y{})")
                        .add({{"x","8"},{"y","9"}}, 11)
                        .add({{"x","9"},{"y","9"}}, 11),
                TensorSpec("tensor(x{},y{})")
                        .add({{"x","8"},{"y","9"}}, 2),
                TensorSpec("tensor(x{},y{})")
                        .add({{"x","8"},{"y","9"}}, 13)
                        .add({{"x","9"},{"y","9"}}, 11));
}

TEST(TensorModifyTest, dense_tensors_can_be_modified)
{
    checkUpdate(TensorSpec("tensor(x[10],y[10])")
                        .add({{"x",8},{"y",9}}, 11)
                        .add({{"x",9},{"y",9}}, 11),
                TensorSpec("tensor(x{},y{})")
                        .add({{"x","8"},{"y","9"}}, 2),
                TensorSpec("tensor(x[10],y[10])")
                        .add({{"x",8},{"y",9}}, 13)
                        .add({{"x",9},{"y",9}}, 11));
}

TEST(TensorModifyTest, mixed_tensors_can_be_modified)
{
    checkUpdate(TensorSpec("tensor(x{},y[2])")
                        .add({{"x","a"},{"y",0}}, 2)
                        .add({{"x","a"},{"y",1}}, 3)
                        .add({{"x","b"},{"y",0}}, 4)
                        .add({{"x","b"},{"y",1}}, 5),
                TensorSpec("tensor(x{},y{})")
                        .add({{"x","a"},{"y","0"}}, 6)
                        .add({{"x","b"},{"y","1"}}, 7),
                TensorSpec("tensor(x{},y[2])")
                        .add({{"x","a"},{"y",0}}, 8)
                        .add({{"x","a"},{"y",1}}, 3)
                        .add({{"x","b"},{"y",0}}, 4)
                        .add({{"x","b"},{"y",1}}, 12));
}

TEST(TensorModifyTest, sparse_tensors_ignore_updates_to_missing_cells)
{
    checkUpdate(TensorSpec("tensor(x{},y{})")
                        .add({{"x","8"},{"y","9"}}, 11)
                        .add({{"x","9"},{"y","9"}}, 11),
                TensorSpec("tensor(x{},y{})")
                        .add({{"x","7"},{"y","9"}}, 2)
                        .add({{"x","8"},{"y","9"}}, 2),
                TensorSpec("tensor(x{},y{})")
                        .add({{"x","8"},{"y","9"}}, 13)
                        .add({{"x","9"},{"y","9"}}, 11));
}

TEST(TensorModifyTest, dense_tensors_ignore_updates_to_out_of_range_cells)
{
    checkUpdate(TensorSpec("tensor(x[10],y[10])")
                        .add({{"x",8},{"y",9}}, 11)
                        .add({{"x",9},{"y",9}}, 11),
                TensorSpec("tensor(x{},y{})")
                        .add({{"x","8"},{"y","9"}}, 2)
                        .add({{"x","10"},{"y","9"}}, 2),
                TensorSpec("tensor(x[10],y[10])")
                        .add({{"x",8},{"y",9}}, 13)
                        .add({{"x",9},{"y",9}}, 11));
}

TEST(TensorModifyTest, mixed_tensors_ignore_updates_to_missing_or_out_of_range_cells)
{
    checkUpdate(TensorSpec("tensor(x{},y[2])")
                        .add({{"x","a"},{"y",0}}, 2)
                        .add({{"x","a"},{"y",1}}, 3),
                TensorSpec("tensor(x{},y{})")
                        .add({{"x","a"},{"y","2"}}, 4)
                        .add({{"x","c"},{"y","0"}}, 5),
                TensorSpec("tensor(x{},y[2])")
                        .add({{"x","a"},{"y",0}}, 2)
                        .add({{"x","a"},{"y",1}}, 3));
}

GTEST_MAIN_RUN_ALL_TESTS()
