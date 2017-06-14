// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::Directory
 * \ingroup persistence
 *
 * \brief Class representing a directory used by Vespa storage.
 *
 * IMPORTANT: Directory objects may be generated for faulty directories too,
 * thus creating the object must not result in a disk operation.
 */
#pragma once

#include "partition.h"

namespace storage {

namespace memfile {

class Directory : public Device {
    uint16_t _index;
    std::string _path;
    Partition::SP _partition;

    // Only DeviceManager can create these objects, so we only need
    // to cope with these constructors being so similar there.
    Directory(DeviceManager&, uint16_t index, const std::string& path);
    Directory(const std::string& serialized, DeviceManager& manager);

    void addEvents(const Directory& d);

    friend class DeviceManager;

public:
    using SP = std::shared_ptr<Directory>;
    void setIndex(uint16_t index) { _index = index; } // Used when deserializing

    uint16_t getIndex() const { return _index; }
    const std::string& getPath() const { return _path; }
    Partition& getPartition() { return *_partition; }
    const Partition& getPartition() const { return *_partition; }

    const IOEvent* getLastEvent() const override;
    void addEvent(const IOEvent& e) override;
    virtual void addEvent(Device::State s,
                          const std::string& description,
                          const std::string& location);

    State getState() const;
    bool isOk() const { return (getLastEvent() == 0); }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    bool operator==(const Directory& d) const { return (_path == d._path); }
    bool operator!=(const Directory& d) const { return (_path != d._path); }

    // Easy access functions, using the partition monitor to query state of
    // partition

    /** Query whether partition is full after adding given amount of data. */
    bool isFull(int64_t afterAdding = 0, double maxFillRate = -1) const {
        return _partition->getMonitor() == 0
            || _partition->getMonitor()->isFull(afterAdding, maxFillRate);
    }

};

} // memfile

} // storage

