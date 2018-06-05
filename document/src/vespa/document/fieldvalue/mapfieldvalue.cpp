// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mapfieldvalue.h"
#include "weightedsetfieldvalue.h"
#include "iteratorhandler.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <cassert>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".document.fieldvalue.map");

using vespalib::Identifiable;
using namespace vespalib::xml;

/// \todo TODO (was warning):
// Find a way to search through internal map without
// duplicating keys to create shared pointers.

namespace document {

using namespace fieldvalue;

IMPLEMENT_IDENTIFIABLE_ABSTRACT(MapFieldValue, FieldValue);

namespace {
const MapDataType *verifyMapType(const DataType& type) {
    const MapDataType *ptr(Identifiable::cast<const MapDataType *>(&type));
    if (!ptr) {
        throw vespalib::IllegalArgumentException("Datatype given is not a map type", VESPA_STRLOC);
    }
    return ptr;
}

struct Hasher {
    Hasher(const MapFieldValue::IArray * keys) : _keys(keys) {}
    uint32_t operator () (uint32_t index) const { return (*_keys)[index].hash(); }
    const MapFieldValue::IArray * _keys;
};

struct Extract {
    Extract(const MapFieldValue::IArray * keys) : _keys(keys) {}
    const FieldValue & operator () (uint32_t index) const { return (*_keys)[index]; }
    const MapFieldValue::IArray * _keys;
};

struct Equal {
    Equal(const MapFieldValue::IArray * keys) : _keys(keys) {}
    bool operator () (uint32_t a, uint32_t b) const { return (*_keys)[a].fastCompare((*_keys)[b]) == 0; }
    const MapFieldValue::IArray * _keys;
};

using HashMapT = vespalib::hash_set<uint32_t, Hasher, Equal>;

}

namespace mapfieldvalue {

class HashMap : public HashMapT {
public:
    using HashMapT::HashMapT;
};

}

MapFieldValue::MapFieldValue(const DataType &mapType)
    : FieldValue(),
      _type(verifyMapType(mapType)),
      _count(0),
      _keys(static_cast<IArray *>(createArray(getMapType().getKeyType()).release())),
      _values(static_cast<IArray *>(createArray(getMapType().getValueType()).release())),
      _present(),
      _lookupMap(),
      _altered(true)
{
}

MapFieldValue::~MapFieldValue() = default;

MapFieldValue::MapFieldValue(const MapFieldValue & rhs) :
    FieldValue(rhs),
    _type(rhs._type),
    _count(rhs._count),
    _keys(rhs._keys ? rhs._keys->clone() : nullptr),
    _values(rhs._values ? rhs._values->clone() : nullptr),
    _present(rhs._present),
    _lookupMap(),
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

void
MapFieldValue::swap(MapFieldValue & rhs) {
    std::swap(_type, rhs._type);
    std::swap(_count, rhs._count);
    std::swap(_keys, rhs._keys);
    std::swap(_values, rhs._values);
    std::swap(_present, rhs._present);
    std::swap(_lookupMap, rhs._lookupMap);
    std::swap(_altered, rhs._altered);
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
        result = true;
    }
    return result;
}

void
MapFieldValue::push_back(const FieldValue& key, const FieldValue& value)
{
    _count++;
    _keys->push_back(key);
    _values->push_back(value);
    _present.push_back(true);
    if (_lookupMap) {
        _lookupMap->insert(_present.size() - 1);
    }

    _altered = true;
}


