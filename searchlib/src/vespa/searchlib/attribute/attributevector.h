// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "address_space_usage.h"
#include "changevector.h"
#include "readable_attribute_vector.h"
#include "basename.h"
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchcommon/attribute/status.h>
#include <vespa/searchcommon/common/range.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/common/i_compactable_lid_space.h>
#include <vespa/searchlib/common/commit_param.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/util/generationholder.h>
#include <vespa/vespalib/util/time.h>
#include <cmath>
#include <mutex>
#include <shared_mutex>

namespace document {
    class ArithmeticValueUpdate;
    class AssignValueUpdate;
    class MapValueUpdate;
    class FieldValue;
}

namespace vespalib {
    class GenericHeader;
    class Executor;
}

namespace vespalib::alloc {
    class MemoryAllocator;
    class Alloc;
}

namespace vespalib::eval { struct Value; }

namespace search {

    template <typename T> class ComponentGuard;
    class AttributeReadGuard;
    class AttributeSaver;
    class IEnumStore;
    class IAttributeSaveTarget;
    class IDocidWithWeightPostingStore;
    class QueryTermSimple;
    class QueryTermUCS4;

    namespace fef {
        class TermFieldMatchData;
    }

    namespace attribute {
        class AttributeHeader;
        class IPostingListSearchContext;
        class IPostingListAttributeBase;
        class Interlock;
        class InterlockGuard;
        class SearchContext;
        class MultiValueMappingBase;
        class Config;
        class ValueModifier;
        class EnumModifier;
    }

    namespace fileutil {
        class LoadedBuffer;
    }
}

namespace search {

using search::attribute::WeightedType;
using search::attribute::Status;
using document::ArithmeticValueUpdate;
using document::MapValueUpdate;
using document::FieldValue;

class IExtendAttribute
{
public:
    virtual bool add(int64_t, int32_t = 1) { return false; }
    virtual bool add(double, int32_t = 1) { return false; }
    virtual bool add(const char *, int32_t = 1) { return false; }
    virtual bool add(std::span<const char>, int32_t = 1) { return false; }
    virtual bool add(const vespalib::eval::Value&, int32_t = 1) { return false; }

