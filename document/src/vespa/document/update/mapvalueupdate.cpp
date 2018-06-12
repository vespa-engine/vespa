// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mapvalueupdate.h"
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <vespa/vespalib/objects/nbostream.h>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::nbostream;
using namespace vespalib::xml;

namespace document {

IMPLEMENT_IDENTIFIABLE(MapValueUpdate, ValueUpdate);

MapValueUpdate::MapValueUpdate(const FieldValue& key, const ValueUpdate& update)
    : ValueUpdate(),
      _key(key.clone()),
      _update(update.clone())
{}

MapValueUpdate::MapValueUpdate(const MapValueUpdate &) = default;
MapValueUpdate & MapValueUpdate::operator = (const MapValueUpdate &) = default;
MapValueUpdate::~MapValueUpdate() = default;

bool
MapValueUpdate::operator==(const ValueUpdate& other) const
{
    if (other.getClass().id() != MapValueUpdate::classId) return false;
    const MapValueUpdate& o(static_cast<const MapValueUpdate&>(other));
    if (*_key != *o._key) return false;
    if (*_update != *o._update) return false;
    return true;
}

// Ensure that this update is compatible with given field.
void
MapValueUpdate::checkCompatibility(const Field& field) const
{
    // Check compatibility of nested types.
    if (field.getDataType().getClass().id() == ArrayDataType::classId) {
	    if (_key->getClass().id() != IntFieldValue::classId) {
            throw IllegalArgumentException(vespalib::make_string(
                    "Key for field '%s' is of wrong type (expected '%s', was '%s').",
                    field.getName().c_str(), DataType::INT->toString().c_str(),
                    _key->getDataType()->toString().c_str()), VESPA_STRLOC);
        }
    } else if (field.getDataType().getClass().id() == WeightedSetDataType::classId) {
        const WeightedSetDataType& type = static_cast<const WeightedSetDataType&>(field.getDataType());
        if (!type.getNestedType().isValueType(*_key)) {
            throw IllegalArgumentException(vespalib::make_string(
                    "Key for field '%s' is of wrong type (expected '%s', was '%s').",
                    field.getName().c_str(), DataType::INT->toString().c_str(),
                    _key->getDataType()->toString().c_str()), VESPA_STRLOC);
        }
    } else {
        throw IllegalArgumentException("MapValueUpdate does not support "
                "datatype " + field.getDataType().toString() + ".", VESPA_STRLOC);
    }
}

// Apply this update to the given document.
bool
MapValueUpdate::applyTo(FieldValue& value) const
{
    if (value.getDataType()->getClass().id() == ArrayDataType::classId) {
        ArrayFieldValue& val(static_cast<ArrayFieldValue&>(value));
        int32_t index = _key->getAsInt();
        if (index < 0 || static_cast<uint32_t>(index) >= val.size()) {
            throw IllegalStateException(vespalib::make_string(
                    "Tried to update element %i in an array of %zu elements",
		            index, val.size()), VESPA_STRLOC);
        }
        if (!_update->applyTo(val[_key->getAsInt()])) {
            val.remove(_key->getAsInt());
        }
    } else if (value.getDataType()->getClass().id() == WeightedSetDataType::classId) {
        const WeightedSetDataType& type(static_cast<const WeightedSetDataType&>(*value.getDataType()));
        WeightedSetFieldValue& val(static_cast<WeightedSetFieldValue&>(value));
        WeightedSetFieldValue::iterator it = val.find(*_key);
        if (it == val.end() && type.createIfNonExistent()) {
            // Add item and ensure it does not get initially auto-removed if
            // remove-if-zero is set, as this would void the update.
            val.addIgnoreZeroWeight(*_key, 0);
            it = val.find(*_key);
        }
        if (it == val.end()) {
            // Are we sure we don't want updates to fail on missing values?
            return true;
        }
        // XXX why are we removing items if updates fail? Surely, a failed
        // update should have as a postcondition that it did not mutate the
        // item in question? This is not exception safe either way.
        IntFieldValue& weight = dynamic_cast<IntFieldValue&>(*it->second);
        if (!_update->applyTo(weight)) {
            val.remove(*_key);
        } else if (weight.getAsInt() == 0 && type.removeIfZero()) {
            val.remove(*_key);
        }
    } else {
        throw IllegalStateException(
                "Cannot apply map value update to field of type "
                + value.getDataType()->toString() + ".", VESPA_STRLOC);
    }
    return true;
}

// Print this update in human readable form.
void
MapValueUpdate::print(std::ostream& out, bool, const std::string& indent) const
{
    out << indent << "MapValueUpdate(" << *_key << ", " << *_update << ")";
}

void
MapValueUpdate::printXml(XmlOutputStream& xos) const
{
    xos << XmlTag("map")
        << XmlTag("value") << *_key << XmlEndTag()
        << XmlTag("update") << *_update << XmlEndTag()
        << XmlEndTag();
}

// Deserialize this update from the given buffer.
void
MapValueUpdate::deserialize(const DocumentTypeRepo& repo, const DataType& type, ByteBuffer& buffer, uint16_t version)
{
    nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
    VespaDocumentDeserializer deserializer(repo, stream, version);
    switch(type.getClass().id()) {
        case ArrayDataType::classId:
        {
            _key.reset(new IntFieldValue);
            deserializer.read(*_key);
            buffer.incPos(buffer.getRemaining() - stream.size());
            const ArrayDataType& arrayType = static_cast<const ArrayDataType&>(type);
            _update.reset(ValueUpdate::createInstance(repo, arrayType.getNestedType(), buffer, version).release());
            break;
        }
        case WeightedSetDataType::classId:
        {
            const WeightedSetDataType& wset(static_cast<const WeightedSetDataType&>(type));
            _key.reset(wset.getNestedType().createFieldValue().release());
            deserializer.read(*_key);
            buffer.incPos(buffer.getRemaining() - stream.size());
            _update.reset(ValueUpdate::createInstance(repo, *DataType::INT, buffer, version).release());
            break;
        }
        default:
            throw DeserializeException("Can not perform map update on type " + type.toString() + ".", VESPA_STRLOC);
    }
}

}
