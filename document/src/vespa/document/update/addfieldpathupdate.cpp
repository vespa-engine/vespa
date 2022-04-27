// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "addfieldpathupdate.h"
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>
#include <cassert>

using vespalib::nbostream;

namespace document {

using namespace fieldvalue;
using vespalib::make_string;

AddFieldPathUpdate::AddFieldPathUpdate(const DataType& type, stringref fieldPath,
                                       stringref whereClause, std::unique_ptr<ArrayFieldValue> values)
    : FieldPathUpdate(Add, fieldPath, whereClause),
      _values(std::move(values))
{
    checkCompatibility(*_values, type);
}

AddFieldPathUpdate::AddFieldPathUpdate()
    : FieldPathUpdate(Add),
      _values()
{ }

AddFieldPathUpdate::~AddFieldPathUpdate() = default;

namespace {

class AddIteratorHandler : public fieldvalue::IteratorHandler {
public:
    AddIteratorHandler(const ArrayFieldValue &values) : _values(values) {}
    fieldvalue::ModificationStatus doModify(FieldValue &fv) override;
    bool createMissingPath() const override { return true; }
    bool onComplex(const fieldvalue::IteratorHandler::Content &) override { return false; }
private:
    const ArrayFieldValue &_values;
};


ModificationStatus
AddIteratorHandler::doModify(FieldValue &fv) {
    if (fv.isCollection()) {
        auto &cf = static_cast<CollectionFieldValue &>(fv);
        for (std::size_t i = 0; i < _values.size(); ++i) {
            cf.add(_values[i]);
        }
    } else {
        vespalib::string err = make_string("Unable to add a value to a \"%s\" field value.", fv.className());
        throw vespalib::IllegalArgumentException(err, VESPA_STRLOC);
    }
    return ModificationStatus::MODIFIED;
}

}

bool
AddFieldPathUpdate::operator==(const FieldPathUpdate& other) const
{
    if (!FieldPathUpdate::operator==(other)) return false;
    const auto & addOther = static_cast<const AddFieldPathUpdate&>(other);
    return *addOther._values == *_values;
}

void
AddFieldPathUpdate::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "AddFieldPathUpdate(\n";
    FieldPathUpdate::print(out, verbose, indent + "  ");
    out << ",\n" << indent << "  " << "values=";
    _values->print(out, verbose, indent + "  ");
    out << "\n" << indent << ")";
}

void
AddFieldPathUpdate::deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream & stream)
{
    FieldPathUpdate::deserialize(repo, type, stream);

    FieldPath path;
    type.buildFieldPath(path, getOriginalFieldPath());
    const DataType& fieldType = getResultingDataType(path);
    assert(fieldType.isArray());
    FieldValue::UP val = fieldType.createFieldValue();
    _values.reset(static_cast<ArrayFieldValue*>(val.release()));
    VespaDocumentDeserializer deserializer(repo, stream, Document::getNewestSerializationVersion());
    deserializer.read(*_values);
}

std::unique_ptr<IteratorHandler>
AddFieldPathUpdate::getIteratorHandler(Document&, const DocumentTypeRepo &) const {
    return std::make_unique<AddIteratorHandler>(*_values);
}



} // ns document
