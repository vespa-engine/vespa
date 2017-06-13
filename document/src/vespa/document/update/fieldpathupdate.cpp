// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fieldpathupdates.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/util/serializableexceptions.h>
#include <ostream>

#include <vespa/log/log.h>
LOG_SETUP(".document.update.fieldpathupdate");

using vespalib::make_string;
using vespalib::IllegalArgumentException;

namespace document {

using namespace fieldvalue;

IMPLEMENT_IDENTIFIABLE_ABSTRACT(FieldPathUpdate, Identifiable);

namespace {

std::unique_ptr<select::Node>
parseDocumentSelection(vespalib::stringref query, const DocumentTypeRepo& repo)
{
    BucketIdFactory factory;
    select::Parser parser(repo, factory);
    return parser.parse(query);
}

}  // namespace

FieldPathUpdate::FieldPathUpdate() :
    _originalFieldPath(),
    _originalWhereClause(),
    _fieldPath(),
    _whereClause()
{
}

FieldPathUpdate::FieldPathUpdate(const DocumentTypeRepo& repo,
                                 const DataType& type,
                                 stringref fieldPath,
                                 stringref whereClause) :
    _originalFieldPath(fieldPath),
    _originalWhereClause(whereClause),
    _fieldPath(type.buildFieldPath(_originalFieldPath).release()),
    _whereClause(!_originalWhereClause.empty()
                 ? parseDocumentSelection(_originalWhereClause, repo)
                 : std::unique_ptr<select::Node>())
{
    if (!_fieldPath.get()) {
        throw IllegalArgumentException(
                make_string("Could not create field path update for: path='%s', where='%s'",
                            fieldPath.c_str(), whereClause.c_str()), VESPA_STRLOC);
    }
}

FieldPathUpdate::~FieldPathUpdate()  { }

bool
FieldPathUpdate::operator==(const FieldPathUpdate& other) const
{
    return (other._originalFieldPath == _originalFieldPath)
            && (other._originalWhereClause == _originalWhereClause);
}

void
FieldPathUpdate::applyTo(Document& doc) const
{
    std::unique_ptr<IteratorHandler> handler(getIteratorHandler(doc));

    if (!_whereClause) {
        doc.iterateNested(*_fieldPath, *handler);
    } else {
        select::ResultList results = _whereClause->contains(doc);
        for (select::ResultList::const_iterator i = results.begin();
             i != results.end(); ++i)
        {
            LOG(spam, "vars = %s", handler->getVariables().toString().c_str());
            if (*i->second == select::Result::True) {
                handler->setVariables(i->first);
                doc.iterateNested(*_fieldPath, *handler);
            }
        }
    }
}

bool
FieldPathUpdate::affectsDocumentBody() const
{
    if (_fieldPath->empty() || !(*_fieldPath)[0].hasField()) return false;
    const Field& field = (*_fieldPath)[0].getFieldRef();
    return !field.isHeaderField();
}

void
FieldPathUpdate::print(std::ostream& out, bool, const std::string& indent) const
{
    out << indent << "fieldPath='" << _originalFieldPath << "',\n"
        << indent << "whereClause='" << _originalWhereClause << "'";
}

void
FieldPathUpdate::checkCompatibility(const FieldValue& fv) const
{
    if ( !getResultingDataType().isValueType(fv)) {
        throw IllegalArgumentException(
                make_string("Cannot update a '%s' field with a '%s' value",
                            getResultingDataType().toString().c_str(),
                            fv.getDataType()->toString().c_str()),
                VESPA_STRLOC);
    }
}

const DataType&
FieldPathUpdate::getResultingDataType() const
{
    if (_fieldPath->empty()) {
        throw vespalib::IllegalStateException("Cannot get resulting data type from an empty field path", VESPA_STRLOC);
    }
    return _fieldPath->rbegin()->getDataType();
}

vespalib::string
FieldPathUpdate::getString(ByteBuffer& buffer)
{
    int32_t length = 0;
    buffer.getIntNetwork(length);
    vespalib::string s(buffer.getBufferAtPos());
    buffer.incPos(length);
    return s;
}

void
FieldPathUpdate::deserialize(const DocumentTypeRepo& repo,
                             const DataType& type,
                             ByteBuffer& buffer, uint16_t)
{
    _originalFieldPath = getString(buffer);
    _originalWhereClause = getString(buffer);

    try {
        _fieldPath = type.buildFieldPath(_originalFieldPath).release();
        if (!_fieldPath.get()) {
            throw DeserializeException(make_string("Invalid field path: '%s'", _originalFieldPath.c_str()), VESPA_STRLOC);
        }
        _whereClause = !_originalWhereClause.empty()
                       ? parseDocumentSelection(_originalWhereClause, repo)
                       : std::unique_ptr<select::Node>();
    } catch (const select::ParsingFailedException& e) {
        throw DeserializeException(e.what(), VESPA_STRLOC);
    }
}

std::unique_ptr<FieldPathUpdate>
FieldPathUpdate::createInstance(const DocumentTypeRepo& repo,
                                const DataType &type, ByteBuffer& buffer,
                                int serializationVersion)
{
    unsigned char updateType = 0;
    buffer.getByte(updateType);

    std::unique_ptr<FieldPathUpdate> update;
    switch (updateType) {
    case 0:
        update.reset(new AssignFieldPathUpdate());
        break;
    case 1:
        update.reset(new RemoveFieldPathUpdate());
        break;
    case 2:
        update.reset(new AddFieldPathUpdate());
        break;
    default:
        throw DeserializeException(make_string("Unknown fieldpath update type: %d", updateType), VESPA_STRLOC);
    }
    update->deserialize(repo, type, buffer, serializationVersion);
    return update;
}

} // ns document

