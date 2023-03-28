// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_manager.h"
#include "attribute_collection_spec.h"
#include <set>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/searchcore/proton/common/hw_info.h>

namespace search::attribute { class Interlock; }

namespace search::common { class FileHeaderContext; }

namespace searchcorespi { class IFlushTarget; }

namespace vespalib { class ThreadExecutor; }

namespace proton {

class AttributeCollectionSpec;
class AttributeDiskLayout;
class AttributeInitializerResult;
class AttributeSpec;
class FlushableAttribute;
struct IAttributeFactory;
struct IAttributeInitializerRegistry;
class ShrinkLidSpaceFlushTarget;

/**
 * Specialized attribute manager for proton.
 */
class AttributeManager : public proton::IAttributeManager
{
private:
    using AttributeReadGuard = search::attribute::AttributeReadGuard;
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    using Config = search::attribute::Config;
    using FlushableAttributeSP = std::shared_ptr<FlushableAttribute>;
    using IFlushTargetSP = std::shared_ptr<searchcorespi::IFlushTarget>;
    using SerialNum = search::SerialNum;
    using ShrinkerSP = std::shared_ptr<ShrinkLidSpaceFlushTarget>;
    using Spec = AttributeCollectionSpec;

    class AttributeWrap
    {
    private:
        AttributeVectorSP _attr;
        bool _isExtra;
        AttributeWrap(AttributeVectorSP a, bool isExtra_);
    public:
        AttributeWrap();
        AttributeWrap(const AttributeWrap &) = default;
        AttributeWrap & operator=(const AttributeWrap &) = delete;
        AttributeWrap(AttributeWrap &&) noexcept = default;
        AttributeWrap & operator=(AttributeWrap &&) noexcept = default;
        ~AttributeWrap();
        static AttributeWrap extraAttribute(AttributeVectorSP a);
        static AttributeWrap normalAttribute(AttributeVectorSP a);
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

    using AttributeMap = vespalib::hash_map<vespalib::string, AttributeWrap>;
    using FlushableMap = vespalib::hash_map<vespalib::string, FlushableWrap>;

    AttributeMap _attributes;
    FlushableMap _flushables;
    std::vector<search::AttributeVector *> _writableAttributes;
    std::shared_ptr<AttributeDiskLayout> _diskLayout;
    vespalib::string _documentSubDbName;
    const search::TuneFileAttributes _tuneFileAttributes;
    const search::common::FileHeaderContext &_fileHeaderContext;
    std::shared_ptr<IAttributeFactory> _factory;
    std::shared_ptr<search::attribute::Interlock> _interlock;
    vespalib::ISequencedTaskExecutor &_attributeFieldWriter;
    vespalib::Executor& _shared_executor;
    HwInfo _hwInfo;
    std::unique_ptr<ImportedAttributesRepo> _importedAttributes;

    AttributeVectorSP internalAddAttribute(AttributeSpec && spec, uint64_t serialNum, const IAttributeFactory &factory);
    void addAttribute(AttributeWrap attribute, const ShrinkerSP &shrinker);
    AttributeVectorSP findAttribute(const vespalib::string &name) const;
    const FlushableWrap *findFlushable(const vespalib::string &name) const;
    Spec::AttributeList transferExistingAttributes(const AttributeManager &currMgr, Spec::AttributeList && newAttributes);
    void addNewAttributes(const Spec &newSpec, Spec::AttributeList && toBeAdded, IAttributeInitializerRegistry &initializerRegistry);
    void transferExtraAttributes(const AttributeManager &currMgr);
public:
    using SP = std::shared_ptr<AttributeManager>;

    AttributeManager(const vespalib::string &baseDir,
                     const vespalib::string &documentSubDbName,
                     const search::TuneFileAttributes &tuneFileAttributes,
                     const search::common::FileHeaderContext & fileHeaderContext,
                     std::shared_ptr<search::attribute::Interlock> interlock,
                     vespalib::ISequencedTaskExecutor &attributeFieldWriter,
                     vespalib::Executor& shared_executor,
                     const HwInfo &hwInfo);

    AttributeManager(const vespalib::string &baseDir,
                     const vespalib::string &documentSubDbName,
                     const search::TuneFileAttributes &tuneFileAttributes,
                     const search::common::FileHeaderContext & fileHeaderContext,
                     std::shared_ptr<search::attribute::Interlock> interlock,
                     vespalib::ISequencedTaskExecutor &attributeFieldWriter,
                     vespalib::Executor& shared_executor,
                     std::shared_ptr<IAttributeFactory> factory,
                     const HwInfo &hwInfo);

    AttributeManager(const AttributeManager &currMgr, Spec && newSpec,
                     IAttributeInitializerRegistry &initializerRegistry);
    ~AttributeManager() override;

    AttributeVectorSP addAttribute(AttributeSpec && spec, uint64_t serialNum);

    void addInitializedAttributes(const std::vector<AttributeInitializerResult> &attributes, uint32_t docid_limit, SerialNum serial_num);

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

    std::unique_ptr<IAttributeManagerReconfig> prepare_create(Spec&& spec) const override;

    std::vector<IFlushTargetSP> getFlushTargets() const override;

    search::SerialNum getFlushedSerialNum(const vespalib::string &name) const override;

    SerialNum getOldestFlushedSerialNumber() const override;

    search::SerialNum getNewestFlushedSerialNumber() const override;

    void getAttributeListAll(std::vector<search::AttributeGuard> &list) const override;

    void pruneRemovedFields(search::SerialNum serialNum) override;

    const std::shared_ptr<IAttributeFactory> &getFactory() const override { return _factory; }

    vespalib::ISequencedTaskExecutor &getAttributeFieldWriter() const override;

    vespalib::Executor& get_shared_executor() const override { return _shared_executor; }

    search::AttributeVector *getWritableAttribute(const vespalib::string &name) const override;

    const std::vector<search::AttributeVector *> &getWritableAttributes() const override;

    void asyncForEachAttribute(std::shared_ptr<IConstAttributeFunctor> func) const override;
    void asyncForEachAttribute(std::shared_ptr<IAttributeFunctor> func, OnDone onDone) const override;
    void asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const override;

    void setImportedAttributes(std::unique_ptr<ImportedAttributesRepo> attributes) override;

    const ImportedAttributesRepo *getImportedAttributes() const override { return _importedAttributes.get(); }

    std::shared_ptr<search::attribute::ReadableAttributeVector> readable_attribute_vector(const string& name) const override;

    TransientResourceUsage get_transient_resource_usage() const override;
};

} // namespace proton

