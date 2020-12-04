// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "aggr.h"
#include "tensor_spec.h"
#include "operation.h"
#include <variant>

namespace vespalib {
class Stash;
class nbostream;
}

namespace vespalib::eval {

struct TensorEngine;
struct ValueBuilderFactory;
struct TensorFunction;

/**
 * This utility class contains a reference to either a TensorEngine or
 * a ValueBuilderFactory. This is needed during a transition period to
 * support both evaluation models. We want to get rid of the
 * TensorEngine concept since using the Value API directly removes the
 * need to constrain operations to only calculate on tensors belonging
 * to the same tensor engine. The factory is a hint to the preferred
 * Value implementation.
 **/
class EngineOrFactory {
private:
    using engine_t = const TensorEngine *;
    using factory_t = const ValueBuilderFactory *;
    std::variant<engine_t,factory_t> _value;
    static EngineOrFactory _default;
    static EngineOrFactory get_shared(EngineOrFactory hint);
public:
    EngineOrFactory(const ValueBuilderFactory &factory_in) : _value(&factory_in) {}
    bool is_engine() const { return std::holds_alternative<engine_t>(_value); }
    bool is_factory() const { return std::holds_alternative<factory_t>(_value); }
    const TensorEngine &engine() const { return *std::get<engine_t>(_value); }
    const ValueBuilderFactory &factory() const { return *std::get<factory_t>(_value); }
    // functions that can be called with either engine or factory
    TensorSpec to_spec(const Value &value) const;
    std::unique_ptr<Value> from_spec(const TensorSpec &spec) const;
    void encode(const Value &value, nbostream &output) const;
    std::unique_ptr<Value> decode(nbostream &input) const;
    std::unique_ptr<Value> copy(const Value &value);
    // global switch with default; call set before get to override the default
    static void set(EngineOrFactory wanted);
    static EngineOrFactory get();
    // try to describe the value held by this object as a human-readable string
    vespalib::string to_string() const;
};

}
