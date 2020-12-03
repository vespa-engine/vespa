// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "engine_or_factory.h"
#include "fast_value.h"
#include "simple_value.h"
#include "value_codec.h"
#include <vespa/eval/instruction/generic_concat.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/instruction/generic_map.h>
#include <vespa/eval/instruction/generic_merge.h>
#include <vespa/eval/instruction/generic_reduce.h>
#include <vespa/eval/instruction/generic_rename.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/vespalib/data/memory.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

using namespace vespalib::eval::instruction;

namespace vespalib::eval {

EngineOrFactory EngineOrFactory::_default{FastValueBuilderFactory::get()};


EngineOrFactory
EngineOrFactory::get_shared(EngineOrFactory hint)
{
    static EngineOrFactory shared{hint};
    return shared;
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

std::unique_ptr<Value>
EngineOrFactory::copy(const Value &value)
{
    nbostream stream;
    encode(value, stream);
    return decode(stream);
}

const Value &
EngineOrFactory::map(const Value &a, operation::op1_t function, Stash &stash) const {
    if (is_engine()) {
        return engine().map(a, function, stash);
    } else {
        return *stash.create<Value::UP>(GenericMap::perform_map(a, function, factory()));
    }
}

const Value &
EngineOrFactory::join(const Value &a, const Value &b, operation::op2_t function, Stash &stash) const {
    if (is_engine()) {
        return engine().join(a, b, function, stash);
    } else {
        return *stash.create<Value::UP>(GenericJoin::perform_join(a, b, function, factory()));
    }
}

const Value &
EngineOrFactory::merge(const Value &a, const Value &b, operation::op2_t function, Stash &stash) const {
    if (is_engine()) {
        return engine().merge(a, b, function, stash);
    } else {
        return *stash.create<Value::UP>(GenericMerge::perform_merge(a, b, function, factory()));
    }
}

const Value &
EngineOrFactory::reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const {
    if (is_engine()) {
        return engine().reduce(a, aggr, dimensions, stash);
    } else {
        return *stash.create<Value::UP>(GenericReduce::perform_reduce(a, aggr, dimensions, factory()));
    }
}

const Value &
EngineOrFactory::concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const {
    if (is_engine()) {
        return engine().concat(a, b, dimension, stash);
    } else {
        return *stash.create<Value::UP>(GenericConcat::perform_concat(a, b, dimension, factory()));
    }
}

const Value &
EngineOrFactory::rename(const Value &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const {
    if (is_engine()) {
        return engine().rename(a, from, to, stash);
    } else {
        return *stash.create<Value::UP>(GenericRename::perform_rename(a, from, to, factory()));
    }
}

void
EngineOrFactory::set(EngineOrFactory wanted)
{
    assert(wanted.is_factory());
    auto engine = get_shared(wanted);
    if (engine._value != wanted._value) {
        auto msg = fmt("EngineOrFactory: trying to set implementation to [%s] when [%s] is already in use",
                       wanted.to_string().c_str(), engine.to_string().c_str());
        throw IllegalStateException(msg);
    }
}

EngineOrFactory
EngineOrFactory::get()
{
    return get_shared(_default);
}

vespalib::string
EngineOrFactory::to_string() const
{
    if (is_engine()) {
        if (&engine() == &tensor::DefaultTensorEngine::ref()) {
            return "DefaultTensorEngine";
        }
    }
    if (is_factory()) {
        if (&factory() == &FastValueBuilderFactory::get()) {
            return "FastValueBuilderFactory";
        }
        if (&factory() == &SimpleValueBuilderFactory::get()) {
            return "SimpleValueBuilderFactory";
        }
    }
    return "???";
}

}
