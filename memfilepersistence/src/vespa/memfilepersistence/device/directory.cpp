// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "directory.h"
#include "devicemanager.h"
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.device.directory");

namespace storage {

namespace memfile {

const IOEvent*
Directory::getLastEvent() const
{
    if (!_events.empty()) return &_events.back();
    return _partition->getLastEvent();
}

Device::State
Directory::getState() const
{
    const IOEvent* event = getLastEvent();
    return (event ? event->getState() : Device::OK);
}

void
Directory::print(std::ostream& out, bool verbose,
                 const std::string& indent) const
{
    out << _path << " ";
    Device::print(out, verbose, indent);
}

Directory::Directory(DeviceManager& manager, uint16_t index,
                     const std::string& path)
    : Device(manager),
      _index(index),
      _path(path),
      _partition(manager.getPartition(path))
{
    assert(_partition.get());
}

namespace {
    struct Entry {
        std::string path;
        Device::State status;
        std::string description;
        Entry();
        ~Entry();
    };

    Entry::Entry() {}
    Entry::~Entry() {}

    Entry parseDirectoryString(const std::string& serialized) {
        while (1) {
            Entry e;
            std::string::size_type pos1 = serialized.find(' ');
            if (pos1 == std::string::npos) break;
            e.path = serialized.substr(0, pos1);
            std::string::size_type pos2 = serialized.find(' ', pos1 + 1);
            std::string num = serialized.substr(pos1 + 1, pos2 - pos1 - 1);
            char* c;
            e.status = static_cast<Device::State>(
                                strtoul(num.c_str(), &c, 10));
            if (*c != '\0') break;
            if (pos2 != std::string::npos) {
                e.description = serialized.substr(pos2 + 1);
            }
            return e;
        }
        std::string msg = "Illegal line in disk status file: '" + serialized
                        + "'. Ignoring it.";
        LOG(warning, "%s", msg.c_str());
        throw vespalib::IllegalArgumentException(msg, VESPA_STRLOC);
    }
}

Directory::Directory(const std::string& serialized,
                     DeviceManager& manager)
    : Device(manager),
      _index(0),
      _path(parseDirectoryString(serialized).path),
      _partition(manager.getPartition(_path))
{
    assert(_partition.get());
    Entry e = parseDirectoryString(serialized);
    if (e.status != Device::OK) {
        addEvent(IOEvent(manager.getClock().getTimeInSeconds().getTime(),
                         e.status, e.description, VESPA_STRLOC));
    }
}

void Directory::addEvent(const IOEvent& e)
{
    switch (e.getState()) {
        case Device::IO_FAILURE:
            _partition->addEvent(e);
            break;
        case Device::PATH_FAILURE:
        case Device::NO_PERMISSION:
        case Device::INTERNAL_FAILURE:
        case Device::DISABLED_BY_ADMIN:
        default:
            if (!e.isGlobal()) {
                _events.push_back(e);
            }
            _manager.notifyDirectoryEvent(*this, e);
    }
}

void
Directory::addEvent(Device::State s,
                    const std::string& description,
                    const std::string& location)
{
    addEvent(IOEvent(
                     _manager.getClock().getTimeInSeconds().getTime(),
                     s,
                     description,
                     location));

}

void Directory::addEvents(const Directory& d)
{
    std::list<IOEvent> events;
    events.insert(events.end(), d.getEvents().begin(), d.getEvents().end());
    events.insert(events.end(), d.getPartition().getEvents().begin(),
                                d.getPartition().getEvents().end());
    events.insert(events.end(), d.getPartition().getDisk().getEvents().begin(),
                                d.getPartition().getDisk().getEvents().end());
    for (std::list<IOEvent>::const_iterator it = events.begin();
        it != events.end(); ++it)
    {
        addEvent(*it);
    }
}

} // memfile

} // storage
