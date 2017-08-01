// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "arrayfieldvalue.h"
#include "intfieldvalue.h"
#include "stringfieldvalue.h"
#include "predicatefieldvalue.h"
#include "iteratorhandler.h"
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/vespalib/util/polymorphicarrays.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <vespa/log/log.h>

LOG_SETUP(".document.fieldvalue.array");

using namespace vespalib::xml;
using vespalib::make_string;

namespace document {

using vespalib::IllegalArgumentException;
using fieldvalue::IndexValue;
using fieldvalue::ModificationStatus;
using fieldvalue::IteratorHandler;
using fieldvalue::VariableMap;

IMPLEMENT_IDENTIFIABLE_ABSTRACT(ArrayFieldValue, CollectionFieldValue);

ArrayFieldValue::ArrayFieldValue(const DataType &type)
    : CollectionFieldValue(type),
      _array()
{
    _array.reset(static_cast<IArray *>(createArray(getNestedType()).release()));
}

ArrayFieldValue::ArrayFieldValue(const ArrayFieldValue& other)
    : CollectionFieldValue(other),
      _array(other._array->clone())
{
}

ArrayFieldValue::~ArrayFieldValue()
{
}

ArrayFieldValue&
ArrayFieldValue::operator=(const ArrayFieldValue& other)
{
    if (this != &other) {
        verifyType(other);
        ArrayFieldValue copy(other);
        swap(copy);
    }
    return *this;
}

void
ArrayFieldValue::remove(uint32_t index)
{
    if (_array->size() <= index) {
        throw IllegalArgumentException(make_string(
                "Cannot remove index %u from an array of size %lu.",
                index, (unsigned long)_array->size()), VESPA_STRLOC);
    }
    _array->erase(array().begin() + index);
}

bool
ArrayFieldValue::addValue(const FieldValue& value)
{
    if (getNestedType().isValueType(value)) {
        _array->push_back(value);
    } else {
        throw IllegalArgumentException(make_string(
                "Cannot add value of type %s to array containing type %s.",
                value.getDataType()->toString().c_str(),
                getNestedType().toString().c_str()), VESPA_STRLOC);
    }
    return true;
}

bool
ArrayFieldValue::containsValue(const FieldValue& value) const
{
    if (getNestedType().isValueType(value)) {
        for (IArray::const_iterator it(array().begin()), mt(array().end()); it != mt; ++it) {
            if (*it == value) {
                return true;
            }
        }
        return false;
    } else {
        throw IllegalArgumentException(make_string(
                "Value of type %s can't possibly be in array of type %s.",
                value.getDataType()->toString().c_str(),
                getDataType()->toString().c_str()), VESPA_STRLOC);
    }
}

bool
ArrayFieldValue::removeValue(const FieldValue& value)
{
    if (getNestedType().isValueType(value)) {
        size_t oldSize = _array->size();
        IArray::iterator it = array().begin();
        while (it != array().end()) {
            if (*it == value) {
                it = _array->erase(it);
            } else {
                ++it;
            }
        }
        return (oldSize != _array->size());
    } else {
        throw IllegalArgumentException(make_string(
                "Value of type %s can't possibly be in array of type %s.",
                value.getDataType()->toString().c_str(),
                getDataType()->toString().c_str()), VESPA_STRLOC);
    }
}

FieldValue&
ArrayFieldValue::assign(const FieldValue& value)
{
    if (*value.getDataType() == *getDataType()) {
        const ArrayFieldValue& val(static_cast<const ArrayFieldValue&>(value));
        operator=(val);
        return *this;
    } else {
        return FieldValue::assign(value);
    }
}

int
ArrayFieldValue::compare(const FieldValue& o) const
{
    int diff = CollectionFieldValue::compare(o);
    if (diff != 0) return diff;

    const ArrayFieldValue& other(static_cast<const ArrayFieldValue&>(o));

    if (size() != other.size()) return (size() - other.size());
    for (uint32_t i=0, n=size(); i<n; ++i) {
        diff = array()[i].compare(other.array()[i]);
        if (diff != 0) return diff;
    }
    return 0;
}

void
ArrayFieldValue::printXml(XmlOutputStream& xos) const
{
    for (uint32_t i=0, n=_array->size(); i<n; ++i) {
        xos << XmlTag("item");
        array()[i].printXml(xos);
        xos << XmlEndTag();
    }
}

void
ArrayFieldValue::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    out << "Array(size: " << _array->size();
    try {
        for (uint32_t i=0, n=_array->size(); i<n; ++i) {
            out << ",\n" << indent << "  ";
            array()[i].print(out, verbose, indent + "  ");
        }
    } catch (const DeserializeException & e) {
        out << ",\n" << indent << "(Deserialization failed)";
    }
    out << "\n" << indent << ")";
}

