// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mapdatatype.h"
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace document {

using namespace std::literals;

namespace {

constexpr auto key_keyword = "key"sv;
constexpr auto value_keyword = "value"sv;

vespalib::string createName(const DataType& keyType, const DataType& valueType)
{
    vespalib::asciistream ost;
    ost << "Map<" << keyType.getName() << "," << valueType.getName() << ">";
    return ost.str();
}
}  // namespace

MapDataType::MapDataType(const DataType &key, const DataType &value) noexcept
    : DataType(createName(key, value)),
      _keyType(&key),
      _valueType(&value) {
}

MapDataType::MapDataType(const DataType &key, const DataType &value, int id) noexcept
    : DataType(createName(key, value), id),
      _keyType(&key),
      _valueType(&value) {
}

MapDataType::~MapDataType() = default;

FieldValue::UP MapDataType::createFieldValue() const {
    return std::make_unique<MapFieldValue>(*this);
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
MapDataType::equals(const DataType& other) const noexcept
{
    if (this == &other) return true;
    if (!DataType::equals(other)) return false;
    const MapDataType * w = other.cast_map();
    return w && _keyType->equals(*w->_keyType) && _valueType->equals(*w->_valueType);
}

void
MapDataType::buildFieldPathImpl(FieldPath & path, const DataType &dataType,
                                std::string_view remainFieldName,
                                const DataType &keyType, const DataType &valueType)
{
    if (!remainFieldName.empty() && remainFieldName[0] == '{') {
        std::string_view rest = remainFieldName;
        vespalib::string keyValue = FieldPathEntry::parseKey(rest);

        valueType.buildFieldPath(path, (!rest.empty() && rest[0] == '.') ? rest.substr(1) : rest);

        if (remainFieldName.size() > 1 && remainFieldName[1] == '$') {
            path.insert(path.begin(), std::make_unique<FieldPathEntry>(valueType, keyValue.substr(1)));
        } else {
            FieldValue::UP fv = keyType.createFieldValue();
            *fv = keyValue;
            path.insert(path.begin(), std::make_unique<FieldPathEntry>(valueType, dataType, std::move(fv)));
        }
    } else if (remainFieldName.starts_with(key_keyword)) {
        size_t endPos = key_keyword.size();;
        if (remainFieldName.size() > endPos && remainFieldName[endPos] == '.') {
            endPos++;
        }

        keyType.buildFieldPath(path, remainFieldName.substr(endPos));

        path.insert(path.begin(), std::make_unique<FieldPathEntry>(dataType, keyType, valueType, true, false));
    } else if (remainFieldName.starts_with(value_keyword)) {
        size_t endPos = value_keyword.size();
        if (remainFieldName.size() > endPos && remainFieldName[endPos] == '.') {
            endPos++;
        }

        valueType.buildFieldPath(path, remainFieldName.substr(endPos));

        path.insert(path.begin(), std::make_unique<FieldPathEntry>(dataType, keyType, valueType, false, true));
    } else {
        keyType.buildFieldPath(path, remainFieldName);
    }
}

void
MapDataType::onBuildFieldPath(FieldPath & fieldPath, std::string_view remainFieldName) const
{
    buildFieldPathImpl(fieldPath, *this, remainFieldName, getKeyType(), getValueType());
}

}  // namespace document
