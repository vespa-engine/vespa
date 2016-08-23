// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".seach.docstore");

#include "cachestats.h"
#include "documentstore.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/atomic.h>

namespace search
{

namespace {

class DocumentVisitorAdapter : public IBufferVisitor
{
public:
    DocumentVisitorAdapter(const document::DocumentTypeRepo & repo, IDocumentVisitor & visitor) :
        _repo(repo),
        _visitor(visitor)
    { }
    void visit(uint32_t lid, vespalib::ConstBufferRef buf) override;
private:
    const document::DocumentTypeRepo & _repo;
    IDocumentVisitor & _visitor;
};

void
DocumentVisitorAdapter::visit(uint32_t lid, vespalib::ConstBufferRef buf) {
    if (buf.size() > 0) {
        vespalib::nbostream is(buf.c_str(), buf.size());
        document::Document::UP doc(new document::Document(_repo, is));
        _visitor.visit(lid, std::move(doc));
    }
}

}

using vespalib::nbostream;

void
DocumentStore::Value::set(vespalib::DataBuffer && buf,
                          ssize_t len,
                          const document::CompressionConfig & compression)
{
    //Underlying buffer must be identical to allow swap.
    vespalib::DataBuffer compressed(buf.getData(), 0u);
    document::CompressionConfig::Type type =
        document::compress(compression,
                           vespalib::ConstBufferRef(buf.getData(), len),
                           compressed, true);
    _compressedSize = compressed.getDataLen();
    if (buf.getData() == compressed.getData()) {
        // Uncompressed so we can just steal the underlying buffer.
        buf.stealBuffer().swap(_buf);
    } else {
        compressed.stealBuffer().swap(_buf);
    }
    assert(((type == document::CompressionConfig::NONE) &&
            (len == ssize_t(_compressedSize))) ||
           ((type != document::CompressionConfig::NONE) &&
            (len > ssize_t(_compressedSize))));
    setCompression(type, len);
}


document::Document::UP
DocumentStore::Value::deserializeDocument(const document::DocumentTypeRepo & repo)
{
    vespalib::DataBuffer uncompressed((char *)_buf.get(), (size_t)0);
    document::decompress(getCompression(),
                         getUncompressedSize(),
                         vespalib::ConstBufferRef(*this, size()),
                         uncompressed, true);
    vespalib::nbostream is(uncompressed.getData(), uncompressed.getDataLen());
    return document::Document::UP(new document::Document(repo, is));
}


void DocumentStore::BackingStore::visit(const LidVector & lids, const document::DocumentTypeRepo &repo, IDocumentVisitor & visitor) const {
    DocumentVisitorAdapter adapter(repo, visitor);
    _backingStore.read(lids, adapter);
}

bool
DocumentStore::BackingStore::read(DocumentIdT key, Value & value) const {
    bool found(false);
    vespalib::DataBuffer buf(4096);
    ssize_t len = _backingStore.read(key, buf);
    if (len > 0) {
        value.set(std::move(buf), len, _compression);
        found = true;
    }
    return found;
}

DocumentStore::DocumentStore(const Config & config, IDataStore & store)
    : IDocumentStore(),
      _config(config),
      _backingStore(store),
      _store(_backingStore, config.getCompression()),
      _cache(new Cache(_store, config.getMaxCacheBytes())),
      _visitCache(new VisitCache(store, config.getMaxCacheBytes(), config.getCompression())),
      _uncached_lookups(0)
{
    _cache->reserveElements(config.getInitialCacheEntries());
}

DocumentStore::~DocumentStore()
{
}


void
DocumentStore::visit(const LidVector & lids, const document::DocumentTypeRepo &repo, IDocumentVisitor & visitor) const
{
    if (useCache() && _config.allowVisitCaching() && visitor.allowVisitCaching()) {
        docstore::BlobSet blobSet = _visitCache->read(lids).getBlobSet();
        DocumentVisitorAdapter adapter(repo, visitor);
        for (DocumentIdT lid : lids) {
            adapter.visit(lid, blobSet.get(lid));
        }
    } else {
        _store.visit(lids, repo, visitor);
    }
}

document::Document::UP
DocumentStore::read(DocumentIdT lid, const document::DocumentTypeRepo &repo) const
{
    document::Document::UP retval;
    Value value;
    if (useCache()) {
        value = _cache->read(lid);
    } else {
        vespalib::Atomic::add(&_uncached_lookups, 1UL);
        _store.read(lid, value);
    }
    if ( ! value.empty() ) {
        retval = value.deserializeDocument(repo);
    }
    return retval;
}

void
DocumentStore::write(uint64_t syncToken, const document::Document& doc, DocumentIdT lid)
{
    nbostream stream(12345);
    doc.serialize(stream);
    _backingStore.write(syncToken, lid, stream.peek(), stream.size());
    if (useCache()) {
        _cache->invalidate(lid);
    }
}

void
DocumentStore::remove(uint64_t syncToken, DocumentIdT lid)
{
    _backingStore.remove(syncToken, lid);
    if (useCache()) {
        _cache->invalidate(lid);
    }
}

void
DocumentStore::compact(uint64_t syncToken)
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

fastos::TimeStamp
DocumentStore::getLastFlushTime() const
{
    return _backingStore.getLastFlushTime();
}

template <class Visitor>
class DocumentStore::WrapVisitor : public IDataStoreVisitor
{
    Visitor                           &_visitor;
    const document::DocumentTypeRepo  &_repo;
    const document::CompressionConfig &_compression;
    IDocumentStore                    &_ds;
    uint64_t                           _syncToken;
    
public:
    virtual void
    visit(uint32_t lid, const void *buffer, size_t sz);

