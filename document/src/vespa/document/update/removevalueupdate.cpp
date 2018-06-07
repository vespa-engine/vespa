// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "removevalueupdate.h"
#include <vespa/document/base/field.h>
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/xmlstream.h>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::nbostream;
using namespace vespalib::xml;

namespace document {

IMPLEMENT_IDENTIFIABLE(RemoveValueUpdate, ValueUpdate);

RemoveValueUpdate::RemoveValueUpdate(const FieldValue& key)
    : ValueUpdate(),
      _key(key.clone())
{}

RemoveValueUpdate::~RemoveValueUpdate() = default;

bool
RemoveValueUpdate::operator==(const ValueUpdate& other) const
{
    if (other.getClass().id() != RemoveValueUpdate::classId) return false;
    const RemoveValueUpdate& o(static_cast<const RemoveValueUpdate&>(other));
    if (*_key != *o._key) return false;
    return true;
}

// Ensure that this update is compatible with given field.
void
RemoveValueUpdate::checkCompatibility(const Field& field) const
{
    if (field.getDataType().inherits(CollectionDataType::classId)) {
        const CollectionDataType& type = static_cast<const CollectionDataType&>(field.getDataType());
        if (!type.getNestedType().isValueType(*_key)) {
            throw IllegalArgumentException(
                    "Cannot remove value of type "
                    + _key->getDataType()->toString() + " from field "
                    + field.getName().c_str() + " of container type "
                    + field.getDataType().toString(), VESPA_STRLOC);
        }
    } else {
        throw IllegalArgumentException("Can not remove a value from field of type " + field.getDataType().toString(), VESPA_STRLOC);
    }
}

// Apply this update to the given document.
bool
RemoveValueUpdate::applyTo(FieldValue& value) const
{
    if (value.inherits(ArrayFieldValue::classId)) {
        ArrayFieldValue& doc(static_cast<ArrayFieldValue&>(value));
        doc.remove(*_key);	
    } else if (value.inherits(WeightedSetFieldValue::classId)) {
        WeightedSetFieldValue& doc(static_cast<WeightedSetFieldValue&>(value));
        doc.remove(*_key);
    } else {
        std::string err = vespalib::make_string("Unable to remove a value from a \"%s\" field value.", value.getClass().name());
        throw IllegalStateException(err, VESPA_STRLOC);
    }
    return true;
}

void
RemoveValueUpdate::printXml(XmlOutputStream& xos) const
{
    xos << XmlTag("remove") << *_key << XmlEndTag();
}

// Print this update in human readable form.
void
RemoveValueUpdate::print(std::ostream& out, bool, const std::string&) const
{
    out << "RemoveValueUpdate(" << *_key << ")";
}

// Deserialize this update from the given buffer.
void
RemoveValueUpdate::deserialize(const DocumentTypeRepo& repo, const DataType& type, ByteBuffer& buffer, uint16_t version)
{
    switch(type.getClass().id()) {
        case ArrayDataType::classId:
        case WeightedSetDataType::classId:
        {
            const CollectionDataType& c(static_cast<const CollectionDataType&>(type));
            _key.reset(c.getNestedType().createFieldValue().release());
            nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
            VespaDocumentDeserializer deserializer(repo, stream, version);
            deserializer.read(*_key);
            buffer.incPos(buffer.getRemaining() - stream.size());
            break;
        }
        default:
            throw DeserializeException("Can not perform remove operation on type " + type.toString() + ".", VESPA_STRLOC);
    }
}

}
