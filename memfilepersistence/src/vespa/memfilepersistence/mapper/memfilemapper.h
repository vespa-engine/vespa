// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::MemFileMapper
 * \ingroup memfile
 *
 * \brief Maps memory representation of files to and from physical files.
 *
 * The mapper can map to and from all file formats supported. It keeps track
 * of all possible formats and call the implementation of these as needed. This
 * global class is needed such that files can seemlessly change file format when
 * one wants to start using another than one used before.
 *
 * Note that there will be one MemFileMapper instance per disk thread, such that
 * the mapper doesn't have to worry about being threadsafe with multiple
 * threads calling it at the same time.
 */

#pragma once

#include <vespa/memfilepersistence/mapper/versionserializer.h>
#include <vespa/memfilepersistence/spi/threadmetricprovider.h>

namespace storage {
namespace memfile {

class MemFileMapper : private Types {
private:
    std::map<FileVersion, VersionSerializer::UP> _serializers;
    ThreadMetricProvider& _metricProvider;
    void setDefaultMemFileIO(MemFile& file,
                             vespalib::LazyFile::UP lf,
                             const Environment& env);

public:
    MemFileMapper(ThreadMetricProvider&);

    /**
     * Initialize a MemFile entry with the data found in corresponding file.
     * This sets:
     *   - Flag whether file exist or not.
     *   - If file exist, sets header data in file, such as:
     *     - File version
     *     - Meta entry count
     *     - Header block size
     *     - Body block size
     *     - File checksum
     */
    void loadFile(MemFile&, Environment&, bool autoRepair = true);

    /**
     * Flushes all content in MemFile that is not already persisted to disk.
     * This might require a rewrite of the file, if the size of the file need
     * to change. Flush updates the following in the MemFile:
     *   - Updates state saying all is persisted.
     *   - If file was rewritten and was in unwanted version, file version may
     *     have changed to wanted version.
     *   - Sizes of blocks in the file may have changed.
     *   - Rewrite file if changes would leave the file too empty. (Thus,
     *     memfile given might not be dirty but still a write may be needed)
     */
    void flush(MemFile&, Environment&, bool autoRepair = true);

    /**
     * Verify that file is not corrupt.
     * @return True if file is fine.
     */
    bool verify(MemFile& file, Environment& env,
                std::ostream& errorReport, uint16_t fileVerifyFlags = 0)
        { return verify(file, env, errorReport, false, fileVerifyFlags); }

    /**
     * Verify that file is not corrupt and repair it if it is.
     * @return True if file was fine. False if any errors were fixed.
     */
    bool repair(MemFile& file, Environment& env,
                std::ostream& errorReport, uint16_t fileVerifyFlags = 0)
        { return verify(file, env, errorReport, true, fileVerifyFlags); }

    /**
     * Utility functions used by verify to remove data from memfile that is no
     * longer pointing to valid data.
     */
    void deleteFile(const MemFile& file, Environment& env);
    void removeAllSlotsExcept(MemFile& file, std::vector<Timestamp>& keep);

private:
    void addVersionSerializer(VersionSerializer::UP);
    VersionSerializer& getVersionSerializer(const MemFile& file);

    void loadFileImpl(MemFile&, Environment&);

    /**
     * Check file for errors, generate report of errors. Fix if repairErrors
     * is set. Returns true if no failures were found.
     */
    bool verify(MemFile& file, Environment&,
                std::ostream& errorReport, bool repairErrors,
                uint16_t fileVerifyFlags);

    MemFilePersistenceThreadMetrics& getMetrics() const {
        return _metricProvider.getMetrics();
    }

    void sendNotifyBucketCommand(const MemFile&, Environment&);
};

} // storage
} // memfile

