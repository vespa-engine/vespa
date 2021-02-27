// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_writer.h"
#include "attributemanager.h"
#include "document_field_extractor.h"
#include "ifieldupdatecallback.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/common/attribute_updater.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/searchlib/tensor/prepare_result.h>
#include <vespa/searchcommon/attribute/attribute_utils.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/threadexecutor.h>
#include <future>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_writer");

using namespace document;
using namespace search;

using ExecutorId = vespalib::ISequencedTaskExecutor::ExecutorId;
using search::attribute::ImportedAttributeVector;
using search::tensor::PrepareResult;
using vespalib::ISequencedTaskExecutor;

namespace proton {

using LidVector = LidVectorContext::LidVector;

namespace {

bool
use_two_phase_put_for_attribute(const AttributeVector& attr)
{
    const auto& cfg = attr.getConfig();
    if (cfg.basicType() == search::attribute::BasicType::Type::TENSOR &&
        cfg.hnsw_index_params().has_value() &&
        cfg.hnsw_index_params().value().multi_threaded_indexing())
    {
        return true;
    }
    return false;
}

}

AttributeWriter::WriteField::WriteField(AttributeVector &attribute)
    : _fieldPath(),
      _attribute(attribute),
      _structFieldAttribute(false),
      _use_two_phase_put(use_two_phase_put_for_attribute(attribute))
{
    const vespalib::string &name = attribute.getName();
    _structFieldAttribute = search::attribute::isStructFieldAttribute(name);
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

AttributeWriter::WriteContext::WriteContext(ExecutorId executorId) noexcept
    : _executorId(executorId),
      _fields(),
      _hasStructFieldAttribute(false),
      _use_two_phase_put(false)
{
}

AttributeWriter::WriteContext::WriteContext(WriteContext &&rhs) noexcept = default;

AttributeWriter::WriteContext::~WriteContext() = default;

AttributeWriter::WriteContext &AttributeWriter::WriteContext::operator=(WriteContext &&rhs) noexcept = default;

void
AttributeWriter::WriteContext::add(AttributeVector &attr)
{
    _fields.emplace_back(attr);
    if (_fields.back().isStructFieldAttribute()) {
        _hasStructFieldAttribute = true;
    }
    if (_fields.back().use_two_phase_put()) {
        // Only support for one field per context when this is true.
        assert(_fields.size() == 1);
        _use_two_phase_put = true;
    } else {
        assert(!_use_two_phase_put);
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
                    AttributeVector &attr, AttributeWriter::OnWriteDoneType)
{
    ensureLidSpace(serialNum, lid, attr);
    if (fieldValue.get()) {
        AttributeUpdater::handleValue(attr, lid, *fieldValue);
    } else {
        attr.clearDoc(lid);
    }
}

void
complete_put_to_attribute(SerialNum serial_num,
                          uint32_t docid,
                          AttributeVector& attr,
                          const FieldValue::SP& field_value,
                          std::future<std::unique_ptr<PrepareResult>>& result_future,
                          AttributeWriter::OnWriteDoneType)
{
    ensureLidSpace(serial_num, docid, attr);
    if (field_value.get()) {
        auto result = result_future.get();
        AttributeUpdater::complete_set_value(attr, docid, *field_value, std::move(result));
    } else {
        attr.clearDoc(docid);
    }
}

void
applyRemoveToAttribute(SerialNum serialNum, DocumentIdT lid,
                       AttributeVector &attr, AttributeWriter::OnWriteDoneType)
{
    ensureLidSpace(serialNum, lid, attr);
    attr.clearDoc(lid);
}

void
applyUpdateToAttribute(SerialNum serialNum, const FieldUpdate &fieldUpd,
                       DocumentIdT lid, AttributeVector &attr)
{
    ensureLidSpace(serialNum, lid, attr);
    AttributeUpdater::handleUpdate(attr, lid, fieldUpd);
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
        attr.commit(search::CommitParam(serialNum));
    }
}

void
applyCommit(CommitParam param, AttributeWriter::OnWriteDoneType , AttributeVector &attr)
{
    SerialNum serialNum = param.lastSerialNum();
    if (attr.getStatus().getLastSyncToken() <= serialNum) {
        if (serialNum > attr.getCreateSerialNum()) {
            attr.commit(param);
        } else {
            attr.commit(param.forceUpdateStats());
        }
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
        attr.commit(false);
        if (wantedLidLimit <= attr.getCommittedDocIdLimit()) {
            attr.compactLidSpace(wantedLidLimit);
        }
        attr.commit(CommitParam(serialNum));
    }
}

using AttrUpdates = std::vector<std::pair<AttributeVector *, const FieldUpdate *>>;

struct BatchUpdateTask : public vespalib::Executor::Task {

    BatchUpdateTask(SerialNum serialNum, DocumentIdT lid)
        : vespalib::Executor::Task(),
          _serialNum(serialNum),
          _lid(lid),
          _onWriteDone()
    { }
    ~BatchUpdateTask() override;

    void run() override {
        for (const auto & update : _updates) {
            applyUpdateToAttribute(_serialNum, *update.second, _lid, *update.first);
        }
    }

    SerialNum                        _serialNum;
    DocumentIdT                      _lid;
    AttrUpdates                      _updates;
    vespalib::IDestructorCallback::SP  _onWriteDone;
};

BatchUpdateTask::~BatchUpdateTask() = default;

class FieldContext
{
    vespalib::string   _name;
    ExecutorId         _executorId;
    AttributeVector   *_attr;
    bool               _use_two_phase_put;

public:
    FieldContext(ISequencedTaskExecutor &writer, AttributeVector *attr);
    ~FieldContext();
    bool operator<(const FieldContext &rhs) const;
    ExecutorId getExecutorId() const { return _executorId; }
    AttributeVector *getAttribute() const { return _attr; }
    bool use_two_phase_put() const { return _use_two_phase_put; }
};

FieldContext::FieldContext(ISequencedTaskExecutor &writer, AttributeVector *attr)
    :  _name(attr->getName()),
       _executorId(writer.getExecutorIdFromName(attr->getNamePrefix())),
       _attr(attr),
       _use_two_phase_put(use_two_phase_put_for_attribute(*attr))
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
    const bool           _allAttributes;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
    std::shared_ptr<DocumentFieldExtractor> _fieldExtractor;
    std::vector<FieldValue::UP> _fieldValues;
public:
    PutTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, std::shared_ptr<DocumentFieldExtractor> fieldExtractor, uint32_t lid, bool allAttributes, AttributeWriter::OnWriteDoneType onWriteDone);
    ~PutTask() override;
    void run() override;
};