    WrapVisitor(Visitor &visitor,
                const document::DocumentTypeRepo &repo,
                const document::CompressionConfig &compresion,
                IDocumentStore &ds,
                uint64_t syncToken);
    
    inline void rewrite(uint32_t lid, const document::Document &doc);

    inline void rewrite(uint32_t lid);

    inline void visitRemove(uint32_t lid);
};


class DocumentStore::WrapVisitorProgress : public IDataStoreVisitorProgress
{
    IDocumentStoreVisitorProgress &_visitorProgress;
public:
    virtual void
    updateProgress(double progress)
    {
        _visitorProgress.updateProgress(progress);
    }
    
    WrapVisitorProgress(IDocumentStoreVisitorProgress &visitProgress)
        : _visitorProgress(visitProgress)
    {
    }
};


template <>
void
DocumentStore::WrapVisitor<IDocumentStoreReadVisitor>::
rewrite(uint32_t lid, const document::Document &doc)
{
    (void) lid;
    (void) doc;
}

template <>
void
DocumentStore::WrapVisitor<IDocumentStoreReadVisitor>::
rewrite(uint32_t lid)
{
    (void) lid;
}


template <>
void
DocumentStore::WrapVisitor<IDocumentStoreReadVisitor>::
visitRemove(uint32_t lid)
{
    _visitor.visit(lid);
}


template <>
void
DocumentStore::WrapVisitor<IDocumentStoreRewriteVisitor>::
rewrite(uint32_t lid, const document::Document &doc)
{
    _ds.write(_syncToken, doc, lid);
}

template <>
void
DocumentStore::WrapVisitor<IDocumentStoreRewriteVisitor>::
rewrite(uint32_t lid)
{
    _ds.remove(_syncToken, lid);
}


template <>
void
DocumentStore::WrapVisitor<IDocumentStoreRewriteVisitor>::
visitRemove(uint32_t lid)
{
    (void) lid;
}



template <class Visitor>
void
DocumentStore::WrapVisitor<Visitor>::visit(uint32_t lid,
                                           const void *buffer,
                                           size_t sz)
{
    (void) lid;
    (void) buffer;
    (void) sz;
    
    Value value;
    vespalib::DataBuffer buf(4096);
    buf.clear();
    buf.writeBytes(buffer, sz);
    ssize_t len = sz;
    if (len > 0) {
        value.set(std::move(buf), len, _compression);
    }
    if (! value.empty()) {
        document::Document::UP doc(value.deserializeDocument(_repo)); 
        _visitor.visit(lid, *doc);
        rewrite(lid, *doc);
    } else {
        visitRemove(lid);
        rewrite(lid);
    }
}


template <class Visitor>
DocumentStore::WrapVisitor<Visitor>::
WrapVisitor(Visitor &visitor,
            const document::DocumentTypeRepo &repo,
            const document::CompressionConfig &compression,
            IDocumentStore &ds,
            uint64_t syncToken)
    : _visitor(visitor),
      _repo(repo),
      _compression(compression),
      _ds(ds),
      _syncToken(syncToken)
{
}


void
DocumentStore::accept(IDocumentStoreReadVisitor &visitor,
                      IDocumentStoreVisitorProgress &visitorProgress,
                      const document::DocumentTypeRepo &repo)
{
    WrapVisitor<IDocumentStoreReadVisitor> wrap(visitor, repo,
                                                _store.getCompression(),
                                                *this,
                                                _backingStore.
                                                tentativeLastSyncToken());
    WrapVisitorProgress wrapVisitorProgress(visitorProgress);
    _backingStore.accept(wrap, wrapVisitorProgress, false);
}


void
DocumentStore::accept(IDocumentStoreRewriteVisitor &visitor,
                      IDocumentStoreVisitorProgress &visitorProgress,
                      const document::DocumentTypeRepo &repo)
{
    WrapVisitor<IDocumentStoreRewriteVisitor> wrap(visitor,
                                                   repo,
                                                   _store.getCompression(),
                                                   *this,
                                                   _backingStore.
                                                   tentativeLastSyncToken());
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

std::vector<DataStoreFileChunkStats>
DocumentStore::getFileChunkStats() const
{
    return _backingStore.getFileChunkStats();
}

CacheStats DocumentStore::getCacheStats() const {
    CacheStats visitStats = _visitCache->getCacheStats();
    CacheStats singleStats(_cache->getHit(), _cache->getMiss() + _uncached_lookups,
                           _cache->size(), _cache->sizeBytes());
    singleStats += visitStats;
    return singleStats;
}

} // namespace search

