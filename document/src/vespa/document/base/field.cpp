// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field.h"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/bobhash.h>
#include <algorithm>

namespace document {

Field::Set::Set(std::vector<CPtr> fields)
    : _fields(std::move(fields))
{
    std::sort(_fields.begin(), _fields.end(), Field::FieldPtrLess());
    _fields.erase(std::unique(_fields.begin(), _fields.end(), Field::FieldPtrEqual()), _fields.end());
}

bool
Field::Set::contains(const Field & field) const {
    return std::binary_search(_fields.begin(), _fields.end(), &field, Field::FieldPtrLess());
}

bool
Field::Set::contains(const Set & fields) const {
    return std::includes(_fields.begin(), _fields.end(),
                         fields._fields.begin(), fields._fields.end(),
                         Field::FieldPtrLess());
}

Field::Field()
    : Field("", 0, *DataType::INT)
{ }

Field::Field(vespalib::stringref name, int fieldId, const DataType& dataType)
    : FieldSet(),
      _name(name),
      _dataType(&dataType),
      _fieldId(fieldId)
{ }

Field::Field(vespalib::stringref name, const DataType& dataType)
    : FieldSet(),
      _name(name),
      _dataType(&dataType),
      _fieldId(calculateIdV7())
{ }

Field::~Field() = default;

FieldValue::UP
Field::createValue() const {
    return _dataType->createFieldValue();
}

vespalib::string
Field::toString(bool verbose) const
{
    vespalib::asciistream out;
    out << "Field(" << getName();
    if (verbose) {
        out << ", id " << _fieldId;
    }
    out << ", " << _dataType->toString();
    out << ")";
    return out.str();
}

bool
Field::contains(const FieldSet& fields) const
{
    switch (fields.getType()) {
        case Type::FIELD:
            return static_cast<const Field&>(fields).getId() == getId();
        case Type::SET: {
            const auto & set = static_cast<const FieldCollection &>(fields);
            return (set.getFields().size() == 1) && ((*set.getFields().begin())->getId() == getId());
        }
        case Type::NONE:
        case Type::DOCID:
        return true;
        case Type::DOCUMENT_ONLY:
        case Type::ALL:
        return false;
    }

    return false;
}

int
Field::calculateIdV7()
{
    vespalib::asciistream ost;
    ost << getName();
    ost << _dataType->getId();

    int newId = vespalib::BobHash::hash(ost.str().data(), ost.str().length(), 0);
    // Highest bit is reserved to tell 7-bit id's from 31-bit ones
    if (newId < 0) newId = -newId;
    validateId(newId);
    return newId;
}

void
Field::validateId(int newId) {
    if (newId >= 100 && newId <= 127) {
        throw vespalib::IllegalArgumentException(vespalib::make_string(
                    "Attempt to set the id of %s to %d failed, values from "
                    "100 to 127 are reserved for internal use",
                    getName().data(), newId));
    }

    if ((uint32_t(newId) & 0x80000000u) != 0) // Highest bit must not be set
    {
        throw vespalib::IllegalArgumentException(vespalib::make_string(
                    "Attempt to set the id of %s to %d"
                    " failed, negative id values are illegal",
                    getName().data(), newId));
    }
}

} // document
