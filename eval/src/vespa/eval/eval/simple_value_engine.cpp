// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_value_engine.h"
#include "simple_value.h"
#include "value_codec.h"
#include <vespa/eval/instruction/generic_concat.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/instruction/generic_map.h>
#include <vespa/eval/instruction/generic_merge.h>
#include <vespa/eval/instruction/generic_reduce.h>
#include <vespa/eval/instruction/generic_rename.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

using namespace vespalib::eval::instruction;

namespace vespalib {
namespace eval {

const SimpleValueEngine SimpleValueEngine::_engine;

//-----------------------------------------------------------------------------

TensorSpec
SimpleValueEngine::to_spec(const Value &value) const
{
    return spec_from_value(value);
}

Value::UP
SimpleValueEngine::from_spec(const TensorSpec &spec) const
{
    return value_from_spec(spec, SimpleValueBuilderFactory::get());
}

//-----------------------------------------------------------------------------

void
SimpleValueEngine::encode(const Value &value, nbostream &output) const
{
    return encode_value(value, output);
}

Value::UP
SimpleValueEngine::decode(nbostream &input) const
{
    return decode_value(input, SimpleValueBuilderFactory::get());
}

//-----------------------------------------------------------------------------

const Value &
SimpleValueEngine::map(const Value &a, map_fun_t function, Stash &stash) const
{
    if (a.is_double()) {
        return stash.create<DoubleValue>(function(a.as_double()));
    }
    auto up = GenericMap::perform_map(a, function, SimpleValueBuilderFactory::get());
    auto &result = stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    return result_ref;
}

const Value &
SimpleValueEngine::join(const Value &a, const Value &b, join_fun_t function, Stash &stash) const
{
    if (a.is_double() && b.is_double()) {
        return stash.create<DoubleValue>(function(a.as_double(), b.as_double()));
    }
    auto up = GenericJoin::perform_join(a, b, function, SimpleValueBuilderFactory::get());
    auto &result = stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    return result_ref;
}

const Value &
SimpleValueEngine::merge(const Value &a, const Value &b, join_fun_t function, Stash &stash) const
{
    auto up = GenericMerge::perform_merge(a, b, function, SimpleValueBuilderFactory::get());
    auto &result = stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    return result_ref;
}

const Value &
SimpleValueEngine::reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const
{
    auto up = GenericReduce::perform_reduce(a, aggr, dimensions, SimpleValueBuilderFactory::get());
    auto &result = stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    return result_ref;
}

const Value &
SimpleValueEngine::concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const
{
    auto up = GenericConcat::perform_concat(a, b, dimension, SimpleValueBuilderFactory::get());
    auto &result = stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    return result_ref;
}

const Value &
SimpleValueEngine::rename(const Value &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const
{
    auto up = GenericRename::perform_rename(a, from, to, SimpleValueBuilderFactory::get());
    auto &result = stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    return result_ref;
}

//-----------------------------------------------------------------------------

} // namespace vespalib::eval
} // namespace vespalib
