// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simple_value.h"
#include <vespa/vespalib/stllike/string.h>

namespace vespalib { class nbostream; }

namespace vespalib::eval {

/**
 * encode a value to binary format
 **/
void new_encode(const Value &value, nbostream &output);

/**
 * decode a value from binary format
 **/
std::unique_ptr<Value> new_decode(nbostream &input, const ValueBuilderFactory &factory);

/**
 * Make a value from a tensor spec using a value builder factory
 * interface, making it work with any value implementation.
 **/
std::unique_ptr<Value> value_from_spec(const TensorSpec &spec, const ValueBuilderFactory &factory);

/**
 * Convert a generic value to a tensor spec.
 **/
TensorSpec spec_from_value(const Value &value);

}