    virtual ~IExtendAttribute() = default;
};

class AttributeVector : public attribute::IAttributeVector,
                        public common::ICompactableLidSpace,
                        public attribute::ReadableAttributeVector
{
protected:
    using Config = search::attribute::Config;
    using CollectionType = search::attribute::CollectionType;
    using BasicType = search::attribute::BasicType;
    using QueryTermSimpleUP = std::unique_ptr<QueryTermSimple>;
    using QueryPacketT = std::string_view;
    using string_view = std::string_view;
    using ValueModifier = attribute::ValueModifier;
    using EnumModifier = attribute::EnumModifier;
public:
    using SP = std::shared_ptr<AttributeVector>;

    using GenerationHandler = vespalib::GenerationHandler;
    using GenerationHolder = vespalib::GenerationHolder;
    using generation_t = GenerationHandler::generation_t;

    ~AttributeVector() override;
protected:
    /**
     * Will update statistics by calling onUpdateStat if necessary.
     */
    void updateStat(bool forceUpdate);

    void updateStatistics(uint64_t numValues, uint64_t numUniqueValue, uint64_t allocated,
                          uint64_t used, uint64_t dead, uint64_t onHold);

    AttributeVector(std::string_view baseFileName, const Config & c);

    void checkSetMaxValueCount(int index) {
        if (index > _highestValueCount.load(std::memory_order_relaxed)) {
            _highestValueCount.store(index, std::memory_order_relaxed);
        }
    }

    void setEnum(bool hasEnum_)          { _hasEnum = hasEnum_; }
    void setNumDocs(uint32_t n)          { _status.setNumDocs(n); }
    void incNumDocs()                    { _status.incNumDocs(); }

public:

    EnumModifier getEnumModifier();
protected:
    ValueModifier getValueModifier();

    void updateCommittedDocIdLimit() {
        if (_uncommittedDocIdLimit != 0) {
            if (_uncommittedDocIdLimit > _committedDocIdLimit.load(std::memory_order_relaxed)) {
                _committedDocIdLimit.store(_uncommittedDocIdLimit, std::memory_order_release);
            }
            _uncommittedDocIdLimit = 0;
        }
    }

public:
    void incGeneration();
    void reclaim_unused_memory();

    generation_t get_oldest_used_generation() const {
        return _genHandler.get_oldest_used_generation();
    }

    generation_t getCurrentGeneration() const {
        return _genHandler.getCurrentGeneration();
    }

    /**
     * Used for unit testing. Must not be called from the thread owning the enum guard(s).
     */
    bool hasActiveEnumGuards();

    virtual IExtendAttribute * getExtendInterface();

    /**
     * Returns the number of readers holding a generation guard.
     * Should be called by the writer thread.
     **/
    uint32_t getGenerationRefCount(generation_t gen) const {
        return _genHandler.getGenerationRefCount(gen);
    }

protected:
    /**
     * Called when a new document has been added, but only for
     * multivalue, enumerated, and string attributes.
     * Can be overridden by subclasses that need to resize structures as a result of this.
     * Should return true if underlying structures were resized.
     **/
    virtual bool onAddDoc(DocId) { return false; }

    const GenerationHandler & getGenerationHandler() const {
        return _genHandler;
    }

    GenerationHandler & getGenerationHandler() {
        return _genHandler;
    }

    GenerationHolder & getGenerationHolder() {
        return _genHolder;
    }

    const GenerationHolder& getGenerationHolder() const {
        return _genHolder;
    }

    template<typename T>
    bool clearDoc(ChangeVectorT< ChangeTemplate<T> > &changes, DocId doc);

    template<typename T>
    bool update(ChangeVectorT< ChangeTemplate<T> > &changes, DocId doc, const T & v) __attribute__((noinline));

    template<typename T>
    bool append(ChangeVectorT< ChangeTemplate<T> > &changes, DocId doc, const T &v, int32_t w, bool doCount = true) __attribute__((noinline));
    template<typename T, typename Accessor>
    bool append(ChangeVectorT< ChangeTemplate<T> > &changes, DocId doc, Accessor & ac) __attribute__((noinline));

    template<typename T>
    bool remove(ChangeVectorT< ChangeTemplate<T> > & changes, DocId doc, const T &v, int32_t w);

    template<typename T>
    bool adjustWeight(ChangeVectorT< ChangeTemplate<T> > &changes, DocId doc, const T &v, const ArithmeticValueUpdate &wd);

    template<typename T>
    bool adjustWeight(ChangeVectorT< ChangeTemplate<T> > &changes, DocId doc, const T &v, const document::AssignValueUpdate &wu);

    template <typename T>
    static int32_t
    applyWeightChange(int32_t weight, const ChangeTemplate<T> &weightChange) {
        if (weightChange._type == ChangeBase::INCREASEWEIGHT) {
            return weight + weightChange._weight;
        } else if (weightChange._type == ChangeBase::MULWEIGHT) {
            return weight * weightChange._weight;
        } else if (weightChange._type == ChangeBase::DIVWEIGHT) {
            return weight / weightChange._weight;
        } else if (weightChange._type == ChangeBase::SETWEIGHT) {
            return weightChange._weight;
        }
        return weight;
    }

    template<typename T>
    bool applyArithmetic(ChangeVectorT< ChangeTemplate<T> > &changes, DocId doc, const T &v, const ArithmeticValueUpdate & arithm);

    static double round(double v, double & r) { return r = v; }
    static largeint_t round(double v, largeint_t &r) { return r = static_cast<largeint_t>(::floor(v+0.5)); }

    template <typename BaseType, typename LargeType>
    static BaseType
    applyArithmetic(const BaseType &value, double operand, ChangeBase::Type type)
    {
        if (attribute::isUndefined(value)) {
            return value;
        } else if (type == ChangeBase::ADD) {
            return value + static_cast<LargeType>(operand);
        } else if (type == ChangeBase::SUB) {
            return value - static_cast<LargeType>(operand);
        } else if (type == ChangeBase::MUL) {
            LargeType r;
            return round((static_cast<double>(value) * operand), r);
        } else if (type == ChangeBase::DIV) {
            LargeType r;
            return round(static_cast<double>(value) / operand, r);
        }
        return value;
    }

    virtual vespalib::MemoryUsage getEnumStoreValuesMemoryUsage() const;
    virtual void populate_address_space_usage(AddressSpaceUsage& usage) const;

    const std::shared_ptr<vespalib::alloc::MemoryAllocator>& get_memory_allocator() const noexcept { return _memory_allocator; }
    vespalib::alloc::Alloc get_initial_alloc();
public:
    bool isLoaded() const { return _loaded; }
    void logEnumStoreEvent(const char *reason, const char *stage);

    /** Return the fixed length of the attribute. If 0 then you must inquire each document. */
    size_t getFixedWidth() const override;
    BasicType getInternalBasicType() const;
    CollectionType getInternalCollectionType() const;
    bool hasArrayType() const;
    bool getIsFilter() const override final;
    bool getIsFastSearch() const override final;
    bool isMutable() const;

    const Config &getConfig() const noexcept { return *_config; }
    void update_config(const Config& cfg);
    const attribute::BaseName & getBaseFileName() const { return _baseFileName; }
    void setBaseFileName(std::string_view name) { _baseFileName = name; }
    bool isUpdateableInMemoryOnly() const { return _isUpdateableInMemoryOnly; }

    const std::string & getName() const override final { return _baseFileName.getAttributeName(); }

    bool hasEnum() const override final;
    uint32_t getMaxValueCount() const override;
    uint32_t getEnumMax() const { return _enumMax; }

    // Implements IAttributeVector
    uint32_t getNumDocs() const override final { return _status.getNumDocs(); }
    const std::atomic<uint32_t>& getCommittedDocIdLimitRef() noexcept { return _committedDocIdLimit; }
    void setCommittedDocIdLimit(uint32_t committedDocIdLimit) {
        _committedDocIdLimit.store(committedDocIdLimit, std::memory_order_release);
    }
    void updateUncommittedDocIdLimit(DocId doc) {
        if (_uncommittedDocIdLimit <= doc)  {
            _uncommittedDocIdLimit = doc + 1;
        }
    }
    void clear_uncommitted_doc_id_limit() noexcept { _uncommittedDocIdLimit = 0; }

    const Status & getStatus() const { return _status; }
    Status & getStatus() { return _status; }

    AddressSpaceUsage getAddressSpaceUsage() const;

    BasicType::Type getBasicType() const override final;
    CollectionType::Type getCollectionType() const override final;
    uint32_t getCommittedDocIdLimit() const override final { return _committedDocIdLimit.load(std::memory_order_acquire); }
    bool isImported() const override;

    /**
     * Saves this attribute vector to named file(s)
     */
    bool save(std::string_view fileName);

    /** Saves this attribute vector to file(s) **/
    bool save();

    /** Saves this attribute vector using the given saveTarget and fileName **/
    bool save(IAttributeSaveTarget & saveTarget, std::string_view fileName);

    attribute::AttributeHeader createAttributeHeader(std::string_view fileName) const;

    /** Returns whether this attribute has load data files on disk **/
    bool hasLoadData() const;

    bool isEnumeratedSaveFormat() const;
    bool load();
    bool load(vespalib::Executor * executor);
    void commit() { commit(false); }
    void commit(bool forceUpdateStats);
    void commit(const CommitParam & param);
    void setCreateSerialNum(uint64_t createSerialNum);
    uint64_t getCreateSerialNum() const;
    virtual uint32_t getVersion() const;

////// Interface to access single documents.
    /**
     * Interface to access the individual elements both for update and
     * retrival are type specific.  They are accessed by their proper
     * type.
     */

    virtual uint32_t clearDoc(DocId doc) = 0;

    // Implements IAttributeVector
    virtual uint32_t get(DocId doc, EnumHandle *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, const char **v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, largeint_t *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, double *v, uint32_t sz) const override = 0;

    virtual uint32_t get(DocId doc, std::string *v, uint32_t sz) const = 0;


    // Implements IAttributeVector
    virtual uint32_t get(DocId doc, WeightedEnum *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, WeightedString *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, WeightedConstChar *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, WeightedInt *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, WeightedFloat *v, uint32_t sz) const override = 0;

    virtual int32_t getWeight(DocId doc, uint32_t idx) const;

    // Implements IAttributeVector
    bool findEnum(const char *value, EnumHandle &e) const override;
    std::vector<EnumHandle> findFoldedEnums(const char *) const override;

    const char * getStringFromEnum(EnumHandle e) const override;

///// Modify API
    virtual void onCommit() = 0;
    virtual bool addDoc(DocId &doc) = 0;
    virtual bool addDocs(DocId & startDoc, DocId & lastDoc, uint32_t numDocs);
    virtual bool addDocs(uint32_t numDocs);
    bool apply(DocId doc, const MapValueUpdate &map);

////// Search API

    const IDocidPostingStore* as_docid_posting_store() const override;
    const IDocidWithWeightPostingStore *as_docid_with_weight_posting_store() const override;

    const tensor::ITensorAttribute *asTensorAttribute() const override;
    const attribute::IMultiValueAttribute* as_multi_value_attribute() const override;

    std::unique_ptr<attribute::SearchContext> getSearch(QueryPacketT searchSpec, const attribute::SearchContextParams &params) const;
    std::unique_ptr<attribute::ISearchContext> createSearchContext(QueryTermSimpleUP term,
                                                      const attribute::SearchContextParams &params) const override;
    virtual std::unique_ptr<attribute::SearchContext> getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams &params) const = 0;
    virtual const IEnumStore* getEnumStoreBase() const;
    virtual IEnumStore* getEnumStoreBase();
    virtual const attribute::MultiValueMappingBase *getMultiValueBase() const;

private:
    /**
     * This is called before adding docs will commence.
     * The attributes can then ensure space is available to avoid reallocation as it grows.
     * @param docIdLimit
     */
    virtual void onAddDocs(DocId docIdLimit) = 0;
    void divideByZeroWarning();
    virtual bool applyWeight(DocId doc, const FieldValue &fv, const ArithmeticValueUpdate &wAdjust);
    virtual bool applyWeight(DocId doc, const FieldValue& fv, const document::AssignValueUpdate& wAdjust);
    virtual void onSave(IAttributeSaveTarget & saveTarget);
    virtual bool onLoad(vespalib::Executor * executor);


