// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "structdatatype.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>
#include <ostream>

#include <vespa/log/log.h>
LOG_SETUP(".document.datatype.struct");

namespace document {

using vespalib::make_string;
using vespalib::IllegalArgumentException;

StructDataType::StructDataType(vespalib::stringref name)
    : StructuredDataType(name),
      _nameFieldMap(),
      _idFieldMap()
{ }

StructDataType::StructDataType(vespalib::stringref name, int32_t dataTypeId)
    : StructuredDataType(name, dataTypeId),
      _nameFieldMap(),
      _idFieldMap()
{ }

StructDataType::StructDataType(const StructDataType & rhs) = default;
StructDataType::~StructDataType() = default;

void
StructDataType::print(std::ostream& out, bool verbose,
                      const std::string& indent) const
{
    out << "StructDataType(" << getName();
    if (verbose) {
        out << ", id " << getId();
    }
    out << ")";
    if (verbose) {
        out << " {";
        assert(_idFieldMap.size() == _nameFieldMap.size());
        if (_nameFieldMap.size() > 0) {
                // Use fieldset to print even though inefficient. Don't need
                // efficient print, and this gets fields in order
            Field::Set fields(getFieldSet());
            for (const Field * field : fields) {
                out << "\n" << indent << "  " << field->toString(verbose);
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
        throw IllegalArgumentException(make_string("Failed to add field '%s' to struct '%s': %s",
                                                   field.getName().data(), getName().c_str(),
                                                   error.c_str()), VESPA_STRLOC);
    }
    if (hasField(field.getName())) {
        return;
    }
    std::shared_ptr<Field> newF(new Field(field));
    _nameFieldMap[field.getName()] = newF;
    _idFieldMap[field.getId()] = newF;
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
        LOG(warning, "Inherited field %s conflicts with existing field. Field not added to struct %s: %s",
            field.toString().c_str(), getName().c_str(), error.c_str());
        return;
    }
    if (hasField(field.getName())) {
        return;
    }
    std::shared_ptr<Field> newF(new Field(field));
    _nameFieldMap[field.getName()] = newF;
    _idFieldMap[field.getId()] = newF;
}

FieldValue::UP
StructDataType::createFieldValue() const
{
    return std::make_unique<StructFieldValue>(*this);
}

const Field&
StructDataType::getField(vespalib::stringref name) const
{
    StringFieldMap::const_iterator it(_nameFieldMap.find(name));
    if (it == _nameFieldMap.end()) {
        throw FieldNotFoundException(name, VESPA_STRLOC);
    } else {
        return *it->second;
    }
}

namespace {

[[noreturn]] void throwFieldNotFound(int32_t fieldId, int version) __attribute__((noinline));

void throwFieldNotFound(int32_t fieldId, int version)
{
    throw FieldNotFoundException(fieldId, version, VESPA_STRLOC);
}

}

const Field&
StructDataType::getField(int32_t fieldId) const
{
    IntFieldMap::const_iterator it(_idFieldMap.find(fieldId));
    if (__builtin_expect(it == _idFieldMap.end(), false)) {
        throwFieldNotFound(fieldId, 7);
    }
    return *it->second;
}

bool
StructDataType::hasField(vespalib::stringref name) const noexcept {
    return _nameFieldMap.find(name) != _nameFieldMap.end();
}

bool
StructDataType::hasField(int32_t fieldId) const noexcept {
    return _idFieldMap.find(fieldId) != _idFieldMap.end();
}

Field::Set
StructDataType::getFieldSet() const
{
    Field::Set::Builder builder;
    builder.reserve(_idFieldMap.size());
    for (const auto & entry : _idFieldMap) {
        builder.add(entry.second.get());
    }
    return builder.build();
}

namespace {
// We cannot use Field::operator==(), since that only compares id.
bool differs(const Field &field1, const Field &field2) {
    return field1.getId() != field2.getId()
        || field1.getName() != field2.getName();
}
}  // namespace

vespalib::string
StructDataType::containsConflictingField(const Field& field) const
{
    StringFieldMap::const_iterator it1( _nameFieldMap.find(field.getName()));
    IntFieldMap::const_iterator it2(_idFieldMap.find(field.getId()));

    if (it1 != _nameFieldMap.end() && differs(field, *it1->second)) {
        return make_string("Name in use by field with different id %s.", it1->second->toString().c_str());
    }
    if (it2 != _idFieldMap.end() && differs(field, *it2->second)) {
        return make_string("Field id in use by field %s.", it2->second->toString().c_str());
    }

    return "";
}

} // document
