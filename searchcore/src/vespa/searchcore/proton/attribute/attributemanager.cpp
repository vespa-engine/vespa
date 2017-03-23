// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_factory.h"
#include "attributedisklayout.h"
#include "attributemanager.h"
#include "i_attribute_functor.h"
#include "imported_attributes_context.h"
#include "imported_attributes_repo.h"
#include "sequential_attributes_initializer.h"
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/common/isequencedtaskexecutor.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attributemanager");

using search::AttributeContext;
using search::AttributeEnumGuard;
using search::AttributeGuard;
using search::AttributeVector;
using search::IndexMetaInfo;
using search::TuneFileAttributes;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::common::FileHeaderContext;
using search::index::Schema;

namespace proton {

namespace {

bool matchingTypes(const AttributeVector::SP &av, const search::attribute::Config &newConfig) {
    if (av) {
        const auto &oldConfig = av->getConfig();
        return ((oldConfig.basicType() == newConfig.basicType()) &&
                (oldConfig.collectionType() == newConfig.collectionType()));
    } else {
        return false;
    }
}

}

AttributeVector::SP
AttributeManager::internalAddAttribute(const vespalib::string &name,
                                       const Config &cfg,
                                       uint64_t serialNum,
                                       const IAttributeFactory &factory)
{
    AttributeInitializer initializer(_diskLayout->createAttributeDir(name), _documentSubDbName, cfg, serialNum, factory);
    AttributeVector::SP attr = initializer.init();
    if (attr.get() != NULL) {
        attr->setInterlock(_interlock);
        addAttribute(attr);
    }
    return attr;
}

void
AttributeManager::addAttribute(const AttributeWrap &attribute)
{
    LOG(debug, "Adding attribute vector '%s'", attribute->getBaseFileName().c_str());
    _attributes[attribute->getName()] = attribute;
    assert(attribute->getInterlock() == _interlock);
    if ( ! attribute.isExtra() ) {
        // Flushing of extra attributes is handled elsewhere
        _flushables[attribute->getName()] = FlushableAttribute::SP
                                            (new FlushableAttribute(attribute, _diskLayout->createAttributeDir(attribute->getName()),
                                        _tuneFileAttributes,
                                        _fileHeaderContext,
                                        _attributeFieldWriter,
                                        _hwInfo));
        _writableAttributes.push_back(attribute.get());
    }
}

AttributeVector::SP
AttributeManager::findAttribute(const vespalib::string &name) const
{
    AttributeMap::const_iterator itr = _attributes.find(name);
    return (itr != _attributes.end())
        ? static_cast<const AttributeVector::SP &>(itr->second)
        : AttributeVector::SP();
}

FlushableAttribute::SP
AttributeManager::findFlushable(const vespalib::string &name) const
{
    FlushableMap::const_iterator itr = _flushables.find(name);
    return (itr != _flushables.end()) ? itr->second : FlushableAttribute::SP();
}

void
AttributeManager::transferExistingAttributes(const AttributeManager &currMgr,
                                             const Spec &newSpec,
                                             Spec::AttributeList &toBeAdded)
{
    for (const auto &aspec : newSpec.getAttributes()) {
        AttributeVector::SP av = currMgr.findAttribute(aspec.getName());
        if (matchingTypes(av, aspec.getConfig())) { // transfer attribute
            LOG(debug, "Transferring attribute vector '%s' with %u docs and serial number %lu from current manager",
                       av->getName().c_str(), av->getNumDocs(), av->getStatus().getLastSyncToken());
            addAttribute(av);
        } else {
            toBeAdded.push_back(aspec);
        }
    }
}

void
AttributeManager::addNewAttributes(const Spec &newSpec,
                                   const Spec::AttributeList &toBeAdded,
                                   IAttributeInitializerRegistry &initializerRegistry)
{
    for (const auto &aspec : toBeAdded) {
        LOG(debug, "Creating initializer for attribute vector '%s': docIdLimit=%u, serialNumber=%lu",
                   aspec.getName().c_str(), newSpec.getDocIdLimit(), newSpec.getCurrentSerialNum());

        AttributeInitializer::UP initializer =
            std::make_unique<AttributeInitializer>(_diskLayout->createAttributeDir(aspec.getName()), _documentSubDbName,
                        aspec.getConfig(), newSpec.getCurrentSerialNum(), *_factory);
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
            addAttribute(kv.second);
        }
    }
}