    attribute::BaseName                   _baseFileName;
    std::unique_ptr<Config>               _config;
    std::shared_ptr<attribute::Interlock> _interlock;
    mutable std::shared_mutex             _enumLock;
    GenerationHandler                     _genHandler;
    GenerationHolder                      _genHolder;
    Status                                _status;
    std::atomic<int>                      _highestValueCount;
    uint32_t                              _enumMax;
    std::atomic<uint32_t>                 _committedDocIdLimit; // docid limit for search
    uint32_t                              _uncommittedDocIdLimit; // based on queued changes
    uint64_t                              _createSerialNum;
    std::atomic<uint64_t>                 _compactLidSpaceGeneration;
    bool                                  _hasEnum;
    bool                                  _loaded;
    bool                                  _isUpdateableInMemoryOnly;
    vespalib::steady_time                 _nextStatUpdateTime;
    std::shared_ptr<vespalib::alloc::MemoryAllocator> _memory_allocator;
    std::atomic<uint64_t>                 _size_on_disk;

    /// Clean up [0, firstUsed>
    virtual void reclaim_memory(generation_t oldest_used_gen);
    virtual void before_inc_generation(generation_t current_gen);
    virtual void onUpdateStat() = 0;
    friend class AttributeTest;

public:
    ////// Locking strategy interface.
    /**
     * Used to guard that a value you reference will always reference
     * a value. It might not be the same value, but at least it will
     * be a value for that document.  The guarantee holds as long as
     * the guard is alive.
    */
    GenerationHandler::Guard takeGenerationGuard() { return _genHandler.takeGuard(); }
    bool headerTypeOK(const vespalib::GenericHeader &header) const;
    bool hasMultiValue() const override final;
    bool hasWeightedSetType() const override final;
    /**
     * Should be called by the writer thread.
     */
    void update_oldest_used_generation() {
        _genHandler.update_oldest_used_generation();
    }

