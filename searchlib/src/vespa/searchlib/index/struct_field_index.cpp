// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "struct_field_index.h"

#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/fieldpath.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/searchlib/common/document_field_extractor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search::index {

using document::DocumentType;
using document::FieldPath;
using vespalib::make_string_short::fmt;

std::unique_ptr<const FieldPath> get_struct_field_path(const DocumentType& doc_type, std::string_view name) {
    if (name.find('.') == std::string_view::npos) {
        return {};
    }
    auto field_path = std::make_unique<FieldPath>();
    try {
        doc_type.buildFieldPath(*field_path, name);
    } catch (document::FieldNotFoundException&) {
        return {};
    } catch (vespalib::IllegalArgumentException&) {
        return {};
    }
    const auto& complex_type = (*field_path)[0].getDataType();
    if (!(complex_type.isArray() || complex_type.isMap()) || !DocumentFieldExtractor::isSupported(*field_path)) {
        return {};
    }
    return field_path;
}

std::string get_struct_field_index_problem(const Schema::IndexField& index_field, const FieldPath& field_path) {
    std::string problem;
    if (index_field.getDataType() != Schema::DataType::STRING ||
        index_field.getCollectionType() != Schema::CollectionType::ARRAY) {
        problem = fmt("the index is %s %s, expected %s %s", getTypeName(index_field.getCollectionType()).c_str(),
                      getTypeName(index_field.getDataType()).c_str(),
                      getTypeName(Schema::CollectionType::ARRAY).c_str(),
                      getTypeName(Schema::DataType::STRING).c_str());
    }
    const auto& field_type = field_path.back().getDataType();
    if (field_type != *document::DataType::STRING) {
        if (!problem.empty()) {
            problem += ", and ";
        }
        problem += fmt("the field has type %s, expected %s", field_type.getName().c_str(),
                       document::DataType::STRING->getName().c_str());
    }
    return problem;
}

} // namespace search::index
