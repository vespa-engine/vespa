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

#include <vespa/memfilepersistence/device/devicemapper.h>
#include <vespa/memfilepersistence/device/directory.h>
#include <vespa/memfilepersistence/device/disk.h>
#include <vespa/memfilepersistence/device/ioevent.h>
#include <vespa/memfilepersistence/device/partition.h>
#include <set>
#include <vector>
#include <vespa/vespalib/util/xmlserializable.h>
#include <vespa/storageframework/generic/clock/clock.h>

namespace storage {

namespace memfile {

class DeviceManager : public vespalib::XmlSerializable {
    DeviceMapper::UP _deviceMapper;
    std::map<int, Disk::LP> _disks;
    std::map<std::string, Partition::LP> _partitions;
    std::map<std::string, Directory::LP> _directories;
    std::set<IOEventListener*> _eventListeners;
    vespa::config::storage::StorDevicesConfig::StatfsPolicy _statPolicy;
    uint32_t _statPeriod;
    const framework::Clock& _clock;

    DeviceManager(const DeviceManager&);
    DeviceManager& operator=(const DeviceManager&);

    void setFindDeviceFunction();

public:
    typedef vespalib::LinkedPtr<DeviceManager> LP;

    DeviceManager(DeviceMapper::UP mapper,
                  const framework::Clock& clock);

    void setPartitionMonitorPolicy(
            vespa::config::storage::StorDevicesConfig::StatfsPolicy, uint32_t period = 0);

    void notifyDiskEvent(Disk& disk, const IOEvent& e);
    void notifyDirectoryEvent(Directory& dir, const IOEvent& e);
    void notifyPartitionEvent(Partition& part, const IOEvent& e);

    void addIOEventListener(IOEventListener& listener);
    void removeIOEventListener(IOEventListener& listener);

    Directory::LP getDirectory(const std::string& dir, uint16_t index);
    Directory::LP deserializeDirectory(const std::string& serialized);
    Partition::LP getPartition(const std::string& path);
    Disk::LP getDisk(const std::string& path);

    std::vector<Directory::LP> getDirectories(const Disk& disk) const;
    std::vector<Directory::LP> getDirectories(const Partition& part) const;

    vespa::config::storage::StorDevicesConfig::StatfsPolicy getStatPolicy() const
        { return _statPolicy; }
    uint32_t getStatPeriod() const { return _statPeriod; }

    virtual void printXml(vespalib::XmlOutputStream&) const;

    const framework::Clock& getClock() const { return _clock; }
};

} // memfile

} // storage

