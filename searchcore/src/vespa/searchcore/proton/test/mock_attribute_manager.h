// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/hdr_abort.h>
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

    search::AttributeGuard::UP getAttribute(const vespalib::string &name) const override {
        return _mock.getAttribute(name);
    }
    std::unique_ptr<search::attribute::AttributeReadGuard> getAttributeReadGuard(const vespalib::string &name, bool stableEnumGuard) const override {
        return _mock.getAttributeReadGuard(name, stableEnumGuard);
    }
    void getAttributeList(std::vector<search::AttributeGuard> &list) const override {
        _mock.getAttributeList(list);
    }
    search::attribute::IAttributeContext::UP createContext() const override {
        return _mock.createContext();
    }
    IAttributeManager::SP create(const AttributeCollectionSpec &) const override {
        return IAttributeManager::SP();
    }
    std::vector<searchcorespi::IFlushTarget::SP> getFlushTargets() const override {
        return std::vector<searchcorespi::IFlushTarget::SP>();
    }
    search::SerialNum getFlushedSerialNum(const vespalib::string &) const override {
        return search::SerialNum();
    }
    search::SerialNum getOldestFlushedSerialNumber() const override {
        return search::SerialNum();
    }
    search::SerialNum getNewestFlushedSerialNumber() const override {
        return search::SerialNum();
    }
    void getAttributeListAll(std::vector<search::AttributeGuard> &) const override { }
    void pruneRemovedFields(search::SerialNum) override { }
    const IAttributeFactory::SP &getFactory() const override {
        HDR_ABORT("should not be reached");
    }
    search::ISequencedTaskExecutor &getAttributeFieldWriter() const override {
        HDR_ABORT("should not be reached");
    }
    search::AttributeVector *getWritableAttribute(const vespalib::string &) const override {
        return nullptr;
    }
    const std::vector<search::AttributeVector *> &getWritableAttributes() const override {
        HDR_ABORT("should not be reached");
    }
    void asyncForEachAttribute(std::shared_ptr<IConstAttributeFunctor>) const override {
    }
    ExclusiveAttributeReadAccessor::UP getExclusiveReadAccessor(const vespalib::string &) const override {
        return ExclusiveAttributeReadAccessor::UP();
    }
    void setImportedAttributes(std::unique_ptr<ImportedAttributesRepo> importedAttributes) override {
        _importedAttributes = std::move(importedAttributes);
    }
    const ImportedAttributesRepo *getImportedAttributes() const override {
        return _importedAttributes.get();
    }
    void asyncForAttribute(const vespalib::string & name, std::unique_ptr<IAttributeFunctor> func) const override {
        _mock.asyncForAttribute(name, std::move(func));
    }
    std::shared_ptr<search::attribute::ReadableAttributeVector> readable_attribute_vector(const string& name) const override {
        return _mock.readable_attribute_vector(name);
    }
};

}
