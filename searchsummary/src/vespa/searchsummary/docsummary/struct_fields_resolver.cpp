// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "struct_fields_resolver.h"

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/vespalib/util/issue.h>

#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".searchsummary.docsummary.struct_fields_resolver");

using search::attribute::CollectionType;
using search::attribute::IAttributeContext;
using vespalib::Issue;

namespace search::docsummary {

StructFieldsResolver::StructFieldsResolver(const std::string& field_name, const IAttributeContext& attr_ctx,
                                           std::span<const std::string> selected_struct_fields,
                                           CombinerShape                declared_shape)
    : _field_name(field_name),
      _map_key_attribute(),
      _map_value_fields(),
      _map_value_attributes(),
      _array_fields(),
      _array_attributes(),
      _element_count_attributes(),
      _has_map_key(false),
      _has_map_value(false),
      _is_map_of_scalar(false),
      _is_map_of_struct(false),
      _error(false) {
    std::vector<const search::attribute::IAttributeVector*> attrs;
    attr_ctx.getAttributeList(attrs);
    std::string prefix = field_name + ".";
    _map_key_attribute = prefix + "key";
    std::string  map_value_attribute_name = prefix + "value";
    std::string  value_prefix = prefix + "value.";
    StringVector non_array_attributes;
    for (const auto attr : attrs) {
        std::string name = attr->getName();
        if (name.substr(0, prefix.size()) != prefix) {
            continue;
        }
        if (attr->getCollectionType() != CollectionType::Type::ARRAY) {
            // Only an error if this attribute is used, i.e. if it survives the struct field selection below.
            non_array_attributes.emplace_back(name);
        }
        if (name.substr(0, value_prefix.size()) == value_prefix) {
            _map_value_fields.emplace_back(name.substr(value_prefix.size()));
        } else {
            _array_fields.emplace_back(name.substr(prefix.size()));
            if (name == _map_key_attribute) {
                _has_map_key = true;
            } else if (name == map_value_attribute_name) {
                _has_map_value = true;
            }
        }
    }
    // The shape of the field is what it is regardless of which sub-fields are selected, so it must be
    // settled before the selection is applied. Both uses_attribute() and the choice of writer made by
    // AttributeCombinerDFW::create() depend on it.
    resolve_shape(declared_shape);
    StringVector unselected_array_fields;
    if (!selected_struct_fields.empty()) {
        apply_struct_field_selection(selected_struct_fields, unselected_array_fields);
    }

    std::sort(_map_value_fields.begin(), _map_value_fields.end());
    for (const auto& field : _map_value_fields) {
        _map_value_attributes.emplace_back(value_prefix + field);
    }

    std::sort(_array_fields.begin(), _array_fields.end());
    for (const auto& field : _array_fields) {
        _array_attributes.emplace_back(prefix + field);
    }

    // For an array of struct, and for a map of scalar, a sub-field left out by the selection still takes
    // part in determining how many elements the array has, so that selecting a subset changes which
    // sub-fields are written but not which elements are present. Only array attributes count; the value
    // count of a non-array attribute is unrelated to the length of the array.
    std::sort(unselected_array_fields.begin(), unselected_array_fields.end());
    for (const auto& field : unselected_array_fields) {
        auto attribute_name = prefix + field;
        if (std::find(non_array_attributes.begin(), non_array_attributes.end(), attribute_name) ==
            non_array_attributes.end())
        {
            _element_count_attributes.emplace_back(attribute_name);
        }
    }

    for (const auto& name : non_array_attributes) {
        if (uses_attribute(name)) {
            Issue::report("StructFieldsResolver: Attribute '%s' is not an array attribute", name.c_str());
            _error = true;
        }
    }

    if (!_error && _is_map_of_struct) {
        if (!_has_map_key) {
            Issue::report("StructFieldsResolver: Missing key attribute '%s', have value attributes for map",
                          _map_key_attribute.c_str());
            _error = true;
        } else if (_array_fields.size() != 1u) {
            if (declared_shape == CombinerShape::MAP_OF_STRUCT) {
                Issue::report("StructFieldsResolver: Field '%s' is configured as a map of struct, but has struct "
                              "field attributes besides '%s.key' which are not sub-fields of '%s.value'",
                              field_name.c_str(), field_name.c_str(), field_name.c_str());
            } else {
                Issue::report("StructFieldsResolver: Could not determine if field '%s' is array or map of struct",
                              field_name.c_str());
            }
            _error = true;
        }
    }
}

void StructFieldsResolver::resolve_shape(CombinerShape declared_shape) {
    // What the attributes which are actually present say. This is the answer for CombinerShape::INFER,
    // and the fallback whenever a declared shape disagrees with them.
    bool has_value_sub_fields = !_map_value_fields.empty();
    bool looks_like_map_of_scalar =
        _has_map_key && _has_map_value && (_array_fields.size() == 2u) && !has_value_sub_fields;
    switch (declared_shape) {
    case CombinerShape::ARRAY_OF_STRUCT:
        if (!has_value_sub_fields) {
            _is_map_of_struct = false;
            _is_map_of_scalar = false;
            return;
        }
        Issue::report("StructFieldsResolver: Field '%s' is configured as an array of struct, but has "
                      "'%s.value.<name>' struct field attributes; deducing its shape instead",
                      _field_name.c_str(), _field_name.c_str());
        break;
    case CombinerShape::MAP_OF_STRUCT:
        if (has_value_sub_fields) {
            _is_map_of_struct = true;
            _is_map_of_scalar = false;
            return;
        }
        Issue::report("StructFieldsResolver: Field '%s' is configured as a map of struct, but has no "
                      "'%s.value.<name>' struct field attribute; deducing its shape instead",
                      _field_name.c_str(), _field_name.c_str());
        break;
    case CombinerShape::MAP_OF_SCALAR:
        if (looks_like_map_of_scalar) {
            _is_map_of_struct = false;
            _is_map_of_scalar = true;
            return;
        }
        Issue::report("StructFieldsResolver: Field '%s' is configured as a map of scalar, but does not have "
                      "exactly the struct field attributes '%s.key' and '%s.value'; deducing its shape instead",
                      _field_name.c_str(), _field_name.c_str(), _field_name.c_str());
        break;
    case CombinerShape::INFER:
        break;
    }
    _is_map_of_struct = has_value_sub_fields;
    _is_map_of_scalar = looks_like_map_of_scalar;
}

void StructFieldsResolver::apply_struct_field_selection(std::span<const std::string> selected_struct_fields,
                                                        StringVector&                unselected_array_fields) {
    auto is_selected = [selected_struct_fields](const std::string& name) {
        return std::find(selected_struct_fields.begin(), selected_struct_fields.end(), name) !=
               selected_struct_fields.end();
    };
    // The names which may legally be used to select a sub-field of this field, given its shape.
    StringVector selectable_fields;
    if (!_is_map_of_struct) {
        // Array of struct, or map of scalar. The entries in _array_fields are the selectable struct
        // sub-fields, i.e. "key" and "value" for a map of scalar.
        selectable_fields = _array_fields;
        auto first_unselected = std::partition(_array_fields.begin(), _array_fields.end(), is_selected);
        unselected_array_fields.assign(first_unselected, _array_fields.end());
        _array_fields.erase(first_unselected, _array_fields.end());
    } else {
        // Map of struct. Here the "key" entry in _array_fields is a structural marker used to detect the
        // map, not a selectable struct sub-field; the key is always part of the output. Selecting it is
        // allowed but has no effect. The sub-fields of the value struct are selected as "value.<name>".
        selectable_fields.emplace_back("key");
        for (const auto& field : _map_value_fields) {
            selectable_fields.emplace_back("value." + field);
        }
        auto is_selected_value_field = [&is_selected](const std::string& name) {
            return is_selected("value." + name);
        };
        // The value sub-fields left out are dropped outright, with nothing kept for the element count:
        // for a map it is the key attribute alone which determines how many elements there are, cf.
        // StructMapAttributeCombinerDFW, so the count is unaffected by the selection anyway.
        auto first_unselected =
            std::partition(_map_value_fields.begin(), _map_value_fields.end(), is_selected_value_field);
        _map_value_fields.erase(first_unselected, _map_value_fields.end());
    }
    bool has_unknown_name = false;
    for (auto name = selected_struct_fields.begin(); name != selected_struct_fields.end(); ++name) {
        if (std::find(selectable_fields.begin(), selectable_fields.end(), *name) == selectable_fields.end()) {
            Issue::report("StructFieldsResolver: '%s' is not a struct field attribute sub-field of field '%s'",
                          name->c_str(), _field_name.c_str());
            _error = true;
            has_unknown_name = true;
        } else if (std::find(selected_struct_fields.begin(), name, *name) != name) {
            // Harmless, since the selection is only ever used for membership tests, but it means the
            // configuration says something it does not need to say.
            Issue::report("StructFieldsResolver: Struct field '%s' of field '%s' is selected more than once",
                          name->c_str(), _field_name.c_str());
        }
    }
    // Only reported when the selection is otherwise valid, since an unknown name explains an empty
    // selection better than this does. Without any value sub-fields the field would silently be written
    // as an array of key-only structs instead of as a map, so refuse it instead.
    if (_is_map_of_struct && _map_value_fields.empty() && !has_unknown_name) {
        Issue::report("StructFieldsResolver: Selected struct fields for map field '%s' do not include "
                      "any of its value sub-fields",
                      _field_name.c_str());
        _error = true;
    }
}

bool StructFieldsResolver::uses_attribute(const std::string& attribute_name) const {
    if (_is_map_of_struct) {
        // StructMapAttributeCombinerDFW writes the key and the selected value sub-fields, and never
        // looks at _array_attributes.
        return attribute_name == _map_key_attribute ||
               std::find(_map_value_attributes.begin(), _map_value_attributes.end(), attribute_name) !=
                   _map_value_attributes.end();
    }
    // ArrayAttributeCombinerDFW writes the selected array sub-fields. The unselected ones only take part
    // in the element count, and are kept out of that above unless they are array attributes.
    return std::find(_array_attributes.begin(), _array_attributes.end(), attribute_name) != _array_attributes.end();
}

StructFieldsResolver::~StructFieldsResolver() = default;

} // namespace search::docsummary
