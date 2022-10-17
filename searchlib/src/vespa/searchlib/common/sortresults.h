// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rankedhit.h"
#include "sortspec.h"
#include <vespa/vespalib/stllike/allocator.h>
#include <vespa/vespalib/util/doom.h>

#define INSERT_SORT_LEVEL 80

namespace search::attribute {
    class IAttributeContext;
    class IAttributeVector;
}
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

class FastS_DefaultResultSorter : public FastS_IResultSorter
{
private:
    static FastS_DefaultResultSorter _instance;

public:
    static FastS_DefaultResultSorter *instance() { return &_instance; }
    void sortResults(search::RankedHit a[], uint32_t n, uint32_t ntop) override {
        return FastS_SortResults(a, n, ntop);
    }
};

//-----------------------------------------------------------------------------

class FastS_SortSpec : public FastS_IResultSorter
{
private:
    friend class MultilevelSortTest;
public:
    enum {
        ASC_VECTOR  = 0,
        DESC_VECTOR = 1,
        ASC_RANK    = 2,
        DESC_RANK   = 3,
        ASC_DOCID   = 4,
        DESC_DOCID  = 5
    };

    struct VectorRef
    {
        VectorRef(uint32_t type, const search::attribute::IAttributeVector * vector, const search::common::BlobConverter *converter) noexcept
            : _type(type),
              _vector(vector),
              _converter(converter)
        { }
        uint32_t                 _type;
        const search::attribute::IAttributeVector *_vector;
        const search::common::BlobConverter *_converter;
    };

    struct SortData : public search::RankedHit
    {
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
    vespalib::string         _documentmetastore;
    uint16_t                 _partitionId;
    vespalib::Doom           _doom;
    const ConverterFactory & _ucaFactory;
    search::common::SortSpec _sortSpec;
    VectorRefList            _vectors;
    BinarySortData           _binarySortData;
    SortDataArray            _sortDataArray;

    bool Add(search::attribute::IAttributeContext & vecMan, const search::common::SortInfo & sInfo);
    void initSortData(const search::RankedHit *a, uint32_t n);
    int initSortData(const VectorRef & vec, const search::RankedHit & hit, size_t offset);

public:
    FastS_SortSpec(const FastS_SortSpec &) = delete;
    FastS_SortSpec & operator = (const FastS_SortSpec &) = delete;
    FastS_SortSpec(vespalib::stringref documentmetastore, uint32_t partitionId, const vespalib::Doom & doom, const ConverterFactory & ucaFactory);
    ~FastS_SortSpec() override;

    std::pair<const char *, size_t> getSortRef(size_t i) const {
        return {(const char*)(&_binarySortData[0] + _sortDataArray[i]._idx), _sortDataArray[i]._len };
    }
    bool Init(const vespalib::string & sortSpec, search::attribute::IAttributeContext & vecMan);
    void sortResults(search::RankedHit a[], uint32_t n, uint32_t topn) override;
    uint32_t getSortDataSize(uint32_t offset, uint32_t n);
    void copySortData(uint32_t offset, uint32_t n, uint32_t *idx, char *buf);
    void freeSortData();
    void initWithoutSorting(const search::RankedHit * hits, uint32_t hitCnt);
};

//-----------------------------------------------------------------------------

