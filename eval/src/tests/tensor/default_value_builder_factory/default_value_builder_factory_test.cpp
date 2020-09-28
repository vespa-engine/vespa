// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/default_value_builder_factory.h>
#include <vespa/eval/tensor/mixed/packed_mixed_tensor.h>
#include <vespa/eval/tensor/sparse/sparse_tensor_value.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::tensor;
using namespace vespalib::eval::packed_mixed_tensor;

Value::UP v_of(const TensorSpec &spec) {
    return value_from_spec(spec, DefaultValueBuilderFactory::get());
}

TEST(MakeInputTest, print_some_test_input) {
    auto dbl = v_of(TensorSpec("double").add({}, 3.0));
    auto trivial = v_of(TensorSpec("tensor(x[1])").add({{"x",0}}, 7.0));
    auto dense = v_of(TensorSpec("tensor<float>(x[2],y[3])").add({{"x",1},{"y",2}}, 17.0));
    auto sparse = v_of(TensorSpec("tensor(x{},y{})").add({{"x","foo"},{"y","bar"}}, 31.0));
    auto mixed = v_of(TensorSpec("tensor<float>(x[2],y{})").add({{"x",1},{"y","quux"}}, 42.0));

    EXPECT_TRUE(dynamic_cast<DoubleValue *>(dbl.get()));
    EXPECT_TRUE(dynamic_cast<DenseTensorView *>(trivial.get()));
    EXPECT_TRUE(dynamic_cast<DenseTensorView *>(dense.get()));
    EXPECT_TRUE(dynamic_cast<SparseTensorValue<double> *>(sparse.get()));
    EXPECT_TRUE(dynamic_cast<PackedMixedTensor *>(mixed.get()));

    EXPECT_EQ(dbl->as_double(), 3.0);
    EXPECT_EQ(trivial->cells().typify<double>()[0], 7.0);
    EXPECT_EQ(dense->cells().typify<float>()[5], 17.0);
    EXPECT_EQ(sparse->cells().typify<double>()[0], 31.0);
    EXPECT_EQ(mixed->cells().typify<float>()[1], 42.0);

    stringref y_look = "bar";
    stringref x_res = "xxx";
    auto view = sparse->index().create_view({1});
    view->lookup({&y_look});
    size_t ss = 12345;
    bool br = view->next_result({&x_res}, ss);
    EXPECT_TRUE(br);
    EXPECT_EQ(ss, 0);
    EXPECT_EQ(x_res, "foo");
    br = view->next_result({&x_res}, ss);
    EXPECT_FALSE(br);

    ss = 12345;
    view = mixed->index().create_view({});
    view->lookup({});
    br = view->next_result({&x_res}, ss);
    EXPECT_TRUE(br);
    EXPECT_EQ(ss, 0);
    EXPECT_EQ(x_res, "quux");
}

GTEST_MAIN_RUN_ALL_TESTS()
