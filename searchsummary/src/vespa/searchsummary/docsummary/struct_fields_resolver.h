// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "combiner_shape.h"

#include <span>
#include <string>
#include <vector>

namespace search {
namespace attribute {
class IAttributeContext;
}
} // namespace search

namespace search::docsummary {

/**
 * Class used to resolve which struct sub fields a complex field consists of,
 * based on which attribute vectors are present.
 */
class StructFieldsResolver {
private:
    using StringVector = std::vector<std::string>;
    std::string  _field_name;
    std::string  _map_key_attribute;
    StringVector _map_value_fields;
    StringVector _map_value_attributes;
    StringVector _array_fields;
    StringVector _array_attributes;
    StringVector _element_count_attributes;
    bool         _has_map_key;
    bool         _has_map_value;
    bool         _is_map_of_scalar;
    bool         _is_map_of_struct;
    bool         _error;

    /**
     * Settle whether this field is an array of struct, a map of scalar or a map of struct. A declared
     * shape is checked against the attributes which are actually present, and a mismatch is reported
     * and then resolved as if the shape had not been declared at all: config saying one thing and the
     * attributes another can only come from a config model which disagrees with this backend, and
     * failing the field would cost the whole node its summary config.
     */
    void resolve_shape(CombinerShape declared_shape);

    /**
     * Restrict this field to the given sub-fields, reporting an issue and failing the field if the
     * selection names something which is not a sub-field of it, or leaves a map without any value
     * sub-fields. The array sub-fields left out by the selection are returned in unselected_array_fields;
     * for a map of struct that is nothing, see get_element_count_attributes().
     */
    void apply_struct_field_selection(std::span<const std::string> selected_struct_fields,
                                      StringVector&                unselected_array_fields);

    /** Whether the attribute with the given name is used for writing this field. */
    bool uses_attribute(const std::string& attribute_name) const;

public:
    StructFieldsResolver(const std::string& field_name, const search::attribute::IAttributeContext& attr_ctx,
                         std::span<const std::string> selected_struct_fields, CombinerShape declared_shape);
    ~StructFieldsResolver();
    bool is_map_of_scalar() const { return _is_map_of_scalar; }
    bool is_map_of_struct() const { return _is_map_of_struct; }
    const std::string& get_map_key_attribute() const { return _map_key_attribute; }
    const StringVector& get_map_value_fields() const { return _map_value_fields; }
    const StringVector& get_map_value_attributes() const { return _map_value_attributes; }
    const StringVector& get_array_fields() const { return _array_fields; }
    const StringVector& get_array_attributes() const { return _array_attributes; }
    /**
     * Attributes for the array sub-fields left out by the struct field selection. They are not written,
     * but still take part in determining how many elements the array has. Only relevant for an array of
     * struct and for a map of scalar; for a map of struct the key attribute alone determines the number
     * of elements, so there is nothing to keep track of here.
     */
    const StringVector& get_element_count_attributes() const { return _element_count_attributes; }
    bool has_error() const { return _error; }
};

} // namespace search::docsummary
