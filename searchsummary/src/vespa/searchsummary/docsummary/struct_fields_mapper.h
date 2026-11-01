// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <set>
#include <string>
#include <vector>

namespace search::attribute {
class IAttributeContext;
}
namespace search::index {
class Schema;
}

namespace search::docsummary {

/*
 * Mapping from document field to struct fields within the document field.
 */
class StructFieldsMapper {
    std::map<std::string, std::set<std::string>> _fields;

public:
    StructFieldsMapper();
    StructFieldsMapper(const StructFieldsMapper& rhs);
    StructFieldsMapper(StructFieldsMapper&& rhs) noexcept;
    ~StructFieldsMapper();
    void add(const std::string& field);
    // Adds the struct field attributes (e.g. 'persons.age').
    void setup(const search::attribute::IAttributeContext& ctx);
    // Adds the struct field indexes (e.g. 'persons.name'), see Schema::is_struct_field_index().
    void setup(const search::index::Schema& schema);
    std::vector<std::string> get_struct_fields(const std::string& field) const;
};

} // namespace search::docsummary
