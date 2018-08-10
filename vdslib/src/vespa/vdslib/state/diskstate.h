// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::DiskState
 *
 * Defines the state a given disk can have.
 */
#pragma once

#include "state.h"
#include <vespa/document/util/printable.h>
#include <vespa/vespalib/objects/floatingpointtype.h>

namespace storage::lib {

class DiskState : public document::Printable {
    const State* _state;
    vespalib::string _description;
    vespalib::Double _capacity;

public:
    typedef std::shared_ptr<const DiskState> CSP;
    typedef std::shared_ptr<DiskState> SP;

    DiskState();
    DiskState(const State&, const vespalib::stringref & description = "", double capacity = 1.0);
    explicit DiskState(vespalib::stringref  serialized);

    void serialize(vespalib::asciistream & out, const vespalib::stringref & prefix = "",
                   bool includeReason = true, bool useOldFormat = false) const;

    const State& getState() const { return *_state; }
    vespalib::Double getCapacity() const { return _capacity; }
    const vespalib::string& getDescription() const { return _description; }

    void setState(const State& state);
    void setCapacity(double capacity);
    void setDescription(vespalib::stringref  desc) { _description = desc; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    bool operator==(const DiskState& other) const;
    bool operator!=(const DiskState& other) const;

};

}
