// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::VersionSerializer
 * \ingroup memfile
 *
 * \brief Super class for file mappers implementing a file format.
 *
 * An implementation of this handles all specifics of reading and writing
 * a file format.
 */

#pragma once

#include "buffer.h"
#include "mapperslotoperation.h"
#include <vespa/memfilepersistence/memfile/memfile.h>
#include <vespa/memfilepersistence/memfile/memfileiointerface.h>
#include <vespa/memfilepersistence/common/types.h>


namespace storage {
namespace memfile {

// Avoid circular dependencies
class MemFileEnvironment;
class Options;

struct VersionSerializer : protected Types {
    using UP = std::unique_ptr<VersionSerializer>;

    virtual ~VersionSerializer() {}

    /** Returns the file version this implementation handles. */
    virtual FileVersion getFileVersion() = 0;

    /**
     * The MemFileMapper main class reads file header to figure out what version
     * it is in. Then loadFile is called on correct implementation to interpret
     * the file. The part of the file already read is given to loadFile to avoid
     * a re-read of the initial data.
     */
    virtual void loadFile(MemFile& file, Environment&,
                          Buffer& buffer, uint64_t bytesRead) = 0;

    /**
     * Flushes all content in MemFile that is altered or not persisted to disk
     * to the physical file. This function should not handle file rewriting. If
     * updates cannot be done to the existing file it needs to return in case
     * we then want to rewrite the file in another format.
     *
     * Flush must update the following in the MemFile:
     *   - Update state saying all is persisted and nothing is altered
     *   - All block position and sizes need to be correct after flush.
     *
     * @return True if written successfully, false if file rewrite is required.
     */
    enum class FlushResult {
        ChangesWritten,
        TooFewMetaEntries,
        TooSmall,
        TooLarge,
        UnAltered
    };
    virtual FlushResult flushUpdatesToFile(MemFile&, Environment&) = 0;

    /**
     * This function is typically called when file doesn't already exist or
     * flushUpdatesToFile return false, indicating that file need a total
     * rewrite. Before calling this function, all data must be cached in the
     * MemFile instance.
     */
    virtual void rewriteFile(MemFile&, Environment&) = 0;

    /**
     * Check file for errors, generate report of errors. Fix if repairErrors
     * is set. Returns true if no failures were found or no errors were fixed.
     */
    virtual bool verify(MemFile&, Environment&,
                        std::ostream& errorReport, bool repairErrors,
                        uint16_t fileVerifyFlags) = 0;


    /**
     * Cache locations into the given buffer.
     */
    virtual void cacheLocations(MemFileIOInterface& buffer,
                                Environment& env,
                                const Options& options,
                                DocumentPart part,
                                const std::vector<DataLocation>& locations) = 0;

};

} // memfile
} // storage

