// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sortresults.h"
#include "sort.h"
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/make_sort_blob_writer.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/issue.h>

using vespalib::Issue;

#include <vespa/log/log.h>
LOG_SETUP(".search.attribute.sortresults");

using search::RankedHit;
using search::common::SortSpec;
using search::common::FieldSortSpec;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::attribute::make_sort_blob_writer;
using vespalib::alloc::Alloc;
using namespace vespalib;

namespace {

constexpr size_t MMAP_LIMIT = 0x2000000;

template<typename T>
class RadixHelper
{
public:
    using C = convertForSort<T, true>;
    inline typename C::UIntType
    operator()(typename C::InputType v) const {
        return C::convert(v);
    }
};

void
insertion_sort(RankedHit a[], uint32_t n) {
    uint32_t i, j;
    RankedHit swap;
    using RT = RadixHelper<search::HitRank>;
    RT R;

    for (i = 1; i < n; i++) {
        swap = a[i];
        j = i;
        while (R(swap.getRank()) > R(a[j - 1].getRank())) {
            a[j] = a[j - 1];
            if (!(--j)) break;
        }
        a[j] = swap;
    }
}

}

template<int SHIFT>
void
FastS_radixsort(RankedHit a[], uint32_t n, uint32_t ntop)
{
    uint32_t last[256], ptr[256], cnt[256];
    uint32_t sorted, remain;
    uint32_t i, j, k;
    RankedHit temp, swap;
    using RT = RadixHelper<search::HitRank>;
    RT R;

    memset(cnt, 0, 256*sizeof(uint32_t));
    // Count occurrences [NB: will fail with n < 3]
    for(i = 0; i < n - 3; i += 4) {
        cnt[(R(a[i].getRank()) >> SHIFT) & 0xFF]++;
        cnt[(R(a[i + 1].getRank()) >> SHIFT) & 0xFF]++;
        cnt[(R(a[i + 2].getRank()) >> SHIFT) & 0xFF]++;
        cnt[(R(a[i + 3].getRank()) >> SHIFT) & 0xFF]++;
    }
    for(; i < n; i++)
        cnt[(R(a[i].getRank()) >> SHIFT) & 0xFF]++;

    // Accumulate cnt positions
    sorted = (cnt[0]==n);
    ptr[0] = n-cnt[0];
    last[0] = n;
    for(i=1; i<256; i++) {
        ptr[i] = (last[i]=ptr[i-1]) - cnt[i];
        sorted |= (cnt[i]==n);
    }

    if (!sorted) {
        // Go through all permutation cycles until all
        // elements are moved or found to be already in place
        i = 255;
        remain = n;

        while(remain>0) {
            // Find first uncompleted class
            while(ptr[i]==last[i]) {
                i--;
            }

            // Stop if top candidates in place
            if (last[i]-cnt[i]>=ntop) break;

            // Grab first element to move
            j = ptr[i];
            swap = a[j];
            k = (R(swap.getRank()) >> SHIFT) & 0xFF;

            // Swap into correct class until cycle completed
            if (i!=k) {
                do {
                    temp = a[ptr[k]];
                    a[ptr[k]++] = swap;
                    k = (R((swap = temp).getRank()) >> SHIFT) & 0xFF;
                    remain--;
                } while (i!=k);
                // Place last element in cycle
                a[j] = swap;
            }
            ptr[k]++;
            remain--;
        }
    } else {
        FastS_radixsort<SHIFT - 8>(a, n, ntop);
        return;
    }

    if (SHIFT>0) {
        // Sort on next key
        for(i=0; i<256 ; i++) {
            if ((last[i]-cnt[i])<ntop) {
                if (cnt[i]>INSERT_SORT_LEVEL) {
                    if (last[i]<ntop) {
                        FastS_radixsort<SHIFT - 8>(&a[last[i]-cnt[i]], cnt[i], cnt[i]);
                    } else {
                        FastS_radixsort<SHIFT - 8>(&a[last[i]-cnt[i]], cnt[i], cnt[i]+ntop-last[i]);
                    }
                } else if (cnt[i]>1) {
                    insertion_sort(&a[last[i]-cnt[i]], cnt[i]);
                }
            }
        }
    }
}
template<>
void
FastS_radixsort<-8>(RankedHit *, uint32_t, uint32_t) {}

