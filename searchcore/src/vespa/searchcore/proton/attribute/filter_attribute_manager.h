// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_manager.h"
#include <vespa/vespalib/util/exceptions.h>
#include <set>

namespace proton {

/**
 * An attribute manager that wraps another attribute manager and only gives access to a
 * subset of the attribute vectors in the wrapped manager.
 *
 * This manager only implements the subset of functions needed when used by
 * and attribute adapter in the context of an attribute populator.
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

    // Implements search::IAttributeManager
    virtual search::AttributeGuard::UP getAttribute(const vespalib::string &name) const;
    virtual search::AttributeGuard::UP getAttributeStableEnum(const vespalib::string &) const {
        throw vespalib::IllegalArgumentException("Not implemented");
    }
    virtual void getAttributeList(std::vector<search::AttributeGuard> &list) const;
    virtual search::attribute::IAttributeContext::UP createContext() const {
        throw vespalib::IllegalArgumentException("Not implemented");
    }

    // Implements proton::IAttributeManager
    virtual IAttributeManager::SP create(const AttributeCollectionSpec &) const {
        throw vespalib::IllegalArgumentException("Not implemented");
    }
    virtual std::vector<searchcorespi::IFlushTarget::SP> getFlushTargets() const {
        throw vespalib::IllegalArgumentException("Not implemented");
    }
    virtual search::SerialNum getFlushedSerialNum(const vespalib::string &name) const;
    virtual search::SerialNum getOldestFlushedSerialNumber() const {
        throw vespalib::IllegalArgumentException("Not implemented");
    }
    virtual search::SerialNum getNewestFlushedSerialNumber() const {
        throw vespalib::IllegalArgumentException("Not implemented");
    }
    virtual void getAttributeListAll(std::vector<search::AttributeGuard> &) const {
        throw vespalib::IllegalArgumentException("Not implemented");
    }
    virtual void wipeHistory(const search::index::Schema &) {
        throw vespalib::IllegalArgumentException("Not implemented");
    }
    virtual const IAttributeFactory::SP &getFactory() const {
        throw vespalib::IllegalArgumentException("Not implemented");
    }

    virtual search::ISequencedTaskExecutor &
    getAttributeFieldWriter() const override;

    virtual search::AttributeVector *
    getWritableAttribute(const vespalib::string &name) const override;

    virtual const std::vector<search::AttributeVector *> &
    getWritableAttributes() const override;

    virtual void
    asyncForEachAttribute(std::shared_ptr<IAttributeFunctor> func) const override;

    virtual ExclusiveAttributeReadAccessor::UP
    getExclusiveReadAccessor(const vespalib::string &name) const override;
};

} // namespace proton

