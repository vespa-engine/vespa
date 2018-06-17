// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_writer.h"
#include "ifieldupdatecallback.h"
#include "attributemanager.h"
#include "document_field_extractor.h"
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/common/attrupdate.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/common/isequencedtaskexecutor.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.attributeadapter");

using namespace document;
using namespace search;
using search::attribute::ImportedAttributeVector;
using ExecutorId = search::ISequencedTaskExecutor::ExecutorId;

namespace proton {

using LidVector = LidVectorContext::LidVector;

AttributeWriter::WriteField::WriteField(AttributeVector &attribute)
    : _fieldPath(),
      _attribute(attribute),
      _structFieldAttribute(false)
{
    const vespalib::string &name = attribute.getName();
    _structFieldAttribute = name.find('.') != vespalib::string::npos;
}

AttributeWriter::WriteField::~WriteField() = default;

void
AttributeWriter::WriteField::buildFieldPath(const DocumentType &docType)
{
    const vespalib::string &name = _attribute.getName();
    FieldPath fp;
    try {
        docType.buildFieldPath(fp, name);
    } catch (document::FieldNotFoundException & e) {
        fp = FieldPath();
    } catch (vespalib::IllegalArgumentException &e) {
        fp = FieldPath();
    }
    _fieldPath = std::move(fp);
}

AttributeWriter::WriteContext::WriteContext(ExecutorId executorId)
    : _executorId(executorId),
      _fields(),
      _hasStructFieldAttribute(false)
{
}


AttributeWriter::WriteContext::WriteContext(WriteContext &&rhs) = default;

AttributeWriter::WriteContext::~WriteContext() = default;

AttributeWriter::WriteContext &AttributeWriter::WriteContext::operator=(WriteContext &&rhs) = default;

void
AttributeWriter::WriteContext::add(AttributeVector &attr)
{
    _fields.emplace_back(attr);
    if (_fields.back().isStructFieldAttribute()) {
        _hasStructFieldAttribute = true;
    }
}

void
AttributeWriter::WriteContext::buildFieldPaths(const DocumentType &docType)
{
    for (auto &field : _fields) {
        field.buildFieldPath(docType);
    }
}

namespace {

vespalib::stringref
getPrefix(vespalib::stringref name ) {
    return name.substr(0, name.find('.'));
}

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
                       DocumentIdT lid, AttributeVector &attr)
{
    ensureLidSpace(serialNum, lid, attr);
    AttrUpdate::handleUpdate(attr, lid, fieldUpd);
}

void
applyUpdateToAttributeAndCommit(SerialNum serialNum, const FieldUpdate &fieldUpd,
                                DocumentIdT lid, AttributeVector &attr)
{
    ensureLidSpace(serialNum, lid, attr);
    AttrUpdate::handleUpdate(attr, lid, fieldUpd);
    attr.commit(serialNum, serialNum);
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
applyCommit(SerialNum serialNum, AttributeWriter::OnWriteDoneType , AttributeVector &attr)
{
    if (attr.getStatus().getLastSyncToken() <= serialNum) {
        attr.commit(serialNum, serialNum);
    }
}


void
applyCompactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum, AttributeVector &attr)
{
    if (attr.getStatus().getLastSyncToken() < serialNum) {
        /*
         * If the attribute is an empty placeholder attribute due to
         * later config changes removing the attribute then it might
         * be smaller than expected during transaction log replay.
         */
        attr.commit();
        if (wantedLidLimit <= attr.getCommittedDocIdLimit()) {
            attr.compactLidSpace(wantedLidLimit);
        }
        attr.commit(serialNum, serialNum);
    }
}

using AttrUpdates = std::vector<std::pair<AttributeVector *, const FieldUpdate *>>;

struct BatchUpdateTask : public vespalib::Executor::Task {

    BatchUpdateTask(SerialNum serialNum, DocumentIdT lid, bool immediateCommit)
        : vespalib::Executor::Task(),
          _serialNum(serialNum),
          _lid(lid),
          _immediateCommit(immediateCommit),
          _onWriteDone()
    { }

