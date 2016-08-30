// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "as_tensor.h"
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/tensor/default_tensor_engine.h>
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/tensor_mapper.h>
#include <vespa/vespalib/eval/function.h>
#include <iostream>

using vespalib::eval::Function;
using vespalib::tensor::DefaultTensorEngine;
using vespalib::tensor::TensorType;
using vespalib::tensor::TensorMapper;

namespace search {
namespace fef {
namespace test {

AsTensor::AsTensor(const vespalib::string &expr)
    : ifun(DefaultTensorEngine::ref(), Function::parse(expr)), ctx(), result(&ifun.eval(ctx))
{
    ASSERT_TRUE(result->is_tensor());
    tensor = static_cast<const Tensor *>(result->as_tensor());
}

AsTensor::~AsTensor()
{
}

bool AsTensor::operator==(const Tensor &rhs) const {
    return tensor->equals(rhs);
};

AsEmptyTensor::AsEmptyTensor(const vespalib::string &type)
    : AsTensor("{ }"),
      mappedTensor(TensorMapper(TensorType::fromSpec(type)).map(*tensor))
{
}

AsEmptyTensor::~AsEmptyTensor()
{
}

bool AsEmptyTensor::operator==(const Tensor &rhs) const {
    return mappedTensor->equals(rhs);
}

std::ostream &operator<<(std::ostream &os, const AsTensor &my_tensor) {
    os << my_tensor.result->as_tensor();
    return os;
}

} // search::fef::test
} // search::fef
} // search