PutTask::PutTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, std::shared_ptr<DocumentFieldExtractor> fieldExtractor, uint32_t lid, bool allAttributes, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _serialNum(serialNum),
      _lid(lid),
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
                applyPutToAttribute(_serialNum, _fieldValues[fieldId], _lid, attr, _onWriteDone);
            }
            ++fieldId;
        }
    }
}


class PreparePutTask : public vespalib::Executor::Task {
private:
    const SerialNum _serial_num;
    const uint32_t _docid;
    AttributeVector& _attr;
    FieldValue::SP _field_value;
    std::promise<std::unique_ptr<PrepareResult>> _result_promise;

public:
    PreparePutTask(SerialNum serial_num_in,
                   uint32_t docid_in,
                   const AttributeWriter::WriteField& field,
                   std::shared_ptr<DocumentFieldExtractor> field_extractor);
    ~PreparePutTask() override;
    void run() override;
    SerialNum serial_num() const { return _serial_num; }
    uint32_t docid() const { return _docid; }
    AttributeVector& attr() { return _attr; }
    FieldValue::SP field_value() { return _field_value; }
    std::future<std::unique_ptr<PrepareResult>> result_future() {
        return _result_promise.get_future();
    }
};

PreparePutTask::PreparePutTask(SerialNum serial_num_in,
                               uint32_t docid_in,
                               const AttributeWriter::WriteField& field,
                               std::shared_ptr<DocumentFieldExtractor> field_extractor)
    : _serial_num(serial_num_in),
      _docid(docid_in),
      _attr(field.getAttribute()),
      _field_value(),
      _result_promise()
{
    // Note: No need to store the field extractor as we are not extracting struct fields.
    auto value = field_extractor->getFieldValue(field.getFieldPath());
    _field_value.reset(value.release());
}

PreparePutTask::~PreparePutTask() = default;

void
PreparePutTask::run()
{
    if (_attr.getStatus().getLastSyncToken() < _serial_num) {
        if (_field_value.get()) {
            _result_promise.set_value(AttributeUpdater::prepare_set_value(_attr, _docid, *_field_value));
        }
    }
}

class CompletePutTask : public vespalib::Executor::Task {
private:
    const SerialNum _serial_num;
    const uint32_t _docid;
    AttributeVector& _attr;
    FieldValue::SP _field_value;
    std::future<std::unique_ptr<PrepareResult>> _result_future;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _on_write_done;

public:
    CompletePutTask(PreparePutTask& prepare_task,
                    AttributeWriter::OnWriteDoneType on_write_done);
    ~CompletePutTask() override;
    void run() override;
};

