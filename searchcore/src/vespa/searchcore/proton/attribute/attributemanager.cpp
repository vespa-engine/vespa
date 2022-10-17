// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributemanager.h"
#include "attribute_directory.h"
#include "attribute_factory.h"
#include "attribute_type_matcher.h"
#include "attributedisklayout.h"
#include "flushableattribute.h"
#include "imported_attributes_context.h"
#include "imported_attributes_repo.h"
#include "sequential_attributes_initializer.h"
#include <vespa/searchcommon/attribute/i_attribute_functor.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/searchlib/common/threaded_compactable_lid_space.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/threadexecutor.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attributemanager");

using search::AttributeContext;
using search::AttributeGuard;
using search::AttributeVector;
using search::common::ThreadedCompactableLidSpace;
using search::TuneFileAttributes;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::common::FileHeaderContext;
using search::attribute::BasicType;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

bool matchingTypes(const AttributeVector::SP &av, const search::attribute::Config &newConfig) {
    if (av) {
        AttributeTypeMatcher matching_types;
        return matching_types(av->getConfig(), newConfig);
    } else {
        return false;
    }
}

search::SerialNum estimateShrinkSerialNum(const AttributeVector &attr)
{
    search::SerialNum serialNum = attr.getCreateSerialNum();
    if (serialNum > 0) {
        --serialNum;
    }
    return std::max(attr.getStatus().getLastSyncToken(), serialNum);
}

std::shared_ptr<ShrinkLidSpaceFlushTarget>
allocShrinker(const AttributeVector::SP &attr, vespalib::ISequencedTaskExecutor & executor, AttributeDiskLayout &diskLayout)
{
    using Type = IFlushTarget::Type;
    using Component = IFlushTarget::Component;

    auto shrinkwrap = std::make_shared<ThreadedCompactableLidSpace>(attr, executor,
                                                                    executor.getExecutorIdFromName(attr->getNamePrefix()));
    const vespalib::string &name = attr->getName();
    auto dir = diskLayout.createAttributeDir(name);
    search::SerialNum shrinkSerialNum = estimateShrinkSerialNum(*attr);
    return std::make_shared<ShrinkLidSpaceFlushTarget>("attribute.shrink." + name, Type::GC, Component::ATTRIBUTE, shrinkSerialNum, dir->getLastFlushTime(), shrinkwrap);
}

}

AttributeManager::AttributeWrap::AttributeWrap(AttributeVectorSP a, bool isExtra_)
    : _attr(std::move(a)),
      _isExtra(isExtra_)
{
}

AttributeManager::AttributeWrap::AttributeWrap()
    : _attr(),
      _isExtra(false)
{
}

AttributeManager::AttributeWrap::~AttributeWrap() = default;

AttributeManager::AttributeWrap
AttributeManager::AttributeWrap::extraAttribute(AttributeVectorSP a)
{
    return {std::move(a), true};
}

AttributeManager::AttributeWrap
AttributeManager::AttributeWrap::normalAttribute(AttributeVectorSP a)
{
    return {std::move(a), false};
}

AttributeManager::FlushableWrap::FlushableWrap()
    : _flusher(),
      _shrinker()
{
}

AttributeManager::FlushableWrap::FlushableWrap(FlushableAttributeSP flusher, ShrinkerSP shrinker)
    : _flusher(std::move(flusher)),
      _shrinker(std::move(shrinker))
{
}

AttributeManager::FlushableWrap::~FlushableWrap() = default;

AttributeVector::SP
AttributeManager::internalAddAttribute(AttributeSpec && spec,
                                       uint64_t serialNum,
                                       const IAttributeFactory &factory)
{
    vespalib::string name = spec.getName();
    AttributeInitializer initializer(_diskLayout->createAttributeDir(name), _documentSubDbName, std::move(spec), serialNum, factory, _shared_executor);
    AttributeInitializerResult result = initializer.init();
    if (result) {
        result.getAttribute()->setInterlock(_interlock);
        auto shrinker = allocShrinker(result.getAttribute(), _attributeFieldWriter, *_diskLayout);
        addAttribute(AttributeWrap::normalAttribute(result.getAttribute()), shrinker);
    }
    return result.getAttribute();
}

