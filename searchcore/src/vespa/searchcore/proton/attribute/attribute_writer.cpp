// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.attributeadapter");

#include "attribute_writer.h"
#include "attributemanager.h"
#include <vespa/searchcore/proton/common/attrupdate.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/common/isequencedtaskexecutor.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>

using namespace document;
using namespace search;

namespace proton {

namespace {

void
ensureLidSpace(SerialNum serialNum, DocumentIdT lid, AttributeVector &attr)
{
    size_t docIdLimit = lid + 1;
    if (attr.getStatus().getLastSyncToken() < serialNum) {
        AttributeManager::padAttribute(attr, docIdLimit);
    }
}

void
applyPutToAttribute(SerialNum serialNum, const FieldValue::UP &fieldValue, DocumentIdT lid,
                    bool immediateCommit, AttributeVector &attr,
                    AttributeWriter::OnWriteDoneType)
{
    ensureLidSpace(serialNum, lid, attr);
    if (fieldValue.get()) {
        AttrUpdate::handleValue(attr, lid, *fieldValue);
    } else {
        attr.clearDoc(lid);
    }
    if (immediateCommit) {
        attr.commit(serialNum, serialNum);
    }
}

void
applyRemoveToAttribute(SerialNum serialNum, DocumentIdT lid, bool immediateCommit,
                       AttributeVector &attr, AttributeWriter::OnWriteDoneType)
{
    ensureLidSpace(serialNum, lid, attr);
    attr.clearDoc(lid);
    if (immediateCommit) {
        attr.commit(serialNum, serialNum);
    }
}

void
applyUpdateToAttribute(SerialNum serialNum, const FieldUpdate &fieldUpd,
                       DocumentIdT lid, bool immediateCommit, AttributeVector &attr,
                       AttributeWriter::OnWriteDoneType)
{
    ensureLidSpace(serialNum, lid, attr);
    AttrUpdate::handleUpdate(attr, lid, fieldUpd);
    if (immediateCommit) {
        attr.commit(serialNum, serialNum);
    }
}


void
applyReplayDone(uint32_t docIdLimit, AttributeVector &attr)
{
    AttributeManager::padAttribute(attr, docIdLimit);
    attr.compactLidSpace(docIdLimit);
}


void
applyHeartBeat(SerialNum serialNum, AttributeVector &attr)
{
    attr.removeAllOldGenerations();
    if (attr.getStatus().getLastSyncToken() <= serialNum) {
        attr.commit(serialNum, serialNum);
    }
}

void
applyCommit(SerialNum serialNum, AttributeWriter::OnWriteDoneType onWriteDone,
            AttributeVector &attr)
{
    (void) onWriteDone;
    if (attr.getStatus().getLastSyncToken() <= serialNum) {
        attr.commit(serialNum, serialNum);
    }
}


void
applyCompactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum,
                     AttributeVector &attr)
{
    if (attr.getStatus().getLastSyncToken() < serialNum) {
        attr.compactLidSpace(wantedLidLimit);
        attr.commit(serialNum, serialNum);
    }
}

}

void
AttributeWriter::buildFieldPath(const DocumentType & docType, const DataType *dataType)
{
    _fieldPaths.clear();
    _fieldPaths.resize(_writableAttributes.size());
    size_t i = 0;
    for (auto attrp : _writableAttributes) {
        const search::attribute::IAttributeVector & attribute(*attrp);
        FieldPath::UP fp = docType.buildFieldPath(attribute.getName());
        if (fp.get() == NULL) {
            /// Should be exception but due to incomplete unit test we can not be strict enough, must fix unit test proton/docsummary
            // The above comment is actually incorrect. This is expected during reconfig as long as do not stop accepting feed while doing reconfig.
            // throw std::runtime_error(vespalib::make_string("Mismatch between documentdefinition and schema. No field named '%s' from schema in document type '%s'", attribute.getName().c_str(), docType.getName().c_str()));
            LOG(warning,
                "Mismatch between documentdefinition and schema. "
                "No field named '%s' from schema in document type '%s'. "
                "This might happen if an attribute field has been added and you are feeding while reconfiguring",
                attribute.getName().c_str(),
                docType.getName().c_str());
        }
        _fieldPaths[i] = std::move(fp);
        ++i;
    }
    _dataType = dataType;
}

void
AttributeWriter::internalPut(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                             bool immediateCommit, OnWriteDoneType onWriteDone)
{
    size_t fieldId = 0;
    for (auto attrp : _writableAttributes) {
        AttributeVector &attr = *attrp;
        if (attr.getStatus().getLastSyncToken() >= serialNum) {
            LOG(debug, "internalPut(): change already applied: serial=%" PRIu64 ""
                    ", docId='%s', lid=%u, attribute='%s', lastSyncToken=%" PRIu64 "",
                    serialNum, doc.getId().toString().c_str(), lid, attr.getName().c_str(),
                    attr.getStatus().getLastSyncToken());
            ++fieldId;
            continue;	// Change already applied
        }
        const FieldPath *const fieldPath(_fieldPaths[fieldId].get());
        FieldValue::UP fv;
        if (fieldPath != NULL) {
            fv = doc.getNestedFieldValue(fieldPath->getFullRange());
        }

        _attributeFieldWriter.execute(attr.getName(),
                [serialNum, fv(std::move(fv)), lid, immediateCommit, &attr, onWriteDone]()
                { applyPutToAttribute(serialNum, fv, lid, immediateCommit, attr, onWriteDone); });
        ++fieldId;
    }
}

