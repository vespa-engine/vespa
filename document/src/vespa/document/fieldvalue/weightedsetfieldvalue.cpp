// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weightedsetfieldvalue.h"
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

using vespalib::Identifiable;
using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using namespace vespalib::xml;

/// \todo TODO (was warning):  Find a way to search through internal map without duplicating keys to create shared pointers.

namespace document {

using namespace fieldvalue;

namespace {
const DataType &getKeyType(const DataType &type) {
    const WeightedSetDataType *wtype = dynamic_cast<const WeightedSetDataType *>(&type);
    if (!wtype) {
        throw IllegalArgumentException("Cannot generate a weighted set value with non-weighted set "
                                       "type " + type.toString() + ".", VESPA_STRLOC);
    }
    return wtype->getNestedType();
}
}  // namespace

WeightedSetFieldValue::WeightedSetFieldValue(const DataType &type)
    : CollectionFieldValue(Type::WSET, type),
      _map_type(std::make_shared<MapDataType>(getKeyType(type), *DataType::INT)),
      _map(*_map_type)
{ }

WeightedSetFieldValue::WeightedSetFieldValue(const WeightedSetFieldValue &) = default;
WeightedSetFieldValue & WeightedSetFieldValue::operator = (const WeightedSetFieldValue &) = default;
WeightedSetFieldValue::~WeightedSetFieldValue() = default;

void WeightedSetFieldValue::verifyKey(const FieldValue & v)
{
    if (!getNestedType().isValueType(v)) {
        throw InvalidDataTypeException(*v.getDataType(), getNestedType(), VESPA_STRLOC);
    }
}

bool
WeightedSetFieldValue::add(const FieldValue& key, int weight)
{
    verifyKey(key);
    const WeightedSetDataType & wdt(static_cast<const WeightedSetDataType&>(*_type));
    if (wdt.removeIfZero() && (weight == 0)) {
        _map.erase(key);
        return false;
    }
    return _map.insert(FieldValue::UP(key.clone()), IntFieldValue::make(weight));
}

bool
WeightedSetFieldValue::addIgnoreZeroWeight(const FieldValue& key, int32_t weight)
{
    verifyKey(key);
    return _map.insert(FieldValue::UP(key.clone()), IntFieldValue::make(weight));
}

void
WeightedSetFieldValue::push_back(FieldValue::UP key, int weight)
{
    _map.push_back(std::move(key), IntFieldValue::make(weight));
}

void
WeightedSetFieldValue::increment(const FieldValue& key, int val)
{
    verifyKey(key);
    WeightedFieldValueMap::iterator it(_map.find(key));
    const WeightedSetDataType & wdt(static_cast<const WeightedSetDataType&>(*_type));
    if (wdt.createIfNonExistent()) {
        if (it == _map.end()) {
            _map.insert(FieldValue::UP(key.clone()), FieldValue::UP(new IntFieldValue(val)));
        } else {
            IntFieldValue& fv = static_cast<IntFieldValue&>(*it->second);
            fv.setValue(fv.getValue() + val);
            if (wdt.removeIfZero() && fv.getValue() == 0) {
                _map.erase(key);
            }
        }
    } else {
        if (it == _map.end()) {
           throw IllegalStateException("Cannot modify non-existing entry in weightedset without createIfNonExistent set", VESPA_STRLOC);
        }
        IntFieldValue& fv = static_cast<IntFieldValue&>(*it->second);
        fv.setValue(fv.getValue() + val);
        if (wdt.removeIfZero() && fv.getValue() == 0) {
            _map.erase(key);
        }
    }
}

int32_t
WeightedSetFieldValue::get(const FieldValue& key, int32_t defaultValue) const
{
    WeightedFieldValueMap::const_iterator it = find(key);
    return (it == end()
            ? defaultValue
            : static_cast<const IntFieldValue&>(*it->second).getValue());
}

bool
WeightedSetFieldValue::containsValue(const FieldValue& key) const
{
    return _map.contains(key);
}

bool
WeightedSetFieldValue::removeValue(const FieldValue& key)
{
    bool result = _map.erase(key);
    return result;
}

FieldValue&
WeightedSetFieldValue::assign(const FieldValue& value)
{
    if (getDataType()->isValueType(value)) {
        return operator=(static_cast<const WeightedSetFieldValue&>(value));
    }
    return FieldValue::assign(value);
}

int
WeightedSetFieldValue::compare(const FieldValue& other) const
{
    int diff = CollectionFieldValue::compare(other);
    if (diff != 0) return diff;

    const WeightedSetFieldValue& wset(dynamic_cast<const WeightedSetFieldValue&>(other));
    return _map.compare(wset._map);
}

void
WeightedSetFieldValue::printXml(XmlOutputStream& xos) const
{
    for (const auto & entry : _map) {

        const IntFieldValue& fv = static_cast<const IntFieldValue&>(*entry.second);
        xos << XmlTag("item") << XmlAttribute("weight", fv.getValue())
            << *entry.first
            << XmlEndTag();
    }
}

void
WeightedSetFieldValue::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << getDataType()->getName() << "(";

    int count = 0;
    for (const auto & entry : _map) {
        if (count++ != 0) {
            out << ",";
        }
        out << "\n" << indent << "  ";
        entry.first->print(out, verbose, indent + "  ");
        const IntFieldValue& fv = static_cast<const IntFieldValue&>(*entry.second);
        out << " - weight " << fv.getValue();
    }
    if (_map.size() > 0) out << "\n" << indent;
    out << ")";
}

WeightedSetFieldValue::const_iterator
WeightedSetFieldValue::find(const FieldValue& key) const
{
    return _map.find(key);
}

WeightedSetFieldValue::iterator
WeightedSetFieldValue::find(const FieldValue& key)
{
    return _map.find(key);
}

ModificationStatus
WeightedSetFieldValue::onIterateNested(PathRange nested, IteratorHandler & handler) const
{
    return _map.iterateNestedImpl(nested, handler, *this);
}

} // document
