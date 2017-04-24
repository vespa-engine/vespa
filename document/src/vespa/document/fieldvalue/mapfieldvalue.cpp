// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mapfieldvalue.h"
#include "weightedsetfieldvalue.h"
#include <vespa/document/base/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".document.fieldvalue.map");

using vespalib::Identifiable;

/// \todo TODO (was warning):
// Find a way to search through internal map without
// duplicating keys to create shared pointers.

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(MapFieldValue, FieldValue);

namespace {
const MapDataType *verifyMapType(const DataType& type) {
    const MapDataType *ptr(Identifiable::cast<const MapDataType *>(&type));
    if (!ptr) {
        throw vespalib::IllegalArgumentException(
                "Datatype given is not a map type", VESPA_STRLOC);
    }
    return ptr;
}
}  // namespace

MapFieldValue::MapFieldValue(const DataType &mapType)
    : FieldValue(),
      _type(verifyMapType(mapType)),
      _keys(createArray(getMapType().getKeyType())),
      _values(createArray(getMapType().getValueType())),
      _altered(true)
{
}

MapFieldValue::~MapFieldValue()
{
}

MapFieldValue::MapFieldValue(const MapFieldValue & rhs) :
    FieldValue(rhs),
    _type(rhs._type),
    _keys(rhs._keys ? rhs._keys->clone() : nullptr),
    _values(rhs._values ? rhs._values->clone() : nullptr),
    _altered(rhs._altered)
{
}

MapFieldValue &
MapFieldValue::operator = (const MapFieldValue & rhs)
{
    if (this != & rhs) {
        MapFieldValue copy(rhs);
        swap(copy);
    }
    return *this;
}

void MapFieldValue::verifyKey(const FieldValue & fv) const
{
    const DataType &dt = getMapType().getKeyType();
    if (!dt.isValueType(fv)) {
        throw InvalidDataTypeException(*fv.getDataType(), dt, VESPA_STRLOC);
    }
}

void MapFieldValue::verifyValue(const FieldValue & fv) const
{
    const DataType &dt = getMapType().getValueType();
    if (!dt.isValueType(fv)) {
        throw InvalidDataTypeException(*fv.getDataType(), dt, VESPA_STRLOC);
    }
}

bool
MapFieldValue::insertVerify(const FieldValue& key, const FieldValue& value)
{
    verifyKey(key);
    verifyValue(value);
    iterator found = find(key);
    bool result(false);
    if (found != end()) {
        if (!(value == *found->second)) {
            _altered = true;
            found->second->assign(value);
        }
    } else {
        push_back(key, value);
        _altered = true;
        result = true;
    }
    return result;
}

void
MapFieldValue::push_back(const FieldValue& key, const FieldValue& value)
{
    _keys->push_back(key);
    _values->push_back(value);
    _altered = true;
}


void
MapFieldValue::push_back(FieldValue::UP key, FieldValue::UP value)
{
    _keys->push_back(*key);
    _values->push_back(*value);
    _altered = true;
}

bool
MapFieldValue::insert(FieldValue::UP key, FieldValue::UP value)
{
    return insertVerify(*key, *value);
}

bool
MapFieldValue::put(FieldValue::UP key, FieldValue::UP value)
{
    return insertVerify(*key, *value);
}

bool
MapFieldValue::put(const FieldValue& key, const FieldValue& value)
{
    return insertVerify(key, value);
}

bool
MapFieldValue::addValue(const FieldValue& fv)
{
    return put(fv, fv);
}

FieldValue::UP
MapFieldValue::get(const FieldValue& key) const
{
    const_iterator it = find(key);
    return FieldValue::UP(it == end() ? nullptr : it->second->clone());
}

bool
MapFieldValue::contains(const FieldValue& key) const
{
    verifyKey(key);
    return find(key) != end();
}

bool
MapFieldValue::erase(const FieldValue& key)
{
    verifyKey(key);
    iterator found(find(key));
    bool result(found != end());
    if (result) {
        _keys->erase(_keys->begin() + found.offset());
        _values->erase(_values->begin() + found.offset());
        _altered = true;
    }
    return result;
}
FieldValue&
MapFieldValue::assign(const FieldValue& value)
{
    if (getDataType()->isValueType(value)) {
        return operator=(static_cast<const MapFieldValue&>(value));
    }
    return FieldValue::assign(value);
}

