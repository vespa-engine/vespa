// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "memfilemapper.h"
#include "memfile_v1_serializer.h"
#include <vespa/memfilepersistence/spi/memfilepersistenceprovidermetrics.h>
#include <vespa/memfilepersistence/common/exceptions.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <sstream>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.memfile.mapper");

namespace storage::memfile {

// Repair defined in macro, such that log entries will be unique for the various
// instances calling it (different file line numbers)
#define VESPA_REPAIR_MEMFILE(file) \
{ \
    std::ostringstream memFileErrors; \
    bool memFileRepairResult = repair(file, env, memFileErrors); \
    if (!memFileRepairResult) { \
        LOG(warning, "Repaired %s: %s", \
            file.toString().c_str(), memFileErrors.str().c_str()); \
        sendNotifyBucketCommand(file, env); \
    } else { \
        LOGBP(warning, "Repair for %s triggered but found nothing to repair.", \
              file.toString().c_str()); \
    } \
}

// To avoid duplicating code, this macro is used when autoRepair is on, and
// call itself with autorepair off, handling the autorepair.
#define VESPA_HANDLE_AUTOREPAIR(file, func) { \
    try{ \
        func; \
    } catch (CorruptMemFileException& e) { \
        LOGBP(warning, "Corrupt file %s: %s", \
              file.toString().c_str(), e.what()); \
        VESPA_REPAIR_MEMFILE(file); \
        func; \
    } \
    return; \
}

void
MemFileMapper::sendNotifyBucketCommand(const MemFile&,
                                       Environment&)
{
/* TODO: Move to service layer.
    BucketInfo info(file.getBucketInfo());
        // Send notify bucket change command to update distributor
    api::NotifyBucketChangeCommand::SP msg(
            new api::NotifyBucketChangeCommand(file.getFile().getBucketId(),
                                               info));
    uint16_t distributor(
            env._storageServer.getDistribution()->getIdealDistributorNode(
                *env._storageServer.getStateUpdater().getSystemState(),
                file.getFile().getBucketId()));
    msg->setAddress(api::StorageMessageAddress(
                env._storageServer.getClusterName(),
                lib::NodeType::DISTRIBUTOR,
                distributor));
    msg->setSourceIndex(env._nodeIndex);
    env._fileStorHandler.sendCommand(msg);
*/
}

void
MemFileMapper::addVersionSerializer(VersionSerializer::UP serializer)
{
    FileVersion version = serializer->getFileVersion();
    if (_serializers.find(version) != _serializers.end()) {
        std::ostringstream error;
        error << "A serializer for version " << version
              << " is already registered.";
        throw vespalib::IllegalStateException(error.str(), VESPA_STRLOC);
    }
    _serializers[version] = std::move(serializer);
}

VersionSerializer&
MemFileMapper::getVersionSerializer(const MemFile& file)
{
    std::map<FileVersion, VersionSerializer::UP>::iterator it(
            _serializers.find(file.getCurrentVersion()));
    if (it == _serializers.end()) {
        std::ostringstream ost;
        ost << "Unknown serialization version "
            << getFileVersionName(file.getCurrentVersion())
            << " (" << file.getCurrentVersion() << ")\n";
        throw CorruptMemFileException(ost.str(), file.getFile(), VESPA_STRLOC);
    }
    return *it->second;
}

MemFileMapper::MemFileMapper(ThreadMetricProvider& metricProvider)
    : _metricProvider(metricProvider)
{
    addVersionSerializer(VersionSerializer::UP(new MemFileV1Serializer(metricProvider)));
}

void
MemFileMapper::setDefaultMemFileIO(MemFile& file,
                                   vespalib::LazyFile::UP lf,
                                   const Environment& env)
{
    std::map<FileVersion, VersionSerializer::UP>::iterator serializer(
            _serializers.find(file.getFile().getWantedFileVersion()));
    assert(serializer != _serializers.end());

    file.setMemFileIO(
            std::unique_ptr<MemFileIOInterface>(
                    new SimpleMemFileIOBuffer(
                            *serializer->second,
                            std::move(lf),
                            FileInfo::UP(new FileInfo()),
                            file.getFile(),
                            env)));
}

