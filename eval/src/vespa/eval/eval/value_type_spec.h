// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_type.h"

namespace vespalib::eval::value_type {

ValueType parse_spec(const char *pos_in, const char *end_in, const char *&pos_out,
                     std::vector<ValueType::Dimension> *unsorted = nullptr);

ValueType from_spec(const vespalib::string &spec);
ValueType from_spec(const vespalib::string &spec, std::vector<ValueType::Dimension> &unsorted);
vespalib::string to_spec(const ValueType &type);

}