    void run() override {
        if (_immediateCommit) {
            for (const auto & update : _updates) {
                applyUpdateToAttributeAndCommit(_serialNum, *update.second, _lid, *update.first);
            }
        } else {
            for (const auto & update : _updates) {
                applyUpdateToAttribute(_serialNum, *update.second, _lid, *update.first);
            }
        }
    }


    SerialNum                        _serialNum;
    DocumentIdT                      _lid;
    bool                             _immediateCommit;
    AttrUpdates                      _updates;
    search::IDestructorCallback::SP  _onWriteDone;
};

class FieldContext
{
    vespalib::string   _name;
    ExecutorId         _executorId;
    AttributeVector   *_attr;

public:
    FieldContext(ISequencedTaskExecutor &writer, AttributeVector *attr);
    ~FieldContext();
    bool operator<(const FieldContext &rhs) const;
    ExecutorId getExecutorId() const { return _executorId; }
    AttributeVector *getAttribute() const { return _attr; }
};


FieldContext::FieldContext(ISequencedTaskExecutor &writer, AttributeVector *attr)
    :  _name(attr->getName()),
       _executorId(writer.getExecutorId(getPrefix(_name))),
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
    const bool           _allAttributes;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
    std::shared_ptr<DocumentFieldExtractor> _fieldExtractor;
    std::vector<FieldValue::UP> _fieldValues;
public:
    PutTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, std::shared_ptr<DocumentFieldExtractor> fieldExtractor, uint32_t lid, bool immediateCommit, bool allAttributes, AttributeWriter::OnWriteDoneType onWriteDone);
    ~PutTask() override;
    void run() override;
};

PutTask::PutTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, std::shared_ptr<DocumentFieldExtractor> fieldExtractor, uint32_t lid, bool immediateCommit, bool allAttributes, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _serialNum(serialNum),
      _lid(lid),
      _immediateCommit(immediateCommit),
      _allAttributes(allAttributes),
      _onWriteDone(onWriteDone),
      _fieldExtractor(std::move(fieldExtractor)),
      _fieldValues()
{
    const auto &fields = _wc.getFields();
    _fieldValues.reserve(fields.size());
    for (const auto &field : fields) {
        if (_allAttributes || field.isStructFieldAttribute()) {
            FieldValue::UP fv = _fieldExtractor->getFieldValue(field.getFieldPath());
            _fieldValues.emplace_back(std::move(fv));
        }
    }
}

PutTask::~PutTask() = default;

void
PutTask::run()
{
    uint32_t fieldId = 0;
    const auto &fields = _wc.getFields();
    for (auto field : fields) {
        if (_allAttributes || field.isStructFieldAttribute()) {
            AttributeVector &attr = field.getAttribute();
            if (attr.getStatus().getLastSyncToken() < _serialNum) {
                applyPutToAttribute(_serialNum, _fieldValues[fieldId], _lid, _immediateCommit, attr, _onWriteDone);
            }
            ++fieldId;
        }
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
    ~RemoveTask() override;
    void run() override;
};


RemoveTask::RemoveTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, uint32_t lid, bool immediateCommit, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _serialNum(serialNum),
      _lid(lid),
      _immediateCommit(immediateCommit),
      _onWriteDone(onWriteDone)
{
}

RemoveTask::~RemoveTask() = default;

void
RemoveTask::run()
{
    const auto &fields = _wc.getFields();
    for (auto &field : fields) {
        AttributeVector &attr = field.getAttribute();
        // Must use <= due to how move operations are handled
        if (attr.getStatus().getLastSyncToken() <= _serialNum) {
            applyRemoveToAttribute(_serialNum, _lid, _immediateCommit, attr, _onWriteDone);
        }
    }
}

class BatchRemoveTask : public vespalib::Executor::Task
{
private:
    const AttributeWriter::WriteContext &_writeCtx;
    const SerialNum _serialNum;
    const LidVector _lidsToRemove;
    const bool _immediateCommit;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
public:
    BatchRemoveTask(const AttributeWriter::WriteContext &writeCtx,
                    SerialNum serialNum,
                    const LidVector &lidsToRemove,
                    bool immediateCommit,
                    AttributeWriter::OnWriteDoneType onWriteDone)
        : _writeCtx(writeCtx),
          _serialNum(serialNum),
          _lidsToRemove(lidsToRemove),
          _immediateCommit(immediateCommit),
          _onWriteDone(onWriteDone)
    {}
    ~BatchRemoveTask() override {}
    void run() override {
        for (auto field : _writeCtx.getFields()) {
            auto &attr = field.getAttribute();
            if (attr.getStatus().getLastSyncToken() < _serialNum) {
                for (auto lidToRemove : _lidsToRemove) {
                    applyRemoveToAttribute(_serialNum, lidToRemove, false, attr, _onWriteDone);
                }
                if (_immediateCommit) {
                    attr.commit(_serialNum, _serialNum);
                }
            }
        }
    }
};

class CommitTask : public vespalib::Executor::Task
{
    const AttributeWriter::WriteContext  &_wc;
    const SerialNum      _serialNum;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
public:
    CommitTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, AttributeWriter::OnWriteDoneType onWriteDone);
    ~CommitTask() override;
    void run() override;
};


