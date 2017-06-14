// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bufferedfilewriter.h"
#include "versionserializer.h"
#include "fileinfo.h"
#include "simplememfileiobuffer.h"
#include <vespa/memfilepersistence/common/environment.h>
#include <vespa/memfilepersistence/spi/threadmetricprovider.h>

namespace storage {
namespace memfile {

class MemFileV1Serializer : public VersionSerializer
{
    ThreadMetricProvider& _metricProvider;
    MemFilePersistenceThreadMetrics& getMetrics() {
        return _metricProvider.getMetrics();
    }
public:
    using UP = std::unique_ptr<MemFileV1Serializer>;

    MemFileV1Serializer(ThreadMetricProvider&);

    FileVersion getFileVersion() override { return TRADITIONAL_SLOTFILE; }
    void loadFile(MemFile& file, Environment&, Buffer& buffer, uint64_t bytesRead) override;

    void cacheLocationsForPart(SimpleMemFileIOBuffer& cache, DocumentPart part, uint32_t blockIndex,
                               const std::vector<DataLocation>& locationsToCache,
                               const std::vector<DataLocation>& locationsRead,
                               SimpleMemFileIOBuffer::BufferAllocation& buf);

    void cacheLocations(MemFileIOInterface& cache, Environment& env, const Options& options,
                        DocumentPart part, const std::vector<DataLocation>& locations) override;

    FlushResult flushUpdatesToFile(MemFile&, Environment&) override;
    void rewriteFile(MemFile&, Environment&) override;
    bool verify(MemFile&, Environment&, std::ostream& errorReport,
                bool repairErrors, uint16_t fileVerifyFlags) override;

    uint64_t read(vespalib::LazyFile& file, char* buf, const std::vector<DataLocation>& readOps);
    void ensureFormatSpecificDataSet(const MemFile& file);
    uint32_t writeMetaData(BufferedFileWriter& writer, const MemFile& file);

    uint32_t writeAndUpdateLocations(
            MemFile& file,
            SimpleMemFileIOBuffer& ioBuf,
            BufferedFileWriter& writer,
            DocumentPart part,
            const MemFile::LocationMap& locationsToWrite,
            const Environment& env);
};

} // memfile
} // storage
