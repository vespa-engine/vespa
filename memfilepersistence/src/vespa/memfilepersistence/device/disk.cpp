// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/memfilepersistence/device/disk.h>

#include <vespa/log/log.h>
#include <vespa/memfilepersistence/device/devicemanager.h>

LOG_SETUP(".persistence.device.disk");

namespace storage {

namespace memfile {

Disk::Disk(DeviceManager& manager, uint64_t id)
    : Device(manager),
      _id(id)
{
}

void Disk::addEvent(const IOEvent& e)
{
    if (!e.isGlobal()) {
        _events.push_back(e);
    }
    _manager.notifyDiskEvent(*this, e);
}

const IOEvent*
Disk::getLastEvent() const
{
    if (getEvents().size() > 0)
        return &getEvents().back();
    return 0;
}

void
Disk::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "Disk id: " << _id << " ";
    Device::print(out, verbose, indent);
}

} // memfile

} // storage