int
MapFieldValue::compare(const FieldValue& other) const
{
    int diff = FieldValue::compare(other);
    if (diff != 0) return diff;

    const MapFieldValue& o(dynamic_cast<const MapFieldValue&>(other));

    if (_keys->size() != o._keys->size()) {
        return (_keys->size() - o._keys->size());
    }

    const_iterator it1 = begin();

    while (it1 != end()) {
        const_iterator it2 = o.find(*it1->first);
        if (it2 != o.end()) {
            diff = it1->second->compare(*it2->second);
            if (diff != 0) {
                return diff;
            }
        } else {
            return -1;
        }
        ++it1;
    }
    return 0;
}

void
MapFieldValue::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    out << "Map(";

    int count = 0;
    for (const auto & item : *this) {
        if (count++ != 0) {
            out << ",";
        }
        out << "\n" << indent << "  ";
        item.first->print(out, verbose, indent + "  ");
        out << " - ";
        item.second->print(out, verbose, indent + "  ");
    }
    if (_keys->size() > 0) out << "\n" << indent;
    out << ")";
}

void
MapFieldValue::printXml(XmlOutputStream& xos) const
{
    for (const auto & item : *this) {
        xos << XmlTag("item");
        xos << XmlTag("key");
        item.first->printXml(xos);
        xos << XmlEndTag();
        xos << XmlTag("value");
        item.second->printXml(xos);
        xos << XmlEndTag();
        xos << XmlEndTag();
    }
}

bool
MapFieldValue::hasChanged() const
{
    // Keys are not allowed to change in a map, so the keys should not be
    // referred to externally, and should thus not need to be checked.
    return _altered;
}

MapFieldValue::const_iterator
MapFieldValue::find(const FieldValue& key) const
{
    for(size_t i(0), m(_keys->size()); i < m; i++) {
        if ((*_keys)[i] == key) {
            return const_iterator(*this, i);
        }
    }
    return end();
}

MapFieldValue::iterator
MapFieldValue::find(const FieldValue& key)
{
    for(size_t i(0), m(_keys->size()); i < m; i++) {
        if ((*_keys)[i] == key) {
            return iterator(*this, i);
        }
    }
    return end();
}
bool
MapFieldValue::checkAndRemove(const FieldValue& key,
                              FieldValue::IteratorHandler::ModificationStatus status,
                              bool wasModified,
                              std::vector<const FieldValue*>& keysToRemove) const
{
    if (status == FieldValue::IteratorHandler::REMOVED) {
        LOG(spam, "will remove: %s", key.toString().c_str());
        keysToRemove.push_back(&key);
        return true;
    } else if (status == FieldValue::IteratorHandler::MODIFIED) {
        return true;
    }

    return wasModified;
}

