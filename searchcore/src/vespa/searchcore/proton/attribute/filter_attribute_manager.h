// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    using AttributeSet = std::set<vespalib::string>;

private:
    AttributeSet                           _acceptedAttributes;
    IAttributeManager::SP                  _mgr;
    std::vector<search::AttributeVector *> _acceptedWritableAttributes;

    bool acceptAttribute(const vespalib::string &name) const;

public:
    FilterAttributeManager(const AttributeSet &acceptedAttributes, IAttributeManager::SP mgr);
    ~FilterAttributeManager() override;

    // Implements search::IAttributeManager
    search::AttributeGuard::UP getAttribute(const vespalib::string &name) const override;
    void getAttributeList(std::vector<search::AttributeGuard> &list) const override;
    search::SerialNum getFlushedSerialNum(const vespalib::string &name) const override;
    search::attribute::IAttributeContext::UP createContext() const override;
    std::unique_ptr<search::attribute::AttributeReadGuard> getAttributeReadGuard(const vespalib::string &name, bool stableEnumGuard) const override;

    // Implements proton::IAttributeManager
    std::unique_ptr<IAttributeManagerReconfig> prepare_create(AttributeCollectionSpec&& spec) const override;
    std::vector<searchcorespi::IFlushTarget::SP> getFlushTargets() const override;
    search::SerialNum getOldestFlushedSerialNumber() const override;
    search::SerialNum getNewestFlushedSerialNumber() const override;
    void getAttributeListAll(std::vector<search::AttributeGuard> &) const override;
    void pruneRemovedFields(search::SerialNum serialNum) override;
    const IAttributeFactory::SP &getFactory() const override;
    vespalib::ISequencedTaskExecutor & getAttributeFieldWriter() const override;
    vespalib::Executor& get_shared_executor() const override { return _mgr->get_shared_executor(); }

    search::AttributeVector * getWritableAttribute(const vespalib::string &name) const override;
    const std::vector<search::AttributeVector *> & getWritableAttributes() const override;
    void asyncForEachAttribute(std::shared_ptr<IConstAttributeFunctor> func) const override;
    void asyncForEachAttribute(std::shared_ptr<IAttributeFunctor> func, OnDone onDone) const override;
    void setImportedAttributes(std::unique_ptr<ImportedAttributesRepo> attributes) override;
    const ImportedAttributesRepo *getImportedAttributes() const override;
    std::shared_ptr<search::attribute::ReadableAttributeVector> readable_attribute_vector(const string& name) const override;

    void asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const override;

    TransientResourceUsage get_transient_resource_usage() const override { return {}; }
};

} // namespace proton

