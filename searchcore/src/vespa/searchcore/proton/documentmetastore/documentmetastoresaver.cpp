// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastoresaver.h"

#include "document_meta_store_versions.h"
#include "documentidsaver.h"

#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/util/bufferwriter.h>

#include <vespa/vespalib/btree/btreenode.hpp>

using search::IAttributeSaveTarget;
using vespalib::GenerationGuard;

namespace proton {

namespace {

/*
 * Functor class to write metadata for a single lid. Note that during
 * a background save with active feeding, timestamp, bucket used bits
 * and size might reflect future values due to missing snapshot
 * properties in RcuVector. Size might also reflect a mix between
 * current and future value due to non-atomic access.
 */
class WriteMetadata {
    using MetadataView = DocumentMetaStoreSaver::MetadataView;
    using GlobalId = documentmetastore::IStore::GlobalId;
    using BucketId = documentmetastore::IStore::BucketId;
    using Timestamp = documentmetastore::IStore::Timestamp;
    search::BufferWriter& _datWriter;
    MetadataView          _metadataView;
    bool                  _writeDocSize;

public:
    WriteMetadata(search::BufferWriter& datWriter, MetadataView metadataView, bool writeDocSize)
        : _datWriter(datWriter), _metadataView(metadataView), _writeDocSize(writeDocSize) {}

    void operator()(documentmetastore::GidToLidMapKey key) {
        auto lid = key.get_lid();
        assert(lid < _metadataView.size());
        const RawDocumentMetadata& metadata = _metadataView[lid];
        const GlobalId&            gid = metadata.getGid();
        // 6 bits used for bucket bits
        uint8_t bucketUsedBits = metadata.getBucketUsedBits();
        assert(BucketId::validUsedBits(bucketUsedBits));
        assert((bucketUsedBits >> BucketId::CountBits) == 0);
        Timestamp             timestamp = metadata.getTimestamp();
        search::BufferWriter& datWriter(_datWriter);
        datWriter.write(&lid, sizeof(lid));
        datWriter.write(gid.get(), GlobalId::LENGTH);
        datWriter.write(&bucketUsedBits, sizeof(bucketUsedBits));
        if (_writeDocSize) {
            uint32_t docSize = metadata.getDocSize();
            assert(docSize < (1u << 24));
            uint8_t  docSizeLow = docSize;
            uint16_t docSizeHigh = docSize >> 8;
            datWriter.write(&docSizeLow, sizeof(docSizeLow));
            datWriter.write(&docSizeHigh, sizeof(docSizeHigh));
        }
        datWriter.write(&timestamp, sizeof(timestamp));
    }
};

} // namespace

DocumentMetaStoreSaver::DocumentMetaStoreSaver(GenerationGuard&&                         guard,
                                               const search::attribute::AttributeHeader& header,
                                               const GidIterator& gidIterator, MetadataView metadataView,
                                               std::unique_ptr<DocumentIdSaver> docid_saver)
    : AttributeSaver(std::move(guard), header),
      _gidIterator(gidIterator),
      _metadataView(metadataView),
      _writeDocSize(true),
      _docid_saver(std::move(docid_saver)) {
    if (header.getVersion() == documentmetastore::NO_DOCUMENT_SIZE_TRACKING_VERSION) {
        _writeDocSize = false;
    }
}

DocumentMetaStoreSaver::~DocumentMetaStoreSaver() = default;

bool DocumentMetaStoreSaver::onSave(IAttributeSaveTarget& saveTarget) {
    if (_docid_saver) {
        if (!saveTarget.setup_writer(docid_file_suffix(), "Binary data file for document id strings")) {
            return false;
        }

        auto& docid_file_writer = saveTarget.get_writer(docid_file_suffix());
        auto  docid_writer = docid_file_writer.allocBufferWriter();
        // Note: Implementation of save() is responsible to call BufferWriter::flush().
        _docid_saver->save(*docid_writer);
        docid_file_writer.close();
        _docid_saver.reset();
    }
    // write <lid,gid> pairs, sorted on gid
    std::unique_ptr<search::BufferWriter> datWriter(saveTarget.datWriter().allocBufferWriter());
    _gidIterator.foreach_key(WriteMetadata(*datWriter, _metadataView, _writeDocSize));
    datWriter->flush();
    return true;
}

std::string DocumentMetaStoreSaver::docid_file_suffix() {
    return "docids.dat";
}

} // namespace proton
