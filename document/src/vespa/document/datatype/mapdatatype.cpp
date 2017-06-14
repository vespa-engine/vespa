// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mapdatatype.h"
#include "primitivedatatype.h"
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace document {

IMPLEMENT_IDENTIFIABLE(MapDataType, DataType);

namespace {
vespalib::string createName(const DataType& keyType, const DataType& valueType)
{
    vespalib::asciistream ost;
    ost << "Map<" << keyType.getName() << "," << valueType.getName() << ">";
    return ost.str();
}
}  // namespace

MapDataType::MapDataType(const DataType &key, const DataType &value)
    : DataType(createName(key, value)),
      _keyType(&key),
      _valueType(&value) {
}

MapDataType::MapDataType(const DataType &key, const DataType &value, int id)
    : DataType(createName(key, value), id),
      _keyType(&key),
      _valueType(&value) {
}

FieldValue::UP MapDataType::createFieldValue() const {
    return FieldValue::UP(new MapFieldValue(*this));
}

void
MapDataType::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    out << "MapDataType(";
    getKeyType().print(out, verbose, indent + "    ");
    out << ", ";
    getValueType().print(out, verbose, indent + "    ");
    out << ", id " << getId() << ")";
}

bool
MapDataType::operator==(const DataType& other) const
{
    if (this == &other) return true;
    if (!DataType::operator==(other)) return false;
    const MapDataType* w(Identifiable::cast<const MapDataType*>(&other));
    return (*_keyType == *w->_keyType) && (*_valueType == *w->_valueType);
}

FieldPath::UP
MapDataType::buildFieldPathImpl(const DataType &dataType,
                                const vespalib::stringref &remainFieldName,
                                const DataType &keyType,
                                const DataType &valueType)
{
    if (!remainFieldName.empty() && remainFieldName[0] == '{') {
        vespalib::string rest = remainFieldName;
        vespalib::string keyValue = FieldPathEntry::parseKey(rest);

        FieldPath::UP path =
            valueType.buildFieldPath((rest[0] == '.') ? rest.substr(1) : rest);
        if (!path.get()) {
            return FieldPath::UP();
        }

        if (remainFieldName[1] == '$') {
            path->insert(path->begin(),
                         FieldPathEntry(valueType, keyValue.substr(1)));
        } else {
            FieldValue::UP fv = keyType.createFieldValue();
            *fv = keyValue;
            path->insert(path->begin(), FieldPathEntry(valueType, dataType,
                                 vespalib::CloneablePtr<FieldValue>(fv.release())));
        }

        return path;
    } else if (memcmp(remainFieldName.c_str(), "key", 3) == 0) {
        size_t endPos = 3;
        if (remainFieldName[endPos] == '.') {
            endPos++;
        }

        FieldPath::UP path
            = keyType.buildFieldPath(remainFieldName.substr(endPos));
        if (!path.get()) {
            return FieldPath::UP();
        }
        path->insert(path->begin(), FieldPathEntry(dataType, keyType,
                                                   valueType, true, false));
        return path;
    } else if (memcmp(remainFieldName.c_str(), "value", 5) == 0) {
        size_t endPos = 5;
        if (remainFieldName[endPos] == '.') {
            endPos++;
        }

        FieldPath::UP path
            = valueType.buildFieldPath(remainFieldName.substr(endPos));
        if (!path.get()) {
            return FieldPath::UP();
        }
        path->insert(path->begin(), FieldPathEntry(dataType, keyType,
                                                   valueType, false, true));
        return path;
    }

    return keyType.buildFieldPath(remainFieldName);
}

FieldPath::UP
MapDataType::onBuildFieldPath(const vespalib::stringref &remainFieldName) const
{
    return buildFieldPathImpl(*this, remainFieldName,
                              getKeyType(), getValueType());
}

}  // namespace document