CompletePutTask::CompletePutTask(PreparePutTask& prepare_task,
                                 AttributeWriter::OnWriteDoneType on_write_done)
    : _serial_num(prepare_task.serial_num()),
      _docid(prepare_task.docid()),
      _attr(prepare_task.attr()),
      _field_value(prepare_task.field_value()),
      _result_future(prepare_task.result_future()),
      _on_write_done(on_write_done)
{
}

CompletePutTask::~CompletePutTask() = default;

void
CompletePutTask::run()
{
    if (_attr.getStatus().getLastSyncToken() < _serial_num) {
        complete_put_to_attribute(_serial_num, _docid, _attr, _field_value, _result_future, _on_write_done);
    }
}

class RemoveTask : public vespalib::Executor::Task
{
    const AttributeWriter::WriteContext  &_wc;
    const SerialNum      _serialNum;
    const uint32_t       _lid;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
public:
    RemoveTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, uint32_t lid, AttributeWriter::OnWriteDoneType onWriteDone);
    ~RemoveTask() override;
    void run() override;
};

RemoveTask::RemoveTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, uint32_t lid, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _serialNum(serialNum),
      _lid(lid),
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
            applyRemoveToAttribute(_serialNum, _lid, attr, _onWriteDone);
        }
    }
}

class BatchRemoveTask : public vespalib::Executor::Task
{
private:
    const AttributeWriter::WriteContext &_writeCtx;
    const SerialNum _serialNum;
    const LidVector _lidsToRemove;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
public:
    BatchRemoveTask(const AttributeWriter::WriteContext &writeCtx,
                    SerialNum serialNum,
                    const LidVector &lidsToRemove,
                    AttributeWriter::OnWriteDoneType onWriteDone)
        : _writeCtx(writeCtx),
          _serialNum(serialNum),
          _lidsToRemove(lidsToRemove),
          _onWriteDone(onWriteDone)
    {}
    ~BatchRemoveTask() override;
    void run() override {
        for (auto field : _writeCtx.getFields()) {
            auto &attr = field.getAttribute();
            if (attr.getStatus().getLastSyncToken() < _serialNum) {
                for (auto lidToRemove : _lidsToRemove) {
                    applyRemoveToAttribute(_serialNum, lidToRemove, attr, _onWriteDone);
                }
            }
        }
    }
};

BatchRemoveTask::~BatchRemoveTask() = default;

class CommitTask : public vespalib::Executor::Task
{
    const AttributeWriter::WriteContext  &_wc;
    const CommitParam                     _param;
    std::remove_reference_t<AttributeWriter::OnWriteDoneType> _onWriteDone;
public:
    CommitTask(const AttributeWriter::WriteContext &wc, CommitParam param, AttributeWriter::OnWriteDoneType onWriteDone);
    ~CommitTask() override;
    void run() override;
};


CommitTask::CommitTask(const AttributeWriter::WriteContext &wc, CommitParam param, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _param(param),
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
        applyCommit(_param, _onWriteDone, attr);
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
    for (const auto& fc : fieldContexts) {
        if (fc.use_two_phase_put()) {
            continue;
        }
        if (_writeContexts.empty() ||
            (_writeContexts.back().getExecutorId() != fc.getExecutorId())) {
            _writeContexts.emplace_back(fc.getExecutorId());
        }
        _writeContexts.back().add(*fc.getAttribute());
    }
    for (const auto& fc : fieldContexts) {
        if (fc.use_two_phase_put()) {
            _writeContexts.emplace_back(fc.getExecutorId());
            _writeContexts.back().add(*fc.getAttribute());
        }
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
                             bool allAttributes, OnWriteDoneType onWriteDone)
{
    const DataType *dataType(doc.getDataType());
    if (_dataType != dataType) {
        buildFieldPaths(doc.getType(), dataType);
    }
    auto extractor = std::make_shared<DocumentFieldExtractor>(doc);
    for (const auto &wc : _writeContexts) {
        if (wc.use_two_phase_put()) {
            assert(wc.getFields().size() == 1);
            auto prepare_task = std::make_unique<PreparePutTask>(serialNum, lid, wc.getFields()[0], extractor);
            auto complete_task = std::make_unique<CompletePutTask>(*prepare_task, onWriteDone);
            _shared_executor.execute(std::move(prepare_task));
            _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(complete_task));
        } else {
            if (allAttributes || wc.hasStructFieldAttribute()) {
                auto putTask = std::make_unique<PutTask>(wc, serialNum, extractor, lid, allAttributes, onWriteDone);
                _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(putTask));
            }
        }
    }
}

