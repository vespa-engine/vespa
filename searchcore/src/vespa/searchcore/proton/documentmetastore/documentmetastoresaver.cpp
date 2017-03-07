// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastoresaver.h"
#include <vespa/searchlib/util/bufferwriter.h>

using vespalib::GenerationHandler;
using search::IAttributeSaveTarget;

namespace proton {

namespace {

constexpr uint32_t NO_DOCUMENT_SIZE_TRACKING_VERSION = 0u;

/*
 * Functor class to write meta data for a single lid. Note that during
 * a background save with active feeding, timestamp, bucketused bits
 * and size might reflect future values due to missing snapshot
 * properties in MetaDataStore.  size might also reflect a mix between
 * current ant future value due to non-atomic access.
 */
class WriteMetaData
{
    search::BufferWriter &_datWriter;
    const RawDocumentMetaData *_metaDataStore;
    uint32_t _metaDataStoreSize;
    bool _writeSize;
    using MetaDataStore = DocumentMetaStoreSaver::MetaDataStore;
    using GlobalId = documentmetastore::IStore::GlobalId;
    using BucketId = documentmetastore::IStore::BucketId;
    using Timestamp = documentmetastore::IStore::Timestamp;
public:
    WriteMetaData(search::BufferWriter &datWriter, const MetaDataStore &metaDataStore, bool writeSize)
        : _datWriter(datWriter),
          _metaDataStore(&metaDataStore[0]),
          _metaDataStoreSize(metaDataStore.size()),
          _writeSize(writeSize)
    { }

    void operator()(uint32_t lid) {
        assert(lid < _metaDataStoreSize);
        const RawDocumentMetaData &metaData = _metaDataStore[lid];
        const GlobalId &gid = metaData.getGid();
        // 6 bits used for bucket bits
        uint8_t bucketUsedBits = metaData.getBucketUsedBits();
        assert(BucketId::validUsedBits(bucketUsedBits));
        assert((bucketUsedBits >> BucketId::CountBits) == 0);
        Timestamp::Type timestamp = metaData.getTimestamp();
        search::BufferWriter &datWriter(_datWriter);
        datWriter.write(&lid, sizeof(lid));
        datWriter.write(gid.get(), GlobalId::LENGTH);
        datWriter.write(&bucketUsedBits, sizeof(bucketUsedBits));
        if (_writeSize) {
            uint32_t size = metaData.getSize();
            assert(size < (1u << 24));
            uint8_t sizeLow = size;
            uint16_t sizeHigh = size >> 8;
            datWriter.write(&sizeLow, sizeof(sizeLow));
            datWriter.write(&sizeHigh, sizeof(sizeHigh));
        }
        datWriter.write(&timestamp, sizeof(timestamp));
    }
};


}


DocumentMetaStoreSaver::
DocumentMetaStoreSaver(vespalib::GenerationHandler::Guard &&guard,
                             const search::IAttributeSaveTarget::Config &cfg,
                             const GidIterator &gidIterator,
                             const MetaDataStore &metaDataStore)
    : AttributeSaver(std::move(guard), cfg),
      _gidIterator(gidIterator),
      _metaDataStore(metaDataStore),
      _writeSize(true)
{
    if (cfg.getVersion() == NO_DOCUMENT_SIZE_TRACKING_VERSION) {
        _writeSize = false;
    }
}


DocumentMetaStoreSaver::~DocumentMetaStoreSaver() { }


bool
DocumentMetaStoreSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    // write <lid,gid> pairs, sorted on gid
    std::unique_ptr<search::BufferWriter>
        datWriter(saveTarget.datWriter().allocBufferWriter());
    _gidIterator.foreach_key(WriteMetaData(*datWriter, _metaDataStore, _writeSize));
    datWriter->flush();
    return true;
}


}  // namespace proton
