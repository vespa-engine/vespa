// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_collection_spec.h"
#include "i_attribute_factory.h"
#include "i_attribute_manager.h"
#include "i_attribute_initializer_registry.h"
#include <set>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/searchcore/proton/common/hw_info.h>

namespace search::attribute { class Interlock; }

namespace search::common { class FileHeaderContext; }

namespace searchcorespi { class IFlushTarget; }

namespace proton {

class AttributeDiskLayout;
class FlushableAttribute;
class ShrinkLidSpaceFlushTarget;

/**
 * Specialized attribute manager for proton.
 */
class AttributeManager : public proton::IAttributeManager
{
private:
    typedef search::attribute::Config Config;
    typedef search::SerialNum SerialNum;
    typedef AttributeCollectionSpec Spec;
    using FlushableAttributeSP = std::shared_ptr<FlushableAttribute>;
    using ShrinkerSP = std::shared_ptr<ShrinkLidSpaceFlushTarget>;
    using IFlushTargetSP = std::shared_ptr<searchcorespi::IFlushTarget>;
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    using AttributeReadGuard = search::attribute::AttributeReadGuard;

    class AttributeWrap
    {
    private:
        AttributeVectorSP _attr;
        bool _isExtra;
        AttributeWrap(const AttributeVectorSP & a, bool isExtra_);
    public:
        AttributeWrap();
        ~AttributeWrap();
        static AttributeWrap extraAttribute(const AttributeVectorSP &a);
        static AttributeWrap normalAttribute(const AttributeVectorSP &a);
        bool isExtra() const { return _isExtra; }
        const AttributeVectorSP & getAttribute() const { return _attr; }
    };

    class FlushableWrap
    {
        FlushableAttributeSP _flusher;
        ShrinkerSP           _shrinker;
    public:
        FlushableWrap();
        FlushableWrap(FlushableAttributeSP flusher, ShrinkerSP shrinker);
        ~FlushableWrap();
        const FlushableAttributeSP &getFlusher() const { return _flusher; }
        const ShrinkerSP &getShrinker() const { return _shrinker; }
    };

    typedef vespalib::hash_map<vespalib::string, AttributeWrap> AttributeMap;
    typedef vespalib::hash_map<vespalib::string, FlushableWrap> FlushableMap;

    AttributeMap _attributes;
    FlushableMap _flushables;
    std::vector<search::AttributeVector *> _writableAttributes;
    std::shared_ptr<AttributeDiskLayout> _diskLayout;
    vespalib::string _documentSubDbName;
    const search::TuneFileAttributes _tuneFileAttributes;
    const search::common::FileHeaderContext &_fileHeaderContext;
    IAttributeFactory::SP _factory;
    std::shared_ptr<search::attribute::Interlock> _interlock;
    search::ISequencedTaskExecutor &_attributeFieldWriter;
    HwInfo _hwInfo;
    std::unique_ptr<ImportedAttributesRepo> _importedAttributes;

    AttributeVectorSP internalAddAttribute(const AttributeSpec &spec, uint64_t serialNum, const IAttributeFactory &factory);

    void addAttribute(const AttributeWrap &attribute, const ShrinkerSP &shrinker);

    AttributeVectorSP findAttribute(const vespalib::string &name) const;

    const FlushableWrap *findFlushable(const vespalib::string &name) const;

    void transferExistingAttributes(const AttributeManager &currMgr, const Spec &newSpec, Spec::AttributeList &toBeAdded);

    void addNewAttributes(const Spec &newSpec, const Spec::AttributeList &toBeAdded,
                          IAttributeInitializerRegistry &initializerRegistry);

    void transferExtraAttributes(const AttributeManager &currMgr);

public:
    typedef std::shared_ptr<AttributeManager> SP;

    AttributeManager(const vespalib::string &baseDir,
                     const vespalib::string &documentSubDbName,
                     const search::TuneFileAttributes &tuneFileAttributes,
                     const search::common::FileHeaderContext & fileHeaderContext,
                     search::ISequencedTaskExecutor &attributeFieldWriter,
                     const HwInfo &hwInfo);

    AttributeManager(const vespalib::string &baseDir,
                     const vespalib::string &documentSubDbName,
                     const search::TuneFileAttributes &tuneFileAttributes,
                     const search::common::FileHeaderContext & fileHeaderContext,
                     search::ISequencedTaskExecutor &attributeFieldWriter,
                     const IAttributeFactory::SP &factory,
                     const HwInfo &hwInfo);

    AttributeManager(const AttributeManager &currMgr, const Spec &newSpec,
                     IAttributeInitializerRegistry &initializerRegistry);
    ~AttributeManager() override;

    AttributeVectorSP addAttribute(const AttributeSpec &spec, uint64_t serialNum);

    void addInitializedAttributes(const std::vector<AttributeInitializerResult> &attributes);

    void addExtraAttribute(const AttributeVectorSP &attribute);

    void flushAll(SerialNum currentSerial);

    FlushableAttributeSP getFlushable(const vespalib::string &name);

    ShrinkerSP getShrinker(const vespalib::string &name);

    size_t getNumDocs() const;

    static void padAttribute(search::AttributeVector &v, uint32_t docIdLimit);

    // Implements search::IAttributeManager
    search::AttributeGuard::UP getAttribute(const vespalib::string &name) const override;
    std::unique_ptr<AttributeReadGuard> getAttributeReadGuard(const string &name, bool stableEnumGuard) const override;

    /**
     * Fills all regular registered attributes (not extra attributes)
     * into the given list.
     */
    void getAttributeList(std::vector<search::AttributeGuard> &list) const override;

    search::attribute::IAttributeContext::UP createContext() const override;

    // Implements proton::IAttributeManager

    proton::IAttributeManager::SP create(const Spec &spec) const override;

    std::vector<IFlushTargetSP> getFlushTargets() const override;

    search::SerialNum getFlushedSerialNum(const vespalib::string &name) const override;

    SerialNum getOldestFlushedSerialNumber() const override;

    search::SerialNum getNewestFlushedSerialNumber() const override;

    void getAttributeListAll(std::vector<search::AttributeGuard> &list) const override;

    void pruneRemovedFields(search::SerialNum serialNum) override;

    const IAttributeFactory::SP &getFactory() const override { return _factory; }

    search::ISequencedTaskExecutor &getAttributeFieldWriter() const override;

    search::AttributeVector *getWritableAttribute(const vespalib::string &name) const override;

    const std::vector<search::AttributeVector *> &getWritableAttributes() const override;

    void asyncForEachAttribute(std::shared_ptr<IConstAttributeFunctor> func) const override;
    void asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const override;

    ExclusiveAttributeReadAccessor::UP getExclusiveReadAccessor(const vespalib::string &name) const override;

    void setImportedAttributes(std::unique_ptr<ImportedAttributesRepo> attributes) override;

    const ImportedAttributesRepo *getImportedAttributes() const override { return _importedAttributes.get(); }

    std::shared_ptr<search::attribute::ReadableAttributeVector> readable_attribute_vector(const string& name) const override;
};

} // namespace proton

