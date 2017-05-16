// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "addfieldpathupdate.h"
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/log/log.h>

using vespalib::nbostream;

LOG_SETUP(".document.update.fieldpathupdate");

namespace document {

IMPLEMENT_IDENTIFIABLE(AddFieldPathUpdate, FieldPathUpdate);

AddFieldPathUpdate::AddFieldPathUpdate(
        const DocumentTypeRepo& repo,
        const DataType& type,
        stringref fieldPath,
        stringref whereClause,
        const ArrayFieldValue& values)
    : FieldPathUpdate(repo, type, fieldPath, whereClause),
      _values(vespalib::CloneablePtr<ArrayFieldValue>(values.clone()))
{
    checkCompatibility(*_values);
}

AddFieldPathUpdate::AddFieldPathUpdate()
    : FieldPathUpdate(), _values()
{ }

AddFieldPathUpdate::~AddFieldPathUpdate() { }

FieldPathUpdate*
AddFieldPathUpdate::clone() const {
    return new AddFieldPathUpdate(*this);
}

FieldValue::IteratorHandler::ModificationStatus
AddFieldPathUpdate::AddIteratorHandler::doModify(FieldValue& fv)
{
    LOG(spam, "Adding values to %s", fv.toString().c_str());
    if (fv.inherits(CollectionFieldValue::classId)) {
        CollectionFieldValue& cf = static_cast<CollectionFieldValue&>(fv);
        for (std::size_t i = 0; i < _values.size(); ++i) {
            cf.add(_values[i]);
        }
    } else {
        vespalib::string err = vespalib::make_string(
                "Unable to add a value to a \"%s\" field value.",
                fv.getClass().name());
                throw vespalib::IllegalArgumentException(err, VESPA_STRLOC);
    }
    return MODIFIED;
}

bool
AddFieldPathUpdate::operator==(const FieldPathUpdate& other) const
{
    if (other.getClass().id() != AddFieldPathUpdate::classId) return false;
    if (!FieldPathUpdate::operator==(other)) return false;
    const AddFieldPathUpdate& addOther
        = static_cast<const AddFieldPathUpdate&>(other);
    return *addOther._values == *_values;
}

void
AddFieldPathUpdate::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    out << "AddFieldPathUpdate(\n";
    FieldPathUpdate::print(out, verbose, indent + "  ");
    out << ",\n" << indent << "  " << "values=";
    _values->print(out, verbose, indent + "  ");
    out << "\n" << indent << ")";
}

void
AddFieldPathUpdate::deserialize(const DocumentTypeRepo& repo,
                                const DataType& type,
                                ByteBuffer& buffer, uint16_t version)
{
    FieldPathUpdate::deserialize(repo, type, buffer, version);

    const DataType& fieldType = getResultingDataType();
    assert(fieldType.inherits(ArrayDataType::classId));
    FieldValue::UP val = fieldType.createFieldValue();
    _values.reset(static_cast<ArrayFieldValue*>(val.release()));
    nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
    VespaDocumentDeserializer deserializer(repo, stream, version);
    deserializer.read(*_values);
    buffer.incPos(buffer.getRemaining() - stream.size());
}

} // ns document
