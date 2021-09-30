// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastoresaver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include "document_meta_store_versions.h"
#include <vespa/searchlib/attribute/iattributesavetarget.h>

using vespalib::GenerationHandler;
using search::IAttributeSaveTarget;

namespace proton {

namespace {

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
    bool _writeDocSize;
    using MetaDataStore = DocumentMetaStoreSaver::MetaDataStore;
    using GlobalId = documentmetastore::IStore::GlobalId;
    using BucketId = documentmetastore::IStore::BucketId;
    using Timestamp = documentmetastore::IStore::Timestamp;
public:
    WriteMetaData(search::BufferWriter &datWriter, const MetaDataStore &metaDataStore, bool writeDocSize)
        : _datWriter(datWriter),
          _metaDataStore(&metaDataStore[0]),
          _metaDataStoreSize(metaDataStore.size()),
          _writeDocSize(writeDocSize)
    { }

    void operator()(documentmetastore::GidToLidMapKey key) {
        auto lid = key.get_lid();
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
        if (_writeDocSize) {
            uint32_t docSize = metaData.getDocSize();
            assert(docSize < (1u << 24));
            uint8_t docSizeLow = docSize;
            uint16_t docSizeHigh = docSize >> 8;
            datWriter.write(&docSizeLow, sizeof(docSizeLow));
            datWriter.write(&docSizeHigh, sizeof(docSizeHigh));
        }
        datWriter.write(&timestamp, sizeof(timestamp));
    }
};


}


DocumentMetaStoreSaver::
DocumentMetaStoreSaver(vespalib::GenerationHandler::Guard &&guard,
                       const search::attribute::AttributeHeader &header,
                       const GidIterator &gidIterator,
                       const MetaDataStore &metaDataStore)
    : AttributeSaver(std::move(guard), header),
      _gidIterator(gidIterator),
      _metaDataStore(metaDataStore),
      _writeDocSize(true)
{
    if (header.getVersion() == documentmetastore::NO_DOCUMENT_SIZE_TRACKING_VERSION) {
        _writeDocSize = false;
    }
}


DocumentMetaStoreSaver::~DocumentMetaStoreSaver() = default;


bool
DocumentMetaStoreSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    // write <lid,gid> pairs, sorted on gid
    std::unique_ptr<search::BufferWriter>
        datWriter(saveTarget.datWriter().allocBufferWriter());
    _gidIterator.foreach_key(WriteMetaData(*datWriter, _metaDataStore, _writeDocSize));
    datWriter->flush();
    return true;
}


}  // namespace proton