void
AttributeWriter::internalRemove(SerialNum serialNum, DocumentIdT lid, OnWriteDoneType onWriteDone)
{
    for (const auto &wc : _writeContexts) {
        auto removeTask = std::make_unique<RemoveTask>(wc, serialNum, lid, onWriteDone);
        _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(removeTask));
    }
}

AttributeWriter::AttributeWriter(proton::IAttributeManager::SP mgr)
    : _mgr(std::move(mgr)),
      _attributeFieldWriter(_mgr->getAttributeFieldWriter()),
      _shared_executor(_mgr->get_shared_executor()),
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
        _attrMap[name] = AttrWithId(attr, _attributeFieldWriter.getExecutorIdFromName(attr->getNamePrefix()));
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
AttributeWriter::put(SerialNum serialNum, const Document &doc, DocumentIdT lid, OnWriteDoneType onWriteDone)
{
    LOG(spam, "Handle put: serial(%" PRIu64 "), docId(%s), lid(%u), document(%s)",
        serialNum, doc.getId().toString().c_str(), lid, doc.toString(true).c_str());
    internalPut(serialNum, doc, lid, true, onWriteDone);
}

void
AttributeWriter::update(SerialNum serialNum, const Document &doc, DocumentIdT lid, OnWriteDoneType onWriteDone)
{
    LOG(spam, "Handle update: serial(%" PRIu64 "), docId(%s), lid(%u), document(%s)",
        serialNum, doc.getId().toString().c_str(), lid, doc.toString(true).c_str());
    internalPut(serialNum, doc, lid, false, onWriteDone);
}

void
AttributeWriter::remove(SerialNum serialNum, DocumentIdT lid, OnWriteDoneType onWriteDone)
{
    internalRemove(serialNum, lid, onWriteDone);
}

void
AttributeWriter::remove(const LidVector &lidsToRemove, SerialNum serialNum, OnWriteDoneType onWriteDone)
{
    for (const auto &writeCtx : _writeContexts) {
        auto removeTask = std::make_unique<BatchRemoveTask>(writeCtx, serialNum, lidsToRemove, onWriteDone);
        _attributeFieldWriter.executeTask(writeCtx.getExecutorId(), std::move(removeTask));
    }
}

void
AttributeWriter::update(SerialNum serialNum, const DocumentUpdate &upd, DocumentIdT lid,
                        OnWriteDoneType onWriteDone, IFieldUpdateCallback & onUpdate)
{
    LOG(debug, "Inspecting update for document %d.", lid);
    std::vector<std::unique_ptr<BatchUpdateTask>> args;
    uint32_t numExecutors = _attributeFieldWriter.getNumExecutors();
    args.reserve(numExecutors);
    for (uint32_t i(0); i < numExecutors; i++) {
        args.emplace_back(std::make_unique<BatchUpdateTask>(serialNum, lid));
        args.back()->_updates.reserve((2*upd.getUpdates().size())/numExecutors);
    }

    for (const auto &fupd : upd.getUpdates()) {
        LOG(debug, "Retrieving guard for attribute vector '%s'.", fupd.getField().getName().data());
        auto found = _attrMap.find(fupd.getField().getName());
        AttributeVector * attrp = (found != _attrMap.end()) ? found->second.first : nullptr;
        onUpdate.onUpdateField(fupd.getField().getName(), attrp);
        if (__builtin_expect(attrp == nullptr, false)) {
            LOG(spam, "Failed to find attribute vector %s", fupd.getField().getName().data());
            continue;
        }
        // TODO: Check if we must use > due to multiple entries for same
        // document and attribute.
        if (__builtin_expect(attrp->getStatus().getLastSyncToken() >= serialNum, false))
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
AttributeWriter::forceCommit(const CommitParam & param, OnWriteDoneType onWriteDone)
{
    if (_mgr->getImportedAttributes() != nullptr) {
        std::vector<std::shared_ptr<ImportedAttributeVector>> importedAttrs;
        _mgr->getImportedAttributes()->getAll(importedAttrs);
        for (const auto &attr : importedAttrs) {
            attr->clearSearchCache();
        }
    }
    for (const auto &wc : _writeContexts) {
        auto commitTask = std::make_unique<CommitTask>(wc, param, onWriteDone);
        _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(commitTask));
    }
    _attributeFieldWriter.wakeup();
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
        _attributeFieldWriter.execute(entry.second.second,
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
