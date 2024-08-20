// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <set>
#include <string>

namespace search {

/**
 * Keeps track of a set of field names to calculate MatchingElements for.
 *
 * Also has mapping of the full name of struct fields into the name of the enclosing field.
 * Example:
 * A map<string, string> field "my_map" could contain two struct fields: "my_map.key" and "my_map.value".
 **/
class MatchingElementsFields {
private:
    std::set<std::string> _fields;
    std::map<std::string, std::string> _struct_fields;

public:
    MatchingElementsFields();
    ~MatchingElementsFields();
    bool empty() const { return _fields.empty(); }
    void add_field(const std::string &field_name) {
        _fields.insert(field_name);
    }
    void add_mapping(const std::string &field_name,
                     const std::string &struct_field_name) {
        _fields.insert(field_name);
        _struct_fields[struct_field_name] = field_name;
    }
    bool has_field(const std::string &field_name) const {
        return (_fields.count(field_name) > 0);
    }
    bool has_struct_field(const std::string &struct_field_name) const {
        return (_struct_fields.find(struct_field_name) != _struct_fields.end());
    }
    const std::string &get_enclosing_field(const std::string &struct_field_name) const {
        static const std::string empty;
        auto res = _struct_fields.find(struct_field_name);
        if (res == _struct_fields.end()) {
            return empty;
        }
        return res->second;
    }
};

} // namespace search
