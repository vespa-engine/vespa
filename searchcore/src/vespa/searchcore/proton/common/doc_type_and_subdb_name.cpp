// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doc_type_and_subdb_name.h"

#include <format>

namespace proton {

DocTypeAndSubDbName::DocTypeAndSubDbName(const DocTypeName& doc_type_name_in, const std::string& subdb_name_in)
    : _doc_type_name(doc_type_name_in), _subdb_name(subdb_name_in) {
}

DocTypeAndSubDbName::~DocTypeAndSubDbName() = default;

std::string DocTypeAndSubDbName::combined_name() const {
    return std::format("{}.{}", _doc_type_name.getName(), _subdb_name);
}

std::shared_ptr<const DocTypeAndSubDbName> DocTypeAndSubDbName::create(const DocTypeName& doc_type_name_in,
                                                                       const std::string& subdb_name_in) {
    return std::make_shared<const DocTypeAndSubDbName>(doc_type_name_in, subdb_name_in);
}

} // namespace proton