void
FastS_SortResults(RankedHit a[], uint32_t n, uint32_t ntop)
{
    if (n > INSERT_SORT_LEVEL) {
        FastS_radixsort<sizeof(search::HitRank)*8 - 8>(a, n, ntop);
    } else {
        insertion_sort(a, n);
    }
}

//-----------------------------------------------------------------------------

FastS_DefaultResultSorter FastS_DefaultResultSorter::_instance;

//-----------------------------------------------------------------------------

FastS_SortSpec::VectorRef::VectorRef(uint32_t type, const search::attribute::IAttributeVector* vector,
                                     std::unique_ptr<search::attribute::ISortBlobWriter> writer) noexcept
    : _type(type),
      _vector(vector),
      _writer(std::move(writer))
{
}

bool
FastS_SortSpec::Add(IAttributeContext & vecMan, const FieldSortSpec & field_sort_spec)
{
    if (field_sort_spec._field.empty())
        return false;

    uint32_t          type   = ASC_VECTOR;
    const IAttributeVector * vector(nullptr);

    if ((field_sort_spec._field.size() == 6) && (field_sort_spec._field == "[rank]")) {
        type = (field_sort_spec.is_ascending()) ? ASC_RANK : DESC_RANK;
    } else if ((field_sort_spec._field.size() == 7) && (field_sort_spec._field == "[docid]")) {
        type = (field_sort_spec.is_ascending()) ? ASC_DOCID : DESC_DOCID;
        vector = vecMan.getAttribute(_documentmetastore);
    } else {
        type = (field_sort_spec.is_ascending()) ? ASC_VECTOR : DESC_VECTOR;
        
        // Check if this is a FieldPath (contains {...} syntax)
        size_t open_brace = field_sort_spec._field.find('{');
        if (open_brace != std::string::npos && field_sort_spec._field.find('}', open_brace) != std::string::npos) {
            // FieldPath syntax: extract base attribute name
            std::string base_attr = field_sort_spec._field.substr(0, open_brace);
            vector = vecMan.getAttribute(base_attr);
            if (!vector) {
                Issue::report("sort spec: Base attribute '%s' for FieldPath '%s' is not valid. Skipped in sorting", 
                             base_attr.c_str(), field_sort_spec._field.c_str());
                return false;
            }
            if (!vector->is_sortable()) {
                Issue::report("sort spec: Base attribute '%s' for FieldPath '%s' is not sortable. Skipped in sorting", 
                             base_attr.c_str(), field_sort_spec._field.c_str());
                return false;
            }
        } else {
            // Regular attribute
            vector = vecMan.getAttribute(field_sort_spec._field);
            if ( !vector) {
                Issue::report("sort spec: Attribute vector '%s' is not valid. Skipped in sorting", field_sort_spec._field.c_str());
                return false;
            }
            if (!vector->is_sortable()) {
                Issue::report("sort spec: Attribute vector '%s' is not sortable. Skipped in sorting", field_sort_spec._field.c_str());
                return false;
            }
        }
    }

    std::unique_ptr<search::attribute::ISortBlobWriter> sort_blob_writer;
    
    // Check if this is a FieldPath and handle accordingly
    size_t open_brace = field_sort_spec._field.find('{');
    if (open_brace != std::string::npos && field_sort_spec._field.find('}', open_brace) != std::string::npos) {
        // FieldPath: extract key from {...} syntax
        size_t close_brace = field_sort_spec._field.find('}', open_brace);
        std::string key = field_sort_spec._field.substr(open_brace + 1, close_brace - open_brace - 1);
        
        // Get key and value attributes for the map
        std::string base_attr = field_sort_spec._field.substr(0, open_brace);
        const IAttributeVector* keyVector = vecMan.getAttribute(base_attr + ".key");
        const IAttributeVector* valueVector = vecMan.getAttribute(base_attr + ".value");
        
        if (!keyVector || !valueVector) {
            Issue::report("sort spec: FieldPath '%s' requires both .key and .value attributes. Skipped in sorting", 
                         field_sort_spec._field.c_str());
            return false;
        }
        
        sort_blob_writer = make_fieldpath_sort_blob_writer(keyVector, valueVector, key, field_sort_spec);
    } else {
        // Regular attribute
        sort_blob_writer = make_sort_blob_writer(vector, field_sort_spec);
    }
    
    if (vector != nullptr && !sort_blob_writer) {
        return false;
    }

    LOG(spam, "SortSpec: adding vector (%s)'%s'",
        (field_sort_spec.is_ascending()) ? "+" : "-", field_sort_spec._field.c_str());

    _vectors.emplace_back(type, vector, std::move(sort_blob_writer));

    return true;
}

