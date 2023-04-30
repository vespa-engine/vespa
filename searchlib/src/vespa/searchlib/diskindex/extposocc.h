// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search {
    class TuneFileSeqRead;
    class TuneFileSeqWrite;
}

namespace search::index {
    class FieldLengthInfo;
    class PostingListParams;
    class PostingListCountFileSeqWrite;
    class PostingListCountFileSeqRead;
    class PostingListFileSeqWrite;
    class PostingListFileSeqRead;
    class Schema;
}

namespace search::diskindex {


void
setupDefaultPosOccParameters(index::PostingListParams *countParams,
                             index::PostingListParams *params,
                             uint64_t numWordIds,
                             uint32_t docIdLimit);

std::unique_ptr<index::PostingListFileSeqWrite>
makePosOccWrite(index::PostingListCountFileSeqWrite *const posOccCountWrite,
                bool dynamicK,
                const index::PostingListParams &params,
                const index::PostingListParams &featureParams,
                const index::Schema &schema,
                uint32_t indexId,
                const index::FieldLengthInfo &field_length_info);

std::unique_ptr<index::PostingListFileSeqRead>
makePosOccRead(const vespalib::string &name,
               index::PostingListCountFileSeqRead *const posOccCountRead,
               const index::PostingListParams &featureParams,
               const TuneFileSeqRead &tuneFileRead);

}
