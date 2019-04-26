// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("dense_tensor_store_test");
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/tensor/dense_tensor_store.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>

using search::tensor::DenseTensorStore;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::tensor::MutableDenseTensorView;
using vespalib::tensor::Tensor;
using vespalib::tensor::DefaultTensorEngine;

using EntryRef = DenseTensorStore::EntryRef;

Tensor::UP
makeTensor(const TensorSpec &spec)
{
    auto tensor = DefaultTensorEngine::ref().from_spec(spec);
    return Tensor::UP(dynamic_cast<Tensor *>(tensor.release()));
}

struct Fixture
{
    DenseTensorStore store;
    Fixture(const vespalib::string &tensorType)
        : store(ValueType::from_spec(tensorType))
    {}
    void assertSetAndGetTensor(const TensorSpec &tensorSpec) {
        Tensor::UP expTensor = makeTensor(tensorSpec);
        EntryRef ref = store.setTensor(*expTensor);
        Tensor::UP actTensor = store.getTensor(ref);
        EXPECT_EQUAL(expTensor->toSpec(), actTensor->toSpec());
        assertTensorView(ref, *expTensor);
    }
    void assertEmptyTensor(const TensorSpec &tensorSpec) {
        Tensor::UP expTensor = makeTensor(tensorSpec);
        EntryRef ref;
        Tensor::UP actTensor = store.getTensor(ref);
        EXPECT_TRUE(actTensor.get() == nullptr);
        assertTensorView(ref, *expTensor);
    }
    void assertTensorView(EntryRef ref, const Tensor &expTensor) {
        MutableDenseTensorView actTensor(store.type());
        store.getTensor(ref, actTensor);
        EXPECT_EQUAL(expTensor.toSpec(), actTensor.toSpec());
    }
};

TEST_F("require that we can store 1d bound tensor", Fixture("tensor(x[3])"))
{
    f.assertSetAndGetTensor(TensorSpec("tensor(x[3])").
                                       add({{"x", 0}}, 2).
                                       add({{"x", 1}}, 3).
                                       add({{"x", 2}}, 5));
}

TEST_F("require that correct empty tensor is returned for 1d bound tensor", Fixture("tensor(x[3])"))
{
    f.assertEmptyTensor(TensorSpec("tensor(x[3])").
                                   add({{"x", 0}}, 0).
                                   add({{"x", 1}}, 0).
                                   add({{"x", 2}}, 0));
}

void
assertArraySize(const vespalib::string &tensorType, uint32_t expArraySize) {
    Fixture f(tensorType);
    EXPECT_EQUAL(expArraySize, f.store.getArraySize());
}

TEST("require that array size is calculated correctly")
{
    TEST_DO(assertArraySize("tensor(x[1])", 32));
    TEST_DO(assertArraySize("tensor(x[10])", 96));
    TEST_DO(assertArraySize("tensor(x[3])", 32));
    TEST_DO(assertArraySize("tensor(x[10],y[10])", 800));
}

TEST_MAIN() { TEST_RUN_ALL(); }

