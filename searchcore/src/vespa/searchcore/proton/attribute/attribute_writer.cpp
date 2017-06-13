// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_writer.h"
#include "attributemanager.h"
#include <vespa/searchcore/proton/common/attrupdate.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/common/isequencedtaskexecutor.h>
#include <vespa/document/datatype/documenttype.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.attributeadapter");

using namespace document;
using namespace search;

namespace proton {

AttributeWriter::WriteContext::WriteContext(uint32_t executorId)
    : _executorId(executorId),
      _fieldPaths(),
      _attributes()
{
}


AttributeWriter::WriteContext::WriteContext(WriteContext &&rhs) = default;

AttributeWriter::WriteContext::~WriteContext() = default;

AttributeWriter::WriteContext &AttributeWriter::WriteContext::operator=(WriteContext &&rhs) = default;

void
AttributeWriter::WriteContext::add(AttributeVector *attr)
{
    _attributes.emplace_back(attr);
    _fieldPaths.emplace_back();
}

void
AttributeWriter::WriteContext::buildFieldPaths(const DocumentType &docType)
{
    size_t fieldId = 0;
    for (const auto &attrp : _attributes) {
        const vespalib::string &name = attrp->getName();
        FieldPath::UP fp = docType.buildFieldPath(name);
        if (!fp) {
            LOG(warning,
                "Mismatch between documentdefinition and schema. "
                "No field named '%s' from schema in document type '%s'. "
                "This might happen if an attribute field has been added and you are feeding while reconfiguring",
                name.c_str(),
                docType.getName().c_str());
        }
        assert(fieldId < _fieldPaths.size());
        _fieldPaths[fieldId] = std::move(fp);
        ++fieldId;
    }
    assert(fieldId == _fieldPaths.size());
}

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
    attr.shrinkLidSpace();
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

class FieldContext
{
    vespalib::string _name;
    uint32_t         _executorId;
    AttributeVector *_attr;

public:
    FieldContext(ISequencedTaskExecutor &writer, AttributeVector *attr);
    ~FieldContext();
    bool operator<(const FieldContext &rhs) const;
    uint32_t getExecutorId() const { return _executorId; }
    AttributeVector *getAttribute() const { return _attr; }
};


FieldContext::FieldContext(ISequencedTaskExecutor &writer, AttributeVector *attr)
    :  _name(attr->getName()),
       _executorId(writer.getExecutorId(_name)),
       _attr(attr)
{
}

FieldContext::~FieldContext() = default;

bool
FieldContext::operator<(const FieldContext &rhs) const
{
    if (_executorId != rhs._executorId) {
        return _executorId < rhs._executorId;
    }
    return _name < rhs._name;
}

class PutTask : public vespalib::Executor::Task
{
    const AttributeWriter::WriteContext  &_wc;
    const SerialNum      _serialNum;
    const uint32_t       _lid;
    const bool           _immediateCommit;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
    std::vector<FieldValue::UP> _fieldValues;
public:
    PutTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, const Document &doc, uint32_t lid, bool immediateCommit, AttributeWriter::OnWriteDoneType onWriteDone);
    virtual ~PutTask() override;
    virtual void run() override;
};

PutTask::PutTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, const Document &doc, uint32_t lid, bool immediateCommit, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _serialNum(serialNum),
      _lid(lid),
      _immediateCommit(immediateCommit),
      _onWriteDone(onWriteDone)
{
    const auto &fieldPaths = _wc.getFieldPaths();
    _fieldValues.reserve(fieldPaths.size());
    for (const auto &fieldPath : fieldPaths) {
        FieldValue::UP fv;
        if (fieldPath) {
            fv = doc.getNestedFieldValue(fieldPath->getFullRange());
        }
        _fieldValues.emplace_back(std::move(fv));
    }
}

PutTask::~PutTask()
{
}

void
PutTask::run()
{
    uint32_t fieldId = 0;
    const auto &attributes = _wc.getAttributes();
    for (auto attrp : attributes) {
        AttributeVector &attr = *attrp;
        if (attr.getStatus().getLastSyncToken() < _serialNum) {
            applyPutToAttribute(_serialNum, _fieldValues[fieldId], _lid, _immediateCommit, attr, _onWriteDone);
        }
        ++fieldId;
    }
}

