// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "partition.h"
#include "devicemanager.h"
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.device.partition");

namespace storage {

namespace memfile {

Partition::Partition(DeviceManager& manager,
                     uint64_t id,
                     const std::string& mountPoint)
    : Device(manager),
      _id(id),
      _mountPoint(mountPoint),
      _disk(manager.getDisk(mountPoint)),
      _monitor()
{
    assert(_disk.get());
}

void Partition::initializeMonitor()
{
    try{
        _monitor.reset(new PartitionMonitor(_mountPoint));
        _monitor->setPolicy(_manager.getStatPolicy(), _manager.getStatPeriod());
    } catch (vespalib::IoException& e) {
        std::ostringstream error;
        error << "Failed to create partition monitor for partition "
              << _mountPoint << ": " << e.getMessage();
        LOG(warning, "%s", error.str().c_str());
        addEvent(IOEvent(_manager.getClock().getTimeInSeconds().getTime(),
                         Device::IO_FAILURE, error.str(), VESPA_STRLOC));
    }
}

void Partition::addEvent(const IOEvent& e)
{
        // No events yet defined that is partition specific
    _disk->addEvent(e);
}

const IOEvent*
Partition::getLastEvent() const
{
    if (!_events.empty()) return &_events.back();
    return _disk->getLastEvent();
}

void
Partition::print(std::ostream& out, bool verbose,
                 const std::string& indent) const
{
    out << "Partition: " << _id << " " << _mountPoint << " ";
    Device::print(out, verbose, indent);
}

} // memfile

} // storage
