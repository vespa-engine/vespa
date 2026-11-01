// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "struct_fields_mapper.h"

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/common/schema.h>

namespace search::docsummary {

StructFieldsMapper::StructFieldsMapper() : _fields() {
}

StructFieldsMapper::StructFieldsMapper(const StructFieldsMapper& rhs) = default;
StructFieldsMapper::StructFieldsMapper(StructFieldsMapper&& rhs) noexcept = default;
StructFieldsMapper::~StructFieldsMapper() = default;

void StructFieldsMapper::add(const std::string& field) {
    auto pos = field.find('.');
    if (pos != std::string::npos && field.size() > pos + 1) {
        // struct field
        _fields[field.substr(0, pos)].insert(field);
    }
}

void StructFieldsMapper::setup(const search::attribute::IAttributeContext& ctx) {
    std::vector<const search::attribute::IAttributeVector*> attrv;
    ctx.getAttributeList(attrv);
    for (auto& attr : attrv) {
        add(attr->getName());
    }
}

void StructFieldsMapper::setup(const search::index::Schema& schema) {
    for (const auto& index_field : schema.getIndexFields()) {
        if (schema.is_struct_field_index(index_field.getName())) {
            add(index_field.getName());
        }
    }
}

std::vector<std::string> StructFieldsMapper::get_struct_fields(const std::string& field) const {
    std::vector<std::string> result;
    auto                     it = _fields.find(field);
    if (it != _fields.end()) {
        result.reserve(it->second.size());
        for (const auto& sf : it->second) {
            result.emplace_back(sf);
        }
    }
    return result;
}

} // namespace search::docsummary
