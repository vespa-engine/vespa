// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/schema.h>

#include <memory>
#include <string>
#include <string_view>

namespace document {

class DocumentType;
class FieldPath;

} // namespace document

namespace search::index {

/*
 * Returns the field path of an index of a field in an array of struct or map
 * (e.g. "array_of_struct.f", "map.key", "map.value" or "map.value.f"), or nullptr if the name
 * does not resolve to such a field path in the document type.
 */
std::unique_ptr<const document::FieldPath> get_struct_field_path(const document::DocumentType& doc_type,
                                                                 std::string_view               name);

/*
 * A struct field index gets one element per element in the complex field, so the schema
 * must have it as an array of string, and the field it indexes must be a string.
 *
 * Returns an empty string if the index is valid for the field path, otherwise why it is not.
 */
std::string get_struct_field_index_problem(const Schema::IndexField& index_field,
                                           const document::FieldPath& field_path);

inline bool is_valid_struct_field_index(const Schema::IndexField& index_field, const document::FieldPath& field_path) {
    return get_struct_field_index_problem(index_field, field_path).empty();
}

} // namespace search::index
