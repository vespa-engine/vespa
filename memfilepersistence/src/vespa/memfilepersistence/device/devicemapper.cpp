// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "devicemapper.h"
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/exceptions.h>
#include <fstream>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.devicemapper");

namespace storage {

namespace memfile {

namespace {
    uint64_t getDevice(const std::string& path) {
        struct stat info;
        if (stat(path.c_str(), &info) != 0) {
            std::ostringstream ost;
            ost << "Failed to run stat to find data on file " << path
                << ": errno(" << errno << ") - " << vespalib::getLastErrorString() << ".";
            throw vespalib::IoException(
                    ost.str(), vespalib::IoException::getErrorType(errno),
                    VESPA_STRLOC);
        }
        return info.st_dev;
    }
}

AdvancedDeviceMapper::AdvancedDeviceMapper()
    : _mountPoints()
{
        // Initialize the mount point map
    std::ifstream is;
    is.exceptions(std::ifstream::badbit); // Throw exception on failure
    is.open("/proc/mounts");
    init(is);
}

void
AdvancedDeviceMapper::init(std::istream& is)
{
    std::string line;
    while (std::getline(is, line)) {
        vespalib::StringTokenizer st(line, " \t\f\r\n", "");
        if (st[0] == "none") {
            LOG(debug, "Ignoring special mount point '%s'.", line.c_str());
            continue;
        }
        if (st.size() < 3 || st[1][0] != '/') {
            LOG(warning, "Found unexpected line in /proc/mounts: '%s'.",
                line.c_str());
            continue;
        }
        std::string mountPoint(st[1]);
        try{
            uint64_t deviceId = getDevice(mountPoint);
            LOG(debug, "Added mountpoint '%s' with device id %" PRIu64 ".",
                mountPoint.c_str(), deviceId);
            _mountPoints[deviceId] = mountPoint;
        } catch (vespalib::Exception& e) {
            LOG(info, "Failed to get device of mountpoint %s. This is normal "
                      "for some special mountpoints, and doesn't matter unless "
                      "the device is used by VDS: %s",
                mountPoint.c_str(), e.getMessage().c_str());
        }
    }
}

std::string
AdvancedDeviceMapper::getMountPoint(const std::string& fileOnFS) const
{
    uint64_t dev = getDevice(fileOnFS);
    std::map<uint64_t, std::string>::const_iterator it(_mountPoints.find(dev));
    if (it == _mountPoints.end()) {
        std::ostringstream ost;
        ost << "Failed to find a device for file '" << fileOnFS << "'. Stat "
            << "returned device " << dev << " but only the following devices "
            << "are known:";
        for (it = _mountPoints.begin(); it != _mountPoints.end(); ++it) {
            ost << " (" << it->first << " - " << it->second << ")";
        }
        throw vespalib::IoException(
                ost.str(), vespalib::IoException::INTERNAL_FAILURE,
                VESPA_STRLOC);
    }
    return it->second;
}

uint64_t
AdvancedDeviceMapper::getPartitionId(const std::string& fileOnFS) const
{
    return getDevice(fileOnFS);
}

}

} // storage
