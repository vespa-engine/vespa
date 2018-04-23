// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>

namespace proton::test {

class MockAttributeManager : public IAttributeManager {
private:
    search::attribute::test::MockAttributeManager _mock;
    std::unique_ptr<ImportedAttributesRepo> _importedAttributes;

public:
    MockAttributeManager()
        : _mock(),
          _importedAttributes()
    {}

    void addAttribute(const vespalib::string &name, const search::AttributeVector::SP &attr) {
        _mock.addAttribute(name, attr);
    }

    virtual search::AttributeGuard::UP getAttribute(const vespalib::string &name) const override {
        return _mock.getAttribute(name);
    }
    virtual std::unique_ptr<search::attribute::AttributeReadGuard> getAttributeReadGuard(const vespalib::string &name, bool stableEnumGuard) const override {
        return _mock.getAttributeReadGuard(name, stableEnumGuard);
    }
    virtual void getAttributeList(std::vector<search::AttributeGuard> &list) const override {
        _mock.getAttributeList(list);
    }
    virtual search::attribute::IAttributeContext::UP createContext() const override {
        return _mock.createContext();
    }
    virtual IAttributeManager::SP create(const AttributeCollectionSpec &) const override {
        return IAttributeManager::SP();
    }
    virtual std::vector<searchcorespi::IFlushTarget::SP> getFlushTargets() const override {
        return std::vector<searchcorespi::IFlushTarget::SP>();
    }
    virtual search::SerialNum getFlushedSerialNum(const vespalib::string &) const override {
        return search::SerialNum();
    }
    virtual search::SerialNum getOldestFlushedSerialNumber() const override {
        return search::SerialNum();
    }
    virtual search::SerialNum getNewestFlushedSerialNumber() const override {
        return search::SerialNum();
    }
    virtual void getAttributeListAll(std::vector<search::AttributeGuard> &) const override {
    }
    virtual void pruneRemovedFields(search::SerialNum) override {
    }
    virtual const IAttributeFactory::SP &getFactory() const override {
        abort();
    }
    virtual search::ISequencedTaskExecutor &getAttributeFieldWriter() const override {
        abort();
    }
    virtual search::AttributeVector *getWritableAttribute(const vespalib::string &) const override {
        return nullptr;
    }
    virtual const std::vector<search::AttributeVector *> &getWritableAttributes() const override {
        abort();
    }
    virtual void asyncForEachAttribute(std::shared_ptr<IAttributeFunctor>) const override {
    }
    virtual ExclusiveAttributeReadAccessor::UP getExclusiveReadAccessor(const vespalib::string &) const override {
        return ExclusiveAttributeReadAccessor::UP();
    }
    virtual void setImportedAttributes(std::unique_ptr<ImportedAttributesRepo> importedAttributes) override {
        _importedAttributes = std::move(importedAttributes);
    }
    virtual const ImportedAttributesRepo *getImportedAttributes() const override {
        return _importedAttributes.get();
    }
};

}