FieldValue::IteratorHandler::ModificationStatus
MapFieldValue::iterateNestedImpl(PathRange nested,
                                 IteratorHandler & handler,
                                 const FieldValue& complexFieldValue) const
{
    IteratorHandler::CollectionScope autoScope(handler, complexFieldValue);
    std::vector<const FieldValue*> keysToRemove;
    bool wasModified = false;
    const bool isWSet(complexFieldValue.inherits(WeightedSetFieldValue::classId));

    if ( ! nested.atEnd() ) {
        LOG(spam, "not yet at end of field path");
        const FieldPathEntry & fpe = nested.cur();
        switch (fpe.getType()) {
        case FieldPathEntry::MAP_KEY:
        {
            LOG(spam, "MAP_KEY");
            const_iterator iter = find(*fpe.getLookupKey());
            if (iter != end()) {
                wasModified = checkAndRemove(*fpe.getLookupKey(),
                        iter->second->iterateNested(nested.next(), handler),
                        wasModified, keysToRemove);
            } else if (handler.createMissingPath()) {
                LOG(spam, "creating missing path");
                FieldValue::UP val =
                    getMapType().getValueType().createFieldValue();
                IteratorHandler::ModificationStatus status = val->iterateNested(nested.next(), handler);
                if (status == IteratorHandler::MODIFIED) {
                    const_cast<MapFieldValue&>(*this).put(FieldValue::UP(fpe.getLookupKey()->clone()), std::move(val));
                    return status;
                }
            }
            break;
        }
        case FieldPathEntry::MAP_ALL_KEYS:
            LOG(spam, "MAP_ALL_KEYS");
            for (const_iterator it(begin()), mt(end()); it != mt; it++) {
                if (isWSet) {
                    handler.setWeight(static_cast<const IntFieldValue &>(*it->second).getValue());
                }
                wasModified = checkAndRemove(*it->first,
                                             it->first->iterateNested(nested.next(), handler),
                                             wasModified, keysToRemove);
            }
            break;
        case FieldPathEntry::MAP_ALL_VALUES:
            LOG(spam, "MAP_ALL_VALUES");
            for (const_iterator it(begin()), mt(end()); it != mt; it++) {
                wasModified = checkAndRemove(*it->second,
                                             it->second->iterateNested(nested.next(), handler),
                                             wasModified, keysToRemove);
            }
            break;
        case FieldPathEntry::VARIABLE:
        {
            LOG(spam, "VARIABLE");
            IteratorHandler::VariableMap::iterator
                iter = handler.getVariables().find(fpe.getVariableName());
            if (iter != handler.getVariables().end()) {
                LOG(spam, "variable key = %s", iter->second.key->toString().c_str());
                const_iterator found = find(*iter->second.key);
                if (found != end()) {
                    wasModified = checkAndRemove(*iter->second.key,
                                                 found->second->iterateNested(nested.next(), handler),
                                                 wasModified, keysToRemove);
                }
            } else {
                PathRange next = nested.next();
                for (const_iterator it(begin()), mt(end()); it != mt; it++) {
                    LOG(spam, "key is '%s'", it->first->toString().c_str());
                    handler.getVariables()[fpe.getVariableName()]
                        = IteratorHandler::IndexValue(*it->first);
                    LOG(spam, "vars at this time = %s",
                        FieldValue::IteratorHandler::toString(handler.getVariables()).c_str());
                    wasModified = checkAndRemove(*it->first,
                                                 it->second->iterateNested(next, handler),
                                                 wasModified, keysToRemove);
                }
                handler.getVariables().erase(fpe.getVariableName());
            }
            break;
        }
        default:
            LOG(spam, "default");
            for (const_iterator it(begin()), mt(end()); it != mt; it++) {
                if (isWSet) {
                    handler.setWeight(static_cast<const IntFieldValue &>(*it->second).getValue());
                }
                wasModified = checkAndRemove(*it->first,
                                             it->first->iterateNested(nested, handler),
                                             wasModified, keysToRemove);
                // Don't iterate over values
                /*wasModified = checkAndRemove(*it->second,
                        it->second->iterateNested(start, end_, handler),
                        wasModified, keysToRemove);*/
            }
            break;
        }
    } else {
        LOG(spam, "at end of field path");
        IteratorHandler::ModificationStatus
            status = handler.modify(const_cast<FieldValue&>(complexFieldValue));

        if (status == IteratorHandler::REMOVED) {
            LOG(spam, "status = REMOVED");
            return status;
        } else if (status == IteratorHandler::MODIFIED) {
            LOG(spam, "status = MODIFIED");
            wasModified = true;
        }

        if (handler.handleComplex(complexFieldValue)) {
            LOG(spam, "calling handler.handleComplex for all map keys");
            for (const_iterator it(begin()), mt(end()); it != mt; it++) {
                if (isWSet) {
                    handler.setWeight(static_cast<const IntFieldValue &>(*it->second).getValue());
                }
                wasModified = checkAndRemove(*it->first,
                        it->first->iterateNested(nested, handler),
                        wasModified, keysToRemove);
                // XXX: Map value iteration is currently disabled since it changes
                // existing search behavior
                /*wasModified = checkAndRemove(*it->second,
                        it->second->iterateNested(start, end_, handler),
                        wasModified, keysToRemove);*/
            }
        }
    }
    handler.setWeight(1);
    for (std::vector<const FieldValue*>::iterator
             i = keysToRemove.begin(), last = keysToRemove.end();
         i != last; ++i)
    {
        LOG(spam, "erasing map entry with key %s", (*i)->toString().c_str());
        const_cast<MapFieldValue&>(*this).erase(**i);
    }
    return wasModified ? IteratorHandler::MODIFIED : IteratorHandler::NOT_MODIFIED;
}

FieldValue::IteratorHandler::ModificationStatus
MapFieldValue::onIterateNested(PathRange nested, IteratorHandler & handler) const
{
    LOG(spam, "iterating over MapFieldValue");
    return iterateNestedImpl(nested, handler, *this);
}


} // document