AttributeManager::AttributeManager(const vespalib::string &baseDir,
                                   const vespalib::string &documentSubDbName,
                                   const TuneFileAttributes &tuneFileAttributes,
                                   const FileHeaderContext &fileHeaderContext,
                                   search::ISequencedTaskExecutor &
                                   attributeFieldWriter,
                                   const HwInfo &hwInfo)
    : proton::IAttributeManager(),
      _attributes(),
      _flushables(),
      _writableAttributes(),
      _diskLayout(AttributeDiskLayout::create(baseDir)),
      _documentSubDbName(documentSubDbName),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _factory(new AttributeFactory()),
      _interlock(std::make_shared<search::attribute::Interlock>()),
      _attributeFieldWriter(attributeFieldWriter),
      _hwInfo(hwInfo),
      _importedAttributes()
{
}


AttributeManager::AttributeManager(const vespalib::string &baseDir,
                                   const vespalib::string &documentSubDbName,
                                   const search::TuneFileAttributes &tuneFileAttributes,
                                   const search::common::FileHeaderContext &fileHeaderContext,
                                   search::ISequencedTaskExecutor &
                                   attributeFieldWriter,
                                   const IAttributeFactory::SP &factory,
                                   const HwInfo &hwInfo)
    : proton::IAttributeManager(),
      _attributes(),
      _flushables(),
      _writableAttributes(),
      _diskLayout(AttributeDiskLayout::create(baseDir)),
      _documentSubDbName(documentSubDbName),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _factory(factory),
      _interlock(std::make_shared<search::attribute::Interlock>()),
      _attributeFieldWriter(attributeFieldWriter),
      _hwInfo(hwInfo),
      _importedAttributes()
{
}

