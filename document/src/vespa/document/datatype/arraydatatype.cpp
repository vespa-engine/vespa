// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "arraydatatype.h"
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>

namespace document {

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
    return std::make_unique<ArrayFieldValue>(*this);
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
ArrayDataType::equals(const DataType& other) const noexcept
{
    if (this == &other) return true;
    return CollectionDataType::equals(other) && other.isArray();
}

void
ArrayDataType::onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const
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

            getNestedType().buildFieldPath(path, remainFieldName.substr(pos));

            if (remainFieldName[1] == '$') {
                path.insert(path.begin(), std::make_unique<FieldPathEntry>(getNestedType(), remainFieldName.substr(2, endPos - 2)));
            } else {
                // FIXME C++17 range-safe from_chars() instead of atoi()
                path.insert(path.begin(), std::make_unique<FieldPathEntry>(getNestedType(), atoi(remainFieldName.substr(1, endPos - 1).data())));
            }
        }
    } else {
        getNestedType().buildFieldPath(path, remainFieldName);
    }
}

} // document
