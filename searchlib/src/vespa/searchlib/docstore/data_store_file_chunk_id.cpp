// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_store_file_chunk_id.h"
#include "filechunk.h"

namespace search {

vespalib::string
DataStoreFileChunkId::createName(const vespalib::string &baseName) const
{
    FileChunk::NameId id(_nameId);
    return id.createName(baseName);
}

} // namespace search
