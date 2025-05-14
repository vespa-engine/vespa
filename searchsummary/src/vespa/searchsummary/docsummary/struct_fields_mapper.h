// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <set>
#include <string>
#include <vector>

namespace search::attribute { class IAttributeContext; }

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
    void setup(const search::attribute::IAttributeContext& ctx);
    std::vector<std::string> get_struct_fields(const std::string& field) const;
};

}
