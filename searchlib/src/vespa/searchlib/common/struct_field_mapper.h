// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <set>
#include <map>

namespace search {

/**
 * Keeps track of a set of struct field names and enables mapping the
 * full name of struct subfields into the name of the enclosing struct
 * field.
 **/
class StructFieldMapper
{
private:
    std::set<vespalib::string> _struct_fields;
    std::map<vespalib::string,vespalib::string> _struct_subfields;

public:
    StructFieldMapper();
    ~StructFieldMapper();
    bool empty() const { return _struct_fields.empty(); }
    void add_mapping(const vespalib::string &struct_field_name,
                     const vespalib::string &struct_subfield_name)
    {
        _struct_fields.insert(struct_field_name);
        _struct_subfields[struct_subfield_name] = struct_field_name;
    }
    bool is_struct_field(const vespalib::string &field_name) const {
        return (_struct_fields.count(field_name) > 0);
    }
    bool is_struct_subfield(const vespalib::string &field_name) const {
        return (_struct_subfields.find(field_name) != _struct_subfields.end());
    }
    const vespalib::string &get_struct_field(const vespalib::string &struct_subfield_name) const {
        static const vespalib::string empty;
        auto res = _struct_subfields.find(struct_subfield_name);
        if (res == _struct_subfields.end()) {
            return empty;
        }
        return res->second;
    }
};

} // namespace search
