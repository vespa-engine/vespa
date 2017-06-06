// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "assignfieldpathupdate.h"
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/select/variablemap.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <boost/numeric/conversion/cast.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".document.update.fieldpathupdate");

using vespalib::nbostream;

namespace document {

IMPLEMENT_IDENTIFIABLE(AssignFieldPathUpdate, FieldPathUpdate);

AssignFieldPathUpdate::AssignFieldPathUpdate()
    : FieldPathUpdate(),
      _repo(),
      _newValue(),
      _expression(),
      _removeIfZero(false),
      _createMissingPath(false)
{ }


AssignFieldPathUpdate::AssignFieldPathUpdate(
        const DocumentTypeRepo& repo,
        const DataType& type,
        stringref fieldPath,
        stringref whereClause,
        const FieldValue& newValue)
    : FieldPathUpdate(repo, type, fieldPath, whereClause),
      _repo(&repo),
      _newValue(newValue.clone()),
      _expression(),
      _removeIfZero(false),
      _createMissingPath(true)
{
    checkCompatibility(*_newValue);
}

AssignFieldPathUpdate::AssignFieldPathUpdate(
        const DocumentTypeRepo& repo,
        const DataType& type,
        stringref fieldPath,
        stringref whereClause,
        stringref expression)
    : FieldPathUpdate(repo, type, fieldPath, whereClause),
      _repo(&repo),
      _newValue(),
      _expression(expression),
      _removeIfZero(false),
      _createMissingPath(true)
{
    if (_expression.empty()) {
        throw vespalib::IllegalArgumentException("Cannot create an arithmetic "
                "assignment update with an empty expression", VESPA_STRLOC);
    }
}

AssignFieldPathUpdate::~AssignFieldPathUpdate() { }

FieldPathUpdate*
AssignFieldPathUpdate::clone() const {
    return new AssignFieldPathUpdate(*this);
}

std::unique_ptr<FieldValue::IteratorHandler>
AssignFieldPathUpdate::getIteratorHandler(Document& doc) const
{
    if (!_expression.empty()) {
        return std::unique_ptr<FieldValue::IteratorHandler>(
                new AssignExpressionIteratorHandler(
                        *_repo, doc, _expression, _removeIfZero, _createMissingPath));
    } else {
        return std::unique_ptr<FieldValue::IteratorHandler>(
                new AssignValueIteratorHandler(
                        *_newValue, _removeIfZero, _createMissingPath));
    }
}


FieldValue::IteratorHandler::ModificationStatus
AssignFieldPathUpdate::AssignValueIteratorHandler::doModify(FieldValue& fv) {
    LOG(spam, "fv = %s", fv.toString().c_str());
    if (!(*fv.getDataType() == *_newValue.getDataType())) {
        std::string err = vespalib::make_string(
                "Trying to assign \"%s\" of type %s to an instance of type %s",
                _newValue.toString().c_str(), _newValue.getClass().name(),
                fv.getClass().name());
        throw vespalib::IllegalArgumentException(err, VESPA_STRLOC);
    }
    if (_removeIfZero
        && _newValue.inherits(NumericFieldValueBase::classId)
        && static_cast<const NumericFieldValueBase&>(_newValue).getAsLong() == 0)
    {
        return REMOVED;
    }
    fv.assign(_newValue);
    return MODIFIED;
}

FieldValue::IteratorHandler::ModificationStatus
AssignFieldPathUpdate::AssignExpressionIteratorHandler::doModify(FieldValue& fv) {
    LOG(spam, "fv = %s", fv.toString().c_str());
    if (fv.inherits(NumericFieldValueBase::classId)) {
        std::unique_ptr<select::VariableMap> varHolder = std::make_unique<select::VariableMap>();
        select::VariableMap & vars = *varHolder;
        for (VariableMap::const_iterator i(getVariables().begin()),
                 e(getVariables().end()); i != e; ++i)
        {
            if (i->second.key.get() && i->second.key->inherits(NumericFieldValueBase::classId)) {
                vars[i->first] = i->second.key->getAsDouble();
            } else {
                vars[i->first] = i->second.index;
            }
        }

        vars["value"] = fv.getAsDouble();

        try {
            double res = _calc.evaluate(_doc, std::move(varHolder));
            if (_removeIfZero && static_cast<uint64_t>(res) == 0) {
                return REMOVED;
            } else {
                fv.assign(DoubleFieldValue(res));
            }
        } catch (const vespalib::IllegalArgumentException&) {
            // Divide by zero does not modify the document field
            return NOT_MODIFIED;
        } catch (const boost::bad_numeric_cast&) {
            // Underflow/overflow does not modify
            return NOT_MODIFIED;
        }
    } else {
        throw vespalib::IllegalArgumentException(
                vespalib::make_string("Trying to perform arithmetic on %s of type %s",
                                      fv.toString().c_str(), fv.getDataType()->toString().c_str()),
                VESPA_STRLOC);
    }
    return MODIFIED;
}

bool
AssignFieldPathUpdate::operator==(const FieldPathUpdate& other) const
{
    if (other.getClass().id() != AssignFieldPathUpdate::classId) return false;
    if (!FieldPathUpdate::operator==(other)) return false;
    const AssignFieldPathUpdate& assignOther
        = static_cast<const AssignFieldPathUpdate&>(other);
    if (assignOther._newValue.get() && _newValue.get()) {
        if (*assignOther._newValue != *_newValue) return false;
    }
    // else: should always have at least 1 with non-empty expression
    return (assignOther._expression == _expression)
            && (assignOther._removeIfZero == _removeIfZero)
            && (assignOther._createMissingPath == _createMissingPath);
}

void
AssignFieldPathUpdate::print(std::ostream& out, bool verbose,
                             const std::string& indent) const
{
    out << "AssignFieldPathUpdate(\n";
    FieldPathUpdate::print(out, verbose, indent + "  ");
    if (_newValue.get()) {
        out << ",\n" << indent << "  " << "newValue=";
        _newValue->print(out, verbose, indent + "  ");
    } else {
        out << ",\n" << indent << "  " << "expression='" << _expression << "'";
    }
    out << ", removeIfZero=" << (_removeIfZero ? "yes" : "no")
        << ", createMissingPath=" << (_createMissingPath ? "yes" : "no")
        << "\n" << indent << ")";
}

void
AssignFieldPathUpdate::deserialize(const DocumentTypeRepo& repo,
                                   const DataType& type,
                                   ByteBuffer& buffer, uint16_t version)
{
    FieldPathUpdate::deserialize(repo, type, buffer, version);
    _repo = &repo;

    uint8_t flags = 0x00;
    buffer.getByte(flags);

    _removeIfZero = (flags & REMOVE_IF_ZERO) != 0;
    _createMissingPath = (flags & CREATE_MISSING_PATH) != 0;

    if (flags & ARITHMETIC_EXPRESSION) {
        _expression = getString(buffer);
    } else {
        _newValue.reset(getResultingDataType().createFieldValue().release());
        nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
        VespaDocumentDeserializer deserializer(*_repo, stream, version);
        deserializer.read(*_newValue);
        buffer.incPos(buffer.getRemaining() - stream.size());
    }
}

} // ns document
