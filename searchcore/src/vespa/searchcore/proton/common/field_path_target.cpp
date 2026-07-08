// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_path_target.h"

#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/fieldpath.h>
#include <vespa/document/datatype/documenttype.h>

using document::DocumentType;
using document::FieldNotFoundException;
using document::FieldPath;
using document::FieldPathEntry;
using vespalib::IllegalArgumentException;

namespace proton {

FieldPathTarget FieldPathTarget::unsupported() {
    FieldPathTarget target;
    target._kind = Kind::UNSUPPORTED;
    return target;
}

FieldPathTarget FieldPathTarget::array_index(std::string attribute_name, uint32_t index) {
    FieldPathTarget target;
    target._kind = Kind::ARRAY_INDEX;
    target._attribute_name = std::move(attribute_name);
    target._index = index;
    return target;
}

namespace {

// my_arr [index]
bool is_simple_array_lookup(const FieldPath& field_path) {
    return field_path.size() == 2 && field_path[0].getType() == FieldPathEntry::Type::STRUCT_FIELD &&
           field_path[1].getType() == FieldPathEntry::Type::ARRAY_INDEX;
}

} // namespace

FieldPathTarget FieldPathTarget::parse(const std::string& original_field_path, const DocumentType& doc_type) {
    FieldPath field_path;
    try {
        doc_type.buildFieldPath(field_path, original_field_path);
    } catch (FieldNotFoundException&) {
        return unsupported();
    } catch (vespalib::IllegalArgumentException&) {
        return unsupported();
    }

    if (is_simple_array_lookup(field_path)) {
        return array_index(field_path[0].getFieldRef().getName(), field_path[1].getIndex());
    }

    return unsupported();
}

} // namespace proton
