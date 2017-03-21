// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attributedisklayout");
#include "attributedisklayout.h"
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/vespalib/io/fileutil.h>

using search::IndexMetaInfo;
using search::index::SchemaBuilder;
using search::index::Schema;
using search::AttributeVector;

namespace proton
{

AttributeDiskLayout::AttributeDiskLayout(const vespalib::string &baseDir)
    : _baseDir(baseDir)
{
}

AttributeDiskLayout::~AttributeDiskLayout()
{
}

void
AttributeDiskLayout::createBaseDir()
{
    vespalib::mkdir(_baseDir, false);
}

vespalib::string
AttributeDiskLayout::getSnapshotDir(uint64_t syncToken)
{
    return vespalib::make_string("snapshot-%" PRIu64, syncToken);
}

vespalib::string
AttributeDiskLayout::getSnapshotRemoveDir(const vespalib::string &baseDir,
                                          const vespalib::string &snapDir)
{
    if (baseDir.empty()) {
        return snapDir;
    }
    return vespalib::make_string("%s/%s",
                                 baseDir.c_str(),
                                 snapDir.c_str());
}

vespalib::string
AttributeDiskLayout::getAttributeBaseDir(const vespalib::string &baseDir,
                                         const vespalib::string &attrName)
{
    if (baseDir.empty()) {
        return attrName;
    }
    return vespalib::make_string("%s/%s",
                                 baseDir.c_str(),
                                 attrName.c_str());
}

AttributeVector::BaseName
AttributeDiskLayout::getAttributeFileName(const vespalib::string &baseDir,
                                          const vespalib::string &attrName,
                                          uint64_t syncToken)
{
    return AttributeVector::BaseName(getAttributeBaseDir(baseDir, attrName),
                                     getSnapshotDir(syncToken),
                                     attrName);
}

bool
AttributeDiskLayout::removeOldSnapshots(IndexMetaInfo &snapInfo,
                                        vespalib::Lock &snapInfoLock)
{
    IndexMetaInfo::Snapshot best = snapInfo.getBestSnapshot();
    if (!best.valid) {
        return true;
    }
    std::vector<IndexMetaInfo::Snapshot> toRemove;
    const IndexMetaInfo::SnapshotList & list = snapInfo.snapshots();
    for (const auto &snap : list) {
        if (!(snap == best)) {
            toRemove.push_back(snap);
        }
    }
    LOG(debug,
        "About to remove %zu old snapshots. "
        "Will keep best snapshot with sync token %" PRIu64,
        toRemove.size(),
        best.syncToken);
    for (const auto &snap : toRemove) {
        if (snap.valid) {
            {
                vespalib::LockGuard guard(snapInfoLock);
                snapInfo.invalidateSnapshot(snap.syncToken);
            }
            if (!snapInfo.save()) {
                LOG(warning,
                    "Could not save meta info file in directory '%s' after "
                    "invalidating snapshot with sync token %" PRIu64,
                    snapInfo.getPath().c_str(),
                    snap.syncToken);
                return false;
            }
        }
        vespalib::string rmDir =
            getSnapshotRemoveDir(snapInfo.getPath(), snap.dirName);
        FastOS_StatInfo statInfo;
        if (!FastOS_File::Stat(rmDir.c_str(), &statInfo) &&
            statInfo._error == FastOS_StatInfo::FileNotFound)
        {
            // Directory already removed
        } else {
            FastOS_FileInterface:: EmptyAndRemoveDirectory(rmDir.c_str());
#if 0
            LOG(warning,
                "Could not remove snapshot directory '%s'",
                rmDir.c_str());
            return false;
#endif
        }
        {
            vespalib::LockGuard guard(snapInfoLock);
            snapInfo.removeSnapshot(snap.syncToken);
        }
        if (!snapInfo.save()) {
            LOG(warning,
                "Could not save meta info file in directory '%s' after "
                "removing snapshot with sync token %" PRIu64,
                snapInfo.getPath().c_str(), snap.syncToken);
            return false;
        }
        LOG(debug, "Removed snapshot directory '%s'", rmDir.c_str());
    }
    return true;
}

bool
AttributeDiskLayout::removeAttribute(const vespalib::string &baseDir,
                                     const vespalib::string &attrName,
                                     uint64_t wipeSerial)
{
    const vespalib::string currDir = getAttributeBaseDir(baseDir, attrName);
    search::IndexMetaInfo snapInfo(currDir);
    IndexMetaInfo::Snapshot best = snapInfo.getBestSnapshot();
    if (best.valid && best.syncToken >= wipeSerial) {
        return true; // Attribute has been resurrected and flushed later on
    }
    const vespalib::string rmDir =
        getAttributeBaseDir(baseDir,
                            vespalib::make_string("remove.%s",
                                    attrName.c_str()));

    FastOS_StatInfo statInfo;
    if (FastOS_File::Stat(rmDir.c_str(), &statInfo) && statInfo._isDirectory) {
        FastOS_FileInterface::EmptyAndRemoveDirectory(rmDir.c_str());
#if 0
        if (!FastOS_FileInterface::EmptyAndRemoveDirectory(rmDir.c_str())) {
            LOG(warning,
                "Could not remove attribute directory '%s'",
                rmDir.c_str());
            return false;
        }
#endif
    }
    if (!FastOS_File::Stat(currDir.c_str(), &statInfo) &&
        statInfo._error == FastOS_StatInfo::FileNotFound)
    {
        // Directory already removed
        return true;
    }
    if (!FastOS_FileInterface::MoveFile(currDir.c_str(), rmDir.c_str())) {
        LOG(warning,
            "Could not move attribute directory '%s' to '%s'",
            currDir.c_str(),
            rmDir.c_str());
        return false;
    }
    FastOS_FileInterface::EmptyAndRemoveDirectory(rmDir.c_str());
#if 0
    if (!FastOS_FileInterface::EmptyAndRemoveDirectory(rmDir.c_str())) {
        LOG(warning,
            "Could not remove attribute directory '%s'",
            rmDir.c_str());
        return false;
    }
#endif
    return true;
}

std::vector<vespalib::string>
AttributeDiskLayout::listAttributes(const vespalib::string &baseDir)
{
    std::vector<vespalib::string> attributes;
    FastOS_DirectoryScan dir(baseDir.c_str());
    while (dir.ReadNext()) {
        if (strcmp(dir.GetName(), "..") != 0 &&
            strcmp(dir.GetName(), ".") != 0)
        {
            if (dir.IsDirectory()) {
                attributes.emplace_back(dir.GetName());
            }
        }
    }
    return attributes;
}

} // namespace proton

