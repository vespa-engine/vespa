// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_writer.h"
#include "attributemanager.h"
#include "document_field_extractor.h"
#include "ifieldupdatecallback.h"
#include "imported_attributes_repo.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/searchcommon/attribute/attribute_utils.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcore/proton/common/attribute_updater.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/tensor/prepare_result.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <future>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_writer");

using namespace document;
using namespace search;

using ExecutorId = vespalib::ISequencedTaskExecutor::ExecutorId;
using search::attribute::ImportedAttributeVector;
using search::tensor::PrepareResult;
using vespalib::CpuUsage;
using vespalib::GateCallback;
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
AttributeWriter::WriteField::buildFieldPath(const DocumentType &docType) const
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
      _data_type(nullptr),
      _two_phase_put_field_path(),
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
AttributeWriter::WriteContext::consider_build_field_paths(const Document& doc) const
{
    auto data_type = doc.getDataType();
    if (_data_type != data_type) {
        _data_type = data_type;
        auto& doc_type = doc.getType();
        for (auto &field : _fields) {
            field.buildFieldPath(doc_type);
        }
        if (_use_two_phase_put) {
            _two_phase_put_field_path = std::make_shared<const FieldPath>(_fields[0].getFieldPath());
        }
    }
}

AttributeWriter::AttributeWithInfo::AttributeWithInfo()
    : attribute(),
      executor_id(),
      use_two_phase_put_for_assign_updates(false)
{
}

AttributeWriter::AttributeWithInfo::AttributeWithInfo(search::AttributeVector* attribute_in,
                                                      ExecutorId executor_id_in)
    : attribute(attribute_in),
      executor_id(executor_id_in),
      use_two_phase_put_for_assign_updates(use_two_phase_put_for_attribute(*attribute_in))
{
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
    attr.commitIfChangeVectorTooLarge();
}

class FieldValueAndPrepareResult {
    std::unique_ptr<const FieldValue> _field_value;
    std::unique_ptr<PrepareResult>    _prepare_result;
public:
    FieldValueAndPrepareResult(std::unique_ptr<const FieldValue> field_value,
                               std::unique_ptr<PrepareResult> prepare_result)
        : _field_value(std::move(field_value)),
          _prepare_result(std::move(prepare_result))
    {
    }
    FieldValueAndPrepareResult()
        : _field_value(),
          _prepare_result()
    {
    }
    ~FieldValueAndPrepareResult();
    FieldValueAndPrepareResult(FieldValueAndPrepareResult&&);
    const std::unique_ptr<const FieldValue>& get_field_value() const noexcept { return _field_value; }
    std::unique_ptr<PrepareResult> steal_prepare_result() { return std::move(_prepare_result); }
};

FieldValueAndPrepareResult::~FieldValueAndPrepareResult() = default;

FieldValueAndPrepareResult::FieldValueAndPrepareResult(FieldValueAndPrepareResult&&) = default;