void
MapFieldValue::push_back(FieldValue::UP key, FieldValue::UP value)
{
    _count++;
    _keys->push_back(*key);
    _values->push_back(*value);
    _present.push_back(true);
    if (_lookupMap) {
        _lookupMap->insert(_present.size() - 1);
    }
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

void
MapFieldValue::clear() {
    _keys->clear();
    _values->clear();
    _present.clear();
    _lookupMap.reset();
    _count = 0;
}
void
MapFieldValue::reserve(size_t sz) {
    _keys->reserve(sz);
    _values->reserve(sz);
    _present.reserve(sz);
}

void MapFieldValue::resize(size_t sz) {
    _keys->resize(sz);
    _values->resize(sz);
    _present.resize(sz, true);
    _lookupMap.reset();
    _count = std::count(_present.begin(), _present.end(), true);
}

bool
MapFieldValue::erase(const FieldValue& key)
{
    verifyKey(key);
    iterator found(find(key));
    bool result(found != end());
    if (result) {
        _count--;
        _present[found.offset()] = false;
        _lookupMap->erase(found.offset());
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

    if (size() != o.size()) {
        return (size() - o.size());
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
MapFieldValue::print(std::ostream& out, bool verbose, const std::string& indent) const
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
    if (size() > 0) out << "\n" << indent;
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
const DataType *
MapFieldValue::getDataType() const {
    return _type;
}

FieldValue::UP
MapFieldValue::createValue() const {
    return getMapType().getValueType().createFieldValue();
}

void
MapFieldValue::ensureLookupMap() const {
    if (!_lookupMap) {
        _lookupMap = std::move(buildLookupMap());
    }
}

MapFieldValue::HashMapUP
MapFieldValue::buildLookupMap() const {
    HashMapUP hashMap = std::make_unique<mapfieldvalue::HashMap>(size()*2, Hasher(_keys.get()), Equal(_keys.get()));
    for (size_t i(0), m(_present.size()); i < m; i++) {
        if (_present[i]) {
            hashMap->insert(i);
        }
    }
    return hashMap;
}

MapFieldValue::const_iterator
MapFieldValue::find(const FieldValue& key) const
{
    if ((size() > 0) && (key.getClass().id() == (*_keys)[0].getClass().id())) {
        ssize_t index = findIndex(key);
        if (index >= 0) {
            return const_iterator(*this, index);
        }
    }
    return end();
}

MapFieldValue::iterator
MapFieldValue::find(const FieldValue& key)
{
    if ((size() > 0) && (key.getClass().id() == (*_keys)[0].getClass().id())) {
        ssize_t index = findIndex(key);
        if (index >= 0) {
            return iterator(*this, index);
        }
    }
    return end();
}

ssize_t
MapFieldValue::findIndex(const FieldValue& key) const
{
    if ((size() > 0) && (key.getClass().id() == (*_keys)[0].getClass().id())) {
        ensureLookupMap();
        Extract extract(_keys.get());
        auto found = _lookupMap->find<FieldValue, Extract, vespalib::hash<FieldValue>, std::equal_to<FieldValue>>(key, extract);
        if (found != _lookupMap->end()) {
            uint32_t index = *found;
            assert(_present[index]);
            return index;
        }
    }
    return -1l;
}

bool
MapFieldValue::checkAndRemove(const FieldValue& key, ModificationStatus status, bool wasModified,
                              std::vector<const FieldValue*>& keysToRemove) const
{
    if (status == ModificationStatus::REMOVED) {
        LOG(spam, "will remove: %s", key.toString().c_str());
        keysToRemove.push_back(&key);
        return true;
    } else if (status == ModificationStatus::MODIFIED) {
        return true;
    }

    return wasModified;
}

ModificationStatus
MapFieldValue::iterateNestedImpl(PathRange nested,
                                 IteratorHandler & handler,
                                 const FieldValue& complexFieldValue) const
{
    IteratorHandler::CollectionScope autoScope(handler, complexFieldValue);
    std::vector<const FieldValue*> keysToRemove;
    bool wasModified = false;
    const bool isWSet(complexFieldValue.inherits(WeightedSetFieldValue::classId));

    uint32_t index(0);
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
                FieldValue::UP val = getMapType().getValueType().createFieldValue();
                ModificationStatus status = val->iterateNested(nested.next(), handler);
                if (status == ModificationStatus::MODIFIED) {
                    const_cast<MapFieldValue&>(*this).put(FieldValue::UP(fpe.getLookupKey()->clone()), std::move(val));
                    return status;
                }
            }
            break;
        }
        case FieldPathEntry::MAP_ALL_KEYS:
            LOG(spam, "MAP_ALL_KEYS");
            for (const auto & entry : *this) {
                handler.setArrayIndex(index++);
                if (isWSet) {
                    handler.setWeight(static_cast<const IntFieldValue &>(*entry.second).getValue());
                }
                wasModified = checkAndRemove(*entry.first,
                                             entry.first->iterateNested(nested.next(), handler),
                                             wasModified, keysToRemove);
            }
            break;
        case FieldPathEntry::MAP_ALL_VALUES:
            LOG(spam, "MAP_ALL_VALUES");
            for (const auto & entry : *this) {
                handler.setArrayIndex(index++);
                wasModified = checkAndRemove(*entry.second,
                                             entry.second->iterateNested(nested.next(), handler),
                                             wasModified, keysToRemove);
            }
            break;
        case FieldPathEntry::VARIABLE:
        {
            LOG(spam, "VARIABLE");
            VariableMap::iterator iter = handler.getVariables().find(fpe.getVariableName());
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
                for (const auto & entry : *this) {
                    handler.setArrayIndex(index++);
                    LOG(spam, "key is '%s'", entry.first->toString().c_str());
                    handler.getVariables()[fpe.getVariableName()] = IndexValue(*entry.first);
                    LOG(spam, "vars at this time = %s", handler.getVariables().toString().c_str());
                    wasModified = checkAndRemove(*entry.first, entry.second->iterateNested(next, handler),
                                                 wasModified, keysToRemove);
                }
                handler.getVariables().erase(fpe.getVariableName());
            }
            break;
        }
        default:
            LOG(spam, "default");
            for (const auto & entry : *this) {
                handler.setArrayIndex(index++);
                if (isWSet) {
                    handler.setWeight(static_cast<const IntFieldValue &>(*entry.second).getValue());
                }
                wasModified = checkAndRemove(*entry.first, entry.first->iterateNested(nested, handler),
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
        ModificationStatus status = handler.modify(const_cast<FieldValue&>(complexFieldValue));

        if (status == ModificationStatus::REMOVED) {
            LOG(spam, "status = REMOVED");
            return status;
        } else if (status == ModificationStatus::MODIFIED) {
            LOG(spam, "status = MODIFIED");
            wasModified = true;
        }

        if (handler.handleComplex(complexFieldValue)) {
            LOG(spam, "calling handler.handleComplex for all map keys");
            for (const auto & entry : *this) {
                handler.setArrayIndex(index++);
                if (isWSet) {
                    handler.setWeight(static_cast<const IntFieldValue &>(*entry.second).getValue());
                }
                wasModified = checkAndRemove(*entry.first, entry.first->iterateNested(nested, handler),
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
    for (const FieldValue * key: keysToRemove) {
        LOG(spam, "erasing map entry with key %s", key->toString().c_str());
        const_cast<MapFieldValue&>(*this).erase(*key);
    }
    return wasModified ? ModificationStatus::MODIFIED : ModificationStatus::NOT_MODIFIED;
}

ModificationStatus
MapFieldValue::onIterateNested(PathRange nested, IteratorHandler & handler) const
{
    LOG(spam, "iterating over MapFieldValue");
    return iterateNestedImpl(nested, handler, *this);
}


} // document
