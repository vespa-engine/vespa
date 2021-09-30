// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldpath.h"
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/datatype/primitivedatatype.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/vespalib/objects/visit.hpp>

using vespalib::IllegalArgumentException;
using vespalib::make_string;

namespace document {

FieldPathEntry::FieldPathEntry(const FieldPathEntry &) = default;
FieldPathEntry::~FieldPathEntry() = default;

FieldPathEntry::FieldPathEntry() :
    _type(NONE),
    _name(""),
    _field(),
    _dataType(0),
    _lookupIndex(0),
    _lookupKey(),
    _variableName(),
    _fillInVal()
{ }

FieldPathEntry::FieldPathEntry(const DataType & dataType, uint32_t arrayIndex) :
    _type(ARRAY_INDEX),
    _name(""),
    _field(),
    _dataType(&dataType),
    _lookupIndex(arrayIndex),
    _lookupKey(),
    _variableName(),
    _fillInVal()
{
    setFillValue(*_dataType);
}

FieldPathEntry::FieldPathEntry(const Field &fieldRef) :
    _type(STRUCT_FIELD),
    _name(fieldRef.getName()),
    _field(fieldRef),
    _dataType(&fieldRef.getDataType()),
    _lookupIndex(0),
    _lookupKey(),
    _variableName(),
    _fillInVal(fieldRef.createValue().release())
{ }

FieldPathEntry::FieldPathEntry(const DataType & dataType, const DataType& fillType,
                               FieldValue::UP lookupKey) :
    _type(MAP_KEY),
    _name("value"),
    _field(),
    _dataType(&dataType),
    _lookupIndex(0),
    _lookupKey(lookupKey.release()),
    _variableName(),
    _fillInVal()
{
    setFillValue(fillType);
}

void
FieldPathEntry::setFillValue(const DataType & dataType)
{
    const DataType * dt = & dataType;

    while (true) {
        if (dt->inherits(CollectionDataType::classId)) {
            dt = &static_cast<const CollectionDataType *>(dt)->getNestedType();
        } else if (dt->inherits(MapDataType::classId)) {
            dt = &static_cast<const MapDataType *>(dt)->getValueType();
        } else {
            break;
        }
    }
    if (dt->inherits(PrimitiveDataType::classId)) {
        _fillInVal.reset(dt->createFieldValue().release());
    }
}

FieldPathEntry::FieldPathEntry(const DataType&, const DataType& keyType,
                               const DataType& valueType, bool keysOnly, bool valuesOnly) :
    _type(keysOnly ? MAP_ALL_KEYS : MAP_ALL_VALUES),
    _name(keysOnly ? "key" : "value"),
    _field(),
    _dataType(keysOnly ? &keyType : &valueType),
    _lookupIndex(0),
    _lookupKey(),
    _variableName(),
    _fillInVal()
{
    (void)valuesOnly;
    setFillValue(*_dataType);
}

FieldPathEntry::FieldPathEntry(const DataType & dataType, vespalib::stringref variableName) :
    _type(VARIABLE),
    _name(""),
    _field(),
    _dataType(&dataType),
    _lookupIndex(0),
    _lookupKey(),
    _variableName(variableName),
    _fillInVal()
{
    setFillValue(*_dataType);
}

const DataType &
FieldPathEntry::getDataType() const
{
     return _field.valid() ? _field.getDataType() : *_dataType;
}

FieldValue::UP
FieldPathEntry::stealFieldValueToSet() const
{
    return FieldValue::UP(_fillInVal.release());
}

void
FieldPathEntry::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "type", _type);
    visit(visitor, "name", _name);
    visit(visitor, "dataType", _dataType);
    visit(visitor, "lookupIndex", _lookupIndex);
    visit(visitor, "lookupKey", _lookupKey);
    visit(visitor, "variableName", _variableName);
    visit(visitor, "fillInVal", _fillInVal);
}

vespalib::string
FieldPathEntry::parseKey(vespalib::stringref & key)
{
    vespalib::string v;
    const char *c = key.data();
    const char *e = c + key.size();
    for(;(c < e) && isspace(c[0]); c++);
    if ((c < e) && (c[0] == '{')) {
        for(c++;(c < e) && isspace(c[0]); c++);
        if ((c < e) && (c[0] == '"')) {
            const char * start = ++c;
            for (; (c < e) && (c[0] != '"'); c++) {
                if (c[0] == '\\') {
                    v.append(start, c-start);
                    start = ++c;
                }
            }
            v.append(start, c-start);
            if ((c < e) && (c[0] == '"')) {
                c++;
            } else {
                throw IllegalArgumentException(make_string("Escaped key '%s' is incomplete. No matching '\"'",
                                                           vespalib::string(key).c_str()), VESPA_STRLOC);
            }
        } else {
            const char * start = c;
            while ((c < e) && (c[0] != '}')) {
                c++;
            }
            v.append(start, c-start);
        }
        for(;(c < e) && isspace(c[0]); c++);
        if ((c < e) && (c[0] == '}')) {
            key = c+1;
        } else {
            throw IllegalArgumentException(make_string("Key '%s' is incomplete. No matching '}'",
                                                       vespalib::string(key).c_str()), VESPA_STRLOC);
        }
    } else {
        throw IllegalArgumentException(make_string("key '%s' does not start with '{'",
                                                   vespalib::string(key).c_str()), VESPA_STRLOC);
    }
    return v;
}

FieldPath::FieldPath()
    : _path()
{ }

FieldPath::FieldPath(const FieldPath &) = default;
FieldPath & FieldPath::operator=(const FieldPath &) = default;
FieldPath::~FieldPath() = default;

FieldPath::iterator FieldPath::insert(iterator pos, std::unique_ptr<FieldPathEntry> entry) {
    return _path.insert(pos, vespalib::CloneablePtr<FieldPathEntry>(entry.release()));
}
void FieldPath::push_back(std::unique_ptr<FieldPathEntry> entry) { _path.emplace_back(entry.release()); }
void FieldPath::pop_back() { _path.pop_back(); }
void FieldPath::clear() { _path.clear(); }
void FieldPath::reserve(size_t sz) { _path.reserve(sz); }

void
FieldPath::visitMembers(vespalib::ObjectVisitor& visitor) const
{
    (void) visitor;
    for (uint32_t i = 0; i < _path.size(); ++i) {
//        visit(visitor, vespalib::make_string("[%u]", i), _path[i]);
    }
}

}
