// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::NodeState
 *
 * Defines a NodeState object that defines the state of a node.
 *
 * If the object knows of the node type of the node it belongs to, it will
 * verify that changes made makes sense for that type of node. To not alter
 * interface too much and allow empty constructor, this nodetype is optional.
 */
#pragma once

#include "diskstate.h"
#include <vespa/document/bucket/bucketidfactory.h>

namespace storage::lib {

class NodeState : public document::Printable
{
    const NodeType* _type;
    const State* _state;
    vespalib::string _description;
    vespalib::Double _capacity;
    uint16_t _reliability;
    vespalib::Double _initProgress;
    uint32_t _minUsedBits;
    std::vector<DiskState> _diskStates;
    bool _anyDiskDown;
    uint64_t _startTimestamp;

    void updateAnyDiskDownFlag();

public:
    typedef std::shared_ptr<const NodeState> CSP;
    typedef std::shared_ptr<NodeState> SP;
    typedef std::unique_ptr<NodeState> UP;

    static double getListingBucketsInitProgressLimit() { return 0.01; }

    NodeState();
    NodeState(const NodeState &);
    NodeState & operator = (const NodeState &);
    NodeState(NodeState &&) noexcept;
    NodeState & operator = (NodeState &&) noexcept;
    NodeState(const NodeType& nodeType, const State&,
              vespalib::stringref description = "",
              double capacity = 1.0, uint16_t reliability = 1);
    /** Set type if you want to verify that content fit with the given type. */
    NodeState(vespalib::stringref serialized, const NodeType* nodeType = 0);
    ~NodeState();

    /**
     * Setting prefix to something implies using this function to write a
     * part of the system state. Don't set prefix if you want to be able to
     * recreate the nodestate with NodeState(string) function.
     */
    void serialize(vespalib::asciistream & out, vespalib::stringref prefix = "",
                   bool includeDescription = true,
                   bool includeDiskDescription = false,
                   bool useOldFormat = false) const;

    const State& getState() const { return *_state; }
    vespalib::Double getCapacity() const { return _capacity; }
    uint32_t getMinUsedBits() const { return _minUsedBits; }
    uint16_t getReliability() const { return _reliability; }
    vespalib::Double getInitProgress() const { return _initProgress; }
    const vespalib::string& getDescription() const { return _description; }
    uint64_t getStartTimestamp() const { return _startTimestamp; }

    bool isAnyDiskDown() const { return _anyDiskDown; }
    uint16_t getDiskCount() const { return _diskStates.size(); }
    const DiskState& getDiskState(uint16_t index) const;

    void setState(const State& state);
    void setCapacity(vespalib::Double capacity);
    void setMinUsedBits(uint32_t usedBits);
    void setReliability(uint16_t reliability);
    void setInitProgress(vespalib::Double initProgress);
    void setStartTimestamp(uint64_t startTimestamp);
    void setDescription(vespalib::stringref desc) { _description = desc; }

    void setDiskCount(uint16_t count);
    void setDiskState(uint16_t index, const DiskState&);

    void print(std::ostream& out, bool verbose,
               const std::string& indent) const override;
    bool operator==(const NodeState& other) const;
    bool operator!=(const NodeState& other) const
        { return !(operator==(other)); }
    bool similarTo(const NodeState& other) const;

    /**
     * Verify that the contents of this object fits with the given nodetype.
     * This is a noop if nodetype was given in constructor of this object.
     *
     * @throws vespalib::IllegalStateException if not fitting.
     */
    void verifySupportForNodeType(const NodeType& type) const;

    std::string getTextualDifference(const NodeState& other) const;

};

}
