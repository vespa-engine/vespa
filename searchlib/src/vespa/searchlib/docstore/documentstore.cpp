// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentstore.h"
#include "visitcache.h"
#include "ibucketizer.h"
#include "value.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/stllike/cache.hpp>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>

LOG_SETUP(".searchlib.docstore.documentstore");

using document::DocumentTypeRepo;
using vespalib::CacheStats;
using vespalib::compression::CompressionConfig;

namespace search {

namespace {

class DocumentVisitorAdapter : public IBufferVisitor
{
public:
    DocumentVisitorAdapter(const DocumentTypeRepo & repo, IDocumentVisitor & visitor) :
        _repo(repo),
        _visitor(visitor)
    { }
    void visit(uint32_t lid, vespalib::ConstBufferRef buf) override;
private:
    const DocumentTypeRepo & _repo;
    IDocumentVisitor & _visitor;
};

void
DocumentVisitorAdapter::visit(uint32_t lid, vespalib::ConstBufferRef buf) {
    if (buf.size() > 0) {
        vespalib::nbostream is(buf.c_str(), buf.size());
        _visitor.visit(lid, std::make_unique<document::Document>(_repo, is));
    }
}

}

using vespalib::nbostream;

namespace docstore {

class BackingStore {
public:
    BackingStore(IDataStore &store, CompressionConfig compression) :
        _backingStore(store),
        _compression(compression)
    { }

