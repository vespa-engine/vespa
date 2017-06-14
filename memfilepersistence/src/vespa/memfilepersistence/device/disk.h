// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::Disk
 * \ingroup persistence
 *
 * \brief Class representing a storage unit on a node.
 *
 * Class representing a storage unit on a node, which can be a physical disk, or
 * a device set up by a RAID controller or similar.
 *
 * IMPORTANT: Disk objects may be generated for faulty disks too, thus creating
 * the object must not result in a disk operation.
 */

#pragma once

#include "device.h"

namespace storage {

namespace memfile {

class Disk : public Device {
    uint64_t _id;

    Disk(DeviceManager&, uint64_t id);

    friend class DeviceManager;

public:
    using SP = std::shared_ptr<Disk>;

    uint64_t getId() const { return _id; }

    void addEvent(const IOEvent& e) override;
    const IOEvent* getLastEvent() const override;

    bool operator==(const Disk& disk) const { return (_id == disk._id); }
    bool operator!=(const Disk& disk) const { return (_id != disk._id); }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

} // memfile

} // storage
