// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>

namespace document {

IMPLEMENT_IDENTIFIABLE(ArrayDataType, CollectionDataType);

ArrayDataType::ArrayDataType(const DataType &nestedType, int32_t id)
    : CollectionDataType("Array<" + nestedType.getName() + ">", nestedType, id)
{
}

ArrayDataType::ArrayDataType(const DataType& nestedType)
    : CollectionDataType("Array<" + nestedType.getName() + ">", nestedType)
{
}

FieldValue::UP
ArrayDataType::createFieldValue() const
{
    return FieldValue::UP(new ArrayFieldValue(*this));
}

void
ArrayDataType::print(std::ostream& out, bool verbose,
                     const std::string& indent) const
{
    out << "ArrayDataType(\n" << indent << "    ";
    getNestedType().print(out, verbose, indent + "    ");
    out << ", id " << getId() << ")";
}

bool
ArrayDataType::operator==(const DataType& other) const
{
    if (this == &other) return true;
    if (!CollectionDataType::operator==(other)) return false;
    return other.inherits(ArrayDataType::classId);
}

FieldPath::UP
ArrayDataType::onBuildFieldPath(const vespalib::stringref & remainFieldName) const
{
    if (remainFieldName[0] == '[') {
        size_t endPos = remainFieldName.find(']');
        if (endPos == vespalib::stringref::npos) {
            throw vespalib::IllegalArgumentException("Array subscript must be closed with ]");
        } else {
            int pos = endPos + 1;
            if (remainFieldName[pos] == '.') {
                pos++;
            }

            FieldPath::UP path = getNestedType().buildFieldPath(remainFieldName.substr(pos));
            if (!path.get()) {
                return FieldPath::UP();
            }
            if (remainFieldName[1] == '$') {
                path->insert(path->begin(), FieldPathEntry(getNestedType(), remainFieldName.substr(2, endPos - 2)));
            } else {
                path->insert(path->begin(), FieldPathEntry(getNestedType(), atoi(remainFieldName.substr(1, endPos - 1).c_str())));
            }

            return path;
        }
    }

    return getNestedType().buildFieldPath(remainFieldName);
}

} // document