void
AttributeManager::addAttribute(AttributeWrap attributeWrap, const ShrinkerSP &shrinker)
{
    AttributeVector::SP attribute = attributeWrap.getAttribute();
    bool isExtra = attributeWrap.isExtra();
    const vespalib::string &name = attribute->getName();
    LOG(debug, "Adding attribute vector '%s'", name.c_str());
    _attributes[name] = std::move(attributeWrap);
    assert(attribute->getInterlock() == _interlock);
    if ( ! isExtra ) {
        // Flushing of extra attributes is handled elsewhere
        AttributeVector * attributeP = attribute.get();
        auto flusher = std::make_shared<FlushableAttribute>(std::move(attribute), _diskLayout->createAttributeDir(name), _tuneFileAttributes, _fileHeaderContext, _attributeFieldWriter, _hwInfo);
        _flushables[name] = FlushableWrap(flusher, shrinker);
        _writableAttributes.push_back(attributeP);
    }
}

AttributeVector::SP
AttributeManager::findAttribute(const vespalib::string &name) const
{
    auto itr = _attributes.find(name);
    return (itr != _attributes.end())
        ? itr->second.getAttribute()
        : AttributeVector::SP();
}

const AttributeManager::FlushableWrap *
AttributeManager::findFlushable(const vespalib::string &name) const
{
    auto itr = _flushables.find(name);
    return (itr != _flushables.end()) ? &itr->second : nullptr;
}

AttributeCollectionSpec::AttributeList
AttributeManager::transferExistingAttributes(const AttributeManager &currMgr,
                                             Spec::AttributeList && newAttributes)
{
    Spec::AttributeList toBeAdded;
    vespalib::Gate gate;
    {
        auto gateCallback = std::make_shared<vespalib::GateCallback>(gate);
        for (auto &aspec: newAttributes) {
            AttributeVector::SP av = currMgr.findAttribute(aspec.getName());
            if (matchingTypes(av, aspec.getConfig())) { // transfer attribute
                LOG(debug,
                    "Transferring attribute vector '%s' with %u docs and serial number %" PRIu64 " from current manager",
                    av->getName().c_str(), av->getNumDocs(), av->getStatus().getLastSyncToken());
                auto wrap = currMgr.findFlushable(aspec.getName());
                assert(wrap != nullptr);
                auto shrinker = wrap->getShrinker();
                assert(shrinker);
                addAttribute(AttributeWrap::normalAttribute(av), shrinker);
                auto id = _attributeFieldWriter.getExecutorIdFromName(av->getNamePrefix());
                auto cfg = aspec.getConfig();
                _attributeFieldWriter.execute(id, [av, cfg, gateCallback]() {
                    (void) gateCallback;
                    av->update_config(cfg);
                });
            } else {
                toBeAdded.push_back(std::move(aspec));
            }
        }
    }
    gate.await();
    return toBeAdded;
}

void
AttributeManager::addNewAttributes(const Spec &newSpec,
                                   Spec::AttributeList && toBeAdded,
                                   IAttributeInitializerRegistry &initializerRegistry)
{
    for (auto &aspec : toBeAdded) {
        LOG(debug, "Creating initializer for attribute vector '%s': docIdLimit=%u, serialNumber=%" PRIu64,
                   aspec.getName().c_str(), newSpec.getDocIdLimit(), newSpec.getCurrentSerialNum());

        auto initializer = std::make_unique<AttributeInitializer>(_diskLayout->createAttributeDir(aspec.getName()),
                                                                  _documentSubDbName, std::move(aspec), newSpec.getCurrentSerialNum(),
                                                                  *_factory, _shared_executor);
        initializerRegistry.add(std::move(initializer));

        // TODO: Might want to use hardlinks to make attribute vector
        // appear to have been flushed at resurrect time, eliminating
        // flushDone serials going backwards in document db, and allowing
        // for pruning of transaction log up to the resurrect serial
        // without having to reflush the resurrected attribute vector.

        // XXX: Need to wash attribute at resurrection time to get rid of
        // ghost values (lid freed and not reused), foreign values
        // (lid freed and reused by another document) and stale values
        // (lid still used by newer versions of the same document).
    }

}

void
AttributeManager::transferExtraAttributes(const AttributeManager &currMgr)
{
    for (const auto &kv : currMgr._attributes) {
        if (kv.second.isExtra()) {
            addAttribute(kv.second, nullptr);
        }
    }
}

