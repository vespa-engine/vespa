// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::DeviceManager
 * \ingroup persistence
 *
 * \brief Class keeping information about all devices.
 *
 * This class keeps track of all the devices so they can be looked up.
 */
#pragma once

#include "devicemapper.h"
#include "directory.h"
#include "disk.h"
#include "ioevent.h"
#include "partition.h"
#include <vespa/vespalib/util/xmlserializable.h>
#include <vespa/storageframework/generic/clock/clock.h>
#include <set>

namespace storage::memfile {

class DeviceManager : public vespalib::XmlSerializable {
    using StatfsPolicy = vespa::config::storage::StorDevicesConfig::StatfsPolicy;
    DeviceMapper::UP _deviceMapper;
    std::map<int, Disk::SP> _disks;
    std::map<std::string, Partition::SP> _partitions;
    std::map<std::string, Directory::SP> _directories;
    std::set<IOEventListener*> _eventListeners;
    StatfsPolicy _statPolicy;
    uint32_t _statPeriod;
    const framework::Clock& _clock;

    void setFindDeviceFunction();
public:
    using UP = std::unique_ptr<DeviceManager>;

    DeviceManager(DeviceMapper::UP mapper, const framework::Clock& clock);
    DeviceManager(const DeviceManager&) = delete;
    DeviceManager& operator=(const DeviceManager&) = delete;
    ~DeviceManager();

    void setPartitionMonitorPolicy(StatfsPolicy, uint32_t period = 0);

    void notifyDiskEvent(Disk& disk, const IOEvent& e);
    void notifyDirectoryEvent(Directory& dir, const IOEvent& e);
    void notifyPartitionEvent(Partition& part, const IOEvent& e);

    void addIOEventListener(IOEventListener& listener);
    void removeIOEventListener(IOEventListener& listener);

    Directory::SP getDirectory(const std::string& dir, uint16_t index);
    Directory::SP deserializeDirectory(const std::string& serialized);
    Partition::SP getPartition(const std::string& path);
    Disk::SP getDisk(const std::string& path);

    std::vector<Directory::SP> getDirectories(const Disk& disk) const;
    std::vector<Directory::SP> getDirectories(const Partition& part) const;

    StatfsPolicy getStatPolicy() const { return _statPolicy; }
    uint32_t getStatPeriod() const { return _statPeriod; }

    void printXml(vespalib::XmlOutputStream&) const override;

    const framework::Clock& getClock() const { return _clock; }
};

} // memfile
