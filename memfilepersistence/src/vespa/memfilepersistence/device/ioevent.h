// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::IOEvent
 * \ingroup persistence
 *
 * \brief Class representing an IO event. An event tied to a device.
 */
#pragma once

#include "device.h"

namespace vespalib { class IoException; }
namespace storage {

namespace memfile {

class IOEvent : public vespalib::Printable {
public:
    IOEvent();

    IOEvent(uint32_t timestamp,
            Device::State s,
            const vespalib::string & description,
            const vespalib::string & location,
            bool global = false);
    IOEvent(const IOEvent &);
    IOEvent & operator = (const IOEvent &);
    IOEvent(IOEvent &&) = default;
    IOEvent & operator = (IOEvent &&) = default;

    ~IOEvent();

    static IOEvent createEventFromErrno(uint32_t timestamp,
                                        int error,
                                        const vespalib::string& extraInfo = "",
                                        const vespalib::string& location = "");
    static IOEvent createEventFromIoException(vespalib::IoException& e,
                                              uint32_t timestamp);

    Device::State getState() const { return _state; }
    const vespalib::string& getDescription() const { return _description; }

    void print(std::ostream& out, bool verbose,
               const std::string& indent) const override;

    /**
     * Global events aren't tied to device they was found in. They should not
     * be saved on each device or be a reason to disable one.
     */
    bool isGlobal() const { return _global; }

    uint32_t getTimestamp() const { return _timestamp; }

private:
    Device::State    _state;
    vespalib::string _description;
    vespalib::string _location;
    bool             _global;
    uint32_t         _timestamp;
};

class Directory;
class Partition;
class Disk;

/**
 * \class storage::IOEventListener
 * \ingroup persistence
 *
 * \brief Interface to implement if you want IO events. Register at manager.
 */
struct IOEventListener {
    virtual void handleDirectoryEvent(Directory& dir, const IOEvent& e) = 0;
    virtual void handlePartitionEvent(Partition& part, const IOEvent& e) = 0;
    virtual void handleDiskEvent(Disk& disk, const IOEvent& e) = 0;

    virtual ~IOEventListener() {}
};

}

}

