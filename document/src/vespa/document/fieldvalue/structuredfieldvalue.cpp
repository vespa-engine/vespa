// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "structuredfieldvalue.hpp"
#include "iteratorhandler.h"
#include "weightedsetfieldvalue.h"
#include "arrayfieldvalue.h"

#include <vespa/log/log.h>
LOG_SETUP(".document.fieldvalue.structured");

using vespalib::IllegalArgumentException;

namespace document {

using namespace fieldvalue;

IMPLEMENT_IDENTIFIABLE_ABSTRACT(StructuredFieldValue, FieldValue);

StructuredFieldValue::Iterator::Iterator()
    : _owner(0),
      _iterator(),
      _field(0)
{
}

StructuredFieldValue::Iterator::Iterator(const StructuredFieldValue& owner, const Field* first)
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
        throw IllegalArgumentException(
                "Cannot assign value of type " + value.getDataType()->toString()
                + "with value : '" + value.toString()
                + "' to field " + field.getName().c_str() + " of type "
                + field.getDataType().toString() + ".", VESPA_STRLOC);
    }
    setFieldValue(field, FieldValue::UP(value.clone()));
}

FieldValue::UP
StructuredFieldValue::onGetNestedFieldValue(PathRange nested) const
{
    FieldValue::UP fv = getValue(nested.cur().getFieldRef());
    if (fv.get() != NULL) {
        PathRange next = nested.next();
        if ( ! next.atEnd() ) {
            return fv->getNestedFieldValue(next);
        }
    }
    return fv;
}

ModificationStatus
StructuredFieldValue::onIterateNested(PathRange nested, IteratorHandler & handler) const
{
    IteratorHandler::StructScope autoScope(handler, *this);

    if ( ! nested.atEnd()) {
        const FieldPathEntry & fpe = nested.cur();
        if (fpe.getType() == FieldPathEntry::STRUCT_FIELD) {
            bool exists = getValue(fpe.getFieldRef(), fpe.getFieldValueToSet());
            LOG(spam, "fieldRef = %s", fpe.getFieldRef().toString().c_str());
            LOG(spam, "fieldValueToSet = %s", fpe.getFieldValueToSet().toString().c_str());
            if (exists) {
                ModificationStatus status = fpe.getFieldValueToSet().iterateNested(nested.next(), handler);
                if (status == ModificationStatus::REMOVED) {
                    LOG(spam, "field exists, status = REMOVED");
                    const_cast<StructuredFieldValue&>(*this).remove(fpe.getFieldRef());
                    return ModificationStatus::MODIFIED;
                } else if (status == ModificationStatus::MODIFIED) {
                    LOG(spam, "field exists, status = MODIFIED");
                    const_cast<StructuredFieldValue&>(*this).setFieldValue(fpe.getFieldRef(), fpe.getFieldValueToSet());
                    return ModificationStatus::MODIFIED;
                } else {
                    return status;
                }
            } else if (handler.createMissingPath()) {
                LOG(spam, "createMissingPath is true");
                ModificationStatus status = fpe.getFieldValueToSet().iterateNested(nested.next(), handler);
                if (status == ModificationStatus::MODIFIED) {
                    LOG(spam, "field did not exist, status = MODIFIED");
                    const_cast<StructuredFieldValue&>(*this).setFieldValue(fpe.getFieldRef(), fpe.getFieldValueToSet());
                    return status;
                }
            }
            LOG(spam, "field did not exist, returning NOT_MODIFIED");
            return ModificationStatus::NOT_MODIFIED;
        } else {
            throw IllegalArgumentException("Illegal field path for struct value");
        }
    } else {
        ModificationStatus status = handler.modify(const_cast<StructuredFieldValue&>(*this));
        if (status == ModificationStatus::REMOVED) {
            LOG(spam, "field REMOVED");
            return status;
        }

        if (handler.handleComplex(*this)) {
            LOG(spam, "handleComplex");
            std::vector<const Field*> fieldsToRemove;
            for (const_iterator it(begin()), mt(end()); it != mt; ++it) {
                ModificationStatus currStatus = getValue(it.field())->iterateNested(nested, handler);
                if (currStatus == ModificationStatus::REMOVED) {
                    fieldsToRemove.push_back(&it.field());
                    status = ModificationStatus::MODIFIED;
                } else if (currStatus == ModificationStatus::MODIFIED) {
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
