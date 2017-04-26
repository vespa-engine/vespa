// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MountPointList
 * \ingroup persistence
 *
 * \brief Class holding information about the mount points used by storage
 *
 * We need to keep a list of mount points, to read and write the mount point
 * file, and to access what mount points should be used and not.
 *
 * NOTE: A mountpoint is often referred to as a disk, even though you technicly
 * can have multiple mountpoints per partition and multiple partitions per disk.
 *
 * IMPORTANT: Remember to call verifyHealthyDisks() before starting to use them.
 */

#pragma once

#include "devicemanager.h"
#include "directory.h"
#include <vespa/storageframework/storageframework.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/vespalib/util/printable.h>


namespace storage {
namespace lib {
    class NodeState;
}

namespace memfile {

struct MountPointList : public framework::XmlStatusReporter {
    typedef std::unique_ptr<MountPointList> UP;

    /** Create a mount point list. */
    MountPointList(const std::string& vdsRoot,
                   const std::vector<vespalib::string>& diskPath,
                   std::unique_ptr<DeviceManager>);

    DeviceManager& getDeviceManager() { return *_deviceManager; }

    /**
     * Call init to initialize the mount point list in the regular fashion.
     * @param diskCount Number of disks to find, or 0 to auto-detect.
     * @return The number of usable disks found.
     */
    void init(uint16_t diskCount);

    /**
     * Initialize the disks, see description of diskPath config in
     * stor-devices.  Will be called as part of init().
     */
    void initDisks();

    /**
     * Scan disks directory for disks. Add entries found, which does not exist,
     * or are marked NOT_FOUND to this instance.
     *
     * To prevent reading from possible bad disks, we cannot access the disks
     * themselves. Thus, in case of symlinks, it assumes the symlink is to a
     * directory.
     */
    void scanForDisks();

    /**
     * Read the disk status file and adjust the list.
     * Important that any entry marking a disk bad (except for NOT_FOUND if it
     * should be in the file) overrides any disks marked ok in this instance.
     *
     * Similarily to scanForDisks(), this does not access the disks itself.
     */
    void readFromFile();

    /**
     * Initialize the partition monitors within the partitions. Done after
     * partition creation, as partition objects are generated for bad disks.
     */
    void initializePartitionMonitors();

    /**
     * Write the current state of disks to the disk status file.
     * Disks that are OK or NOT_FOUND does not need to be written to file.
     */
    void writeToFile() const;

    /**
     * Go through all the mountpoints marked ok, and check that they work.
     * <ul>
     * <li> Verify that symlinks point to a directory, not a file.
     * <li> Read disk chunk files, stating mountpoint is number A/N.
     * <li> Write disk chunk files on mountpoints missing these.
     *
     * IMPORTANT: This must be called before starting to use the disks.
     * getSize() may not return correct size before this has been called.
     *
     * @return True if there are at least one mountpoint appearing healthy.
     * @throws document::IllegalStateException If the mountpoint chunk files
     *                             disagree on how many mountpoints there are.
     */
    bool verifyHealthyDisks(int mountPointCount);

    /** Get how many mountpoints exist. */
    uint32_t getSize() const { return _mountPoints.size(); }

    /** Get the given mountpoint. */
    Directory& operator[](uint16_t i)
        { assert(_mountPoints.size() > i); return *_mountPoints[i]; }
    const Directory& operator[](uint16_t i) const
        { assert(_mountPoints.size() > i); return *_mountPoints[i]; }

    uint16_t findIndex(const Directory& dir) const;

    vespalib::string reportXmlStatus(vespalib::xml::XmlOutputStream&, const framework::HttpUrlPath&) const override;

    /**
     * Returns the current state of the mountpoints.
     */
    spi::PartitionStateList getPartitionStates() const;

private:
    std::unique_ptr<DeviceManager> _deviceManager;
    std::string _vdsRoot;
    std::vector<vespalib::string> _diskPath;
    std::vector<Directory::SP> _mountPoints;

    /** Get the name used for the disk status file. */
    std::string getDiskStatusFileName() const;
};

} // memfile

} // storage
