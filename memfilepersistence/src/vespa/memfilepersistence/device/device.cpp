// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "device.h"
#include "ioevent.h"
#include <sstream>

namespace storage {

namespace memfile {

Device::Device(DeviceManager& manager)
    : _manager(manager)
{}

Device::~Device() {}

std::string Device::getStateString(State s)
{
    switch (s) {
        case OK:                  return "OK";
        case TOO_MANY_OPEN_FILES: return "TOO_MANY_OPEN_FILES";
        case NOT_FOUND:           return "NOT_FOUND";
        case PATH_FAILURE:        return "PATH_FAILURE";
        case NO_PERMISSION:       return "NO_PERMISSION";
        case IO_FAILURE:          return "IO_FAILURE";
        case INTERNAL_FAILURE:    return "INTERNAL_FAILURE";
        case DISABLED_BY_ADMIN:   return "DISABLED_BY_ADMIN";
        default:
        {
            std::ostringstream ost;
            ost << "UNKNOWN(" << s << ")";
            return ost.str();
        }
    }
}

void
Device::print(std::ostream& out, bool, const std::string&) const
{
    const IOEvent* event = getLastEvent();
    if (event == 0) {
        out << Device::OK;
    } else {
        out << event->getState() << " ";
        out << event->getTimestamp() << " ";
        std::string desc = event->getDescription();
        std::replace(desc.begin(), desc.end(), '\n', ' ');
        out << desc;
    }
}

void
Device::clearEvents()
{
    _events.clear();
}

} // memfile

} // storage