void
FastS_SortSpec::initSortData(const RankedHit *hits, uint32_t n)
{
    freeSortData();
    size_t fixedWidth = 0;
    size_t variableWidth = 0;
    for (const auto & vec : _vectors) {
        if (vec._type >= ASC_DOCID) { // doc id
            fixedWidth += (vec._vector != nullptr)
                    ? vec._vector->getFixedWidth()
                    : sizeof(uint32_t) + sizeof(uint16_t);
        } else if (vec._type >= ASC_RANK) { // rank value
            fixedWidth += sizeof(search::HitRank);
        } else {
            size_t numBytes = vec._vector->getFixedWidth();
            if (numBytes == 0) { // string
                variableWidth += 11;
            } else if (!vec._vector->hasMultiValue()) {
                fixedWidth += numBytes;
            } else {
                fixedWidth += (1 + numBytes);
            }
        }
    }
    _binarySortData.resize((fixedWidth + variableWidth) * n);
    _sortDataArray.resize(n);

    size_t offset = 0;
    for (uint32_t i(0), idx(0); (i < n) && !_doom.hard_doom(); ++i) {
        uint32_t len = 0;
        for (const auto & vec : _vectors) {
            int written = initSortData(vec, hits[i], offset);
            offset += written;
            len += written;
        }
        SortData & sd = _sortDataArray[i];
        sd._docId = hits[i]._docId;
        sd._rankValue = hits[i]._rankValue;
        sd._idx = idx;
        sd._len = len;
        sd._pos = 0;
        idx += len;
    }
}

int
FastS_SortSpec::initSortData(const VectorRef & vec, const RankedHit & hit, size_t offset) {
    long written(0);
    do {
        uint8_t * mySortData = _binarySortData.data() + offset;
        uint32_t available = _binarySortData.size() - offset;
        switch (vec._type) {
            case ASC_DOCID:
                if (vec._writer != nullptr) {
                    written = vec._writer->write(hit.getDocId(), mySortData, available);
                } else {
                    if (available >= (sizeof(hit._docId) + sizeof(_partitionId))) {
                        serializeForSort<convertForSort<uint32_t, true> >(hit.getDocId(), mySortData, available);
                        serializeForSort<convertForSort<uint16_t, true> >(_partitionId, mySortData + sizeof(hit._docId), available - sizeof(hit._docId));
                        written = sizeof(hit._docId) + sizeof(_partitionId);
                    } else {
                        written = -1;
                    }
                }
                break;
            case DESC_DOCID:
                if (vec._vector != nullptr) {
                    written = vec._writer->write(hit.getDocId(), mySortData, available);
                } else {
                    if (available >= (sizeof(hit._docId) + sizeof(_partitionId))) {
                        serializeForSort<convertForSort<uint32_t, false> >(hit.getDocId(), mySortData, available);
                        serializeForSort<convertForSort<uint16_t, false> >(_partitionId, mySortData + sizeof(hit._docId), available - sizeof(hit._docId));
                        written = sizeof(hit._docId) + sizeof(_partitionId);
                    } else {
                        written = -1;
                    }
                }
                break;
            case ASC_RANK:
                written = serializeForSort<convertForSort<search::HitRank, true> >(hit.getRank(), mySortData, available);
                break;
            case DESC_RANK:
                written = serializeForSort<convertForSort<search::HitRank, false> >(hit.getRank(), mySortData, available);
                break;
            case ASC_VECTOR:
                written = vec._writer->write(hit.getDocId(), mySortData, available);
                break;
            case DESC_VECTOR:
                written = vec._writer->write(hit.getDocId(), mySortData, available);
                break;
        }
        if (written < 0) {
            _binarySortData.resize(vespalib::roundUp2inN(_binarySortData.size()*2));
        }
    } while (written < 0);
    return written;
}

FastS_SortSpec::FastS_SortSpec(std::string_view documentmetastore, uint32_t partitionId, const Doom & doom, const ConverterFactory & ucaFactory)
    : _documentmetastore(documentmetastore),
      _partitionId(partitionId),
      _doom(doom),
      _ucaFactory(ucaFactory),
      _sortSpec(),
      _vectors()
{ }


