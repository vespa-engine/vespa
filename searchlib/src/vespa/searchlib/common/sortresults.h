// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rankedhit.h"
#include "sortspec.h"

#include <vespa/vespalib/stllike/allocator.h>
#include <vespa/vespalib/util/doom.h>

#include <string_view>

#define INSERT_SORT_LEVEL 80

namespace search::attribute {
class IAttributeContext;
class IAttributeVector;
class ISortBlobWriter;
} // namespace search::attribute
/**
 * Sort the given array of results.
 *
 * @param a the array of hits
 * @param n the number of hits
 * @param ntop the number of hits needed in correct order
 **/
void FastS_SortResults(search::RankedHit a[], unsigned int n, unsigned int ntop);

//-----------------------------------------------------------------------------

struct FastS_IResultSorter {
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FastS_IResultSorter() = default;

    /**
     * Sort the given array of results.
     *
     * @param a the array of hits
     * @param n the number of hits
     * @param ntop the number of hits needed in correct order
     **/
    virtual void sortResults(search::RankedHit a[], uint32_t n, uint32_t ntop) = 0;
};

//-----------------------------------------------------------------------------

class FastS_DefaultResultSorter : public FastS_IResultSorter {
private:
    static FastS_DefaultResultSorter _instance;

public:
    static FastS_DefaultResultSorter* instance() { return &_instance; }
    void sortResults(search::RankedHit a[], uint32_t n, uint32_t ntop) override {
        return FastS_SortResults(a, n, ntop);
    }
};

//-----------------------------------------------------------------------------

/**
 * Supplies numeric feature keys while FastS encodes sort blobs.
 * After binding, the provider must remain alive until consumed(). During
 * encoding, seek() is called once per hit in non-decreasing docid order;
 * get() reads the row bound by the most recent seek(). consumed() ends the
 * binding and permits the provider to release its storage.
 *
 * A provider that cannot honour a seek() records the fact and keeps going,
 * returning placeholder values from get(); failed() then stays true for the
 * rest of the encoding. The resulting sort blobs are meaningless, so the
 * caller must fail the query rather than present the hits in some other
 * order. Check failed() after encoding, before consumed().
 */
class INumericSortValueProvider {
public:
    static constexpr uint32_t invalid_ordinal = ~0u;
    virtual ~INumericSortValueProvider() = default;
    virtual uint32_t ordinal(std::string_view public_name) const = 0;
    virtual void seek(uint32_t docid) = 0;
    virtual double get(uint32_t ordinal) const = 0;
    virtual bool failed() const noexcept = 0;
    virtual void consumed() = 0;
};

class FastS_SortSpec : public FastS_IResultSorter {
private:
    friend class MultilevelSortTest;

public:
    enum {
        ASC_VECTOR = 0,
        DESC_VECTOR = 1,
        ASC_RANK = 2,
        DESC_RANK = 3,
        ASC_DOCID = 4,
        DESC_DOCID = 5,
        ASC_FEATURE = 6,
        DESC_FEATURE = 7
    };

    struct VectorRef {
        VectorRef(uint32_t type, const search::attribute::IAttributeVector* vector,
                  std::unique_ptr<search::attribute::ISortBlobWriter> writer,
                  uint32_t feature_ordinal = INumericSortValueProvider::invalid_ordinal) noexcept;
        uint32_t                                            _type;
        const search::attribute::IAttributeVector*          _vector;
        std::unique_ptr<search::attribute::ISortBlobWriter> _writer;
        uint32_t                                            _feature_ordinal;
        bool has_ascending_sort_order() const {
            return _type == ASC_VECTOR || _type == ASC_RANK || _type == ASC_DOCID || _type == ASC_FEATURE;
        }
    };

    struct SortData : public search::RankedHit {
        SortData() noexcept : RankedHit(), _idx(0u), _len(0u), _pos(0u) {}
        uint32_t _idx;
        uint32_t _len;
        uint32_t _pos;
    };

private:
    using VectorRefList = std::vector<VectorRef>;
    using BinarySortData = std::vector<uint8_t, vespalib::allocator_large<uint8_t>>;
    using SortDataArray = std::vector<SortData, vespalib::allocator_large<SortData>>;
    using ConverterFactory = search::common::ConverterFactory;
    std::string                _documentmetastore;
    uint16_t                   _partitionId;
    vespalib::Doom             _doom;
    const ConverterFactory&    _ucaFactory;
    search::common::SortSpec   _sortSpec;
    VectorRefList              _vectors;
    BinarySortData             _binarySortData;
    SortDataArray              _sortDataArray;
    INumericSortValueProvider* _numeric_provider;
    bool                       _feature_values_failed;

    bool Add(search::attribute::IAttributeContext& vecMan, const search::common::FieldSortSpec& field_sort_spec);
    double feature_value(uint32_t ordinal) const;
    void initSortData(const search::RankedHit* a, uint32_t n);
    int initSortData(const VectorRef& vec, const search::RankedHit& hit, size_t offset);

public:
    FastS_SortSpec(const FastS_SortSpec&) = delete;
    FastS_SortSpec& operator=(const FastS_SortSpec&) = delete;
    FastS_SortSpec(std::string_view documentmetastore, uint32_t partitionId, const vespalib::Doom& doom,
                   const ConverterFactory& ucaFactory);
    ~FastS_SortSpec() override;

    std::pair<const char*, size_t> getSortRef(size_t i) const {
        return {(const char*)(&_binarySortData[0] + _sortDataArray[i]._idx), _sortDataArray[i]._len};
    }
    bool Init(const std::string& sortSpec, search::attribute::IAttributeContext& vecMan);
    void sortResults(search::RankedHit a[], uint32_t n, uint32_t topn) override;
    uint32_t getSortDataSize(uint32_t offset, uint32_t n);
    void copySortData(uint32_t offset, uint32_t n, uint32_t* idx, char* buf);
    void freeSortData();
    void initWithoutSorting(const search::RankedHit* hits, uint32_t hitCnt);

    /**
     * Resolves the feature ordinals of the rank feature sort levels, if any.
     * The provider may be null; it is only required when the sort spec has
     * rank feature levels. Returns false (leaving this object unbound and
     * unusable for sorting) if a rank feature level has no sort value
     * available. The caller cannot order the hits as requested and is expected
     * to fail the query rather than to sort them some other way.
     **/
    [[nodiscard]] bool bind_numeric_provider(INumericSortValueProvider* provider);

    /**
     * True if a rank feature sort level could not be bound, or if the bound
     * provider could not supply the sort values of every hit while the sort
     * blobs were encoded. The encoded blobs order the hits by a placeholder
     * value rather than by the requested rank features, so the caller is
     * expected to fail the query.
     **/
    [[nodiscard]] bool feature_values_failed() const noexcept { return _feature_values_failed; }
};

//-----------------------------------------------------------------------------
