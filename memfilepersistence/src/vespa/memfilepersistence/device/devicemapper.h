// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * The device mapper is used to get some interesting information for
 * storage devies.
 */
#pragma once

#include <iosfwd>
#include <map>
#include <string>
#include <memory>

namespace storage {

namespace memfile {

/**
 * @class DeviceMapper
 * @ingroup persistence
 *
 * @brief Maps directories to partition and disk information.
 */
struct DeviceMapper {
    typedef std::unique_ptr<DeviceMapper> UP;

    virtual ~DeviceMapper() {}

    virtual const char* getName() const = 0;

    virtual std::string getMountPoint(const std::string& fileOnFS) const = 0;
    virtual uint64_t getPartitionId(const std::string& fileOnFS) const = 0;
    virtual uint64_t getDeviceId(const std::string& fileOnFS) const = 0;
};

/**
 * @class SimpleDeviceMapper
 * @ingroup persistence
 *
 * @brief Simple device mapper, not trying to detect any information.
 *
 * This simple device mapper, assumes all directories used are actually
 * mountpoints, and that all mountpoints are on separate disks. This returns
 * dummy device numbers.
 *
 * Using this, each directory used will be handled separately, and there is no
 * dependency on information to retrieve from OS.
 */
class SimpleDeviceMapper : public DeviceMapper {
    mutable std::map<std::string, int> _devices;
    mutable int _lastDevice;

    SimpleDeviceMapper(const SimpleDeviceMapper&);
    SimpleDeviceMapper& operator=(const SimpleDeviceMapper&);

public:
    SimpleDeviceMapper() : _devices(), _lastDevice(0) {}

    uint64_t getPartitionId(const std::string& fileOnFS) const override {
        std::map<std::string, int>::const_iterator it = _devices.find(fileOnFS);
        if (it != _devices.end()) {
            return it->second;
        }
        int dev = ++_lastDevice;
        _devices[fileOnFS] = dev;
        return dev;
    }
    std::string getMountPoint(const std::string& path) const override { return path; }
    uint64_t getDeviceId(const std::string& fileOnFS) const override {
        return getPartitionId(fileOnFS);
    }
    const char* getName() const override { return "Simple (All directories on individual fake devices)"; }
};

/**
 * @class AdvancedDeviceMapper
 * @ingroup persistence
 *
 * @brief Device mapper trying to find a real physical model using stat/statfs.
 *
 * Using this device mapper, stat/statfs will be used to try to find a real
 * model. Directories mapping to common components wil cause all directories to
 * fail if the common component fails.
 */
struct AdvancedDeviceMapper : public DeviceMapper {
    std::map<uint64_t, std::string> _mountPoints;

    AdvancedDeviceMapper();
    void init(std::istream&);

    std::string getMountPoint(const std::string& fileOnFS) const override;
    uint64_t getPartitionId(const std::string& fileOnFS) const override;
    uint64_t getDeviceId(const std::string& fileOnFS) const override {
            // Not found a way to detect partitions on common device.
            // Returning partition ids for now.
        return getPartitionId(fileOnFS);
    }
    const char* getName() const override { return "Advanced (Read devices attempted found)"; }
};

}

} // storage
