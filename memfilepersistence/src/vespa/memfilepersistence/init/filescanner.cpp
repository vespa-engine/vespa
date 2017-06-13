// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filescanner.h"
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <iomanip>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.memfile.filescanner");

namespace storage::memfile {

FileScanner::Metrics::Metrics(framework::Clock& clock)
    : metrics::MetricSet("dbinit.filescan", "",
                         "Metrics for the memfile filescanner"),
      _alienFiles(),
      _alienFileCounter("alienfiles", "",
              "Unknown files found during disk scanning.", this),
      _temporaryFilesDeleted("tempfilesdeleted", "",
              "Temporary files found and deleted during initialization.", this),
      _multipleBucketsSameDisk("multiplebucketssamedisk", "",
              "Multiple buckets found on same disk.", this),
      _wrongDir("wrongdir", "",
              "Number of buckets moved from wrong to right directory.", this),
      _wrongDisk("wrongdisk", "",
              "Number of buckets found on non-ideal disk.", this),
      _dirsListed("dirslisted", "",
              "Directories listed in list step of initialization.", this),
      _startTime(clock),
      _listLatency("listlatency", "",
              "Time used until list phase is done. (in ms)", this)
{
}

FileScanner::Metrics::~Metrics() {}

FileScanner::FileScanner(framework::ComponentRegister& reg,
                         const MountPointList& mountPoints,
                         uint32_t directoryLevels,
                         uint32_t directorySpread)
    : framework::Component(reg, "filescanner"),
      _directoryMapper(directoryLevels, directorySpread),
      _mountPoints(mountPoints),
      _dirLevels(directoryLevels),
      _dirSpread(directorySpread),
      _globalLock(),
      _globalMetrics(getClock())
{
    registerMetric(_globalMetrics);
}

FileScanner::~FileScanner() {}

void
FileScanner::buildBucketList(document::BucketId::List & list,
                             uint16_t partition,
                             uint16_t part, uint16_t totalParts)
{
    Context context(_mountPoints[partition], getClock());
    std::vector<uint32_t> path(_dirLevels);
    if (_dirLevels > 0) {
        // If we have dirlevels, split into parts on top level only
        for (uint32_t i=0, n=_dirSpread; i<n; ++i) {
            if (i % totalParts == part) {
                path[0] = i;
                buildBucketList(list, context, path, 1);
            }
        }
    } else if (part == 0) {
        // If we don't have dirlevels, send all data in part 0
        buildBucketList(list, context, path);
    }
        // Grab lock and update metrics
    vespalib::LockGuard lock(_globalLock);
    std::vector<metrics::Metric::UP> newMetrics;
    context._metrics.addToSnapshot(_globalMetrics, newMetrics);
    assert(newMetrics.empty());
}

void
FileScanner::buildBucketList(document::BucketId::List & list,
                             Context& context,
                             std::vector<uint32_t>& path,
                             uint32_t dirLevel)
{
    if (dirLevel >= _dirLevels) {
        buildBucketList(list, context, path);
        return;
    }
    for (uint32_t i=0, n=_dirSpread; i<n; ++i) {
        path[dirLevel] = i;
        buildBucketList(list, context, path, dirLevel + 1);
    }
}

std::string
FileScanner::getPathName(Context& context, std::vector<uint32_t>& path,
                         const document::BucketId* bucket) const
{
    std::ostringstream ost;
    ost << context._dir.getPath() << std::hex << std::setfill('0');
    for (uint32_t i=0, n=path.size(); i<n; ++i) {
        ost << '/' << std::setw(4) << path[i];
    }
    if (bucket != 0) {
        ost << '/' << std::setw(16)
            << bucket->stripUnused().getRawId() << ".0";
    }
    return ost.str();
}

void
FileScanner::buildBucketList(document::BucketId::List & list,
                             Context& context,
                             std::vector<uint32_t>& path)
{
    std::string pathName(getPathName(context, path));
    if (!vespalib::fileExists(pathName)) {
        LOG(spam, "Directory %s does not exist.", pathName.c_str());
        return;
    }
    LOG(spam, "Listing directory %s", pathName.c_str());
    vespalib::DirectoryList dir(vespalib::listDirectory(pathName));
    for (uint32_t i=0; i<dir.size(); ++i) {
        if (!processFile(list, context, path, pathName, dir[i])) {
                // To only process alien files once, we lock rather than use
                // context object. Should be few (none) alien files so shouldn't
                // matter from a performance point of view
            vespalib::LockGuard lock(_globalLock);
            _globalMetrics._alienFileCounter.inc();
            if (_globalMetrics._alienFiles.size()
                    <= _config._maxAlienFilesLogged)
            {
                LOG(spam, "Detected alien file %s/%s",
                    pathName.c_str(), dir[i].c_str());
                _globalMetrics._alienFiles.push_back(pathName + "/" + dir[i]);
            }
        }
    }
    context._metrics._dirsListed.inc();
}


// Always called from lister thread (which might be worker thread)
bool
FileScanner::processFile(document::BucketId::List & list,
                         Context& context,
                         std::vector<uint32_t>& path,
                         const std::string& pathName,
                         const std::string& name)
{
    if (name == "." || name == ".."
        || name == "chunkinfo" || name == "creationinfo")
    {
        LOG(spam, "Ignoring expected file that is not a slotfile '%s'.",
            name.c_str());
        return true;
    }
    document::BucketId bucket(extractBucketId(name));
    if (bucket.getRawId() == 0) {
        // Delete temporary files generated by storage
        if (name.size() > 4 && name.substr(name.size() - 4) == ".tmp") {
            context._metrics._temporaryFilesDeleted.inc();
            LOG(debug, "Deleting temporary file found '%s'. Assumed it was "
                       "generated by storage temporarily while processing a "
                       "request and process or disk died before operation "
                       "completed.",
                (pathName + "/" + name).c_str());
            vespalib::unlink(pathName + "/" + name);
            return true;
        }
        return false;
    }
    if (handleBadLocation(bucket, context, path)) {
        LOG(spam, "Adding bucket %s.", bucket.toString().c_str());
        list.push_back(bucket);
    }
    return true;
}

document::BucketId
FileScanner::extractBucketId(const std::string& name) const
{
    if (name.size() < 9) return document::BucketId();
    std::string::size_type pos = name.find('.');
    if (pos == std::string::npos || pos > 16) return document::BucketId();
    char *endPtr;
    document::BucketId::Type idnum = strtoull(&name[0], &endPtr, 16);
    if (endPtr != &name[pos]) return document::BucketId();
    uint32_t fileNr = strtol(&name[pos + 1], &endPtr, 16);
    if (*endPtr != '\0') return document::BucketId();
        // Check for deprecated name types
    if (fileNr != 0) {
        LOG(warning, "Found buckets split with old file splitting system. Have "
                     "you upgraded from VDS version < 3.1 to >= 3.1 ? This "
                     "requires a refeed as files stored are not backward "
                     "compatible.");
        return document::BucketId();
    }
    return document::BucketId(idnum);
}

bool
FileScanner::handleBadLocation(const document::BucketId& bucket,
                               Context& context,
                               std::vector<uint32_t>& path)
{
    std::vector<uint32_t> expectedPath(_directoryMapper.getPath(bucket));

    // If in wrong directory on disk, do a rename to move it where VDS will
    // access it.
    if (expectedPath != path) {
        std::string source(getPathName(context, path, &bucket));
        std::string target(getPathName(context, expectedPath, &bucket));

        if (vespalib::fileExists(target)) {
            std::ostringstream err;
            err << "Cannot move file from wrong directory " << source
                << " to " << target << " as file already exist. Multiple "
                << "instances of bucket on same disk. Should not happen. "
                << "Ignoring file at in bad location.";
            LOG(warning, "%s", err.str().c_str());
            context._metrics._multipleBucketsSameDisk.inc();
            return false;
        }
        if (!vespalib::rename(source, target, false, true)) {
            std::ostringstream err;
            err << "Cannot move file from " << source << " to " << target
                << " as source file does not exist. Should not happen.";
            LOG(error, "%s", err.str().c_str());
            throw vespalib::IllegalStateException(err.str(), VESPA_STRLOC);
        }
        LOGBP(warning, "Found bucket in wrong directory. Moved %s to %s.",
              source.c_str(), target.c_str());
        context._metrics._wrongDir.inc();
    }
    return true;
}

}
