// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_raw_attribute_loader.h"
#include "attributevector.h"
#include "blob_sequence_reader.h"
#include "raw_buffer_store.h"
#include "raw_buffer_store_reader.h"

using vespalib::datastore::EntryRef;

namespace search::attribute {

SingleRawAttributeLoader::SingleRawAttributeLoader(AttributeVector& attr, RefVector& ref_vector, RawBufferStore& raw_store)
    : _attr(attr),
      _ref_vector(ref_vector),
      _raw_store(raw_store)
{
}

SingleRawAttributeLoader::~SingleRawAttributeLoader() = default;

void
SingleRawAttributeLoader::load_raw_store(BlobSequenceReader& reader, uint32_t docid_limit)
{
    RawBufferStoreReader raw_reader(_raw_store, reader);
    _raw_store.set_initializing(true);
    for (uint32_t lid = 0; lid < docid_limit; ++lid) {
        _ref_vector.push_back(AtomicEntryRef(raw_reader.read()));
    }
    _raw_store.set_initializing(false);
}

bool
SingleRawAttributeLoader::on_load(vespalib::Executor*)
{
    BlobSequenceReader reader(_attr);
    if (!reader.hasData()) {
        return false;
    }
    _attr.setCreateSerialNum(reader.getCreateSerialNum());
    uint32_t docid_limit(reader.getDocIdLimit());
    _ref_vector.reset();
    _ref_vector.unsafe_reserve(docid_limit);
    load_raw_store(reader, docid_limit);
    _attr.commit();
    _attr.getStatus().setNumDocs(docid_limit);
    _attr.setCommittedDocIdLimit(docid_limit);
    return true;
}

}
