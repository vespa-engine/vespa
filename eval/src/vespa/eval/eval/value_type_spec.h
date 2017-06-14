// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_type.h"

namespace vespalib {
namespace eval {
namespace value_type {

ValueType parse_spec(const char *pos_in, const char *end_in, const char *&pos_out);

ValueType from_spec(const vespalib::string &str);
vespalib::string to_spec(const ValueType &type);

} // namespace vespalib::eval::value_type
} // namespace vespalib::eval
} // namespace vespalib
