// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_buffer_store_reader.h"
#include "raw_buffer_store.h"
#include "blob_sequence_reader.h"

using vespalib::datastore::EntryRef;

namespace search::attribute {

RawBufferStoreReader::RawBufferStoreReader(RawBufferStore& store, BlobSequenceReader& reader)
    : _store(store),
      _reader(reader),
      _buffer(1024)
{
}

RawBufferStoreReader::~RawBufferStoreReader() = default;

EntryRef
RawBufferStoreReader::read()
{
    uint32_t size = _reader.getNextSize();
    if (size == 0) {
        return EntryRef();
    }
    if (size > _buffer.size()) {
        _buffer.resize(size + 1024);
    }
    _reader.readBlob(_buffer.data(), size);
    return _store.set({_buffer.data(), size});
}

}
