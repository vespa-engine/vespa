// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search {
namespace attribute { class IAttributeContext; }
class MatchingElementsFields;
}

namespace search::docsummary {

/**
 * Class used to resolve which struct sub fields a complex field consists of,
 * based on which attribute vectors are present.
 */
class StructFieldsResolver {
private:
    using StringVector = std::vector<vespalib::string>;
    vespalib::string _field_name;
    vespalib::string _map_key_attribute;
    StringVector _map_value_fields;
    StringVector _map_value_attributes;
    StringVector _array_fields;
    StringVector _array_attributes;
    bool _has_map_key;
    bool _has_map_value;
    bool _error;

public:
    StructFieldsResolver(const vespalib::string& field_name, const search::attribute::IAttributeContext& attr_ctx,
                         bool require_all_struct_fields_as_attributes);
    ~StructFieldsResolver();
    bool is_map_of_scalar() const { return (_has_map_key &&
                                            _has_map_value &&
                                            (_array_fields.size() == 2u) &&
                                            _map_value_fields.empty());
    }
    bool is_map_of_struct() const { return !_map_value_fields.empty(); }
    const vespalib::string& get_map_key_attribute() const { return _map_key_attribute; }
    const StringVector& get_map_value_fields() const { return _map_value_fields; }
    const StringVector& get_map_value_attributes() const { return _map_value_attributes; }
    const StringVector& get_array_fields() const { return _array_fields; }
    const StringVector& get_array_attributes() const { return _array_attributes; }
    bool has_error() const { return _error; }
    void apply_to(MatchingElementsFields& fields) const;
};

}

