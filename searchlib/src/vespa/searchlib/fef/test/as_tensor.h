// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <iosfwd>
#include <vespa/vespalib/eval/interpreted_function.h>

namespace vespalib {
namespace eval { struct Value; }
namespace tensor { struct Tensor; }
}

namespace search {
namespace fef {
namespace test {

struct AsTensor {
    using InterpretedFunction = vespalib::eval::InterpretedFunction;
    using Value = vespalib::eval::Value;
    using Tensor = vespalib::tensor::Tensor;
    InterpretedFunction ifun;
    InterpretedFunction::Context ctx;
    const Value *result;
    const Tensor *tensor;
    explicit AsTensor(const vespalib::string &expr);
    ~AsTensor();
    bool operator==(const Tensor &rhs) const;
};

struct AsEmptyTensor : public AsTensor {
    std::unique_ptr<Tensor> mappedTensor;
    AsEmptyTensor(const vespalib::string &type);
    ~AsEmptyTensor();
    bool operator==(const Tensor &rhs) const;
};

std::ostream &operator<<(std::ostream &os, const AsTensor &my_tensor);

} // search::fef::test
} // search::fef
} // search
