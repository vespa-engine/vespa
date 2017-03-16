// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "structuredfieldvalue.hpp"
#include "fieldvalues.h"

#include <vespa/log/log.h>
LOG_SETUP(".document.fieldvalue.structured");

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(StructuredFieldValue, FieldValue);

StructuredFieldValue::Iterator::Iterator()
    : _owner(0),
      _iterator(),
      _field(0)
{
}

StructuredFieldValue::Iterator::Iterator(const StructuredFieldValue& owner,
                                         const Field* first)
    : _owner(&const_cast<StructuredFieldValue&>(owner)),
      _iterator(owner.getIterator(first).release()),
      _field(_iterator->getNextField())
{
}

StructuredFieldValue::StructuredFieldValue(const StructuredFieldValue& other)
    : FieldValue(other),
      _type(other._type)
{
}

StructuredFieldValue::StructuredFieldValue(const DataType &type)
    : FieldValue(),
      _type(&type)
{
}

void StructuredFieldValue::setType(const DataType& type)
{
    _type = &type;
}

StructuredFieldValue&
StructuredFieldValue::operator=(const StructuredFieldValue& other)
{
    if (this == &other) {
        return *this;
    }

    FieldValue::operator=(other);
    _type = other._type;

    return *this;
}

void StructuredFieldValue::setFieldValue(const Field & field, const FieldValue & value)
{
    if (!field.getDataType().isValueType(value) &&
        !value.getDataType()->isA(field.getDataType()))
    {
        throw vespalib::IllegalArgumentException(
                "Cannot assign value of type " + value.getDataType()->toString()
                + "with value : '" + value.toString()
                + "' to field " + field.getName().c_str() + " of type "
                + field.getDataType().toString() + ".", VESPA_STRLOC);
    }
    setFieldValue(field, FieldValue::UP(value.clone()));
}

FieldValue::UP
StructuredFieldValue::onGetNestedFieldValue(
        FieldPath::const_iterator start,
        FieldPath::const_iterator end_) const
{
    FieldValue::UP fv = getValue(start->getFieldRef());
    if (fv.get() != NULL) {
        if ((start + 1) != end_) {
            return fv->getNestedFieldValue(start + 1, end_);
        }
    }
    return fv;
}

FieldValue::IteratorHandler::ModificationStatus
StructuredFieldValue::onIterateNested(
        FieldPath::const_iterator start,
        FieldPath::const_iterator end_,
        IteratorHandler & handler) const
{
    IteratorHandler::StructScope autoScope(handler, *this);

    if (start != end_) {
        if (start->getType() == FieldPathEntry::STRUCT_FIELD) {
            bool exists = getValue(start->getFieldRef(), start->getFieldValueToSet());
            LOG(spam, "fieldRef = %s", start->getFieldRef().toString().c_str());
            LOG(spam, "fieldValueToSet = %s", start->getFieldValueToSet().toString().c_str());
            if (exists) {
                IteratorHandler::ModificationStatus
                    status = start->getFieldValueToSet().iterateNested(start + 1, end_, handler);
                if (status == IteratorHandler::REMOVED) {
                    LOG(spam, "field exists, status = REMOVED");
                    const_cast<StructuredFieldValue&>(*this).remove(start->getFieldRef());
                    return IteratorHandler::MODIFIED;
                } else if (status == IteratorHandler::MODIFIED) {
                    LOG(spam, "field exists, status = MODIFIED");
                    const_cast<StructuredFieldValue&>(*this).setFieldValue(
                            start->getFieldRef(), start->getFieldValueToSet());
                    return IteratorHandler::MODIFIED;
                } else {
                    LOG(spam, "field exists, status = %u", status);
                    return status;
                }
            } else if (handler.createMissingPath()) {
                LOG(spam, "createMissingPath is true");
                IteratorHandler::ModificationStatus status
                    = start->getFieldValueToSet().iterateNested(start + 1, end_, handler);
                if (status == IteratorHandler::MODIFIED) {
                    LOG(spam, "field did not exist, status = MODIFIED");
                    const_cast<StructuredFieldValue&>(*this).setFieldValue(
                            start->getFieldRef(), start->getFieldValueToSet());
                    return status;
                }
            }
            LOG(spam, "field did not exist, returning NOT_MODIFIED");
            return IteratorHandler::NOT_MODIFIED;
        } else {
            throw vespalib::IllegalArgumentException("Illegal field path for struct value");
        }
    } else {
        IteratorHandler::ModificationStatus
            status = handler.modify(const_cast<StructuredFieldValue&>(*this));
        if (status == IteratorHandler::REMOVED) {
            LOG(spam, "field REMOVED");
            return status;
        }

        if (handler.handleComplex(*this)) {
            LOG(spam, "handleComplex");
            std::vector<const Field*> fieldsToRemove;
            for (const_iterator it(begin()), mt(end()); it != mt; ++it) {
                IteratorHandler::ModificationStatus
                    currStatus = getValue(it.field())->iterateNested(start, end_, handler);
                if (currStatus == IteratorHandler::REMOVED) {
                    fieldsToRemove.push_back(&it.field());
                    status = IteratorHandler::MODIFIED;
                } else if (currStatus == IteratorHandler::MODIFIED) {
                    status = currStatus;
                }
            }

            for (const Field * toRemove : fieldsToRemove){
                const_cast<StructuredFieldValue&>(*this).remove(*toRemove);
            }
        }

        return status;
    }
}

using ConstCharP = const char *;
template void StructuredFieldValue::set(const vespalib::stringref & field, int32_t value);
template void StructuredFieldValue::set(const vespalib::stringref & field, int64_t value);
template void StructuredFieldValue::set(const vespalib::stringref & field, double value);
template void StructuredFieldValue::set(const vespalib::stringref & field, ConstCharP value);
template void StructuredFieldValue::set(const vespalib::stringref & field, vespalib::stringref value);
template void StructuredFieldValue::set(const vespalib::stringref & field, vespalib::string value);

template std::unique_ptr<MapFieldValue> StructuredFieldValue::getAs<MapFieldValue>(const Field &field) const;
template std::unique_ptr<ArrayFieldValue> StructuredFieldValue::getAs<ArrayFieldValue>(const Field &field) const;
template std::unique_ptr<WeightedSetFieldValue> StructuredFieldValue::getAs<WeightedSetFieldValue>(const Field &field) const;

} // document