bool
ArrayFieldValue::hasChanged() const
{
    for (uint32_t i=0, n=_array->size(); i<n; ++i) {
        if (array()[i].hasChanged()) return true;
    }
    return false;
}

fieldvalue::ModificationStatus
ArrayFieldValue::iterateSubset(int startPos, int endPos,
                               const vespalib::stringref & variable,
                               PathRange nested,
                               fieldvalue::IteratorHandler& handler) const
{
    fieldvalue::ModificationStatus retVal = ModificationStatus::NOT_MODIFIED;

    LOG(spam, "iterateSubset(start=%d, end=%d, variable='%s')",
        startPos, endPos, variable.c_str());

    std::vector<int> indicesToRemove;

    for (int i = startPos; i <= endPos && i < static_cast<int>(_array->size()); ++i) {
        if (!variable.empty()) {
            handler.getVariables()[variable] = IndexValue(i);
        }

        ModificationStatus status = array()[i].iterateNested(nested, handler);

        if (status == ModificationStatus::REMOVED) {
            indicesToRemove.push_back(i);
            retVal = ModificationStatus::MODIFIED;
        } else if (status == ModificationStatus::MODIFIED) {
            retVal = status;
        }
    }

    if (!variable.empty()) {
        handler.getVariables().erase(variable);
    }

    for (std::vector<int>::reverse_iterator i = indicesToRemove.rbegin();
         i != indicesToRemove.rend(); ++i)
    {
        const_cast<ArrayFieldValue&>(*this).remove(*i);
    }

    return retVal;
}

fieldvalue::ModificationStatus
ArrayFieldValue::onIterateNested(PathRange nested, IteratorHandler & handler) const
{
    IteratorHandler::CollectionScope autoScope(handler, *this);
    LOG(spam, "iterating over ArrayFieldValue %s", toString().c_str());

    if (! nested.atEnd()) {
        const FieldPathEntry & fpe = nested.cur();
        switch (fpe.getType()) {
        case FieldPathEntry::ARRAY_INDEX: {
            LOG(spam, "ARRAY_INDEX");
            return iterateSubset(fpe.getIndex(), fpe.getIndex(), "", nested.next(), handler);
        }
        case FieldPathEntry::VARIABLE:
        {
            LOG(spam, "VARIABLE");
            VariableMap::iterator iter = handler.getVariables().find(fpe.getVariableName());
            if (iter != handler.getVariables().end()) {
                int idx = iter->second.index;

                if (idx == -1) {
                    throw IllegalArgumentException(
                            "Mismatch between variables - trying to iterate through map "
                            "and array with the same variable.");
                }

                if (idx < (int)_array->size()) {
                    return iterateSubset(idx, idx, "", nested.next(), handler);
                }
            } else {
                return iterateSubset(0, static_cast<int>(_array->size()) - 1,
                                     fpe.getVariableName(), nested.next(), handler);
            }
            break;
        }
        default:
            break;
        }
        return iterateSubset(0, static_cast<int>(_array->size()) - 1, "", nested, handler);
    } else {
        ModificationStatus status = handler.modify(const_cast<ArrayFieldValue&>(*this));

        if (status == ModificationStatus::REMOVED) {
            return status;
        }

        if (handler.handleComplex(*this)) {
            if (iterateSubset(0, static_cast<int>(_array->size()) - 1, "", nested, handler)
                != ModificationStatus::NOT_MODIFIED)
            {
                status = ModificationStatus::MODIFIED;
            }
        }

        return status;
    }
}

using vespalib::ComplexArrayT;
using vespalib::PrimitiveArrayT;

} // document
