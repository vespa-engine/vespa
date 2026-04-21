// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentidsaver.h"
#include "documentmetastoresaver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/datastore/dynamic_array_buffer_type.h>
#include <vespa/vespalib/datastore/dynamic_array_buffer_type.h>

namespace proton {

namespace {

/*
 * Functor class to write document id string for a single lid.
 */
class WriteDocumentId {
    using DocumentIdStore = DocumentIdSaver::DocumentIdStore;
    using MetadataView = DocumentMetaStoreSaver::MetadataView;
    search::BufferWriter&  _writer;
    MetadataView           _metadata_view;
    const DocumentIdStore& _docid_store;
public:
    WriteDocumentId(search::BufferWriter &writer, MetadataView metadata_view, const DocumentIdStore &docid_store)
        : _writer(writer),
          _metadata_view(metadata_view),
          _docid_store(docid_store) {
    }

    void operator()(documentmetastore::GidToLidMapKey key) {
        auto lid = key.get_lid();
        assert(lid < _metadata_view.size());
        const RawDocumentMetadata& metadata = _metadata_view[lid];
        auto docid_ref = metadata.acquire_docid_ref();
        assert(docid_ref.valid());
        auto span = _docid_store.get(docid_ref);
        size_t size = span.size();
        _writer.write(&size, sizeof(size));
        _writer.write(span.data(), size);
    }
};
}

DocumentIdSaver::DocumentIdSaver(const GidIterator &gid_iterator, MetadataView metadata_view, const DocumentIdSaver::DocumentIdStore& docid_store)
    : _gid_iterator(gid_iterator),
      _metadata_view(metadata_view),
      _docid_store(docid_store) {
}

DocumentIdSaver::~DocumentIdSaver() {
}

void DocumentIdSaver::save(search::BufferWriter& writer) const {
    _gid_iterator.foreach_key(WriteDocumentId(writer, _metadata_view, _docid_store));
    writer.flush();
}

}  // namespace proton