    /**
     * Add reserved initial document with docId 0 and undefined value.
     */
    void addReservedDoc();
    /**
     * set undefined values for reserved document 0.
     */
    void set_reserved_doc_values();
    bool getEnumeratedSave() const { return _hasEnum; }

    virtual attribute::IPostingListAttributeBase * getIPostingListAttributeBase();
    virtual const attribute::IPostingListAttributeBase * getIPostingListAttributeBase() const;
    bool hasPostings();
    virtual uint64_t getUniqueValueCount() const;
    virtual uint64_t getTotalValueCount() const;
    void compactLidSpace(uint32_t wantedLidLimit) override;
    virtual void clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space);
    bool wantShrinkLidSpace() const { return _committedDocIdLimit.load(std::memory_order_relaxed) < getNumDocs(); }
    bool canShrinkLidSpace() const override;
    void shrinkLidSpace() override;
    virtual void onShrinkLidSpace();
    size_t getEstimatedShrinkLidSpaceGain() const override;

    std::unique_ptr<attribute::AttributeReadGuard> makeReadGuard(bool stableEnumGuard) const override;

    void setInterlock(const std::shared_ptr<attribute::Interlock> &interlock);

    const std::shared_ptr<attribute::Interlock> &getInterlock() const {
        return _interlock;
    }

    std::unique_ptr<AttributeSaver> initSave(std::string_view fileName);

    virtual std::unique_ptr<AttributeSaver> onInitSave(std::string_view fileName);
    virtual uint64_t getEstimatedSaveByteSize() const;

    static bool isEnumerated(const vespalib::GenericHeader &header);

    virtual vespalib::MemoryUsage getChangeVectorMemoryUsage() const;
    bool commitIfChangeVectorTooLarge();

    void drain_hold(uint64_t hold_limit);

    void set_size_on_disk(uint64_t value) noexcept {_size_on_disk.store(value, std::memory_order_release); }
    void set_size_on_disk(const IAttributeSaveTarget& target);
    uint64_t size_on_disk() const noexcept { return _size_on_disk.load(std::memory_order_acquire); }
};

}