AttributeManager::AttributeManager(const vespalib::string &baseDir,
                                   const vespalib::string &documentSubDbName,
                                   const TuneFileAttributes &tuneFileAttributes,
                                   const FileHeaderContext &fileHeaderContext,
                                   std::shared_ptr<search::attribute::Interlock> interlock,
                                   vespalib::ISequencedTaskExecutor &attributeFieldWriter,
                                   vespalib::Executor& shared_executor,
                                   const HwInfo &hwInfo)
    : proton::IAttributeManager(),
      _attributes(),
      _flushables(),
      _writableAttributes(),
      _diskLayout(AttributeDiskLayout::create(baseDir)),
      _documentSubDbName(documentSubDbName),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _factory(std::make_shared<AttributeFactory>()),
      _interlock(std::move(interlock)),
      _attributeFieldWriter(attributeFieldWriter),
      _shared_executor(shared_executor),
      _hwInfo(hwInfo),
      _importedAttributes()
{
}

AttributeManager::AttributeManager(const vespalib::string &baseDir,
                                   const vespalib::string &documentSubDbName,
                                   const search::TuneFileAttributes &tuneFileAttributes,
                                   const search::common::FileHeaderContext &fileHeaderContext,
                                   std::shared_ptr<search::attribute::Interlock> interlock,
                                   vespalib::ISequencedTaskExecutor &attributeFieldWriter,
                                   vespalib::Executor& shared_executor,
                                   IAttributeFactory::SP factory,
                                   const HwInfo &hwInfo)
    : proton::IAttributeManager(),
      _attributes(),
      _flushables(),
      _writableAttributes(),
      _diskLayout(AttributeDiskLayout::create(baseDir)),
      _documentSubDbName(documentSubDbName),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _factory(std::move(factory)),
      _interlock(std::move(interlock)),
      _attributeFieldWriter(attributeFieldWriter),
      _shared_executor(shared_executor),
      _hwInfo(hwInfo),
      _importedAttributes()
{
}

AttributeManager::AttributeManager(const AttributeManager &currMgr,
                                   Spec && newSpec,
                                   IAttributeInitializerRegistry &initializerRegistry)
    : proton::IAttributeManager(),
      _attributes(),
      _flushables(),
      _writableAttributes(),
      _diskLayout(currMgr._diskLayout),
      _documentSubDbName(currMgr._documentSubDbName),
      _tuneFileAttributes(currMgr._tuneFileAttributes),
      _fileHeaderContext(currMgr._fileHeaderContext),
      _factory(currMgr._factory),
      _interlock(currMgr._interlock),
      _attributeFieldWriter(currMgr._attributeFieldWriter),
      _shared_executor(currMgr._shared_executor),
      _hwInfo(currMgr._hwInfo),
      _importedAttributes()
{
    Spec::AttributeList toBeAdded = transferExistingAttributes(currMgr, newSpec.stealAttributes());
    addNewAttributes(newSpec, std::move(toBeAdded), initializerRegistry);
    transferExtraAttributes(currMgr);
}

AttributeManager::~AttributeManager() = default;

AttributeVector::SP
AttributeManager::addAttribute(AttributeSpec && spec, uint64_t serialNum)
{
    return internalAddAttribute(std::move(spec), serialNum, *_factory);
}

void
AttributeManager::addInitializedAttributes(const std::vector<AttributeInitializerResult> &attributes)
{
    for (const auto &result : attributes) {
        assert(result);
        auto attr = result.getAttribute();
        attr->setInterlock(_interlock);
        auto shrinker = allocShrinker(attr, _attributeFieldWriter, *_diskLayout);
        addAttribute(AttributeWrap::normalAttribute(std::move(attr)), shrinker);
    }
}

void
AttributeManager::addExtraAttribute(const AttributeVector::SP &attribute)
{
    attribute->setInterlock(_interlock);
    addAttribute(AttributeWrap::extraAttribute(attribute), ShrinkerSP());
}

void
AttributeManager::flushAll(SerialNum currentSerial)
{
    auto flushTargets = getFlushTargets();
    for (const auto &ft : flushTargets) {
        vespalib::Executor::Task::UP task;
        task = ft->initFlush(currentSerial, std::make_shared<search::FlushToken>());
        if (task) {
            task->run();
        }
    }
}

FlushableAttribute::SP
AttributeManager::getFlushable(const vespalib::string &name)
{
    auto wrap = findFlushable(name);
    return ((wrap != nullptr) ? wrap->getFlusher() : FlushableAttribute::SP());
}

