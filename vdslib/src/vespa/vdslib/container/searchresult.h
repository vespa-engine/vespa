// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/memory.h>
#include <vector>
#include <map>

namespace document { class ByteBuffer; }
namespace vespalib { class GrowableByteBuffer; }
namespace vdslib {

using IntBlobMapT = std::map<size_t, vespalib::MallocPtr>;

class AggregatorList : public IntBlobMapT
{
public:
    void add(size_t id, const vespalib::MallocPtr & aggrBlob);
    void deserialize(document::ByteBuffer & buf);
    void serialize(vespalib::GrowableByteBuffer & buf) const;
    uint32_t getSerializedSize() const;
};

class BlobContainer
{
public:
    BlobContainer(size_t reserve=4096);
    ~BlobContainer();
    size_t append(const void * v, size_t sz);
    void getBlob(size_t index, const void * & blob, size_t & sz) const;
    size_t getCount() const { return _offsets.size() - 1; }
    size_t getSize()  const { return _offsets.back(); }
    size_t getSize(size_t index)      const { return _offsets[index+1] - _offsets[index]; }
    const void * getBuf(size_t index) const { return _blob.c_str() + _offsets[index]; }
    void deserialize(document::ByteBuffer & buf);
    void serialize(vespalib::GrowableByteBuffer & buf) const;
    uint32_t getSerializedSize() const { return (1 + getCount()) * sizeof(uint32_t) + getSize(); }
private:
    using Blob = vespalib::MallocPtr;
    Blob                _blob;
    std::vector<size_t> _offsets;
};

class SearchResult {
public:
    using RankType = double;
public:
    SearchResult();

    /**
     * Constructs a new message from a byte buffer.
     *
     * @param buf A byte buffer that contains a serialized message.
     */
    SearchResult(document::ByteBuffer & buf);
    SearchResult(SearchResult &&) noexcept;
    ~SearchResult();

    AggregatorList       & getGroupingList()               { return _groupingList; }
    const AggregatorList & getGroupingList()         const { return _groupingList; }
    AggregatorList       & getAggregatorList()             { return _aggregatorList; }
    const AggregatorList & getAggregatorList()       const { return _aggregatorList; }
    void getSortBlob(size_t index, const void * & blob, size_t & sz) const { _sortBlob.getBlob(_hits[index].getIndex(), blob, sz); }
    void                   setWantedHitCount(size_t s)     { _wantedHits = s; }
    size_t                 getWantedHitCount()       const { return _wantedHits; }
    void                   setTotalHitCount(uint64_t s)    { _totalHits = s; }
    uint64_t               getTotalHitCount()        const { return _totalHits; }
    size_t                 getHitCount()             const { return _hits.size(); }
    size_t  getHit(size_t hitNo, const char * & docId, RankType & rank) {
        rank = _hits[hitNo].getRank();
        docId = _hits[hitNo].getDocId(_docIdBuffer->c_str());
        return  _hits[hitNo].getLid();
    }
    void setRank(size_t hitNo, RankType rank) {
        _hits[hitNo].setRank(rank);
    }
    void addHit(uint32_t lid, const char * docId, RankType rank, size_t index=0);
    void addHit(uint32_t lid, const char * docId, RankType rank, const void * sortData, size_t sz);
    void sort();

    void deserialize(document::ByteBuffer & buf);
    void serialize(vespalib::GrowableByteBuffer & buf) const;
    uint32_t getSerializedSize() const;
private:
    class Hit {
    public:
        Hit() noexcept : _lid(0), _rank(0), _docIdOffset(0), _index(0) { }
        Hit(uint32_t lid, RankType rank, size_t docIdOffset, size_t index) : _lid(lid), _rank(rank), _docIdOffset(docIdOffset), _index(index) { }
        const char * getDocId(const char * base) const { return base + getDocIdOffset(); }
        uint32_t getLid()                        const { return _lid; }
        size_t getDocIdOffset()                  const { return _docIdOffset; }
        RankType getRank()                       const { return _rank; }
        void setRank(RankType rank)                    { _rank = rank; }
        size_t getIndex()                        const { return _index; }
        bool operator < (const Hit & h)          const { return cmp(h) < 0; }
        bool operator > (const Hit & h)          const { return cmp(h) > 0; }
        int cmp(const Hit & h)                   const { return (getRank() < h.getRank()) ? -1 : (getRank() > h.getRank()) ? 1 : 0; }
    private:
        uint32_t _lid;
        RankType _rank;
        uint32_t _docIdOffset;
        uint32_t _index;  // refers to sortBlob
    };
    class SortDataCompare {
    private:
        const BlobContainer & _sortBlob;
    public:
        SortDataCompare(const BlobContainer & sortBlob) : _sortBlob(sortBlob) { }
        bool operator() (const Hit & x, const Hit & y) const {
            const void * aBuf, * bBuf;
            size_t aLen, bLen;
            _sortBlob.getBlob(x.getIndex(), aBuf, aLen);
            _sortBlob.getBlob(y.getIndex(), bBuf, bLen);
            const size_t sz=std::min(aLen, bLen);
            int diff = memcmp(aBuf, bBuf, sz);
            if (diff == 0) {
                diff = aLen - bLen;
                if (diff == 0) {
                    diff = y.cmp(x);
                }
            }
            return diff < 0;
        }
    };
    size_t getBufCount() const { return _numDocIdBytes; }
    using DocIdBuffer = std::shared_ptr<vespalib::MallocPtr>;
    uint32_t                     _totalHits;
    size_t                       _wantedHits;
    std::vector<Hit>             _hits;            // Corresponding rank.
    DocIdBuffer                  _docIdBuffer;     // Raw zero-terminated documentids in rank order.
    mutable size_t               _numDocIdBytes;   // Number of bytes in docId buffer.
    AggregatorList               _aggregatorList;
    AggregatorList               _groupingList;
    BlobContainer                _sortBlob;
};

}

