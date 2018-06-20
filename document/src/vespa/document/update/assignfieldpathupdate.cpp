// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "assignfieldpathupdate.h"
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
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

using namespace fieldvalue;

IMPLEMENT_IDENTIFIABLE(AssignFieldPathUpdate, FieldPathUpdate);

AssignFieldPathUpdate::AssignFieldPathUpdate()
    : FieldPathUpdate(),
      _newValue(),
      _expression(),
      _removeIfZero(false),
      _createMissingPath(false)
{ }


AssignFieldPathUpdate::AssignFieldPathUpdate(
        const DataType& type,
        stringref fieldPath,
        stringref whereClause,
        const FieldValue& newValue)
    : FieldPathUpdate(fieldPath, whereClause),
      _newValue(newValue.clone()),
      _expression(),
      _removeIfZero(false),
      _createMissingPath(true)
{
    checkCompatibility(*_newValue, type);
}

AssignFieldPathUpdate::AssignFieldPathUpdate(stringref fieldPath, stringref whereClause, stringref expression)
    : FieldPathUpdate(fieldPath, whereClause),
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
namespace {

class AssignValueIteratorHandler : public IteratorHandler
{
public:
    AssignValueIteratorHandler(const FieldValue& newValue,
                               bool removeIfZero,
                               bool createMissingPath_)
            : _newValue(newValue), _removeIfZero(removeIfZero),
              _createMissingPath(createMissingPath_)
    {}

    ModificationStatus doModify(FieldValue& fv) override;
    bool onComplex(const Content&) override { return false; }
    bool createMissingPath() const override { return _createMissingPath; }

private:
    const FieldValue& _newValue;
    bool _removeIfZero;
    bool _createMissingPath;
};

class AssignExpressionIteratorHandler : public IteratorHandler
{
public:
    AssignExpressionIteratorHandler(
            const DocumentTypeRepo& repo,
            Document& doc,
            const vespalib::string& expression,
            bool removeIfZero,
            bool createMissingPath_)
            : _calc(repo, expression),
              _doc(doc),
              _removeIfZero(removeIfZero),
              _createMissingPath(createMissingPath_)
    {}

    ModificationStatus doModify(FieldValue& fv) override;
    bool onComplex(const Content&) override { return false; }
    bool createMissingPath() const override { return _createMissingPath; }

private:
    DocumentCalculator _calc;
    Document& _doc;
    bool _removeIfZero;
    bool _createMissingPath;
};

ModificationStatus
AssignValueIteratorHandler::doModify(FieldValue& fv) {
    LOG(spam, "fv = %s", fv.toString().c_str());
    if (!(*fv.getDataType() == *_newValue.getDataType())) {
        vespalib::string err = vespalib::make_string(
                "Trying to assign \"%s\" of type %s to an instance of type %s",
                _newValue.toString().c_str(), _newValue.getClass().name(),
                fv.getClass().name());
        throw vespalib::IllegalArgumentException(err, VESPA_STRLOC);
    }
    if (_removeIfZero
        && _newValue.inherits(NumericFieldValueBase::classId)
        && static_cast<const NumericFieldValueBase&>(_newValue).getAsLong() == 0)
    {
        return ModificationStatus::REMOVED;
    }
    fv.assign(_newValue);
    return ModificationStatus::MODIFIED;
}

ModificationStatus
AssignExpressionIteratorHandler::doModify(FieldValue& fv) {
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
                return ModificationStatus::REMOVED;
            } else {
                fv.assign(DoubleFieldValue(res));
            }
        } catch (const vespalib::IllegalArgumentException&) {
            // Divide by zero does not modify the document field
            return ModificationStatus::NOT_MODIFIED;
        } catch (const boost::bad_numeric_cast&) {
            // Underflow/overflow does not modify
            return ModificationStatus::NOT_MODIFIED;
        }
    } else {
        throw vespalib::IllegalArgumentException(
                vespalib::make_string("Trying to perform arithmetic on %s of type %s",
                                      fv.toString().c_str(), fv.getDataType()->toString().c_str()),
                VESPA_STRLOC);
    }
    return ModificationStatus::MODIFIED;
}

}

std::unique_ptr<IteratorHandler>
AssignFieldPathUpdate::getIteratorHandler(Document& doc, const DocumentTypeRepo & repo) const
{
    if (!_expression.empty()) {
        return std::make_unique<AssignExpressionIteratorHandler>(repo, doc, _expression, _removeIfZero, _createMissingPath);
    } else {
        return std::make_unique<AssignValueIteratorHandler>(*_newValue, _removeIfZero, _createMissingPath);
    }
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
AssignFieldPathUpdate::print(std::ostream& out, bool verbose, const std::string& indent) const
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
AssignFieldPathUpdate::deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream & stream)
{
    FieldPathUpdate::deserialize(repo, type, stream);

    uint8_t flags = 0x00;
    stream >> flags;

    _removeIfZero = (flags & REMOVE_IF_ZERO) != 0;
    _createMissingPath = (flags & CREATE_MISSING_PATH) != 0;

    if (flags & ARITHMETIC_EXPRESSION) {
        _expression = getString(stream);
    } else {
        FieldPath path;
        type.buildFieldPath(path, getOriginalFieldPath());
        _newValue.reset(getResultingDataType(path).createFieldValue().release());
        VespaDocumentDeserializer deserializer(repo, stream, Document::getNewestSerializationVersion());
        deserializer.read(*_newValue);
    }
}

} // ns document
