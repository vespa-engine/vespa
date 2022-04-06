// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldpath.h"
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/fieldvalue/fieldvalue.h>

using vespalib::IllegalArgumentException;
using vespalib::make_string;

namespace document {

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
    _fillInVal(fieldRef.createValue())
{ }

FieldPathEntry::FieldPathEntry(const DataType & dataType, const DataType& fillType,
                               FieldValue::UP lookupKey) :
    _type(MAP_KEY),
    _name("value"),
    _field(),
    _dataType(&dataType),
    _lookupIndex(0),
    _lookupKey(std::move(lookupKey)),
    _variableName(),
    _fillInVal()
{
    setFillValue(fillType);
}

FieldPathEntry::FieldPathEntry(const FieldPathEntry &rhs)
    : _type(rhs._type),
      _name(rhs._name),
      _field(rhs._field),
      _dataType(rhs._dataType),
      _lookupIndex(rhs._lookupIndex),
      _lookupKey(rhs._lookupKey ? rhs._lookupKey->clone() : nullptr),
      _variableName(rhs._variableName),
      _fillInVal(rhs._fillInVal ? rhs._fillInVal->clone() : nullptr)
{}

void
FieldPathEntry::setFillValue(const DataType & dataType)
{
    const DataType * dt = & dataType;

    while (true) {
        const CollectionDataType *ct = dt->cast_collection();
        if (ct != nullptr) {
            dt = &ct->getNestedType();
        } else {
            const MapDataType * mt = dt->cast_map();
            if (mt != nullptr) {
                dt = &mt->getValueType();
            } else {
                break;
            }
        }
    }
    if (dt->isPrimitive()) {
        _fillInVal = dt->createFieldValue();
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
    return std::move(_fillInVal);
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

FieldPath::FieldPath() = default;
FieldPath::~FieldPath() = default;

FieldPath::FieldPath(const FieldPath & rhs)
    : _path()
{
    _path.reserve(rhs.size());
    for (const auto & e : rhs._path) {
        _path.emplace_back(std::make_unique<FieldPathEntry>(*e));
    }
}
FieldPath::iterator
FieldPath::insert(iterator pos, std::unique_ptr<FieldPathEntry> entry) {
    return _path.insert(pos, std::move(entry));
}
void FieldPath::push_back(std::unique_ptr<FieldPathEntry> entry) { _path.emplace_back(entry.release()); }
void FieldPath::pop_back() { _path.pop_back(); }
void FieldPath::clear() { _path.clear(); }
void FieldPath::reserve(size_t sz) { _path.reserve(sz); }

}
