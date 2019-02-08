// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/vespalib/testkit/test_kit.h>

namespace vespalib::tensor::test {

template <typename T>
std::unique_ptr<const T>
makeTensor(const vespalib::eval::TensorSpec &spec)
{
    auto value = DefaultTensorEngine::ref().from_spec(spec);
    const T *tensor = dynamic_cast<const T *>(value->as_tensor());
    ASSERT_TRUE(tensor);
    value.release();
    return std::unique_ptr<const T>(tensor);
}

}
