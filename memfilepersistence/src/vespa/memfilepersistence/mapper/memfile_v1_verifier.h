// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/memfilepersistence/mapper/fileinfo.h>
#include <vespa/memfilepersistence/common/types.h>
#include <vespa/memfilepersistence/common/environment.h>

namespace storage {


namespace memfile {

class MemFile;
class Environment;
class Buffer;

class MemFileV1Verifier : public Types
{
public:
    bool verify(MemFile&,
                Environment&,
                std::ostream& errorReport,
                bool repairErrors,
                uint16_t fileVerifyFlags);

    bool verifyBlock(Types::DocumentPart part,
                     uint32_t id,
                     vespalib::asciistream & error,
                     const char* data,
                     uint32_t size);


    class ReportCreator;

private:
    const Header* verifyHeader(ReportCreator& report,
                               const Buffer& buffer,
                               size_t fileSize) const;

    void verifyMetaDataBlock(ReportCreator& report,
                             const Buffer& buffer,
                             const Header& header,
                             const BucketInfo& info,
                             std::vector<const MetaSlot*>& slots) const;

    void verifyInBounds(ReportCreator& report,
                        const Header& header,
                        bool doHeader,
                        const FileInfo& data,
                        std::vector<const MetaSlot*>& slots) const;

    void verifyDataBlock(ReportCreator& report,
                         Environment& env,
                         const Buffer& buffer,
                         const FileInfo& data,
                         const BucketId& bucket,
                         std::vector<const MetaSlot*>& slots,
                         bool doHeader) const;

    void verifyNonOverlap(ReportCreator& report,
                          bool doHeader,
                          std::vector<const MetaSlot*>& slots) const;

    bool verifyDocumentHeader(ReportCreator& report,
                              const MetaSlot& slot,
                              const Buffer& buffer,
                              DocumentId& did,
                              uint32_t blockIndex,
                              uint32_t blockSize) const;

    bool verifyDocumentBody(ReportCreator& report,
                            const MetaSlot& slot,
                            const Buffer& buffer,
                            uint32_t blockIndex,
                            uint32_t blockSize) const;

    void verifyUniqueTimestamps(ReportCreator& report,
                                std::vector<const MetaSlot*>& slots) const;
};

}

}

