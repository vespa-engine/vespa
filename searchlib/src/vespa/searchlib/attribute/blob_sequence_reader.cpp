// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blob_sequence_reader.h"
#include <vespa/fastos/file.h>

namespace search::attribute {

void
BlobSequenceReader::readBlob(void *buf, size_t len) {
    _datFile.file().ReadBuf(buf, len);
}

} // namespace
