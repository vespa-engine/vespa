// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "extposocc.h"
#include "zcposocc.h"
#include "fileheader.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/postinglistcountfile.h>
#include <vespa/searchlib/index/postinglistparams.h>

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

namespace {

vespalib::string PosOccIdCooked = "PosOcc.1.Cooked";

}

namespace search::diskindex {

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


std::unique_ptr<PostingListFileSeqWrite>
makePosOccWrite(PostingListCountFileSeqWrite *const posOccCountWrite,
                bool dynamicK,
                const PostingListParams &params,
                const PostingListParams &featureParams,
                const Schema &schema,
                uint32_t indexId,
                const index::FieldLengthInfo &field_length_info)
{
    std::unique_ptr<PostingListFileSeqWrite> posOccWrite;

    if (dynamicK) {
        posOccWrite = std::make_unique<ZcPosOccSeqWrite>(schema, indexId, field_length_info, posOccCountWrite);
    } else {
        posOccWrite = std::make_unique<Zc4PosOccSeqWrite>(schema, indexId, field_length_info, posOccCountWrite);
    }

    posOccWrite->setFeatureParams(featureParams);
    posOccWrite->setParams(params);
    return posOccWrite;
}


std::unique_ptr<PostingListFileSeqRead>
makePosOccRead(const vespalib::string &name,
               PostingListCountFileSeqRead *const posOccCountRead,
               const PostingListParams &featureParams,
               const TuneFileSeqRead &tuneFileRead)
{
    std::unique_ptr<PostingListFileSeqRead> posOccRead;

    FileHeader fileHeader;
    if (fileHeader.taste(name, tuneFileRead)) {
        if (fileHeader.getVersion() == 1 &&
            fileHeader.getBigEndian() &&
            fileHeader.getFormats().size() == 2 &&
            fileHeader.getFormats()[0] ==
            Zc4PosOccSeqRead::getIdentifier(true) &&
            fileHeader.getFormats()[1] ==
            ZcPosOccSeqRead::getSubIdentifier()) {
            posOccRead = std::make_unique<ZcPosOccSeqRead>(posOccCountRead);
        } else if (fileHeader.getVersion() == 1 &&
                   fileHeader.getBigEndian() &&
                   fileHeader.getFormats().size() == 2 &&
                   fileHeader.getFormats()[0] ==
                   Zc4PosOccSeqRead::getIdentifier(false) &&
                   fileHeader.getFormats()[1] ==
                   Zc4PosOccSeqRead::getSubIdentifier()) {
            posOccRead = std::make_unique<Zc4PosOccSeqRead>(posOccCountRead);
        } else {
            LOG(warning,
                "Could not detect format for posocc file read %s",
                name.c_str());
        }
    }
    if (posOccRead) {
        posOccRead->setFeatureParams(featureParams);
    }
    return posOccRead;
}

}
