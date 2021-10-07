// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/rankedhit.h>
#include <vespa/searchlib/common/sortspec.h>
#include <algorithm>
#include <vector>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/doom.h>

#define INSERT_SORT_LEVEL 80

namespace search {
    namespace attribute {
        class IAttributeContext;
        class IAttributeVector;
    }
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
    virtual ~FastS_IResultSorter() {}

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
    static FastS_DefaultResultSorter __instance;

public:
    static FastS_DefaultResultSorter *instance() { return &__instance; }
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
        VectorRef(uint32_t type, const search::attribute::IAttributeVector * vector, const search::common::BlobConverter *converter)
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
        uint32_t _idx;
        uint32_t _len;
        uint32_t _pos;
    };

private:
    typedef std::vector<VectorRef> VectorRefList;
    typedef vespalib::Array<uint8_t> BinarySortData;
    typedef vespalib::Array<SortData> SortDataArray;
    using ConverterFactory = search::common::ConverterFactory;
    uint16_t                 _partitionId;
    vespalib::Doom           _doom;
    const ConverterFactory & _ucaFactory;
    int                      _method;
    search::common::SortSpec _sortSpec;
    VectorRefList            _vectors;
    BinarySortData           _binarySortData;
    SortDataArray            _sortDataArray;

    bool Add(search::attribute::IAttributeContext & vecMan, const search::common::SortInfo & sInfo);
    void initSortData(const search::RankedHit *a, uint32_t n);
    uint8_t * realloc(uint32_t n, size_t & variableWidth, uint32_t & available, uint32_t & dataSize, uint8_t *mySortData);

public:
    FastS_SortSpec(const FastS_SortSpec &) = delete;
    FastS_SortSpec & operator = (const FastS_SortSpec &) = delete;
    FastS_SortSpec(uint32_t partitionId, const vespalib::Doom & doom, const ConverterFactory & ucaFactory, int method=2);
    ~FastS_SortSpec();

    std::pair<const char *, size_t> getSortRef(size_t i) const {
        return std::pair<const char *, size_t>((const char*)(&_binarySortData[0] + _sortDataArray[i]._idx),
                                               _sortDataArray[i]._len);
    }
    bool Init(const vespalib::string & sortSpec, search::attribute::IAttributeContext & vecMan);
    void sortResults(search::RankedHit a[], uint32_t n, uint32_t topn) override;
    uint32_t getSortDataSize(uint32_t offset, uint32_t n);
    void copySortData(uint32_t offset, uint32_t n, uint32_t *idx, char *buf);
    void freeSortData();
    void initWithoutSorting(const search::RankedHit * hits, uint32_t hitCnt);
    static int Compare(const FastS_SortSpec *self, const SortData &a, const SortData &b);
};

//-----------------------------------------------------------------------------