void
MemFileMapper::loadFileImpl(MemFile& file, Environment& env)
{
    framework::MilliSecTimer timer(env._clock);

    if (file.getSlotCount() != 0 || file.getCurrentVersion() != UNKNOWN) {
        throw InvalidStateException("File is already loaded", file.getFile(),
                                    VESPA_STRLOC);
    }

    vespalib::LazyFile::UP f = env.createFile(file.getFile().getPath());
    vespalib::LazyFile* lf = f.get();

    setDefaultMemFileIO(file, std::move(f), env);

    // Early exit for file not found to avoid having to use
    // exception for common control path
    if (!vespalib::fileExists(file.getFile().getPath())) {
        LOG(debug, "Cannot load file '%s' as it does not exist",
            file.getFile().getPath().c_str());
        file.setFlag(HEADER_BLOCK_READ | BODY_BLOCK_READ);
        return;
    }
    file.setFlag(FILE_EXIST);

    Buffer buffer(env.acquireConfigReadLock().options()->_initialIndexRead);
    off_t readBytes = lf->read(buffer, buffer.getSize(), 0);

    if (readBytes < 4) {
        std::ostringstream err;
        err << "Only " << readBytes << " bytes read from file. Not enough to "
            << "get a file version.";
        throw CorruptMemFileException(err.str(), file.getFile(), VESPA_STRLOC);
    }
    SerializationMetrics& metrics(getMetrics().serialization);
    metrics.initialMetaReadLatency.addValue(timer.getElapsedTimeAsDouble());

    file.setFlag(BUCKET_INFO_OUTDATED);

    FileVersion version = static_cast<FileVersion>(
            *reinterpret_cast<uint32_t*>(buffer.getBuffer()));
    std::map<FileVersion, VersionSerializer::UP>::iterator serializer(
            _serializers.find(version));
    file.setCurrentVersion(version);
    if (serializer == _serializers.end()) {
        std::ostringstream err;
        err << "Unknown file version " << std::hex << version;
        throw CorruptMemFileException(err.str(), file.getFile(), VESPA_STRLOC);
    }
    serializer->second->loadFile(file, env, buffer, readBytes);

    metrics.totalLoadFileLatency.addValue(timer.getElapsedTimeAsDouble());
}

void
MemFileMapper::loadFile(MemFile& file, Environment& env, bool autoRepair)
{
    try {
        loadFileImpl(file, env);
    } catch (CorruptMemFileException& e) {
        LOGBP(warning, "Corrupt file %s: %s",
              file.toString().c_str(), e.what());
        if (autoRepair) {
            VESPA_REPAIR_MEMFILE(file);
            // Must reset version info, slots etc to avoid getting errors
            // that file is already loaded.
            file.resetMetaState();
            loadFileImpl(file, env);
        }
        // Add bucket to set of modified buckets so service layer can request
        // new bucket info.
        env.addModifiedBucket(file.getFile().getBucketId());
    }
}

void
MemFileMapper::flush(MemFile& f, Environment& env, bool autoRepair)
{
    (void) autoRepair;
    if (f.fileExists()) {
        VersionSerializer& serializer(getVersionSerializer(f));
        typedef VersionSerializer::FlushResult FlushResult;
        FlushResult result = serializer.flushUpdatesToFile(f, env);
        if (result == FlushResult::TooSmall) {
            f.compact();
            result = serializer.flushUpdatesToFile(f, env);
        }
        if (result == FlushResult::ChangesWritten
            || result == FlushResult::UnAltered)
        {
            return;
        }
        MemFilePersistenceThreadMetrics& metrics(_metricProvider.getMetrics());
        switch (result) {
            case FlushResult::TooFewMetaEntries:
                metrics.serialization.fullRewritesDueToTooSmallFile.inc();
                break;
            case FlushResult::TooSmall:
                metrics.serialization.fullRewritesDueToTooSmallFile.inc();
                break;
            case FlushResult::TooLarge:
                metrics.serialization.fullRewritesDueToDownsizingFile.inc();
                break;
            default:
                break;
        }
    } else {
        // If a file does not yet exist, its content by definition exists
        // entirely in memory. Consequently it costs next to nothing to run
        // compaction since there is no need to read any meta/header blocks
        // from disk. However, the gains from compacting may be significant if
        // the bucket e.g. contains many versions of the same document.
        f.compact();
    }

    // If we get here we failed to write updates only and will rewrite
    std::map<FileVersion, VersionSerializer::UP>::iterator serializer(
            _serializers.find(f.getFile().getWantedFileVersion()));
    assert(serializer != _serializers.end());

    serializer->second->rewriteFile(f, env);
}

