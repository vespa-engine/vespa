// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "address_space_usage.h"
#include "changevector.h"
#include "readable_attribute_vector.h"
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchcommon/attribute/status.h>
#include <vespa/searchcommon/common/range.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/common/address_space.h>
#include <vespa/searchlib/common/i_compactable_lid_space.h>
#include <vespa/searchlib/common/identifiable.h>
#include <vespa/searchlib/common/rcuvector.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/objects/identifiable.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/fastos/time.h>
#include <cmath>
#include <mutex>
#include <shared_mutex>

class Fast_BufferedFile;
class FastOS_FileInterface;

namespace document {
    class ArithmeticValueUpdate;
    class MapValueUpdate;
    class FieldValue;
}

namespace vespalib {
    class GenericHeader;
}

namespace search {

    template <typename T> class ComponentGuard;
    class AttributeReadGuard;
    class AttributeWriteGuard;
    class AttributeSaver;
    class EnumStoreBase;
    class IAttributeSaveTarget;
    class IDocumentWeightAttribute;
    class QueryTermSimple;
    class QueryTermBase;

    namespace fef {
        class TermFieldMatchData;
    }

    namespace attribute {
        class AttributeHeader;
        class IPostingListSearchContext;
        class IPostingListAttributeBase;
        class Interlock;
        class InterlockGuard;
        class MultiValueMappingBase;
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

template <typename T>
class UnWeightedType
{
public:
    UnWeightedType() : _value(T()) { }
    UnWeightedType(T v) : _value(v) { }
    const T & getValue() const { return _value; }
    void setValue(const T & v) { _value = v; }
    int32_t getWeight()  const { return 1; }
    void setWeight(int32_t w)  { (void) w; }

    bool operator==(const UnWeightedType<T> & rhs) const {
        return _value == rhs._value;
    }

private:
    T       _value;
};

template <typename T>
vespalib::asciistream & operator << (vespalib::asciistream & os, const UnWeightedType<T> & v);

class IExtendAttribute
{
public:
    virtual bool add(int64_t, int32_t = 1) { return false; }
    virtual bool add(double, int32_t = 1) { return false; }
    virtual bool add(const char *, int32_t = 1) { return false; }
    
