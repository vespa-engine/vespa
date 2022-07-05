// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "tensor_spec.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/exception.h>

namespace vespalib { class nbostream; }

namespace vespalib::eval {

VESPA_DEFINE_EXCEPTION(DecodeValueException, Exception);

struct ValueBuilderFactory;

/**
 * encode a value (which must support the new APIs) to binary format
 **/
void encode_value(const Value &value, nbostream &output);

/**
 * decode a value from binary format
 *
 * will throw DecodeValueException if input contains malformed data
 **/
std::unique_ptr<Value> decode_value(nbostream &input, const ValueBuilderFactory &factory);

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
