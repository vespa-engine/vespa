// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_path_target.h"

#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/fieldpath.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/update/assignfieldpathupdate.h>

using document::AssignFieldPathUpdate;
using document::DocumentType;
using document::Field;
using document::FieldNotFoundException;
using document::FieldPath;
using document::FieldPathEntry;
using document::FieldPathUpdate;
using vespalib::IllegalArgumentException;

namespace proton {

FieldPathTarget FieldPathTarget::unsupported(Field field) {
    return FieldPathTarget(Kind::UNSUPPORTED, std::move(field), 0);
}

FieldPathTarget FieldPathTarget::assign_element(Field field, uint32_t index) {
    return FieldPathTarget(Kind::ASSIGN_ELEMENT, std::move(field), index);
}

namespace {

// my_arr [index]
bool is_simple_array_lookup(const FieldPath& field_path) {
    return field_path.size() == 2 && field_path[0].getType() == FieldPathEntry::Type::STRUCT_FIELD &&
           field_path[1].getType() == FieldPathEntry::Type::ARRAY_INDEX;
}

Field top_level_field(const FieldPath& field_path) {
    if (!field_path.empty() && field_path[0].hasField()) {
        return field_path[0].getFieldRef();
    }
    return Field();
}

} // namespace

FieldPathTarget FieldPathTarget::parse(const FieldPathUpdate& update, const DocumentType& doc_type) {
    FieldPath field_path;
    try {
        doc_type.buildFieldPath(field_path, update.getOriginalFieldPath());
    } catch (FieldNotFoundException&) {
        return unsupported(Field());
    } catch (vespalib::IllegalArgumentException&) {
        return unsupported(Field());
    }

    Field field = top_level_field(field_path);

    if (is_simple_array_lookup(field_path)) {
        switch (update.type()) {
        case FieldPathUpdate::Assign: {
            const auto& assign = static_cast<const AssignFieldPathUpdate&>(update);
            if (!assign.hasValue()) {
                // Expression assigns (e.g. increment) compute the new value from the current
                // document value, which cannot be evaluated against an attribute vector.
                return unsupported(std::move(field));
            }
            return assign_element(std::move(field), field_path[1].getIndex());
        }
        default:
            break;
        }
    }

    return unsupported(std::move(field));
}

} // namespace proton