class RemoveTask : public vespalib::Executor::Task
{
    const AttributeWriter::WriteContext  &_wc;
    const SerialNum      _serialNum;
    const uint32_t       _lid;
    const bool           _immediateCommit;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
public:
    RemoveTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, uint32_t lid, bool immediateCommit, AttributeWriter::OnWriteDoneType onWriteDone);
    virtual ~RemoveTask() override;
    virtual void run() override;
};


RemoveTask::RemoveTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, uint32_t lid, bool immediateCommit, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _serialNum(serialNum),
      _lid(lid),
      _immediateCommit(immediateCommit),
      _onWriteDone(onWriteDone)
{
}

RemoveTask::~RemoveTask()
{
}

void
RemoveTask::run()
{
    const auto &attributes = _wc.getAttributes();
    for (auto &attrp : attributes) {
        AttributeVector &attr = *attrp;
        // Must use <= due to batch remove
        if (attr.getStatus().getLastSyncToken() <= _serialNum) {
            applyRemoveToAttribute(_serialNum, _lid, _immediateCommit, attr, _onWriteDone);
        }
    }
}

class CommitTask : public vespalib::Executor::Task
{
    const AttributeWriter::WriteContext  &_wc;
    const SerialNum      _serialNum;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
public:
    CommitTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, AttributeWriter::OnWriteDoneType onWriteDone);
    virtual ~CommitTask() override;
    virtual void run() override;
};


CommitTask::CommitTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _serialNum(serialNum),
      _onWriteDone(onWriteDone)
{
}

CommitTask::~CommitTask()
{
}

void
CommitTask::run()
{
    const auto &attributes = _wc.getAttributes();
    for (auto &attrp : attributes) {
        AttributeVector &attr = *attrp;
        applyCommit(_serialNum, _onWriteDone, attr);
    }
}

}

void
AttributeWriter::setupWriteContexts()
{
    std::vector<FieldContext> fieldContexts;
    assert(_writeContexts.empty());
    for (auto attr : _writableAttributes) {
        fieldContexts.emplace_back(_attributeFieldWriter, attr);
    }
    std::sort(fieldContexts.begin(), fieldContexts.end());
    for (auto &fc : fieldContexts) {
        if (_writeContexts.empty() ||
            (_writeContexts.back().getExecutorId() != fc.getExecutorId())) {
            _writeContexts.emplace_back(fc.getExecutorId());
        }
        _writeContexts.back().add(fc.getAttribute());
    }
}

void
AttributeWriter::buildFieldPaths(const DocumentType & docType, const DataType *dataType)
{
    for (auto &wc : _writeContexts) {
        wc.buildFieldPaths(docType);
    }
    _dataType = dataType;
}

void
AttributeWriter::internalPut(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                             bool immediateCommit, OnWriteDoneType onWriteDone)
{
    for (const auto &wc : _writeContexts) {
        auto putTask = std::make_unique<PutTask>(wc, serialNum, doc, lid, immediateCommit, onWriteDone);
        _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(putTask));
    }
}

void
AttributeWriter::internalRemove(SerialNum serialNum, DocumentIdT lid,
                                bool immediateCommit,
                                OnWriteDoneType onWriteDone)
{
    for (const auto &wc : _writeContexts) {
        auto removeTask = std::make_unique<RemoveTask>(wc, serialNum, lid, immediateCommit, onWriteDone);
        _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(removeTask));
    }
}

AttributeWriter::AttributeWriter(const proton::IAttributeManager::SP &mgr)
    : _mgr(mgr),
      _attributeFieldWriter(mgr->getAttributeFieldWriter()),
      _writableAttributes(mgr->getWritableAttributes()),
      _writeContexts(),
      _dataType(nullptr)
{
    setupWriteContexts();
}

AttributeWriter::~AttributeWriter()
{
    _attributeFieldWriter.sync();
}

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
    if (_dataType != dataType) {
        buildFieldPaths(doc.getType(), dataType);
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
    for (const auto &wc : _writeContexts) {
        auto commitTask = std::make_unique<CommitTask>(wc, serialNum, onWriteDone);
        _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(commitTask));
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