void
complete_put_to_attribute(SerialNum serial_num,
                          uint32_t docid,
                          AttributeVector& attr,
                          std::future<FieldValueAndPrepareResult> result_future,
                          AttributeWriter::OnWriteDoneType)
{
    ensureLidSpace(serial_num, docid, attr);
    auto result = result_future.get();
    auto& field_value = result.get_field_value();
    if (field_value) {
        AttributeUpdater::complete_set_value(attr, docid, *field_value, result.steal_prepare_result());
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
    attr.commitIfChangeVectorTooLarge();
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
    attr.reclaim_unused_memory();
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
    if (attr.getStatus().getLastSyncToken() <= serialNum) {
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
    const Document&      _doc;
public:
    PutTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, const Document& doc, uint32_t lid, bool allAttributes, AttributeWriter::OnWriteDoneType onWriteDone);
    ~PutTask() override;
    void run() override;
};

PutTask::PutTask(const AttributeWriter::WriteContext &wc, SerialNum serialNum, const Document& doc, uint32_t lid, bool allAttributes, AttributeWriter::OnWriteDoneType onWriteDone)
    : _wc(wc),
      _serialNum(serialNum),
      _lid(lid),
      _allAttributes(allAttributes),
      _onWriteDone(onWriteDone),
      _doc(doc)
{
}

PutTask::~PutTask() = default;

void
PutTask::run()
{
    _wc.consider_build_field_paths(_doc);
    DocumentFieldExtractor field_extractor(_doc);
    const auto &fields = _wc.getFields();
    for (auto field : fields) {
        if (_allAttributes || field.isStructFieldAttribute()) {
            AttributeVector &attr = field.getAttribute();
            if (attr.getStatus().getLastSyncToken() < _serialNum) {
                auto fv = field_extractor.getFieldValue(field.getFieldPath());
                applyPutToAttribute(_serialNum, fv, _lid, attr, _onWriteDone);
            }
        }
    }
}

class PreparePutTask : public vespalib::Executor::Task {
private:
    const SerialNum _serial_num;
    const uint32_t _docid;
    AttributeVector& _attr;
    std::shared_ptr<const FieldPath> _field_path;
    const Document* const _doc;
    std::unique_ptr<FieldValue> _field_value;
    std::promise<FieldValueAndPrepareResult> _result_promise;

public:
    PreparePutTask(SerialNum serial_num,
                   uint32_t docid,
                   const AttributeWriter::WriteContext& wc,
                   const Document& doc);
    PreparePutTask(SerialNum serial_num,
                   uint32_t docid,
                   AttributeVector& attr,
                   const FieldValue& field_value);
    ~PreparePutTask() override;
    void run() override;
    SerialNum serial_num() const { return _serial_num; }
    uint32_t docid() const { return _docid; }
    AttributeVector& attr() { return _attr; }
    std::future<FieldValueAndPrepareResult> result_future() {
        return _result_promise.get_future();
    }
};

PreparePutTask::PreparePutTask(SerialNum serial_num,
                               uint32_t docid,
                               const AttributeWriter::WriteContext& wc,
                               const Document& doc)
    : _serial_num(serial_num),
      _docid(docid),
      _attr(wc.getFields()[0].getAttribute()),
      _field_path(wc.get_two_phase_put_field_path()),
      _doc(&doc),
      _field_value(),
      _result_promise()
{
}

PreparePutTask::PreparePutTask(SerialNum serial_num,
                               uint32_t docid,
                               AttributeVector& attr,
                               const FieldValue& field_value)
    : _serial_num(serial_num),
      _docid(docid),
      _attr(attr),
      _field_path(),
      _doc(nullptr),
      _field_value(field_value.clone()),
      _result_promise()
{
}

PreparePutTask::~PreparePutTask() = default;

void
PreparePutTask::run()
{
    if (_attr.getStatus().getLastSyncToken() < _serial_num) {
        if (_field_path) {
            DocumentFieldExtractor field_extractor(*_doc);
            _field_value = field_extractor.getFieldValue(*_field_path);
        }
        if (_field_value.get()) {
            auto& fv = *_field_value;
            _result_promise.set_value(FieldValueAndPrepareResult(std::move(_field_value), AttributeUpdater::prepare_set_value(_attr, _docid, fv)));
        } else {
            _result_promise.set_value(FieldValueAndPrepareResult());
        }
    }
}

class CompletePutTask : public vespalib::Executor::Task {
private:
    const SerialNum _serial_num;
    const uint32_t _docid;
    AttributeVector& _attr;
    std::future<FieldValueAndPrepareResult> _result_future;
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
      _result_future(prepare_task.result_future()),
      _on_write_done(on_write_done)
{
}

CompletePutTask::~CompletePutTask() = default;

void
CompletePutTask::run()
{
    if (_attr.getStatus().getLastSyncToken() < _serial_num) {
        complete_put_to_attribute(_serial_num, _docid, _attr, std::move(_result_future), _on_write_done);
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
AttributeWriter::internalPut(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                             bool allAttributes, OnWriteDoneType onWriteDone)
{
    for (const auto &wc : _writeContexts) {
        if (wc.use_two_phase_put()) {
            assert(wc.getFields().size() == 1);
            wc.consider_build_field_paths(doc);
            auto prepare_task = std::make_unique<PreparePutTask>(serialNum, lid, wc, doc);
            auto complete_task = std::make_unique<CompletePutTask>(*prepare_task, onWriteDone);
            _shared_executor.execute(CpuUsage::wrap(std::move(prepare_task), CpuUsage::Category::WRITE));
            _attributeFieldWriter.executeTask(wc.getExecutorId(), std::move(complete_task));
        } else {
            if (allAttributes || wc.hasStructFieldAttribute()) {
                auto putTask = std::make_unique<PutTask>(wc, serialNum, doc, lid, allAttributes, onWriteDone);
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
      _hasStructFieldAttribute(false),
      _attrMap()
{
    setupWriteContexts();
    setupAttributeMapping();
}

void AttributeWriter::setupAttributeMapping() {
    for (auto attr : getWritableAttributes()) {
        vespalib::stringref name = attr->getName();
        _attrMap[name] = AttributeWithInfo(attr, _attributeFieldWriter.getExecutorIdFromName(attr->getNamePrefix()));
    }    
}


AttributeWriter::~AttributeWriter() {
    vespalib::Gate gate;
    drain(std::make_shared<vespalib::GateCallback>(gate));
    gate.await();
}

void
AttributeWriter::drain(OnWriteDoneType onDone) {

    for (const auto &wc : _writeContexts) {
        _attributeFieldWriter.executeLambda(wc.getExecutorId(), [onDone] () { (void) onDone; });
    }
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

namespace {

bool
is_single_assign_update(const FieldUpdate& update)
{
    return (update.getUpdates().size() == 1) &&
            (update.getUpdates()[0]->getType() == ValueUpdate::Assign) &&
            (static_cast<const AssignValueUpdate &>(*update.getUpdates()[0]).hasValue());
}

const FieldValue&
get_single_assign_update_field_value(const FieldUpdate& update)
{
    const auto& assign = static_cast<const AssignValueUpdate &>(*update.getUpdates()[0]);
    return assign.getValue();
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
        AttributeVector * attrp = (found != _attrMap.end()) ? found->second.attribute : nullptr;
        onUpdate.onUpdateField(fupd.getField(), attrp);
        if (__builtin_expect(attrp == nullptr, false)) {
            LOG(spam, "Failed to find attribute vector %s", fupd.getField().getName().data());
            continue;
        }
        // TODO: Check if we must use > due to multiple entries for same
        // document and attribute.
        if (__builtin_expect(attrp->getStatus().getLastSyncToken() >= serialNum, false)) {
            continue;
        }
        if (found->second.use_two_phase_put_for_assign_updates && is_single_assign_update(fupd)) {
            auto prepare_task = std::make_unique<PreparePutTask>(serialNum, lid, *attrp, get_single_assign_update_field_value(fupd));
            auto complete_task = std::make_unique<CompletePutTask>(*prepare_task, onWriteDone);
            LOG(debug, "About to handle assign update as two phase put for docid %u in attribute vector '%s'",
                lid, attrp->getName().c_str());
            _shared_executor.execute(CpuUsage::wrap(std::move(prepare_task), CpuUsage::Category::WRITE));
            _attributeFieldWriter.executeTask(found->second.executor_id, std::move(complete_task));
        } else {
            args[found->second.executor_id.getId()]->_updates.emplace_back(attrp, &fupd);
            LOG(debug, "About to apply update for docId %u in attribute vector '%s'.", lid, attrp->getName().c_str());
        }
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
AttributeWriter::heartBeat(SerialNum serialNum, OnWriteDoneType onDone)
{
    for (auto entry : _attrMap) {
        _attributeFieldWriter.execute(entry.second.executor_id,[serialNum, attr=entry.second.attribute, onDone]() {
            (void) onDone;
            applyHeartBeat(serialNum, *attr);
        });
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
    vespalib::Gate gate;
    {
        auto on_write_done = std::make_shared<GateCallback>(gate);
        for (auto entry : _attrMap) {
            _attributeFieldWriter.execute(entry.second.executor_id,
                                          [docIdLimit, attr = entry.second.attribute, on_write_done]()
                                          {
                                              (void) on_write_done;
                                              applyReplayDone(docIdLimit, *attr);
                                          });
        }
    }
    gate.await();
}


void
AttributeWriter::compactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum)
{
    vespalib::Gate gate;
    {
        auto on_write_done = std::make_shared<GateCallback>(gate);
        for (auto entry : _attrMap) {
            _attributeFieldWriter.execute(entry.second.executor_id,
                                          [wantedLidLimit, serialNum, attr=entry.second.attribute, on_write_done]()
                                          {
                                              (void) on_write_done;
                                              applyCompactLidSpace(wantedLidLimit, serialNum, *attr);
                                          });
        }
    }
    gate.await();
}

bool
AttributeWriter::hasStructFieldAttribute() const
{
    return _hasStructFieldAttribute;
}


} // namespace proton