bool
MemFileMapper::verify(MemFile& file, Environment& env,
                      std::ostream& errorReport, bool repairErrors,
                      uint16_t fileVerifyFlags)
{
    if (file.fileExists()) {
        std::map<FileVersion, VersionSerializer::UP>::iterator serializer(
                _serializers.find(file.getCurrentVersion()));
        if (serializer != _serializers.end()) {
            bool wasOk = serializer->second->verify(
                        file, env, errorReport, repairErrors, fileVerifyFlags);
            if (!wasOk) sendNotifyBucketCommand(file, env);
            return wasOk;
        }
            // If we get here, version is corrupted. Delete file if repairing.
        errorReport << "Header read from " << file.getFile().getPath()
                    << " is of wrong version "
                    << getFileVersionName(file.getCurrentVersion())
                    << "(0x" << std::hex << file.getCurrentVersion() << std::dec
                    << "). Corrupt file or unsupported format.";
        if (repairErrors) {
            deleteFile(file, env);
        }
        sendNotifyBucketCommand(file, env);
        return false;
    }
    return true;
}

void
MemFileMapper::deleteFile(const MemFile& constFile, Environment& env)
{
    MemFile& file(const_cast<MemFile&>(constFile));
    framework::MilliSecTimer timer(env._clock);
    std::vector<Timestamp> keep;
    file.clearFlag(FILE_EXIST);
    file.setCurrentVersion(UNKNOWN);

    SimpleMemFileIOBuffer& ioBuf(
            static_cast<SimpleMemFileIOBuffer&>(file.getMemFileIO()));

    uint32_t fileSize = ioBuf.getFileHandle().getFileSize();
    ioBuf.getFileHandle().unlink();

    // Indicate we get free space to partition monitor
    PartitionMonitor& partitionMonitor(
            *constFile.getFile().getDirectory().getPartition().getMonitor());
    partitionMonitor.removingData(fileSize);
    getMetrics().serialization.deleteFileLatency.addValue(
            timer.getElapsedTimeAsDouble());
}

void
MemFileMapper::removeAllSlotsExcept(MemFile& file, std::vector<Timestamp>& keep)
{
    std::vector<const MemSlot*> slotsToRemove;
    MemFile::const_iterator orgIt(file.begin(ITERATE_REMOVED));
    std::vector<Timestamp>::reverse_iterator keepIt(keep.rbegin());

    // Linear merge of vectors to extract inverse set of `keep`; these will
    // be the slots we should remove. The output of this is pretty much what
    // std::set_symmetric_difference would've given us, but can't use that
    // algorithm directly due to our non-implicitly convertible mixing of
    // iterator value types.
    // Note that iterator ranges are sorted in _descending_ order.
    while (orgIt != file.end()) {
        if (keepIt == keep.rend() || orgIt->getTimestamp() > *keepIt) {
            slotsToRemove.push_back(&*orgIt);
            ++orgIt;
        } else if (orgIt->getTimestamp() == *keepIt) {
            ++orgIt;
            ++keepIt;
        } else {
            // The case where the verifier knows of a slot that the MemFile
            // does not _may_ happen in the case of corruptions causing apparent
            // timestamp collisions. In this case, sending in timestamps to
            // keep could lead to ambiguities, but in general we can assume that
            // one of the slots will be removed before this due to a mismatching
            // checksum.
            LOG(warning,
                "Verifier code requested to keep slot at time %zu in "
                "file %s, but that slot does not exist in the internal state. "
                "Assuming this is due to corruption which will be fixed "
                "automatically.",
                keepIt->getTime(),
                file.getFile().getPath().c_str());
            ++keepIt;
        }
    }
    std::reverse(slotsToRemove.begin(), slotsToRemove.end());
    file.removeSlots(slotsToRemove);
}

}
