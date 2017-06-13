// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldpath.h"
#include "field.h"
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

IMPLEMENT_IDENTIFIABLE_NS(document, FieldPathEntry, vespalib::Identifiable)

FieldPathEntry::~FieldPathEntry() { }

FieldPathEntry::FieldPathEntry() :
    _type(NONE),
    _name(""),
    _fieldRef(),
    _dataType(0),
    _lookupIndex(0),
    _lookupKey(),
    _variableName(),
    _fillInVal()
{ }

FieldPathEntry::FieldPathEntry(const DataType & dataType, uint32_t arrayIndex) :
    _type(ARRAY_INDEX),
    _name(""),
    _fieldRef(),
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
    _fieldRef(new Field(fieldRef)),
    _dataType(&fieldRef.getDataType()),
    _lookupIndex(0),
    _lookupKey(),
    _variableName(),
    _fillInVal(fieldRef.createValue().release())
{ }

FieldPathEntry::FieldPathEntry(const DataType & dataType, const DataType& fillType,
                               const FieldValueCP & lookupKey) :
    _type(MAP_KEY),
    _name("value"),
    _fieldRef(),
    _dataType(&dataType),
    _lookupIndex(0),
    _lookupKey(lookupKey),
    _variableName(),
    _fillInVal()
{
    setFillValue(fillType);
}

void FieldPathEntry::setFillValue(const DataType & dataType)
{
    const DataType * dt = & dataType;
    while (dt->inherits(CollectionDataType::classId) || dt->inherits(MapDataType::classId)) {
        dt = dt->inherits(CollectionDataType::classId)
             ? &static_cast<const CollectionDataType *>(dt)->getNestedType()
             : &static_cast<const MapDataType *>(dt)->getValueType();
    }
    if (dt->inherits(PrimitiveDataType::classId)) {
        _fillInVal.reset(dt->createFieldValue().release());
    }
}

FieldPathEntry::FieldPathEntry(const DataType&, const DataType& keyType,
                               const DataType& valueType, bool keysOnly, bool valuesOnly) :
    _type(keysOnly ? MAP_ALL_KEYS : MAP_ALL_VALUES),
    _name(keysOnly ? "key" : "value"),
    _fieldRef(),
    _dataType(keysOnly ? &keyType : &valueType),
    _lookupIndex(0),
    _lookupKey(),
    _variableName(),
    _fillInVal()
{
    (void)valuesOnly;
    setFillValue(*_dataType);
}

FieldPathEntry::FieldPathEntry(const DataType & dataType, const vespalib::stringref & variableName) :
    _type(VARIABLE),
    _name(""),
    _fieldRef(),
    _dataType(&dataType),
    _lookupIndex(0),
    _lookupKey(),
    _variableName(variableName),
    _fillInVal()
{
    setFillValue(*_dataType);
}

const DataType &FieldPathEntry::getDataType() const
{
     return _fieldRef.get() ? _fieldRef->getDataType()
                            : *_dataType;
}

void
FieldPathEntry::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "type", _type);
    visit(visitor, "name", _name);
    visit(visitor, "fieldRef", _fieldRef);
    visit(visitor, "dataType", _dataType);
    visit(visitor, "lookupIndex", _lookupIndex);
    visit(visitor, "lookupKey", _lookupKey);
    visit(visitor, "variableName", _variableName);
    visit(visitor, "fillInVal", _fillInVal);
}

vespalib::string FieldPathEntry::parseKey(vespalib::string & key)
{
    vespalib::string v;
    const char *c = key.c_str();
    const char *e = c + key.size();
    for(;(c < e) && isspace(c[0]); c++);
    if ((c < e) && (c[0] == '{')) {
        for(c++;(c < e) && isspace(c[0]); c++);
        if ((c < e) && (c[0] == '"')) {
            for (c++; (c < e) && (c[0] != '"'); c++) {
                if (c[0] == '\\') {
                    c++;
                }
                if (c < e) {
                    v += c[0];
                }
            }
            if ((c < e) && (c[0] == '"')) {
                c++;
            } else {
                throw IllegalArgumentException(make_string("Escaped key '%s' is incomplete. No matching '\"'", key.c_str()), VESPA_STRLOC);
            }
        } else {
            for (;(c < e) && (c[0] != '}'); c++) {
                v += c[0];
            }
        }
        for(;(c < e) && isspace(c[0]); c++);
        if ((c < e) && (c[0] == '}')) {
            key = c+1;
        } else {
            throw IllegalArgumentException(make_string("Key '%s' is incomplete. No matching '}'", key.c_str()), VESPA_STRLOC);
        }
    } else {
        throw IllegalArgumentException(make_string("key '%s' does not start with '{'", key.c_str()), VESPA_STRLOC);
    }
    return v;
}

FieldPath::FieldPath()
    : Cloneable(), _path()
{ }

FieldPath::FieldPath(const FieldPath& other)
    : Cloneable(), _path(other._path)
{ }

FieldPath::~FieldPath() { }

FieldPath&
FieldPath::operator=(const FieldPath& rhs)
{
    if (&rhs != this) {
        _path = rhs._path;
    }
    return *this;
}

FieldPath::iterator
FieldPath::insert(iterator pos, const FieldPathEntry& entry)
{
    return _path.insert(pos, entry);
}
void
FieldPath::push_back(const FieldPathEntry& entry)
{
    _path.push_back(entry);
}

void
FieldPath::pop_back()
{
    _path.pop_back();
}

void
FieldPath::clear()
{
    _path.clear();
}

void
FieldPath::visitMembers(vespalib::ObjectVisitor& visitor) const
{
    for (uint32_t i = 0; i < _path.size(); ++i) {
        visit(visitor, vespalib::make_string("[%u]", i), _path[i]);
    }
}

}