    bool read(DocumentIdT key, Value &value) const;
    void visit(const IDocumentStore::LidVector &lids, const DocumentTypeRepo &repo, IDocumentVisitor &visitor) const;
    void write(DocumentIdT, const Value &);
    void erase(DocumentIdT) {}
    CompressionConfig getCompression() const { return _compression.load(std::memory_order_relaxed); }
    void reconfigure(CompressionConfig compression);
private:
    IDataStore &_backingStore;
    std::atomic<CompressionConfig> _compression;
};

void
BackingStore::visit(const IDocumentStore::LidVector &lids, const DocumentTypeRepo &repo,
                    IDocumentVisitor &visitor) const {
    DocumentVisitorAdapter adapter(repo, visitor);
    _backingStore.read(lids, adapter);
}

bool
BackingStore::read(DocumentIdT key, Value &value) const {
    bool found(false);
    vespalib::DataBuffer buf(4_Ki);
    ssize_t len = _backingStore.read(key, buf);
    if (len > 0) {
        value.set(std::move(buf), len, getCompression());
        found = true;
    }
    return found;
}

void
BackingStore::write(DocumentIdT lid, const Value & value)
{
    Value::Result buf = value.decompressed();
    assert(buf.second);
    _backingStore.write(value.getSyncToken(), lid, buf.first.getData(), buf.first.getDataLen());
}

void
BackingStore::reconfigure(CompressionConfig compression) {
    _compression.store(compression, std::memory_order_relaxed);
}

using CacheParams = vespalib::CacheParam<
        vespalib::LruParam<DocumentIdT, docstore::Value>,
        docstore::BackingStore,
        vespalib::zero<DocumentIdT>,
        vespalib::size<docstore::Value> >;

class Cache : public vespalib::cache<CacheParams> {
public:
    Cache(BackingStore & b, size_t maxBytes) : vespalib::cache<CacheParams>(b, maxBytes) { }
};

}

using docstore::Value;

bool
DocumentStore::Config::operator == (const Config &rhs) const {
    return  (_maxCacheBytes == rhs._maxCacheBytes) &&
            (_updateStrategy == rhs._updateStrategy) &&
            (_compression == rhs._compression);
}


DocumentStore::DocumentStore(const Config & config, IDataStore & store)
    : IDocumentStore(),
      _backingStore(store),
      _store(std::make_unique<docstore::BackingStore>(_backingStore, config.getCompression())),
      _cache(std::make_unique<docstore::Cache>(*_store, config.getMaxCacheBytes())),
      _visitCache(std::make_unique<docstore::VisitCache>(store, config.getMaxCacheBytes(), config.getCompression())),
      _updateStrategy(config.updateStrategy()),
      _uncached_lookups(0)
{ }

DocumentStore::~DocumentStore() = default;

void
DocumentStore::reconfigure(const Config & config) {
    _cache->setCapacityBytes(config.getMaxCacheBytes());
    _store->reconfigure(config.getCompression());
    _visitCache->reconfigure(config.getMaxCacheBytes(), config.getCompression());
    _updateStrategy.store(config.updateStrategy(), std::memory_order_relaxed);
}

bool
DocumentStore::useCache() const {
    return (_cache->capacityBytes() != 0) && (_cache->capacity() != 0);
}

DocumentStore::Config::UpdateStrategy DocumentStore::updateStrategy() const {
    return _updateStrategy.load(std::memory_order_relaxed);
}

void
DocumentStore::visit(const LidVector & lids, const DocumentTypeRepo &repo, IDocumentVisitor & visitor) const
{
    if (useCache() && visitor.allowVisitCaching()) {
        docstore::BlobSet blobSet = _visitCache->read(lids).getBlobSet();
        DocumentVisitorAdapter adapter(repo, visitor);
        for (DocumentIdT lid : lids) {
            adapter.visit(lid, blobSet.get(lid));
        }
    } else {
        _store->visit(lids, repo, visitor);
    }
}

std::unique_ptr<document::Document>
DocumentStore::read(DocumentIdT lid, const DocumentTypeRepo &repo) const
{
    Value value;
    if (useCache()) {
        value = _cache->read(lid);
        if (value.empty()) {
            return std::unique_ptr<document::Document>();
        }
        Value::Result result = value.decompressed();
        if ( result.second ) {
            return std::make_unique<document::Document>(repo, std::move(result.first));
        } else {
            LOG(warning, "Summary cache for lid %u is corrupt. Invalidating and reading directly from backing store", lid);
            _cache->invalidate(lid);
        }
    }

    _uncached_lookups.fetch_add(1);
    _store->read(lid, value);
    if ( ! value.empty() ) {
        Value::Result result = value.decompressed();
        assert(result.second);
        return std::make_unique<document::Document>(repo, std::move(result.first));
    }
    return std::unique_ptr<document::Document>();
}

void
DocumentStore::write(uint64_t syncToken, DocumentIdT lid, const document::Document& doc) {
    nbostream stream(12345);
    doc.serialize(stream);
    write(syncToken, lid, stream);
}

void
DocumentStore::write(uint64_t syncToken, DocumentIdT lid, const vespalib::nbostream & stream) {
    if (useCache()) {
        switch (updateStrategy()) {
            case Config::UpdateStrategy::INVALIDATE:
                _backingStore.write(syncToken, lid, stream.peek(), stream.size());
                _cache->invalidate(lid);
                break;
            case Config::UpdateStrategy::UPDATE:
                if (_cache->hasKey(lid)) {
                    Value value(syncToken);
                    vespalib::DataBuffer buf(stream.size());
                    buf.writeBytes(stream.peek(), stream.size());
                    value.set(std::move(buf), stream.size(), _store->getCompression());
                    _cache->write(lid, std::move(value));
                } else {
                    _backingStore.write(syncToken, lid, stream.peek(), stream.size());
                }
                break;
        }
        _visitCache->invalidate(lid); // The cost and complexity of this updating this is not worth it.
    } else {
        _backingStore.write(syncToken, lid, stream.peek(), stream.size());
    }
}

void
DocumentStore::remove(uint64_t syncToken, DocumentIdT lid)
{
    _backingStore.remove(syncToken, lid);
    if (useCache()) {
        _cache->invalidate(lid);
        _visitCache->invalidate(lid);
    }
}

void
DocumentStore::compactBloat(uint64_t syncToken)
{
    (void) syncToken;
    // Most implementations does not offer compact.
}

void
DocumentStore::compactSpread(uint64_t syncToken)
{
    (void) syncToken;
    // Most implementations does not offer compact.
}

void
DocumentStore::flush(uint64_t syncToken)
{
    _backingStore.flush(syncToken);
}

uint64_t
DocumentStore::initFlush(uint64_t syncToken)
{
    return _backingStore.initFlush(syncToken);
}

uint64_t
DocumentStore::lastSyncToken() const
{
    return _backingStore.lastSyncToken();
}

uint64_t
DocumentStore::tentativeLastSyncToken() const
{
    return _backingStore.tentativeLastSyncToken();
}

vespalib::system_time
DocumentStore::getLastFlushTime() const
{
    return _backingStore.getLastFlushTime();
}

template <class Visitor>
class DocumentStore::WrapVisitor : public IDataStoreVisitor
{
    Visitor                 &_visitor;
    const DocumentTypeRepo  &_repo;
    const CompressionConfig  _compression;
    IDocumentStore          &_ds;
    uint64_t                 _syncToken;
    
public:
    void visit(uint32_t lid, const void *buffer, size_t sz) override;

    WrapVisitor(Visitor &visitor, const DocumentTypeRepo &repo, CompressionConfig compresion,
                IDocumentStore &ds, uint64_t syncToken);
    
    void rewrite(uint32_t lid, const document::Document &doc);
    void rewrite(uint32_t lid);
    void visitRemove(uint32_t lid);
};


class DocumentStore::WrapVisitorProgress : public IDataStoreVisitorProgress
{
    IDocumentStoreVisitorProgress &_visitorProgress;
public:
    void updateProgress(double progress) override {
        _visitorProgress.updateProgress(progress);
    }
    
