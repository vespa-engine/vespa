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
    using MetaDataView = DocumentMetaStoreSaver::MetadataView;
    search::BufferWriter&  _writer;
    MetaDataView           _meta_data_view;
    const DocumentIdStore& _docid_store;
public:
    WriteDocumentId(search::BufferWriter &writer, MetaDataView meta_data_view, const DocumentIdStore &docid_store)
        : _writer(writer),
          _meta_data_view(meta_data_view),
          _docid_store(docid_store) {
    }

    void operator()(documentmetastore::GidToLidMapKey key) {
        auto lid = key.get_lid();
        assert(lid < _meta_data_view.size());
        const RawDocumentMetadata& meta_data = _meta_data_view[lid];
        auto docid_ref = meta_data.acquire_docid_ref();
        assert(docid_ref.valid());
        auto span = _docid_store.get(docid_ref);
        size_t size = span.size();
        _writer.write(&size, sizeof(size));
        _writer.write(span.data(), size);
    }
};
}

DocumentIdSaver::DocumentIdSaver(const GidIterator &gid_iterator, MetaDataView meta_data_view, const DocumentIdSaver::DocumentIdStore& docid_store)
    : _gid_iterator(gid_iterator),
      _meta_data_view(meta_data_view),
      _docid_store(docid_store) {
}

DocumentIdSaver::~DocumentIdSaver() {
}

void DocumentIdSaver::save(search::BufferWriter& writer) const {
    _gid_iterator.foreach_key(WriteDocumentId(writer, _meta_data_view, _docid_store));
    writer.flush();
}

}  // namespace proton