FastS_SortSpec::~FastS_SortSpec()
{
    freeSortData();
}

bool
FastS_SortSpec::Init(const std::string & sortStr, IAttributeContext & vecMan)
{
    LOG(spam, "sortStr = %s", sortStr.c_str());
    bool retval(true);
    try {
        _sortSpec = SortSpec(sortStr, _ucaFactory);
        for (auto it(_sortSpec.begin()); retval && (it != _sortSpec.end()); it++) {
            retval = Add(vecMan, *it);
        }
    } catch (const std::exception & e) {
        Issue::report("Failed parsing sortspec: %s", sortStr.c_str());
        return retval;
    }

    return retval;
}


uint32_t
FastS_SortSpec::getSortDataSize(uint32_t offset, uint32_t n)
{
    uint32_t size = 0;
    for (uint32_t i = offset; i < (offset + n); ++i) {
        size += _sortDataArray[i]._len;
    }
    return size;
}

void
FastS_SortSpec::copySortData(uint32_t offset, uint32_t n,
                             uint32_t *idx, char *buf)
{
    const uint8_t * sortData = _binarySortData.data();
    uint32_t totalLen = 0;
    for (uint32_t i = offset; i < (offset + n); ++i, ++idx) {
        const uint8_t * src = sortData + _sortDataArray[i]._idx;
        uint32_t len = _sortDataArray[i]._len;
        memcpy(buf, src, len);
        buf += len;
        *idx = totalLen;
        totalLen += len;
    }
    *idx = totalLen; // end of data index entry
}

void
FastS_SortSpec::freeSortData()
{
    {
        BinarySortData tmp;
        _binarySortData.swap(tmp);
    }
    {
        SortDataArray tmp;
        _sortDataArray.swap(tmp);
    }
}

void
FastS_SortSpec::initWithoutSorting(const RankedHit * hits, uint32_t hitCnt)
{
    initSortData(hits, hitCnt);
}


class StdSortDataCompare
{
public:
    explicit StdSortDataCompare(const uint8_t * s) : _sortSpec(s) { }
    bool operator() (const FastS_SortSpec::SortData & x, const FastS_SortSpec::SortData & y) const {
        return cmp(x, y) < 0;
    }
    int cmp(const FastS_SortSpec::SortData & a, const FastS_SortSpec::SortData & b) const {
        uint32_t len = std::min(a._len, b._len);
        int retval = memcmp(_sortSpec + a._idx, _sortSpec + b._idx, len);
        return retval ? retval : (a._len < b._len) ? -1 : 1;
    }
private:
    const uint8_t * _sortSpec;
};

class SortDataRadix
{
public:
    explicit SortDataRadix(const uint8_t * s) : _data(s) { }
    uint32_t operator () (FastS_SortSpec::SortData & a) const {
        uint32_t r(0);
        uint32_t left(a._len - a._pos);
        switch (left) {
        default:
        case 4:
            r |= _data[a._idx + a._pos + 3] << 0;
            [[fallthrough]];
        case 3:
            r |= _data[a._idx + a._pos + 2] << 8;
            [[fallthrough]];
        case 2:
            r |= _data[a._idx + a._pos + 1] << 16;
            [[fallthrough]];
        case 1:
            r |= _data[a._idx + a._pos + 0] << 24;
            [[fallthrough]];
        case 0:
            break;
        }
        a._pos += std::min(4u, left);
        return r;
    }
private:
    const uint8_t * _data;
};

class SortDataEof
{
public:
    bool operator () (const FastS_SortSpec::SortData & a) const { return a._pos >= a._len; }
    static bool alwaysEofOnCheck() { return false; }
};


void
FastS_SortSpec::sortResults(RankedHit a[], uint32_t n, uint32_t topn)
{
    initSortData(a, n);
    {
        SortData * sortData = _sortDataArray.data();
        const uint8_t * binary = _binarySortData.data();
        Array<uint32_t> radixScratchPad(n, Alloc::alloc(0, MMAP_LIMIT));
        search::radix_sort(SortDataRadix(binary), StdSortDataCompare(binary), SortDataEof(), 1, sortData, n, radixScratchPad.data(), 0, 96, topn);
    }
    for (uint32_t i(0); i < _sortDataArray.size(); ++i) {
        a[i]._rankValue = _sortDataArray[i]._rankValue;
        a[i]._docId = _sortDataArray[i]._docId;
    }
}
