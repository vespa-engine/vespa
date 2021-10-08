// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weightedsetdatatype.h"
#include "mapdatatype.h"
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace document {

IMPLEMENT_IDENTIFIABLE(WeightedSetDataType, CollectionDataType);

namespace {
    vespalib::string createName(const DataType& nestedType, bool create, bool remove)
    {
        if (nestedType.getId() == DataType::T_STRING && create && remove) {
            return "Tag";
        }
        vespalib::asciistream ost;
        ost << "WeightedSet<" << nestedType.getName() << ">";
        if (create) {
            ost << ";Add";
        }
        if (remove) {
            ost << ";Remove";
        }
        return ost.str();
    }
}

WeightedSetDataType::WeightedSetDataType(
        const DataType& nested, bool createIfNon, bool remove)
    : CollectionDataType(createName(nested, createIfNon, remove), nested),
      _createIfNonExistent(createIfNon),
      _removeIfZero(remove)
{
}

WeightedSetDataType::WeightedSetDataType(
        const DataType& nested, bool createIfNon, bool remove, int id)
    : CollectionDataType(createName(nested, createIfNon, remove), nested, id),
      _createIfNonExistent(createIfNon),
      _removeIfZero(remove)
{
}

FieldValue::UP
WeightedSetDataType::createFieldValue() const
{
    return FieldValue::UP(new WeightedSetFieldValue(*this));

}

void
WeightedSetDataType::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    if (getNestedType() == *DataType::STRING &&
        _createIfNonExistent && _removeIfZero)
    {
        out << "Tag()";
    } else {
        out << "WeightedSetDataType(";
        getNestedType().print(out, verbose, indent + "    ");
        if (_createIfNonExistent) {
            out << ", autoIfNonExistent";
        }
        if (_removeIfZero) {
            out << ", removeIfZero";
        }
        out << ", id " << getId() << ")";
    }
}

bool
WeightedSetDataType::operator==(const DataType& other) const
{
    if (this == &other) return true;
    if (!CollectionDataType::operator==(other)) return false;
    const WeightedSetDataType* w(dynamic_cast<const WeightedSetDataType*>(&other));
    return (w != 0 && _createIfNonExistent == w->_createIfNonExistent
                   && _removeIfZero == w->_removeIfZero);
}

void
WeightedSetDataType::onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const
{
    MapDataType::buildFieldPathImpl(path, *this, remainFieldName, getNestedType(), *DataType::INT);
}

} // document
