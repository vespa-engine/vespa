// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchresult.h"
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <algorithm>

namespace vdslib {

void AggregatorList::add(size_t id, const vespalib::MallocPtr & aggrBlob)
{
    insert(value_type(id, aggrBlob));
}

void AggregatorList::deserialize(document::ByteBuffer & buf)
{
    int32_t tmp(0);
    buf.getIntNetwork(tmp);
    for (size_t i(tmp); i; i--) {
        buf.getIntNetwork(tmp);
        size_t id(tmp);
        buf.getIntNetwork(tmp);
        size_t sz(tmp);
        vespalib::MallocPtr aggr(sz);
        buf.getBytes(aggr, sz);
        add(id, aggr);
    }
}

void AggregatorList::serialize(vespalib::GrowableByteBuffer & buf) const
{
    buf.putInt(size());
    for (const auto & entry : *this) {
        buf.putInt(entry.first);
        buf.putInt(entry.second.size());
        buf.putBytes(entry.second, entry.second.size());
    }
}

uint32_t AggregatorList::getSerializedSize() const
{
    size_t sz(sizeof(uint32_t) * (1 + 2*size()));
    for (const auto & entry : *this) {
        sz += entry.second.size();
    }
    return sz;
}

BlobContainer::BlobContainer(size_t reserve) :
    _blob(reserve),
    _offsets()
{
    _offsets.push_back(0);
}

BlobContainer::~BlobContainer() = default;

size_t BlobContainer::append(const void * v, size_t sz)
{
    const size_t index(getCount());
    _offsets.push_back(_offsets.back() + sz);
    if (getSize() > _blob.size()) {
        _blob.realloc(getSize()*2);
    }
    memcpy(_blob.str() + _offsets[index], v, sz);
    return index;
}

void BlobContainer::getBlob(size_t index, const void * & blob, size_t & sz) const
{
    blob = _blob.c_str() + _offsets[index];
    sz = getSize(index);
}

void BlobContainer::deserialize(document::ByteBuffer & buf)
{
    int tmp(0);
    buf.getIntNetwork(tmp);
    _offsets.resize(tmp + 1);
    _offsets[0] = 0;
    for (size_t i(0), m(getCount()); i < m; i++) {
        buf.getIntNetwork(tmp);
        _offsets[i+1] = _offsets[i] + tmp;
    }
    _blob.realloc(getSize());
    buf.getBytes(_blob, getSize());
}

void BlobContainer::serialize(vespalib::GrowableByteBuffer & buf) const
{
    buf.putInt(getCount());
    for(size_t i(0), m(getCount()); i < m; i++) {
        buf.putInt(getSize(i));
    }
    buf.putBytes(_blob, getSize());
}

SearchResult::SearchResult() :
    _totalHits(0),
    _wantedHits(10),
    _hits(),
    _docIdBuffer(),
    _numDocIdBytes(0)
{
    _docIdBuffer.reset(new vespalib::MallocPtr(4_Ki));
}

SearchResult::SearchResult(document::ByteBuffer & buf) :
    _totalHits(0),
    _wantedHits(10),
    _hits(),
    _docIdBuffer(),
    _numDocIdBytes(0),
    _aggregatorList(),
    _groupingList(),
    _sortBlob()
{
    deserialize(buf);
}

SearchResult::~SearchResult() = default;

void SearchResult::deserialize(document::ByteBuffer & buf)
{
    int32_t tmp;
    buf.getIntNetwork(tmp); _totalHits = tmp;
    uint32_t numResults(0), bufSize(0);
    buf.getIntNetwork(tmp); numResults = tmp;
    if (numResults > 0) {
        buf.getIntNetwork(tmp); bufSize = tmp;
        _docIdBuffer.reset(new vespalib::MallocPtr(bufSize));
        buf.getBytes(_docIdBuffer->str(), _docIdBuffer->size());
        _hits.resize(numResults);
        _numDocIdBytes = _docIdBuffer->size();
        double rank(0);
        const char * docIdbp(_docIdBuffer->c_str());
        for (size_t n(0), m(_hits.size()), p(0); n < m; n++) {
            buf.getDoubleNetwork(rank);
            _hits[n] = Hit(0, rank, p, n);
            while(docIdbp[p++]) { }
        }
    }
    _sortBlob.deserialize(buf);
    _aggregatorList.deserialize(buf);
    _groupingList.deserialize(buf);
}

void SearchResult::serialize(vespalib::GrowableByteBuffer & buf) const
{
    buf.putInt(_totalHits);
    uint32_t hitCount = std::min(_hits.size(), _wantedHits);
    buf.putInt(hitCount);
    if (hitCount > 0) {
        uint32_t sz = getBufCount();
        buf.putInt(sz);
        for (size_t i(0), m(hitCount); i < m; i++) {
            const char * s(_hits[i].getDocId(_docIdBuffer->c_str()));
            buf.putBytes(s, strlen(s)+1);
        }
        for (size_t i(0), m(hitCount); i < m; i++) {
            buf.putDouble(_hits[i].getRank());
        }
    }
    uint32_t sortCount = std::min(_sortBlob.getCount(), _wantedHits);
    buf.putInt(sortCount);
    for (size_t i(0); i < sortCount; i++) {
        buf.putInt(_sortBlob.getSize(_hits[i].getIndex()));
    }
    for (size_t i(0); i < sortCount; i++) {
        size_t sz;
        const void * b;
        _sortBlob.getBlob(_hits[i].getIndex(), b, sz);
        buf.putBytes(b, sz);
    }
    _aggregatorList.serialize(buf);
    _groupingList.serialize(buf);
}

uint32_t SearchResult::getSerializedSize() const
{
    uint32_t hitCount = std::min(_hits.size(), _wantedHits);
    return _aggregatorList.getSerializedSize() +
           _groupingList.getSerializedSize() +
           _sortBlob.getSerializedSize() +
           ((hitCount > 0) ? ((4 * 3) + getBufCount() + sizeof(RankType)*hitCount) : 8);
}

void SearchResult::addHit(uint32_t lid, const char * docId, RankType rank, const void * sortData, size_t sz)
{
    addHit(lid, docId, rank, _sortBlob.getCount());
    _sortBlob.append(sortData, sz);
}

void SearchResult::addHit(uint32_t lid, const char * docId, RankType rank, size_t index)
{
    const size_t sz(strlen(docId));
    size_t start = 0;
    if ( ! _hits.empty() ) {
        start = getBufCount();
    }
    Hit h(lid, rank, start, index);
    _hits.push_back(h);
    _totalHits++;
    _numDocIdBytes += sz + 1;
    if (_numDocIdBytes > _docIdBuffer->size()) {
        _docIdBuffer->realloc((_numDocIdBytes)*2);
    }
    memcpy(_docIdBuffer->str() + start, docId, sz+1);
}

void SearchResult::sort()
{
    if (_sortBlob.getCount() == 0) {
        std::sort(_hits.begin(), _hits.end(), std::greater<Hit>());
    } else {
        std::sort(_hits.begin(), _hits.end(), SortDataCompare(_sortBlob));
    }
}

}