AttributeManager::ShrinkerSP
AttributeManager::getShrinker(const vespalib::string &name)
{
    auto wrap = findFlushable(name);
    return ((wrap != nullptr) ? wrap->getShrinker() : ShrinkerSP());
}

size_t
AttributeManager::getNumDocs() const
{
    return _attributes.empty()
        ? 0
        : _attributes.begin()->second.getAttribute()->getNumDocs();
}

void
AttributeManager::padAttribute(AttributeVector &v, uint32_t docIdLimit)
{
    uint32_t needCommit = 0;
    uint32_t docId(v.getNumDocs());
    while (v.getNumDocs() < docIdLimit) {
        if (!v.addDoc(docId)) {
            throw vespalib::IllegalStateException
                (vespalib::make_string("Failed to pad doc %u/%u to "
                                       "attribute vector '%s'",
                                       docId,
                                       docIdLimit,
                                       v.getName().c_str()));
        }
        v.clearDoc(docId);
        if (++needCommit >= 1024) {
            needCommit = 0;
            v.commit();
        }
    }
    if (needCommit > 1) {
        v.commit();
    }
    assert(v.getNumDocs() >= docIdLimit);
}

AttributeGuard::UP
AttributeManager::getAttribute(const vespalib::string &name) const
{
    return std::make_unique<AttributeGuard>(findAttribute(name));
}

std::unique_ptr<search::attribute::AttributeReadGuard>
AttributeManager::getAttributeReadGuard(const string &name, bool stableEnumGuard) const
{
    auto attribute = findAttribute(name);
    if (attribute) {
        return attribute->makeReadGuard(stableEnumGuard);
    } else {
        return {};
    }
}

void
AttributeManager::getAttributeList(std::vector<AttributeGuard> &list) const
{
    list.reserve(_attributes.size());
    for (const auto &kv : _attributes) {
        if (!kv.second.isExtra()) {
            list.emplace_back(kv.second.getAttribute());
        }
    }
}

namespace {

class CombinedAttributeContext : public IAttributeContext {
private:
    using IAttributeFunctor = search::attribute::IAttributeFunctor;
    AttributeContext _ctx;
    ImportedAttributesContext _importedCtx;

public:
    CombinedAttributeContext(const search::IAttributeManager &mgr,
                             const ImportedAttributesRepo &importedAttributes)
        : _ctx(mgr),
          _importedCtx(importedAttributes)
    {
    }
    const IAttributeVector *getAttribute(const vespalib::string &name) const override {
        const IAttributeVector *result = _ctx.getAttribute(name);
        if (result == nullptr) {
            result = _importedCtx.getAttribute(name);
        }
        return result;
    }
    const IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const override {
        const IAttributeVector *result = _ctx.getAttributeStableEnum(name);
        if (result == nullptr) {
            result = _importedCtx.getAttributeStableEnum(name);
        }
        return result;
    }
    void getAttributeList(std::vector<const IAttributeVector *> &list) const override {
        _ctx.getAttributeList(list);
        _importedCtx.getAttributeList(list);
    }
    void releaseEnumGuards() override {
        _ctx.releaseEnumGuards();
        _importedCtx.releaseEnumGuards();
    }
    void asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const override {
        _ctx.asyncForAttribute(name, std::move(func));
    }
};

}

IAttributeContext::UP
AttributeManager::createContext() const
{
    if (_importedAttributes) {
        return std::make_unique<CombinedAttributeContext>(*this, *_importedAttributes);
    }
    return std::make_unique<AttributeContext>(*this);
}

proton::IAttributeManager::SP
AttributeManager::create(Spec && spec) const
{
    SequentialAttributesInitializer initializer(spec.getDocIdLimit());
    proton::AttributeManager::SP result = std::make_shared<AttributeManager>(*this, std::move(spec), initializer);
    result->addInitializedAttributes(initializer.getInitializedAttributes());
    return result;
}

std::vector<IFlushTarget::SP>
AttributeManager::getFlushTargets() const
{
    std::vector<IFlushTarget::SP> list;
    list.reserve(_flushables.size());
    for (const auto &kv : _flushables) {
        list.push_back(kv.second.getFlusher());
        list.push_back(kv.second.getShrinker());
    }
    return list;
}

search::SerialNum
AttributeManager::getFlushedSerialNum(const vespalib::string &name) const
{
    auto wrap = findFlushable(name);
    if (wrap != nullptr) {
        const auto &flusher = wrap->getFlusher();
        if (flusher) {
            return flusher->getFlushedSerialNum();
        }
    }
    return 0;
}

