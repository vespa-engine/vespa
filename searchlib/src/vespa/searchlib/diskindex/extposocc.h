// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2002-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search
{

class TuneFileSeqRead;
class TuneFileSeqWrite;

namespace index {

class PostingListParams;
class PostingListCountFileSeqWrite;
class PostingListCountFileSeqRead;
class PostingListFileSeqWrite;
class PostingListFileSeqRead;
class Schema;

}

namespace diskindex
{


void
setupDefaultPosOccParameters(index::PostingListParams *countParams,
                             index::PostingListParams *params,
                             uint64_t numWordIds,
                             uint32_t docIdLimit);

index::PostingListFileSeqWrite *
makePosOccWrite(const vespalib::string &name,
                index::PostingListCountFileSeqWrite *const posOccCountWrite,
                bool dynamicK,
                const index::PostingListParams &params,
                const index::PostingListParams &featureParams,
                const index::Schema &schema,
                uint32_t indexId,
                const TuneFileSeqWrite &tuneFileWrite);

index::PostingListFileSeqRead *
makePosOccRead(const vespalib::string &name,
               index::PostingListCountFileSeqRead *const posOccCountRead,
               bool dynamicK,
               const index::PostingListParams &featureParams,
               const TuneFileSeqRead &tuneFileRead);

} // namespace diskindex

} // namespace search

