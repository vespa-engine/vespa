// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_buffer_store_writer.h"
#include "raw_buffer_store.h"
#include <vespa/searchlib/util/bufferwriter.h>

using vespalib::datastore::EntryRef;

namespace search::attribute {

RawBufferStoreWriter::RawBufferStoreWriter(const RawBufferStore& store, BufferWriter& writer)
    : _store(store),
      _writer(writer)
{
}

RawBufferStoreWriter::~RawBufferStoreWriter() = default;

void
RawBufferStoreWriter::write(EntryRef ref)
{
    if (ref.valid()) {
        auto raw = _store.get(ref);
        uint32_t size = raw.size();
        _writer.write(&size, sizeof(size));
        _writer.write(raw.data(), raw.size());
    } else {
        uint32_t size = 0;
        _writer.write(&size, sizeof(size));
    }
}

}