search::SerialNum
AttributeManager::getOldestFlushedSerialNumber() const
{
    SerialNum num = -1;
    for (const auto &kv : _flushables) {
        num = std::min(num, kv.second.getFlusher()->getFlushedSerialNum());
    }
    return num;
}

search::SerialNum
AttributeManager::getNewestFlushedSerialNumber() const
{
    SerialNum num = 0;
    for (const auto &kv : _flushables) {
        num = std::max(num, kv.second.getFlusher()->getFlushedSerialNum());
    }
    return num;
}

void
AttributeManager::getAttributeListAll(std::vector<AttributeGuard> &list) const
{
    list.reserve(_attributes.size());
    for (const auto &kv : _attributes) {
        list.emplace_back(kv.second.getAttribute());
    }
}

void
AttributeManager::pruneRemovedFields(search::SerialNum serialNum)
{
    std::vector<vespalib::string> attributes = _diskLayout->listAttributes();
    for (const auto &attribute : attributes) {
        auto itr = _attributes.find(attribute);
        if (itr == _attributes.end()) {
            _diskLayout->removeAttributeDir(attribute, serialNum);
        }
    }
}

vespalib::ISequencedTaskExecutor &
AttributeManager::getAttributeFieldWriter() const
{
    return _attributeFieldWriter;
}

AttributeVector *
AttributeManager::getWritableAttribute(const vespalib::string &name) const
{
    auto itr = _attributes.find(name);
    if (itr == _attributes.end() || itr->second.isExtra()) {
        return nullptr;
    }
    return itr->second.getAttribute().get();
}

const std::vector<AttributeVector *> &
AttributeManager::getWritableAttributes() const
{
    return _writableAttributes;
}

void
AttributeManager::asyncForEachAttribute(std::shared_ptr<IConstAttributeFunctor> func) const
{
    for (const auto &attr : _attributes) {
        if (attr.second.isExtra()) {
            // We must skip extra attributes as they must be handled in other threads. (DocumentMetaStore)
            continue;
        }
        AttributeVector::SP attrsp = attr.second.getAttribute();
        _attributeFieldWriter.execute(_attributeFieldWriter.getExecutorIdFromName(attrsp->getNamePrefix()),
                                      [attrsp, func]() { (*func)(*attrsp); });
    }
}

void
AttributeManager::asyncForEachAttribute(std::shared_ptr<IAttributeFunctor> func, OnDone onDone) const
{
    for (const auto &attr : _attributes) {
        if (attr.second.isExtra()) {
            // We must skip extra attributes as they must be handled in other threads.(DocumentMetaStore)
            continue;
        }
        AttributeVector::SP attrsp = attr.second.getAttribute();
        _attributeFieldWriter.execute(_attributeFieldWriter.getExecutorIdFromName(attrsp->getNamePrefix()),
                                      [attrsp, func, onDone]() {
            (void) onDone;
            (*func)(*attrsp);
        });
    }
}

void
AttributeManager::asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const {
    auto itr = _attributes.find(name);
    if (itr == _attributes.end() || itr->second.isExtra() || !func) {
        return;
    }
    AttributeVector::SP attrsp = itr->second.getAttribute();
    vespalib::string attrName = attrsp->getNamePrefix();
    _attributeFieldWriter.execute(_attributeFieldWriter.getExecutorIdFromName(attrName),
                                  [attr=std::move(attrsp), func=std::move(func)]() { (*func)(*attr); });
}

ExclusiveAttributeReadAccessor::UP
AttributeManager::getExclusiveReadAccessor(const vespalib::string &name) const
{
    AttributeVector::SP attribute = findAttribute(name);
    if (attribute) {
        return std::make_unique<ExclusiveAttributeReadAccessor>(attribute, _attributeFieldWriter);
    }
    return {};
}

void
AttributeManager::setImportedAttributes(std::unique_ptr<ImportedAttributesRepo> attributes)
{
    _importedAttributes = std::move(attributes);
}

std::shared_ptr<search::attribute::ReadableAttributeVector>
AttributeManager::readable_attribute_vector(const string& name) const
{
    auto attribute = findAttribute(name);
    if (attribute || !_importedAttributes) {
        return attribute;
    }
    return _importedAttributes->get(name);
}

} // namespace proton
