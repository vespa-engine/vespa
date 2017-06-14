// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2002-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS


#include "extposocc.h"
#include "zcposocc.h"
#include "fileheader.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/postinglistcountfile.h>
#include <vespa/vespalib/objects/nbostream.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.extposocc");

using search::index::PostingListFileSeqRead;
using search::index::PostingListFileSeqWrite;
using search::index::PostingListCountFileSeqRead;
using search::index::PostingListCountFileSeqWrite;
using search::index::DocIdAndFeatures;
using search::index::WordDocElementFeatures;
using search::index::WordDocElementWordPosFeatures;
using search::index::PostingListCounts;
using search::index::PostingListParams;
using search::index::Schema;

namespace
{

vespalib::string PosOccIdCooked = "PosOcc.1.Cooked";

}

namespace search
{

namespace diskindex
{

void
setupDefaultPosOccParameters(PostingListParams *countParams,
                             PostingListParams *params,
                             uint64_t numWordIds,
                             uint32_t docIdLimit)
{
    params->set("minSkipDocs", 64u);
    params->set("minChunkDocs", 262144u);

    countParams->set("numWordIds", numWordIds);
    /*
     * ZcPosOcc interleaved min: 2 + 1 + 2 + 1 = 6, assuming k == 1
     * for both docid delta and wordpos delta, i.e. average docsize is
     * less than 8.
     */
    countParams->set("avgBitsPerDoc", static_cast<uint32_t>(27));
    countParams->set("minChunkDocs", static_cast<uint32_t>(262144));
    countParams->set("docIdLimit", docIdLimit);
}


PostingListFileSeqWrite *
makePosOccWrite(const vespalib::string &name,
                PostingListCountFileSeqWrite *const posOccCountWrite,
                bool dynamicK,
                const PostingListParams &params,
                const PostingListParams &featureParams,
                const Schema &schema,
                uint32_t indexId,
                const TuneFileSeqWrite &tuneFileWrite)
{
    PostingListFileSeqWrite *posOccWrite = NULL;

    FileHeader fileHeader;
    if (fileHeader.taste(name, tuneFileWrite)) {
        if (fileHeader.getVersion() == 1 &&
            fileHeader.getBigEndian() &&
            fileHeader.getFormats().size() == 2 &&
            fileHeader.getFormats()[0] ==
            ZcPosOccSeqRead::getIdentifier() &&
            fileHeader.getFormats()[1] ==
            ZcPosOccSeqRead::getSubIdentifier()) {
            dynamicK = true;
        } else if (fileHeader.getVersion() == 1 &&
                   fileHeader.getBigEndian() &&
                   fileHeader.getFormats().size() == 2 &&
                   fileHeader.getFormats()[0] ==
                   Zc4PosOccSeqRead::getIdentifier() &&
                   fileHeader.getFormats()[1] ==
                   Zc4PosOccSeqRead::getSubIdentifier()) {
            dynamicK = false;
        } else {
            LOG(warning,
                "Could not detect format for posocc file write %s",
                name.c_str());
        }
    }
    if (dynamicK)
        posOccWrite =  new ZcPosOccSeqWrite(schema, indexId, posOccCountWrite);
    else
        posOccWrite =
            new Zc4PosOccSeqWrite(schema, indexId, posOccCountWrite);

    posOccWrite->setFeatureParams(featureParams);
    posOccWrite->setParams(params);
    return posOccWrite;
}


PostingListFileSeqRead *
makePosOccRead(const vespalib::string &name,
               PostingListCountFileSeqRead *const posOccCountRead,
               bool dynamicK,
               const PostingListParams &featureParams,
               const TuneFileSeqRead &tuneFileRead)
{
    PostingListFileSeqRead *posOccRead = NULL;

    FileHeader fileHeader;
    if (fileHeader.taste(name, tuneFileRead)) {
        if (fileHeader.getVersion() == 1 &&
            fileHeader.getBigEndian() &&
            fileHeader.getFormats().size() == 2 &&
            fileHeader.getFormats()[0] ==
            ZcPosOccSeqRead::getIdentifier() &&
            fileHeader.getFormats()[1] ==
            ZcPosOccSeqRead::getSubIdentifier()) {
            dynamicK = true;
        } else if (fileHeader.getVersion() == 1 &&
                   fileHeader.getBigEndian() &&
                   fileHeader.getFormats().size() == 2 &&
                   fileHeader.getFormats()[0] ==
                   Zc4PosOccSeqRead::getIdentifier() &&
                   fileHeader.getFormats()[1] ==
                   Zc4PosOccSeqRead::getSubIdentifier()) {
            dynamicK = false;
        } else {
            LOG(warning,
                "Could not detect format for posocc file read %s",
                name.c_str());
        }
    }
    if (dynamicK)
        posOccRead =  new ZcPosOccSeqRead(posOccCountRead);
    else
        posOccRead =  new Zc4PosOccSeqRead(posOccCountRead);

    posOccRead->setFeatureParams(featureParams);
    return posOccRead;
}


} // namespace diskindex

} // namespace search
