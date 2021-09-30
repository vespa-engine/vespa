// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <functional>
#include <memory>

namespace vespalib::eval { struct Value; struct ValueBuilderFactory; }

namespace document {

struct TensorUpdate {
protected:
    ~TensorUpdate() = default;
public:
    using Value = vespalib::eval::Value;
    using ValueBuilderFactory = vespalib::eval::ValueBuilderFactory;
    virtual std::unique_ptr<Value> apply_to(const Value &tensor, const ValueBuilderFactory &factory) const = 0;
};

}
