// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/vespalib/util/hdr_abort.h>
#include <cassert>

namespace proton::test {

class MockAttributeManager : public IAttributeManager {
private:
    search::attribute::test::MockAttributeManager _mock;
    std::vector<search::AttributeVector*> _writables;
    std::unique_ptr<ImportedAttributesRepo> _importedAttributes;
    vespalib::ISequencedTaskExecutor* _writer;
    vespalib::Executor* _shared;

public:
    MockAttributeManager()
        : _mock(),
          _writables(),
          _importedAttributes(),
          _writer(),
          _shared()
    {}

    search::AttributeVector::SP addAttribute(const vespalib::string &name, const search::AttributeVector::SP &attr) {
        _mock.addAttribute(name, attr);
        _writables.push_back(attr.get());
        return attr;
    }
    void set_writer(vespalib::ISequencedTaskExecutor& writer) {
        _writer = &writer;
    }
    void set_shared_executor(vespalib::Executor& shared) {
        _shared = &shared;
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
    IAttributeManager::SP create(AttributeCollectionSpec &&) const override {
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
    vespalib::ISequencedTaskExecutor &getAttributeFieldWriter() const override {
        assert(_writer != nullptr);
        return *_writer;
    }
    vespalib::Executor& get_shared_executor() const override {
        assert(_shared != nullptr);
        return *_shared;
    }
    search::AttributeVector *getWritableAttribute(const vespalib::string &name) const override {
        auto attr = getAttribute(name);
        if (attr) {
            return attr->get();
        }
        return nullptr;
    }
    const std::vector<search::AttributeVector *> &getWritableAttributes() const override {
        return _writables;
    }
    void asyncForEachAttribute(std::shared_ptr<IConstAttributeFunctor>) const override { }
    void asyncForEachAttribute(std::shared_ptr<IAttributeFunctor>, OnDone) const override { }

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
