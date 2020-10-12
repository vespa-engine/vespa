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

class TensorEngine;
class ValueBuilderFactory;
class TensorFunction;

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
public:
    EngineOrFactory(const TensorEngine &engine_in) : _value(&engine_in) {}
    EngineOrFactory(const ValueBuilderFactory &factory_in) : _value(&factory_in) {}
    bool is_engine() const { return std::holds_alternative<engine_t>(_value); }
    bool is_factory() const { return std::holds_alternative<factory_t>(_value); }
    const TensorEngine &engine() const { return *std::get<engine_t>(_value); }
    const ValueBuilderFactory &factory() const { return *std::get<factory_t>(_value); }
    // functions that can be called with either engine or factory
    const TensorFunction &optimize(const TensorFunction &expr, Stash &stash) const;
    TensorSpec to_spec(const Value &value) const;
    std::unique_ptr<Value> from_spec(const TensorSpec &spec) const;
    void encode(const Value &value, nbostream &output) const;
    std::unique_ptr<Value> decode(nbostream &input) const;
    // engine-only forwarding functions
    const Value &map(const Value &a, operation::op1_t function, Stash &stash) const;
    const Value &join(const Value &a, const Value &b, operation::op2_t function, Stash &stash) const;
    const Value &merge(const Value &a, const Value &b, operation::op2_t function, Stash &stash) const;
    const Value &reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const;
    const Value &concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const;
    const Value &rename(const Value &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const;
};

}
