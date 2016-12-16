// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "structdatatype.h"

#include <iomanip>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/log/log.h>
LOG_SETUP(".document.datatype.struct");

namespace document {

IMPLEMENT_IDENTIFIABLE(StructDataType, StructuredDataType);

StructDataType::StructDataType() :
    StructuredDataType(),
    _nameFieldMap(),
    _idFieldMap(),
    _idFieldMapV6(),
    _compressionConfig()
{
}

StructDataType::StructDataType(const vespalib::stringref &name)
    : StructuredDataType(name),
      _nameFieldMap(),
      _idFieldMap(),
      _idFieldMapV6()
{
}

StructDataType::StructDataType(const vespalib::stringref & name, int32_t dataTypeId)
    : StructuredDataType(name, dataTypeId),
      _nameFieldMap(),
      _idFieldMap(),
      _idFieldMapV6()
{
}

StructDataType::~StructDataType() { }

StructDataType*
StructDataType::clone() const {
    return new StructDataType(*this);
}

void
StructDataType::print(std::ostream& out, bool verbose,
                      const std::string& indent) const
{
    out << "StructDataType(" << getName();
    if (verbose) {
        out << ", id " << getId();
        if (_compressionConfig.type != CompressionConfig::NONE) {
            out << ", Compression(" << _compressionConfig.type << ","
                << int(_compressionConfig.compressionLevel) << ","
                << int(_compressionConfig.threshold) << ")";
        }
    }
    out << ")";
    if (verbose) {
        out << " {";
        assert(_idFieldMap.size() == _nameFieldMap.size());
        assert(_idFieldMapV6.size() == _nameFieldMap.size());
        if (_nameFieldMap.size() > 0) {
                // Use fieldset to print even though inefficient. Don't need
                // efficient print, and this gets fields in order
            Field::Set fields(getFieldSet());
            for (Field::Set::const_iterator it = fields.begin();
                 it != fields.end(); ++it)
            {
                out << "\n" << indent << "  " << (*it)->toString(verbose);
            }
            out << "\n" << indent;
        }
        out << "}";
    }
}

void
StructDataType::addField(const Field& field)
{
    vespalib::string error = containsConflictingField(field);
    if (error != "") {
        throw vespalib::IllegalArgumentException(vespalib::make_string(
                "Failed to add field '%s' to struct '%s': %s",
                field.getName().c_str(), getName().c_str(), error.c_str()), VESPA_STRLOC);
    }
    if (hasField(field.getName())) {
        return;
    }
    std::shared_ptr<Field> newF(new Field(field));
    _nameFieldMap[field.getName()] = newF;
    _idFieldMap[field.getId(Document::getNewestSerializationVersion())] = newF;
    _idFieldMapV6[field.getId(6)] = newF;
}

void
StructDataType::addInheritedField(const Field& field)
{
    vespalib::string error = containsConflictingField(field);
    if (error != "") {
            // Deploy application should fail if overwriting a field with field
            // of different type. Java version of document sees to this. C++
            // just accepts what it gets, as to make it easier to alter the
            // restrictions.
        LOG(warning, "Inherited field %s conflicts with existing field. Field "
                     "not added to struct %s: %s",
            field.toString().c_str(), getName().c_str(), error.c_str());
        return;
    }
    if (hasField(field.getName())) {
        return;
    }
    std::shared_ptr<Field> newF(new Field(field));
    _nameFieldMap[field.getName()] = newF;
    _idFieldMap[field.getId(Document::getNewestSerializationVersion())] = newF;
    _idFieldMapV6[field.getId(6)] = newF;
}

FieldValue::UP
StructDataType::createFieldValue() const
{
    return FieldValue::UP(new StructFieldValue(*this));
}

const Field&
StructDataType::getField(const vespalib::stringref & name) const
{
    StringFieldMap::const_iterator it(
            _nameFieldMap.find(name));
    if (it == _nameFieldMap.end()) {
        throw FieldNotFoundException(name, VESPA_STRLOC);
    } else {
        return *it->second;
    }
}

namespace {

void throwFieldNotFound(int32_t fieldId, int version) __attribute__((noinline));

void throwFieldNotFound(int32_t fieldId, int version)
{
    throw FieldNotFoundException(fieldId, version, VESPA_STRLOC);
}

}

const Field&
StructDataType::getField(int32_t fieldId, int version) const
{
    if (__builtin_expect(version > 6, true)) {
        IntFieldMap::const_iterator it(_idFieldMap.find(fieldId));
        if (__builtin_expect(it == _idFieldMap.end(), false)) {
            throwFieldNotFound(fieldId, version);
        }
        return *it->second;
    } else {
        return getFieldV6(fieldId);
    }
}

const Field&
StructDataType::getFieldV6(int32_t fieldId) const
{
    IntFieldMap::const_iterator it(_idFieldMapV6.find(fieldId));
    if (it == _idFieldMapV6.end()) {
        throwFieldNotFound(fieldId, 6);
    }
    return *it->second;
}

bool StructDataType::hasField(const vespalib::stringref &name) const {
    return _nameFieldMap.find(name) != _nameFieldMap.end();
}

bool StructDataType::hasField(int32_t fieldId, int version) const {
    if (version > 6) {
        return _idFieldMap.find(fieldId) != _idFieldMap.end();
    } else {
        return _idFieldMapV6.find(fieldId) != _idFieldMapV6.end();
    }
}

Field::Set
StructDataType::getFieldSet() const
{
    Field::Set fields;
    for (IntFieldMap::const_iterator it = _idFieldMap.begin();
         it != _idFieldMap.end(); ++it)
    {
        fields.insert(it->second.get());
    }
    return fields;
}

namespace {
// We cannot use Field::operator==(), since that only compares id.
bool differs(const Field &field1, const Field &field2) {
    return field1.getId() != field2.getId()
        || field1.getId(6) != field2.getId(6)
        || field1.getName() != field2.getName();
}
}  // namespace

vespalib::string
StructDataType::containsConflictingField(const Field& field) const
{
    StringFieldMap::const_iterator it1( _nameFieldMap.find(field.getName()));
    IntFieldMap::const_iterator it2(
            _idFieldMap.find(field.getId(
                    Document::getNewestSerializationVersion())));
    IntFieldMap::const_iterator it3( _idFieldMapV6.find(field.getId(6)));

    if (it1 != _nameFieldMap.end() && differs(field, *it1->second)) {
        return vespalib::make_string(
                "Name in use by field with different id %s.",
                it1->second->toString().c_str());
    }
    if (it2 != _idFieldMap.end() && differs(field, *it2->second)) {
        return vespalib::make_string(
                "Field id in use by field %s.",
                it2->second->toString().c_str());
    }
    if (it3 != _idFieldMapV6.end() && differs(field, *it3->second)) {
        return vespalib::make_string(
                "Version 6 document field id in use by field %s.",
                it3->second->toString().c_str());
    }

    return "";
}

} // document