CommitTask::CommitTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _serialNum(serialNum),
      _onWriteDone(onWriteDone)
{
}

CommitTask::~CommitTask() = default;

void
CommitTask::run()
{
    const auto &fields = _wc.getFields();
    for (auto &field : fields) {
        AttributeVector &attr = field.getAttribute();
        applyCommit(_serialNum, _onWriteDone, attr);
    }
}

}

void
AttributeWriter::setupWriteContexts()
{
    std::vector<FieldContext> fieldContexts;
    assert(_writeContexts.empty());
    for (auto attr : getWritableAttributes()) {
        fieldContexts.emplace_back(_attributeFieldWriter, attr);
    }
    std::sort(fieldContexts.begin(), fieldContexts.end());
    for (auto &fc : fieldContexts) {
        if (_writeContexts.empty() ||
            (_writeContexts.back().getExecutorId() != fc.getExecutorId())) {
            _writeContexts.emplace_back(fc.getExecutorId());
        }
        _writeContexts.back().add(*fc.getAttribute());
    }
    for (const auto &wc : _writeContexts) {
        if (wc.hasStructFieldAttribute()) {
            _hasStructFieldAttribute = true;
        }
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
                             bool immediateCommit, bool allAttributes, OnWriteDoneType onWriteDone)
{
    const DataType *dataType(doc.getDataType());
    if (_dataType != dataType) {
        buildFieldPaths(doc.getType(), dataType);
    }
    auto extractor = std::make_shared<DocumentFieldExtractor>(doc);
    for (const auto &wc : _writeContexts) {
        if (allAttributes || wc.hasStructFieldAttribute()) {
            auto putTask = std::make_unique<PutTask>(wc, serialNum, extractor, lid, immediateCommit, allAttributes, onWriteDone);
            _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(putTask));
        }
    }
}

