// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "engine_or_factory.h"
#include "fast_value.h"
#include <vespa/eval/tensor/default_tensor_engine.h>
#include "value_codec.h"

namespace vespalib::eval {

const TensorFunction &
EngineOrFactory::optimize(const TensorFunction &expr, Stash &stash) const {
    if (is_engine()) {
        return engine().optimize(expr, stash);
    } else if (&factory() == &FastValueBuilderFactory::get()) {
        return tensor::DefaultTensorEngine::ref().optimize(expr, stash);
    } else {
        return expr;
    }
}

TensorSpec
EngineOrFactory::to_spec(const Value &value) const
{
    if (is_engine()) {
        return engine().to_spec(value);
    } else {
        return factory(), spec_from_value(value);
    }
}

std::unique_ptr<Value>
EngineOrFactory::from_spec(const TensorSpec &spec) const
{
    if (is_engine()) {
        return engine().from_spec(spec);
    } else {
        return value_from_spec(spec, factory());
    }
}

void
EngineOrFactory::encode(const Value &value, nbostream &output) const
{
    if (is_engine()) {
        return engine().encode(value, output);
    } else {
        return factory(), encode_value(value, output);
    }
}

std::unique_ptr<Value>
EngineOrFactory::decode(nbostream &input) const
{
    if (is_engine()) {
        return engine().decode(input);
    } else {
        return decode_value(input, factory());
    }
}

const Value &
EngineOrFactory::map(const Value &a, operation::op1_t function, Stash &stash) const {
    return engine().map(a, function, stash);
}

const Value &
EngineOrFactory::join(const Value &a, const Value &b, operation::op2_t function, Stash &stash) const {
    return engine().join(a, b, function, stash);
}

const Value &
EngineOrFactory::merge(const Value &a, const Value &b, operation::op2_t function, Stash &stash) const {
    return engine().merge(a, b, function, stash);
}

const Value &
EngineOrFactory::reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const {
    return engine().reduce(a, aggr, dimensions, stash);
}

const Value &
EngineOrFactory::concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const {
    return engine().concat(a, b, dimension, stash);
}

const Value &
EngineOrFactory::rename(const Value &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const {
    return engine().rename(a, from, to, stash);
}

}
