// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::Device
 * \ingroup persistence
 *
 * @brief Class holding information about a device.
 *
 * Base class for devices, such as directories, partitions and disks.
 */

#pragma once

#include <vespa/vespalib/util/printable.h>
#include <list>

namespace storage {

namespace memfile {

class IOEvent;
class DeviceManager;

class Device : public vespalib::Printable {
private:
        // These objects are not possible to copy. They represents physical
        // resources on a computer
    Device(const Device&);
    Device& operator=(Device&);

protected:
    DeviceManager& _manager;
    std::list<IOEvent> _events;

    Device(DeviceManager& manager);

public:
        /**
         * Storage device states. Most serious states are at the bottom of the
         * list. If a single state is requested from the device, the one with
         * the highest value wins through.
         */
    enum State {
        OK,
        NOT_FOUND,        // Not found
        PATH_FAILURE,     // Illegal path
        NO_PERMISSION,    // Permission problems
        INTERNAL_FAILURE, // Probably problem with process.
        IO_FAILURE,       // Disk problems
        TOO_MANY_OPEN_FILES, // Too many open files so we can't use disk.
                             // This is a global problem that will not be stored
                             // as disk state, but must exist in order to be
                             // able to report event.
        DISABLED_BY_ADMIN // If disabled through admin tool
    };

    static std::string getStateString(State s);

    virtual ~Device();

    virtual void addEvent(const IOEvent& e) = 0;
    virtual void clearEvents();
    virtual const IOEvent* getLastEvent() const = 0;

    const std::list<IOEvent>& getEvents() const { return _events; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

};

} // memfile

} // storage

