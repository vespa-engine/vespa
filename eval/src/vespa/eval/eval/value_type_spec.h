// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_type.h"

#include <optional>

namespace vespalib::eval::value_type {

std::optional<CellType> cell_type_from_name(const std::string& name);
std::string cell_type_to_name(CellType cell_type);

/**
 * Is this a valid dimension name; that is, an identifier that can be
 * parsed as part of a type spec: [A-Za-z_][A-Za-z0-9_]*
 **/
bool is_valid_dimension_name(const std::string& name);

ValueType parse_spec(const char* pos_in, const char* end_in, const char*& pos_out,
                     std::vector<ValueType::Dimension>* unsorted = nullptr);

ValueType from_spec(const std::string& spec);
ValueType from_spec(const std::string& spec, std::vector<ValueType::Dimension>& unsorted);
std::string to_spec(const ValueType& type);

} // namespace vespalib::eval::value_type