    explicit WrapVisitorProgress(IDocumentStoreVisitorProgress &visitProgress)
        : _visitorProgress(visitProgress)
    {
    }
};

template <>
void
DocumentStore::WrapVisitor<IDocumentStoreReadVisitor>::
rewrite(uint32_t , const document::Document &)
{
}

template <>
void
DocumentStore::WrapVisitor<IDocumentStoreReadVisitor>::rewrite(uint32_t )
{
}

template <>
void
DocumentStore::WrapVisitor<IDocumentStoreReadVisitor>::visitRemove(uint32_t lid)
{
    _visitor.visit(lid);
}

template <>
void
DocumentStore::WrapVisitor<IDocumentStoreRewriteVisitor>::
rewrite(uint32_t lid, const document::Document &doc)
{
    _ds.write(_syncToken, lid, doc);
}

template <>
void
DocumentStore::WrapVisitor<IDocumentStoreRewriteVisitor>::rewrite(uint32_t lid)
{
    _ds.remove(_syncToken, lid);
}

template <>
void
DocumentStore::WrapVisitor<IDocumentStoreRewriteVisitor>::visitRemove(uint32_t )
{
}

template <class Visitor>
void
DocumentStore::WrapVisitor<Visitor>::visit(uint32_t lid, const void *buffer, size_t sz)
{
    Value value;
    vespalib::DataBuffer buf(4_Ki);
    buf.clear();
    buf.writeBytes(buffer, sz);
    ssize_t len = sz;
    if (len > 0) {
        value.set(std::move(buf), len);
    }
    if (! value.empty()) {
        auto doc = std::make_shared<document::Document>(_repo, value.decompressed().first);
        _visitor.visit(lid, doc);
        rewrite(lid, *doc);
    } else {
        visitRemove(lid);
        rewrite(lid);
    }
}

template <class Visitor>
DocumentStore::WrapVisitor<Visitor>::
WrapVisitor(Visitor &visitor, const DocumentTypeRepo &repo, CompressionConfig compression,
            IDocumentStore &ds, uint64_t syncToken)
    : _visitor(visitor),
      _repo(repo),
      _compression(compression),
      _ds(ds),
      _syncToken(syncToken)
{
}

void
DocumentStore::accept(IDocumentStoreReadVisitor &visitor, IDocumentStoreVisitorProgress &visitorProgress,
                      const DocumentTypeRepo &repo)
{
    WrapVisitor<IDocumentStoreReadVisitor> wrap(visitor, repo, _store->getCompression(), *this,
                                                _backingStore.tentativeLastSyncToken());
    WrapVisitorProgress wrapVisitorProgress(visitorProgress);
    _backingStore.accept(wrap, wrapVisitorProgress, false);
}

void
DocumentStore::accept(IDocumentStoreRewriteVisitor &visitor, IDocumentStoreVisitorProgress &visitorProgress,
                      const DocumentTypeRepo &repo)
{
    WrapVisitor<IDocumentStoreRewriteVisitor> wrap(visitor, repo, _store->getCompression(), *this,
                                                   _backingStore.tentativeLastSyncToken());
    WrapVisitorProgress wrapVisitorProgress(visitorProgress);
    _backingStore.accept(wrap, wrapVisitorProgress, true);
}

double
DocumentStore::getVisitCost() const
{
    return _backingStore.getVisitCost();
}

DataStoreStorageStats
DocumentStore::getStorageStats() const
{
    return _backingStore.getStorageStats();
}

vespalib::MemoryUsage
DocumentStore::getMemoryUsage() const
{
    vespalib::MemoryUsage usage = _backingStore.getMemoryUsage();
    usage.merge(_cache->getStaticMemoryUsage());
    usage.merge(_visitCache->getStaticMemoryUsage());
    return usage;
}

std::vector<DataStoreFileChunkStats>
DocumentStore::getFileChunkStats() const
{
    return _backingStore.getFileChunkStats();
}

CacheStats DocumentStore::getCacheStats() const {
    CacheStats visitStats = _visitCache->getCacheStats();
    CacheStats singleStats = _cache->get_stats();
    singleStats.add_extra_misses(_uncached_lookups.load(std::memory_order_relaxed));
    singleStats += visitStats;
    return singleStats;
}

void
DocumentStore::compactLidSpace(uint32_t wantedDocLidLimit)
{
    _backingStore.compactLidSpace(wantedDocLidLimit);
}

bool
DocumentStore::canShrinkLidSpace() const
{
    return _backingStore.canShrinkLidSpace();
}

size_t
DocumentStore::getEstimatedShrinkLidSpaceGain() const
{
    return _backingStore.getEstimatedShrinkLidSpaceGain();
}

void
DocumentStore::shrinkLidSpace()
{
    _backingStore.shrinkLidSpace();
}

}
