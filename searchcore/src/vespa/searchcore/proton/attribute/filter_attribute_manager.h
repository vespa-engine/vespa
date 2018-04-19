// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_manager.h"
#include <set>

namespace proton {

/**
 * An attribute manager that wraps another attribute manager and only gives access to a
 * subset of the attribute vectors in the wrapped manager.
 *
 * This manager only implements the subset of functions needed when used by
 * and attribute writer in the context of an attribute populator.
 */
class FilterAttributeManager : public IAttributeManager
{
public:
    typedef std::set<vespalib::string> AttributeSet;

private:
    AttributeSet          _acceptedAttributes;
    IAttributeManager::SP _mgr;
    std::vector<search::AttributeVector *> _acceptedWritableAttributes;

    bool acceptAttribute(const vespalib::string &name) const;

public:
    FilterAttributeManager(const AttributeSet &acceptedAttributes,
                           const IAttributeManager::SP &mgr);
    ~FilterAttributeManager();

    // Implements search::IAttributeManager
    virtual search::AttributeGuard::UP getAttribute(const vespalib::string &name) const override;
    virtual void getAttributeList(std::vector<search::AttributeGuard> &list) const override;
    virtual search::SerialNum getFlushedSerialNum(const vespalib::string &name) const override;
    virtual search::attribute::IAttributeContext::UP createContext() const override;
    virtual std::unique_ptr<search::attribute::AttributeReadGuard> getAttributeReadGuard(const vespalib::string &name, bool stableEnumGuard) const override;

    // Implements proton::IAttributeManager
    virtual IAttributeManager::SP create(const AttributeCollectionSpec &) const override;
    virtual std::vector<searchcorespi::IFlushTarget::SP> getFlushTargets() const override;
    virtual search::SerialNum getOldestFlushedSerialNumber() const override;
    virtual search::SerialNum getNewestFlushedSerialNumber() const override;
    virtual void getAttributeListAll(std::vector<search::AttributeGuard> &) const override;
    virtual void pruneRemovedFields(search::SerialNum serialNum) override;
    virtual const IAttributeFactory::SP &getFactory() const override;
    virtual search::ISequencedTaskExecutor & getAttributeFieldWriter() const override;

    virtual search::AttributeVector * getWritableAttribute(const vespalib::string &name) const override;
    virtual const std::vector<search::AttributeVector *> & getWritableAttributes() const override;
    virtual void asyncForEachAttribute(std::shared_ptr<IAttributeFunctor> func) const override;
    virtual ExclusiveAttributeReadAccessor::UP getExclusiveReadAccessor(const vespalib::string &name) const override;
    virtual void setImportedAttributes(std::unique_ptr<ImportedAttributesRepo> attributes) override;
    virtual const ImportedAttributesRepo *getImportedAttributes() const override;
};

} // namespace proton

