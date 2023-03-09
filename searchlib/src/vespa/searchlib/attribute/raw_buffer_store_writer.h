// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/entryref.h>

namespace search { class BufferWriter; }

namespace search::attribute {

class RawBufferStore;

/**
 * Class for writing raw values from a raw buffer store to a BufferWriter.
 */
class RawBufferStoreWriter
{
    const RawBufferStore& _store;
    BufferWriter&         _writer;
public:
    RawBufferStoreWriter(const RawBufferStore& store, BufferWriter& writer);
    ~RawBufferStoreWriter();
    void write(vespalib::datastore::EntryRef ref);
};

}