void
AttributeWriter::internalRemove(SerialNum serialNum, DocumentIdT lid, bool immediateCommit,
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
      _writeContexts(),
      _dataType(nullptr),
      _hasStructFieldAttribute(false),
      _attrMap()
{
    setupWriteContexts();
    setupAttriuteMapping();
}

void AttributeWriter::setupAttriuteMapping() {
    for (auto attr : getWritableAttributes()) {
        vespalib::stringref name = attr->getName();
        _attrMap[name] = AttrWithId(attr, _attributeFieldWriter.getExecutorId(getPrefix(name)));
    }    
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
    LOG(spam, "Handle put: serial(%" PRIu64 "), docId(%s), lid(%u), document(%s)",
        serialNum, doc.getId().toString().c_str(), lid, doc.toString(true).c_str());
    internalPut(serialNum, doc, lid, immediateCommit, true, onWriteDone);
}

void
AttributeWriter::update(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                        bool immediateCommit, OnWriteDoneType onWriteDone)
{
    LOG(spam, "Handle update: serial(%" PRIu64 "), docId(%s), lid(%u), document(%s)",
        serialNum, doc.getId().toString().c_str(), lid, doc.toString(true).c_str());
    internalPut(serialNum, doc, lid, immediateCommit, false, onWriteDone);
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
    for (const auto &writeCtx : _writeContexts) {
        auto removeTask = std::make_unique<BatchRemoveTask>(writeCtx, serialNum, lidsToRemove, immediateCommit, onWriteDone);
        _attributeFieldWriter.executeTask(writeCtx.getExecutorId(), std::move(removeTask));
    }
}

void
AttributeWriter::update(SerialNum serialNum, const DocumentUpdate &upd, DocumentIdT lid,
                        bool immediateCommit, OnWriteDoneType onWriteDone, IFieldUpdateCallback & onUpdate)
{
    LOG(debug, "Inspecting update for document %d.", lid);
    std::vector<std::unique_ptr<BatchUpdateTask>> args;
    uint32_t numExecutors = _attributeFieldWriter.getNumExecutors();
    args.reserve(numExecutors);
    for (uint32_t i(0); i < numExecutors; i++) {
        args.emplace_back(std::make_unique<BatchUpdateTask>(serialNum, lid, immediateCommit));
        args.back()->_updates.reserve((2*upd.getUpdates().size())/numExecutors);
    }

    for (const auto &fupd : upd.getUpdates()) {
        LOG(debug, "Retrieving guard for attribute vector '%s'.", fupd.getField().getName().c_str());
        auto found = _attrMap.find(fupd.getField().getName());
        AttributeVector * attrp = (found != _attrMap.end()) ? found->second.first : nullptr;
        onUpdate.onUpdateField(fupd.getField().getName(), attrp);
        if (attrp == nullptr) {
            LOG(spam, "Failed to find attribute vector %s", fupd.getField().getName().c_str());
            continue;
        }
        // TODO: Check if we must use > due to multiple entries for same
        // document and attribute.
        if (attrp->getStatus().getLastSyncToken() >= serialNum)
            continue;
        args[found->second.second.getId()]->_updates.emplace_back(attrp, &fupd);
        LOG(debug, "About to apply update for docId %u in attribute vector '%s'.", lid, attrp->getName().c_str());
    }
    // NOTE: The lifetime of the field update will be ensured by keeping the document update alive
    // in a operation done context object.
    for (uint32_t id(0); id < args.size(); id++) {
        if ( ! args[id]->_updates.empty()) {
            args[id]->_onWriteDone = onWriteDone;
            _attributeFieldWriter.executeTask(ExecutorId(id), std::move(args[id]));
        }
    }

}

void
AttributeWriter::heartBeat(SerialNum serialNum)
{
    for (auto entry : _attrMap) {
        _attributeFieldWriter.execute(entry.second.second,
                                      [serialNum, attr=entry.second.first]()
                                      { applyHeartBeat(serialNum, *attr); });
    }
}


void
AttributeWriter::forceCommit(SerialNum serialNum, OnWriteDoneType onWriteDone)
{
    if (_mgr->getImportedAttributes() != nullptr) {
        std::vector<std::shared_ptr<ImportedAttributeVector>> importedAttrs;
        _mgr->getImportedAttributes()->getAll(importedAttrs);
        for (const auto &attr : importedAttrs) {
            attr->clearSearchCache();
        }
    }
    for (const auto &wc : _writeContexts) {
        auto commitTask = std::make_unique<CommitTask>(wc, serialNum, onWriteDone);
        _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(commitTask));
    }
}


void
AttributeWriter::onReplayDone(uint32_t docIdLimit)
{
    for (auto entry : _attrMap) {
        _attributeFieldWriter.execute(entry.second.second,
                                      [docIdLimit, attr = entry.second.first]()
                                      { applyReplayDone(docIdLimit, *attr); });
    }
    _attributeFieldWriter.sync();
}


void
AttributeWriter::compactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum)
{
    for (auto entry : _attrMap) {
        _attributeFieldWriter.
            execute(entry.second.second,
                    [wantedLidLimit, serialNum, attr=entry.second.first]()
                    { applyCompactLidSpace(wantedLidLimit, serialNum, *attr); });
    }
    _attributeFieldWriter.sync();
}

bool
AttributeWriter::hasStructFieldAttribute() const
{
    return _hasStructFieldAttribute;
}


} // namespace proton
