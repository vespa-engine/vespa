// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::Partition
 * \ingroup persistence
 *
 * \brief Class representing a disk partition.
 *
 * IMPORTANT: Partition objects may be generated for faulty partitions too,
 * thus creating the object must not result in a disk operation.
 */

#pragma once

#include <vespa/memfilepersistence/device/disk.h>
#include <vespa/memfilepersistence/device/partitionmonitor.h>

namespace storage {

namespace memfile {

class Partition : public Device {
    uint64_t _id;
    std::string _mountPoint;
    Disk::SP _disk;
    PartitionMonitor::UP _monitor;

    Partition(DeviceManager& manager, uint64_t id,
              const std::string& mountPoint);

    friend class DeviceManager;

public:
    using SP = std::shared_ptr<Partition>;

    void initializeMonitor();

    uint64_t getId() const { return _id; }
    const std::string& getMountPoint() const { return _mountPoint; }

    Disk& getDisk() { return *_disk; }
    const Disk& getDisk() const { return *_disk; }

    PartitionMonitor* getMonitor() { return _monitor.get(); }
    const PartitionMonitor* getMonitor() const { return _monitor.get(); }

    virtual void addEvent(const IOEvent& e);
    const IOEvent* getLastEvent() const;

    void print(std::ostream& out, bool verbose,
               const std::string& indent) const;
    bool operator==(const Partition& p) const { return (_id == p._id); }
    bool operator!=(const Partition& p) const { return (_id != p._id); }

};

} // memfile

} // storage

