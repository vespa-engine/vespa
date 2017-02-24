// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filter_attribute_manager.h"
#include "i_attribute_functor.h"
#include <vespa/searchlib/common/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/exceptions.h>

using search::AttributeGuard;

namespace proton {

bool
FilterAttributeManager::acceptAttribute(const vespalib::string &name) const
{
    return _acceptedAttributes.count(name) > 0;
}

FilterAttributeManager::FilterAttributeManager(const AttributeSet &acceptedAttributes,
                                               const IAttributeManager::SP &mgr)
    : _acceptedAttributes(acceptedAttributes),
      _mgr(mgr)
{
    // Assume that list of attributes in mgr doesn't change
    for (const auto attr : _mgr->getWritableAttributes()) {
        if (acceptAttribute(attr->getName())) {
            _acceptedWritableAttributes.push_back(attr);
        }
    }
}

FilterAttributeManager::~FilterAttributeManager() { }

search::AttributeGuard::UP
FilterAttributeManager::getAttributeStableEnum(const vespalib::string &) const {
    throw vespalib::IllegalArgumentException("Not implemented");
}
search::attribute::IAttributeContext::UP
FilterAttributeManager::createContext() const {
    throw vespalib::IllegalArgumentException("Not implemented");
}

IAttributeManager::SP
FilterAttributeManager::create(const AttributeCollectionSpec &) const {
    throw vespalib::IllegalArgumentException("Not implemented");
}
std::vector<searchcorespi::IFlushTarget::SP>
FilterAttributeManager::getFlushTargets() const {
    throw vespalib::IllegalArgumentException("Not implemented");
}
search::SerialNum
FilterAttributeManager::getOldestFlushedSerialNumber() const {
    throw vespalib::IllegalArgumentException("Not implemented");
}
search::SerialNum
FilterAttributeManager::getNewestFlushedSerialNumber() const {
    throw vespalib::IllegalArgumentException("Not implemented");
}
void
FilterAttributeManager::getAttributeListAll(std::vector<search::AttributeGuard> &) const {
    throw vespalib::IllegalArgumentException("Not implemented");
}
void
FilterAttributeManager::wipeHistory(const search::index::Schema &) {
    throw vespalib::IllegalArgumentException("Not implemented");
}
const IAttributeFactory::SP &
FilterAttributeManager::getFactory() const {
    throw vespalib::IllegalArgumentException("Not implemented");
}

AttributeGuard::UP
FilterAttributeManager::getAttribute(const vespalib::string &name) const
{
    if (acceptAttribute(name)) {
        return _mgr->getAttribute(name);
    }
    return AttributeGuard::UP();
}

void
FilterAttributeManager::getAttributeList(std::vector<AttributeGuard> &list) const
{
    std::vector<AttributeGuard> completeList;
    _mgr->getAttributeList(completeList);
    for (const auto &attr : completeList) {
        if (acceptAttribute(attr->getName())) {
            list.push_back(attr);
        }
    }
}

search::SerialNum
FilterAttributeManager::getFlushedSerialNum(const vespalib::string &name) const
{
    if (acceptAttribute(name)) {
        return _mgr->getFlushedSerialNum(name);
    }
    return 0;
}


search::ISequencedTaskExecutor &
FilterAttributeManager::getAttributeFieldWriter() const
{
    return _mgr->getAttributeFieldWriter();
}


search::AttributeVector *
FilterAttributeManager::getWritableAttribute(const vespalib::string &name) const
{
    if (acceptAttribute(name)) {
        return _mgr->getWritableAttribute(name);
    } else {
        return nullptr;
    }
}

const std::vector<search::AttributeVector *> &
FilterAttributeManager::getWritableAttributes() const
{
    return _acceptedWritableAttributes;
}

void
FilterAttributeManager::asyncForEachAttribute(std::shared_ptr<IAttributeFunctor>
                                        func) const
{
    // Run by document db master thread
    std::vector<AttributeGuard> completeList;
    _mgr->getAttributeList(completeList);
    search::ISequencedTaskExecutor &attributeFieldWriter =
        getAttributeFieldWriter();
    for (auto &guard : completeList) {
        search::AttributeVector::SP attrsp = guard.getSP();
        // Name must be extracted in document db master thread or attribute
        // writer thread
        vespalib::string attributeName = attrsp->getName();
        attributeFieldWriter.
            execute(attributeName, [attrsp, func]() { (*func)(*attrsp); });
    }
}

ExclusiveAttributeReadAccessor::UP
FilterAttributeManager::getExclusiveReadAccessor(const vespalib::string &name) const
{
    if (acceptAttribute(name)) {
        return _mgr->getExclusiveReadAccessor(name);
    } else {
        return ExclusiveAttributeReadAccessor::UP();
    }
}

void
FilterAttributeManager::setImportedAttributes(std::unique_ptr<ImportedAttributesRepo>)
{
    throw vespalib::IllegalArgumentException("Not implemented");
}

}