void
AttributeWriter::internalRemove(SerialNum serialNum, DocumentIdT lid,
                                bool immediateCommit,
                                OnWriteDoneType onWriteDone)
{
    for (auto &attrp : _writableAttributes) {
        AttributeVector &attr = *attrp;
        // XXX: Want to use >=, but must use > due to batch remove
        // Might be OK due to clearDoc() being idempotent.
        if (attr.getStatus().getLastSyncToken() > serialNum)
            continue;	// Change already applied
        LOG(debug, "About to remove docId %u from attribute vector '%s'.",
            lid, attr.getName().c_str());

        _attributeFieldWriter.execute(attr.getName(),
                [serialNum, lid, immediateCommit, &attr, onWriteDone]()
                { applyRemoveToAttribute(serialNum, lid, immediateCommit, attr, onWriteDone); });
    }
}

AttributeWriter::AttributeWriter(const proton::IAttributeManager::SP &mgr)
    : _mgr(mgr),
      _fieldPaths(),
      _dataType(),
      _fieldPathsDocTypeName(),
      _attributeFieldWriter(mgr->getAttributeFieldWriter()),
      _writableAttributes(mgr->getWritableAttributes())
{
}

AttributeWriter::~AttributeWriter() {}

std::vector<search::AttributeVector *>
AttributeWriter::getWritableAttributes() const
{
    return _mgr->getWritableAttributes();
}


search::AttributeVector *
AttributeWriter::getWritableAttribute(const vespalib::string &name) const
{
    return _mgr->getWritableAttribute(name);
}

void
AttributeWriter::put(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                     bool immediateCommit, OnWriteDoneType onWriteDone)
{
    FieldValue::UP attrVal;
    LOG(spam,
        "Handle put: serial(%" PRIu64 "), docId(%s), lid(%u), document(%s)",
        serialNum,
        doc.getId().toString().c_str(),
        lid,
        doc.toString(true).c_str());
    const DataType *dataType(doc.getDataType());
    if (_fieldPaths.empty() ||
        _dataType != dataType ||
        doc.getType().getName() != _fieldPathsDocTypeName) {
        buildFieldPath(doc.getType(), dataType);
        _fieldPathsDocTypeName = doc.getType().getName();
    }
    internalPut(serialNum, doc, lid, immediateCommit, onWriteDone);
}

void
AttributeWriter::remove(SerialNum serialNum, DocumentIdT lid,
                        bool immediateCommit, OnWriteDoneType onWriteDone)
{
    internalRemove(serialNum, lid, immediateCommit, onWriteDone);
}

void
AttributeWriter::remove(const LidVector &lidsToRemove, SerialNum serialNum,
                        bool immediateCommit, OnWriteDoneType onWriteDone)
{
    for (const auto &lid : lidsToRemove) {
        internalRemove(serialNum, lid, immediateCommit, onWriteDone);
    }
}

void
AttributeWriter::update(SerialNum serialNum, const DocumentUpdate &upd, DocumentIdT lid,
                        bool immediateCommit, OnWriteDoneType onWriteDone)
{
    LOG(debug, "Inspecting update for document %d.", lid);
    for (const auto &fupd : upd.getUpdates()) {
        LOG(debug, "Retrieving guard for attribute vector '%s'.",
            fupd.getField().getName().c_str());
        AttributeVector *attrp =
            _mgr->getWritableAttribute(fupd.getField().getName());
        if (attrp == nullptr) {
            LOG(spam, "Failed to find attribute vector %s",
                fupd.getField().getName().c_str());
            continue;
        }
        AttributeVector &attr = *attrp;
        // TODO: Check if we must use > due to multiple entries for same
        // document and attribute.
        if (attr.getStatus().getLastSyncToken() >= serialNum)
            continue;

        LOG(debug, "About to apply update for docId %u in attribute vector '%s'.",
                lid, attr.getName().c_str());

        // NOTE: The lifetime of the field update will be ensured by keeping the document update alive
        // in a operation done context object.
        _attributeFieldWriter.execute(attr.getName(),
                [serialNum, &fupd, lid, immediateCommit, &attr, onWriteDone]()
                { applyUpdateToAttribute(serialNum, fupd, lid, immediateCommit, attr, onWriteDone); });
    }
}

void
AttributeWriter::heartBeat(SerialNum serialNum)
{
    for (auto attrp : _writableAttributes) {
        auto &attr = *attrp;
        _attributeFieldWriter.execute(attr.getName(),
                                      [serialNum, &attr]()
                                      { applyHeartBeat(serialNum, attr); });
    }
}


void
AttributeWriter::commit(SerialNum serialNum, OnWriteDoneType onWriteDone)
{
    for (auto attrp : _writableAttributes) {
        auto &attr = *attrp;
        _attributeFieldWriter.execute(attr.getName(),
                                      [serialNum, onWriteDone, &attr]()
                                      { applyCommit(serialNum, onWriteDone,
                                                    attr); });
    }
}


void
AttributeWriter::onReplayDone(uint32_t docIdLimit)
{
    for (auto attrp : _writableAttributes) {
        auto &attr = *attrp;
        _attributeFieldWriter.execute(attr.getName(),
                                      [docIdLimit, &attr]()
                                      { applyReplayDone(docIdLimit, attr); });
    }
    _attributeFieldWriter.sync();
}


void
AttributeWriter::compactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum)
{
    for (auto attrp : _writableAttributes) {
        auto &attr = *attrp;
        _attributeFieldWriter.
            execute(attr.getName(),
                    [wantedLidLimit, serialNum, &attr]()
                    { applyCompactLidSpace(wantedLidLimit, serialNum, attr); });
    }
    _attributeFieldWriter.sync();
}


} // namespace proton
