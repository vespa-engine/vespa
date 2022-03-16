// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "structuredfieldvalue.hpp"
#include "iteratorhandler.h"
#include "weightedsetfieldvalue.h"
#include "arrayfieldvalue.h"
#include "structuredcache.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".document.fieldvalue.structured");

using vespalib::IllegalArgumentException;

namespace document {

using namespace fieldvalue;

StructuredFieldValue::Iterator::Iterator()
    : _iterator(),
      _field(nullptr)
{
}

StructuredFieldValue::Iterator::Iterator(const StructuredFieldValue& owner, const Field* first)
    : _iterator(owner.getIterator(first).release()),
      _field(_iterator->getNextField())
{
}

void
StructuredFieldValue::setFieldValue(const Field & field, const FieldValue & value)
{
    if (!field.getDataType().isValueType(value) &&
        !value.getDataType()->isA(field.getDataType()))
    {
        throw IllegalArgumentException(
                "Cannot assign value of type " + value.getDataType()->toString()
                + "with value : '" + value.toString()
                + "' to field " + field.getName() + " of type "
                + field.getDataType().toString() + ".", VESPA_STRLOC);
    }
    setFieldValue(field, FieldValue::UP(value.clone()));
}

FieldValue::UP
StructuredFieldValue::onGetNestedFieldValue(PathRange nested) const
{
    FieldValue::UP fv = getValue(nested.cur().getFieldRef());
    if (fv.get() != nullptr) {
        PathRange next = nested.next();
        if ( ! next.atEnd() ) {
            return fv->getNestedFieldValue(next);
        }
    }
    return fv;
}

void
StructuredFieldValue::remove(const Field& field) {
    StructuredCache * cache = getCache();
    if (cache) {
        cache->remove(field);
    } else {
        removeFieldValue(field);
    }
}

void
StructuredFieldValue::updateValue(const Field & field, FieldValue::UP value) const {
    StructuredCache * cache = getCache();
    if (cache) {
        cache->set(field, std::move(value), ModificationStatus::MODIFIED);
    } else {
        const_cast<StructuredFieldValue&>(*this).setFieldValue(field, std::move(value));
    }
}

void
StructuredFieldValue::returnValue(const Field & field, FieldValue::UP value) const {
    StructuredCache * cache = getCache();
    if (cache) {
        cache->set(field, std::move(value), ModificationStatus::NOT_MODIFIED);
    }
}

FieldValue::UP
StructuredFieldValue::getValue(const Field& field, FieldValue::UP container) const {
    StructuredCache * cache = getCache();
    if (cache) {
        auto found = cache->find(field);
        if (found == cache->end()) {
            container = getFieldValue(field);
            cache->set(field, FieldValue::UP(), ModificationStatus::NOT_MODIFIED);
        } else {
            container = std::move(found->second.value);
        }
    } else {
        if (container) {
            getFieldValue(field, *container);
        } else {
            container = getFieldValue(field);
        }
    }
    return container;
}

ModificationStatus
StructuredFieldValue::onIterateNested(PathRange nested, IteratorHandler & handler) const
{
    IteratorHandler::StructScope autoScope(handler, *this);

    if ( ! nested.atEnd()) {
        const FieldPathEntry & fpe = nested.cur();
        if (fpe.getType() == FieldPathEntry::STRUCT_FIELD) {
            const Field & field = fpe.getFieldRef();
            FieldValue::UP value = getValue(field, FieldValue::UP());
            LOG(spam, "fieldRef = %s", field.toString().c_str());
            LOG(spam, "fieldValueToSet = %s", value ? value->toString().c_str() : "<null>");
            ModificationStatus status = ModificationStatus::NOT_MODIFIED;
            if (value) {
                status = value->iterateNested(nested.next(), handler);
                if (status == ModificationStatus::REMOVED) {
                    LOG(spam, "field exists, status = REMOVED");
                    const_cast<StructuredFieldValue&>(*this).remove(field);
                    status = ModificationStatus::MODIFIED;
                } else if (status == ModificationStatus::MODIFIED) {
                    LOG(spam, "field exists, status = MODIFIED");
                    updateValue(field, std::move(value));
                } else {
                    returnValue(field, std::move(value));
                }
            } else if (handler.createMissingPath()) {
                LOG(spam, "createMissingPath is true");
                status = fpe.getFieldValueToSet().iterateNested(nested.next(), handler);
                if (status == ModificationStatus::MODIFIED) {
                    LOG(spam, "field did not exist, status = MODIFIED");
                    updateValue(field, fpe.stealFieldValueToSet());
                }
            } else {
                LOG(spam, "field did not exist, returning NOT_MODIFIED");
            }
            return status;
        } else {
            throw IllegalArgumentException("Illegal field path for struct value");
        }
    } else {
        ModificationStatus status = handler.modify(const_cast<StructuredFieldValue&>(*this));
        if (status == ModificationStatus::REMOVED) {
            LOG(spam, "field REMOVED");
        } else if (handler.handleComplex(*this)) {
            LOG(spam, "handleComplex");
            std::vector<const Field*> fieldsToRemove;
            for (const_iterator it(begin()), mt(end()); it != mt; ++it) {
                ModificationStatus currStatus = getValue(it.field())->iterateNested(nested, handler);
                if (currStatus == ModificationStatus::REMOVED) {
                    fieldsToRemove.push_back(&it.field());
                    status = ModificationStatus::MODIFIED;
                } else if (currStatus == ModificationStatus::MODIFIED) {
                    status = ModificationStatus::MODIFIED;
                }
            }

            for (const Field * toRemove : fieldsToRemove){
                const_cast<StructuredFieldValue&>(*this).remove(*toRemove);
            }
        }

        return status;
    }
}

template std::unique_ptr<MapFieldValue> StructuredFieldValue::getAs<MapFieldValue>(const Field &field) const;
template std::unique_ptr<ArrayFieldValue> StructuredFieldValue::getAs<ArrayFieldValue>(const Field &field) const;
template std::unique_ptr<WeightedSetFieldValue> StructuredFieldValue::getAs<WeightedSetFieldValue>(const Field &field) const;

} // document