    virtual ~IExtendAttribute() {}
};

class AttributeVector : public vespalib::Identifiable,
                        public attribute::IAttributeVector,
                        public common::ICompactableLidSpace,
                        public attribute::ReadableAttributeVector
{
protected:
    using Config = search::attribute::Config;
    using CollectionType = search::attribute::CollectionType;
    using BasicType = search::attribute::BasicType;
    using QueryTermSimpleUP = std::unique_ptr<QueryTermSimple>;
    using QueryPacketT = vespalib::stringref;
    using LoadedBufferUP = std::unique_ptr<fileutil::LoadedBuffer>;
public:
    typedef std::shared_ptr<AttributeVector> SP;
    class BaseName : public vespalib::string
    {
    public:
        typedef vespalib::string string;
        BaseName(const vespalib::stringref &s)
            : string(s),
              _name(createAttributeName(s))
        { }
        BaseName & operator = (const vespalib::stringref & s) {
            BaseName n(s);
            std::swap(*this, n);
            return *this;
        }

        BaseName(const vespalib::stringref &base,
                 const vespalib::stringref &snap,
                 const vespalib::stringref &name);
        ~BaseName();

        string getIndexName() const;
        string getSnapshotName() const;
        const string & getAttributeName() const { return _name; }
        string getDirName() const;
    private:
        static string createAttributeName(const vespalib::stringref & s);
        string _name;
    };

    using GenerationHandler = vespalib::GenerationHandler;
    using GenerationHolder = vespalib::GenerationHolder;
    typedef GenerationHandler::generation_t generation_t;

    virtual ~AttributeVector();
protected:
    /**
     * Will update statistics by calling onUpdateStat if necessary.
     */
    void updateStat(bool forceUpdate);

    void
    updateStatistics(uint64_t numValues,
                     uint64_t numUniqueValue,
                     uint64_t allocated,
                     uint64_t used,
                     uint64_t dead,
                     uint64_t onHold);

    void performCompactionWarning();

    void getByType(DocId doc, const char *&v) const {
        char tmp[1024]; v = getString(doc, tmp, sizeof(tmp));
    }

    void getByType(DocId doc, vespalib::string &v) const {
        char tmp[1024]; v = getString(doc, tmp, sizeof(tmp));
    }

    void getByType(DocId doc, largeint_t & v) const {
        v = getInt(doc);
    }

    void getByType(DocId doc, double &v) const {
        v = getFloat(doc);
    }

    uint32_t getByType(DocId doc, const char **v, uint32_t sz) const {
        return get(doc, v, sz);
    }

    uint32_t getByType(DocId doc, vespalib::string *v, uint32_t sz) const {
        return get(doc, v, sz);
    }

    uint32_t getByType(DocId doc, largeint_t * v, uint32_t sz) const {
        return get(doc, v, sz);
    }

    uint32_t getByType(DocId doc, double *v, uint32_t sz) const {
        return get(doc, v, sz);
    }


    AttributeVector(const vespalib::stringref &baseFileName, const Config & c);

    void checkSetMaxValueCount(int index) {
        _highestValueCount = std::max(index, _highestValueCount);
    }

    void setEnumMax(uint32_t e)          { _enumMax = e; setEnum(); }
    void setEnum(bool hasEnum_=true)     { _hasEnum = hasEnum_; }
    void setSortedEnum(bool sorted=true) { _hasSortedEnum = sorted; }
    void setNumDocs(uint32_t n)          { _status.setNumDocs(n); }
    void incNumDocs()                    { _status.incNumDocs(); }

    LoadedBufferUP loadDAT();
    LoadedBufferUP loadIDX();
    LoadedBufferUP loadWeight();
    LoadedBufferUP loadUDAT();

    class ValueModifier
    {
    public:
        ValueModifier(AttributeVector &attr);
        ValueModifier(const ValueModifier &rhs);
        ~ValueModifier();
    private:
        AttributeVector * stealAttr() const {
            AttributeVector * ret(_attr);
            _attr = NULL;
            return ret;
        }

        mutable AttributeVector * _attr;
    };

    class EnumModifier
    {
        std::unique_lock<std::shared_timed_mutex> _enumLock;
    public:
        EnumModifier(std::shared_timed_mutex &lock,
                     attribute::InterlockGuard &interlockGuard)
            : _enumLock(lock)
        {
            (void) interlockGuard;
        }
        EnumModifier(EnumModifier &&rhs)
            : _enumLock(std::move(rhs._enumLock))
        { }
        EnumModifier &operator=(EnumModifier &&rhs)
        {
            _enumLock = std::move(rhs._enumLock);
            return *this;
        }
        virtual ~EnumModifier() { }
    };

    EnumModifier getEnumModifier();
    ValueModifier getValueModifier() { return ValueModifier(*this); }

    void updateUncommittedDocIdLimit(DocId doc) {
        if (_uncommittedDocIdLimit <= doc)  {
            _uncommittedDocIdLimit = doc + 1;
        }
    }

    void updateCommittedDocIdLimit() {
        if (_uncommittedDocIdLimit != 0) {
            if (_uncommittedDocIdLimit > _committedDocIdLimit) {
                std::atomic_thread_fence(std::memory_order_release);
                _committedDocIdLimit = _uncommittedDocIdLimit;
            }
            _uncommittedDocIdLimit = 0;
        }
    }
    
public:
    std::unique_ptr<FastOS_FileInterface> openDAT();
    std::unique_ptr<FastOS_FileInterface> openIDX();
    std::unique_ptr<FastOS_FileInterface> openWeight();
    std::unique_ptr<FastOS_FileInterface> openUDAT();
    void incGeneration();
    void removeAllOldGenerations();

    generation_t getFirstUsedGeneration() const {
        return _genHandler.getFirstUsedGeneration();
    }

    generation_t getCurrentGeneration() const {
        return _genHandler.getCurrentGeneration();
    }

    /**
     * Used for unit testing. Must not be called from the thread owning the enum guard(s).
     */
    bool hasActiveEnumGuards();

    virtual IExtendAttribute * getExtendInterface();

protected:
    /**
     * Called when a new document has been added, but only for
     * multivalue, enumerated, and string attributes.
     * Can be overridden by subclasses that need to resize structures as a result of this.
     * Should return true if underlying structures were resized.
     **/
    virtual bool onAddDoc(DocId) { return false; }

    /**
     * Returns the number of readers holding a generation guard.
     * Should be called by the writer thread.
     */
    uint32_t getGenerationRefCount(generation_t gen) const {
        return _genHandler.getGenerationRefCount(gen);
    }

    const GenerationHandler & getGenerationHandler() const {
        return _genHandler;
    }

    GenerationHandler & getGenerationHandler() {
        return _genHandler;
    }

    GenerationHolder & getGenerationHolder() {
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

    template <typename T>
    static int32_t
    applyWeightChange(int32_t weight, const ChangeTemplate<T> &weightChange) {
        if (weightChange._type == ChangeBase::INCREASEWEIGHT) {
            return weight + weightChange._weight;
        } else if (weightChange._type == ChangeBase::MULWEIGHT) {
            return weight * weightChange._weight;
        } else if (weightChange._type == ChangeBase::DIVWEIGHT) {
            return weight / weightChange._weight;
        }
        return weight;
    }

    template<typename T>
    bool applyArithmetic(ChangeVectorT< ChangeTemplate<T> > &changes, DocId doc, const T &v, const ArithmeticValueUpdate & arithm);

    static double round(double v, double & r) { return r = v; }
    static largeint_t round(double v, largeint_t &r) { return r = static_cast<largeint_t>(::floor(v+0.5)); }

    template <typename BaseType, typename ChangeData>
    static BaseType
    applyArithmetic(const BaseType &value,
                    const ChangeTemplate<ChangeData> & arithmetic)
    {
        typedef typename ChangeData::DataType LargeType;
        if (attribute::isUndefined(value)) {
            return value;
        } else if (arithmetic._type == ChangeBase::ADD) {
            return value + static_cast<LargeType>(arithmetic._arithOperand);
        } else if (arithmetic._type == ChangeBase::SUB) {
            return value - static_cast<LargeType>(arithmetic._arithOperand);
        } else if (arithmetic._type == ChangeBase::MUL) {
            LargeType r;
            return round((static_cast<double>(value) *
                          arithmetic._arithOperand), r);
        } else if (arithmetic._type == ChangeBase::DIV) {
            LargeType r;
            return round(static_cast<double>(value) /
                         arithmetic._arithOperand, r);
        }
        return value;
    }

    virtual AddressSpace getEnumStoreAddressSpaceUsage() const;
    virtual AddressSpace getMultiValueAddressSpaceUsage() const;

public:
    DECLARE_IDENTIFIABLE_ABSTRACT(AttributeVector);
    bool isLoaded() const { return _loaded; }

    /** Return the fixed length of the attribute. If 0 then you must inquire each document. */
    virtual size_t getFixedWidth() const override;
    const Config &getConfig() const { return _config; }
    BasicType getInternalBasicType() const { return _config.basicType(); }
    CollectionType getInternalCollectionType() const { return _config.collectionType(); }
    const BaseName & getBaseFileName() const { return _baseFileName; }
    void setBaseFileName(const vespalib::stringref & name) { _baseFileName = name; }

    // Implements IAttributeVector
    virtual const vespalib::string & getName() const override;

    bool hasArrayType() const { return _config.collectionType().isArray(); }
    bool hasEnum() const override;
    bool hasSortedEnum() const { return _hasSortedEnum; }
    virtual bool hasEnum2Value() const;
    uint32_t getMaxValueCount() const override;
    uint32_t getEnumMax() const { return _enumMax; }

    // Implements IAttributeVector
    uint32_t getNumDocs() const override;
    uint32_t getCommittedDocIdLimit() const { return _committedDocIdLimit; }
    uint32_t & getCommittedDocIdLimitRef() { return _committedDocIdLimit; }
    void setCommittedDocIdLimit(uint32_t committedDocIdLimit) {
        _committedDocIdLimit = committedDocIdLimit;
    }

    const Status & getStatus() const { return _status; }
    Status & getStatus() { return _status; }

    AddressSpaceUsage getAddressSpaceUsage() const;

    // Implements IAttributeVector
    virtual BasicType::Type getBasicType() const override;
    virtual CollectionType::Type getCollectionType() const override;
    virtual bool getIsFilter() const override;
    virtual bool getIsFastSearch() const override;
    virtual uint32_t getCommittedDocIdLimitSlow() const override;
    virtual bool isImported() const override;

    /**
     * Updates the base file name of this attribute vector and saves
     * it to file(s)
     */
    bool saveAs(const vespalib::stringref &baseFileName);

    /**
     * Updates the base file name of this attribute vector and saves
     * it using the given saveTarget
     */
    bool saveAs(const vespalib::stringref &baseFileName, IAttributeSaveTarget &saveTarget);

    /** Saves this attribute vector to file(s) **/
    bool save();

    /** Saves this attribute vector using the given saveTarget **/
    bool save(IAttributeSaveTarget & saveTarget);

    attribute::AttributeHeader createAttributeHeader() const;

    /** Returns whether this attribute has load data files on disk **/
    bool hasLoadData() const;

    bool isEnumeratedSaveFormat() const;
    bool load();
    void commit(bool forceStatUpdate = false);
    void commit(uint64_t firstSyncToken, uint64_t lastSyncToken);
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
    virtual largeint_t getDefaultValue() const = 0;
    virtual void getEnumValue(const EnumHandle *v, uint32_t *e, uint32_t sz) const = 0;

    uint32_t getEnumValue(EnumHandle eh) const {
        uint32_t e(0);
        getEnumValue(&eh, &e, 1);
        return e;
    }

    // Implements IAttributeVector
    virtual uint32_t get(DocId doc, EnumHandle *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, const char **v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, largeint_t *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, double *v, uint32_t sz) const override = 0;

    virtual uint32_t get(DocId doc, vespalib::string *v, uint32_t sz) const = 0;


    // Implements IAttributeVector
    virtual uint32_t get(DocId doc, WeightedEnum *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, WeightedString *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, WeightedConstChar *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, WeightedInt *v, uint32_t sz) const override = 0;
    virtual uint32_t get(DocId doc, WeightedFloat *v, uint32_t sz) const override = 0;

    virtual int32_t getWeight(DocId doc, uint32_t idx) const;

    // Implements IAttributeVector
    bool findEnum(const char *value, EnumHandle &e) const override;
    const char * getStringFromEnum(EnumHandle e) const override;

///// Modify API
    virtual void onCommit() = 0;
    virtual bool addDoc(DocId &doc) = 0;
    virtual bool addDocs(DocId & startDoc, DocId & lastDoc, uint32_t numDocs);
    virtual bool addDocs(uint32_t numDocs);
    bool apply(DocId doc, const MapValueUpdate &map);

////// Search API

    // type-safe down-cast to attribute supporting direct document weight iterators
    const IDocumentWeightAttribute *asDocumentWeightAttribute() const override;

    const tensor::ITensorAttribute *asTensorAttribute() const override;

    /**
       - Search for equality
       - Range search
    */

    class SearchContext : public attribute::ISearchContext
    {
        template <class SC> friend class AttributeIteratorT;
        template <class SC> friend class FilterAttributeIteratorT;
        template <class PL> friend class AttributePostingListIteratorT;
        template <class PL> friend class FilterAttributePostingListIteratorT;
    protected:
        using QueryTermSimpleUP = std::unique_ptr<QueryTermSimple>;
    public:
        SearchContext(const SearchContext &) = delete;
        SearchContext & operator = (const SearchContext &) = delete;

        typedef std::unique_ptr<SearchContext> UP;
        ~SearchContext();

        // Implements attribute::ISearchContext
        virtual unsigned int approximateHits() const override;
        virtual queryeval::SearchIterator::UP createIterator(fef::TermFieldMatchData *matchData, bool strict) override;
        virtual void fetchPostings(bool strict) override;
        virtual bool valid() const override { return false; }
        virtual Int64Range getAsIntegerTerm() const override { return Int64Range(); }
        virtual const QueryTermBase &queryTerm() const override {
            return *static_cast<const QueryTermBase *>(NULL);
        }
        virtual const vespalib::string &attributeName() const override {
            return _attr.getName();
        }

        const AttributeVector & attribute() const { return _attr; }

    protected:
        SearchContext(const AttributeVector &attr);
        const AttributeVector & _attr;

        attribute::IPostingListSearchContext *_plsc;

        /**
         * Creates an attribute search iterator associated with this
         * search context. Postings lists are not used.
         **/
        virtual queryeval::SearchIterator::UP createFilterIterator(fef::TermFieldMatchData *matchData, bool strict);

        bool getIsFilter() const { return _attr.getConfig().getIsFilter(); }
    };

    SearchContext::UP getSearch(QueryPacketT searchSpec, const attribute::SearchContextParams &params) const;
    virtual attribute::ISearchContext::UP createSearchContext(QueryTermSimpleUP term,
                                                              const attribute::SearchContextParams &params) const override;
    virtual SearchContext::UP getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams &params) const = 0;
    virtual const EnumStoreBase *getEnumStoreBase() const;
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
    virtual void onSave(IAttributeSaveTarget & saveTarget);
    virtual bool onLoad();
    std::unique_ptr<FastOS_FileInterface> openFile(const char *suffix);
    LoadedBufferUP loadFile(const char *suffix);


    BaseName               _baseFileName;
    Config                 _config;
    std::shared_ptr<attribute::Interlock> _interlock;
    mutable std::shared_timed_mutex _enumLock;
    GenerationHandler      _genHandler;
    GenerationHolder       _genHolder;
    Status                 _status;
    int                    _highestValueCount;
    uint32_t               _enumMax;
    uint32_t               _committedDocIdLimit; // docid limit for search
    uint32_t               _uncommittedDocIdLimit; // based on queued changes
    uint64_t               _createSerialNum;
    uint64_t               _compactLidSpaceGeneration; 
    bool                   _hasEnum;
    bool                   _hasSortedEnum;
    bool                   _loaded;
    bool                   _enableEnumeratedSave;
    fastos::TimeStamp      _nextStatUpdateTime;

////// Locking strategy interface. only available from the Guards.
    /**
     * Used to guard that a value you reference will always reference
     * a value. It might not be the same value, but at least it will
     * be a value for that document.  The guarantee holds as long as
     * the guard is alive.
    */
    GenerationHandler::Guard takeGenerationGuard() { return _genHandler.takeGuard(); }

    /// Clean up [0, firstUsed>
    virtual void removeOldGenerations(generation_t firstUsed);
    virtual void onGenerationChange(generation_t generation);
    virtual void onUpdateStat() = 0;
    /**
     * Used to regulate access to critical resources. Apply the
     * reader/writer guards.
     */
    std::shared_timed_mutex & getEnumLock() { return _enumLock; }

    friend class ComponentGuard<AttributeVector>;
    friend class AttributeValueGuard;
    friend class AttributeTest;
    friend class AttributeManagerTest;
public:
    bool headerTypeOK(const vespalib::GenericHeader &header) const;
    bool hasMultiValue() const override;
    bool hasWeightedSetType() const override;
    /**
     * Should be called by the writer thread.
     */
    void updateFirstUsedGeneration() {
        _genHandler.updateFirstUsedGeneration();
    }

    /**
     * Returns true if we might still have readers.  False positives
     * are possible if writer hasn't updated first used generation
     * after last reader left.
     */
    bool hasReaders() const { return _genHandler.hasReaders(); }

    /**
     * Add reserved initial document with docId 0 and undefined value.
     */
    void addReservedDoc();
    void enableEnumeratedSave(bool enable = true);
    bool getEnumeratedSave() const { return _hasEnum && _enableEnumeratedSave; }

    virtual attribute::IPostingListAttributeBase * getIPostingListAttributeBase();
    virtual const attribute::IPostingListAttributeBase * getIPostingListAttributeBase() const;
    bool hasPostings();
    virtual uint64_t getUniqueValueCount() const;
    virtual uint64_t getTotalValueCount() const;
    virtual void compactLidSpace(uint32_t wantedLidLimit) override;
    virtual void clearDocs(DocId lidLow, DocId lidLimit);
    bool wantShrinkLidSpace() const { return _committedDocIdLimit < getNumDocs(); }
    virtual bool canShrinkLidSpace() const override;
    virtual void shrinkLidSpace() override;
    virtual void onShrinkLidSpace();
    virtual size_t getEstimatedShrinkLidSpaceGain() const override;

    virtual std::unique_ptr<attribute::AttributeReadGuard> makeReadGuard(bool stableEnumGuard) const override;

    void setInterlock(const std::shared_ptr<attribute::Interlock> &interlock);

    const std::shared_ptr<attribute::Interlock> &getInterlock() const {
        return _interlock;
    }

    std::unique_ptr<AttributeSaver> initSave();

    virtual std::unique_ptr<AttributeSaver> onInitSave();
    virtual uint64_t getEstimatedSaveByteSize() const;

    static bool isEnumerated(const vespalib::GenericHeader &header);

    virtual MemoryUsage getChangeVectorMemoryUsage() const;
};

}