AttributeManager::AttributeManager(const AttributeManager &currMgr,
                                   const Spec &newSpec,
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
      _hwInfo(currMgr._hwInfo),
      _importedAttributes()
{
    Spec::AttributeList toBeAdded;
    transferExistingAttributes(currMgr, newSpec, toBeAdded);
    addNewAttributes(newSpec, toBeAdded, initializerRegistry);
    transferExtraAttributes(currMgr);
}

AttributeManager::~AttributeManager() { }

AttributeVector::SP
AttributeManager::addAttribute(const vespalib::string &name,
                               const Config &cfg,
                               uint64_t serialNum)
{
    return internalAddAttribute(name, cfg, serialNum, *_factory);
}

void
AttributeManager::addInitializedAttributes(const std::vector<search::AttributeVector::SP> &attributes)
{
    for (const auto &attribute : attributes) {
        attribute->setInterlock(_interlock);
        addAttribute(attribute);
    }
}

void
AttributeManager::addExtraAttribute(const AttributeVector::SP &attribute)
{
    attribute->setInterlock(_interlock);
    addAttribute(AttributeWrap(attribute, true));
}

void
AttributeManager::flushAll(SerialNum currentSerial)
{
    for (const auto &kv : _flushables) {
        vespalib::Executor::Task::UP task;
        task = kv.second->initFlush(currentSerial);
        if (task.get() != NULL) {
            task->run();
        }
    }
}

FlushableAttribute::SP
AttributeManager::getFlushable(const vespalib::string &name)
{
    return findFlushable(name);
}

size_t
AttributeManager::getNumDocs() const
{
    return _attributes.empty()
        ? 0
        : _attributes.begin()->second->getNumDocs();
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
    if (needCommit > 1)
        v.commit();
    assert(v.getNumDocs() >= docIdLimit);
}

AttributeGuard::UP
AttributeManager::getAttribute(const vespalib::string &name) const
{
    return AttributeGuard::UP(new AttributeGuard(findAttribute(name)));
}

AttributeGuard::UP
AttributeManager::getAttributeStableEnum(const vespalib::string &name) const
{
    return AttributeGuard::UP(new AttributeEnumGuard(findAttribute(name)));
}

void
AttributeManager::getAttributeList(std::vector<AttributeGuard> &list) const
{
    list.reserve(_attributes.size());
    for (const auto &kv : _attributes) {
        if (!kv.second.isExtra()) {
            list.push_back(AttributeGuard(kv.second));
        }
    }
}

namespace {

class CombinedAttributeContext : public IAttributeContext {
private:
    AttributeContext _ctx;
    ImportedAttributesContext _importedCtx;

public:
    CombinedAttributeContext(const search::IAttributeManager &mgr,
                             const ImportedAttributesRepo &importedAttributes)
        : _ctx(mgr),
          _importedCtx(importedAttributes)
    {
    }
    virtual const IAttributeVector *getAttribute(const vespalib::string &name) const override {
        const IAttributeVector *result = _ctx.getAttribute(name);
        if (result == nullptr) {
            result = _importedCtx.getAttribute(name);
        }
        return result;
    }
    virtual const IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const override {
        const IAttributeVector *result = _ctx.getAttributeStableEnum(name);
        if (result == nullptr) {
            result = _importedCtx.getAttributeStableEnum(name);
        }
        return result;
    }
    virtual void getAttributeList(std::vector<const IAttributeVector *> &list) const override {
        _ctx.getAttributeList(list);
        _importedCtx.getAttributeList(list);
    }
    virtual void releaseEnumGuards() override {
        _ctx.releaseEnumGuards();
        _importedCtx.releaseEnumGuards();
    }
};

}

IAttributeContext::UP
AttributeManager::createContext() const
{
    if (_importedAttributes.get() != nullptr) {
        return std::make_unique<CombinedAttributeContext>(*this, *_importedAttributes.get());
    }
    return std::make_unique<AttributeContext>(*this);
}

proton::IAttributeManager::SP
AttributeManager::create(const Spec &spec) const
{
    SequentialAttributesInitializer initializer(spec.getDocIdLimit());
    proton::AttributeManager::SP result = std::make_shared<AttributeManager>(*this, spec, initializer);
    result->addInitializedAttributes(initializer.getInitializedAttributes());
    return result;
}

std::vector<IFlushTarget::SP>
AttributeManager::getFlushTargets() const
{
    std::vector<IFlushTarget::SP> list;
    list.reserve(_flushables.size());
    for (const auto &kv : _flushables) {
        list.push_back(kv.second);
    }
    return list;
}

search::SerialNum
AttributeManager::getFlushedSerialNum(const vespalib::string &name) const
{
    FlushableAttribute::SP flushable = findFlushable(name);
    if (flushable.get() != nullptr) {
        return flushable->getFlushedSerialNum();
    }
    return 0;
}

search::SerialNum
AttributeManager::getOldestFlushedSerialNumber() const
{
    SerialNum num = -1;
    for (const auto &kv : _flushables) {
        num = std::min(num, kv.second->getFlushedSerialNum());
    }
    return num;
}

search::SerialNum
AttributeManager::getNewestFlushedSerialNumber() const
{
    SerialNum num = 0;
    for (const auto &kv : _flushables) {
        num = std::max(num, kv.second->getFlushedSerialNum());
    }
    return num;
}

void
AttributeManager::getAttributeListAll(std::vector<AttributeGuard> &list) const
{
    list.reserve(_attributes.size());
    for (const auto &kv : _attributes) {
        list.push_back(AttributeGuard(kv.second));
    }
}

void
AttributeManager::wipeHistory(search::SerialNum wipeSerial)
{
    std::vector<vespalib::string> attributes = _diskLayout->listAttributes();
    for (const auto &attribute : attributes) {
        auto itr = _attributes.find(attribute);
        if (itr == _attributes.end()) {
            _diskLayout->removeAttributeDir(attribute, wipeSerial);
        }
    }
}

search::ISequencedTaskExecutor &
AttributeManager::getAttributeFieldWriter() const
{
    return _attributeFieldWriter;
}


AttributeVector *
AttributeManager::getWritableAttribute(const vespalib::string &name) const
{
    AttributeMap::const_iterator itr = _attributes.find(name);
    if (itr == _attributes.end() || itr->second.isExtra()) {
        return nullptr;
    }
    return itr->second.get();
}


const std::vector<AttributeVector *> &
AttributeManager::getWritableAttributes() const
{
    return _writableAttributes;
}


void
AttributeManager::asyncForEachAttribute(std::shared_ptr<IAttributeFunctor>
                                        func) const
{
    for (const auto &attr : _attributes) {
        if (attr.second.isExtra()) {
            continue;
        }
        AttributeVector::SP attrsp = attr.second;
        _attributeFieldWriter.
            execute(attr.first, [attrsp, func]() { (*func)(*attrsp); });
    }
}

ExclusiveAttributeReadAccessor::UP
AttributeManager::getExclusiveReadAccessor(const vespalib::string &name) const
{
    AttributeVector::SP attribute = findAttribute(name);
    if (attribute) {
        return std::make_unique<ExclusiveAttributeReadAccessor>(attribute, _attributeFieldWriter);
    }
    return ExclusiveAttributeReadAccessor::UP();
}

void
AttributeManager::setImportedAttributes(std::unique_ptr<ImportedAttributesRepo> attributes)
{
    _importedAttributes = std::move(attributes);
}

} // namespace proton
