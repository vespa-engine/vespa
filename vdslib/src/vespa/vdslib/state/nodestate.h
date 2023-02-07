// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include "state.h"
#include <vespa/document/util/printable.h>
#include <vespa/vespalib/objects/floatingpointtype.h>
#include <memory>

namespace storage::lib {

class NodeState : public document::Printable
{
    const NodeType* _type;
    const State* _state;
    vespalib::string _description;
    vespalib::Double _capacity;
    vespalib::Double _initProgress;
    uint32_t _minUsedBits;
    uint64_t _startTimestamp;

public:
    using CSP = std::shared_ptr<const NodeState>;
    using SP = std::shared_ptr<NodeState>;
    using UP = std::unique_ptr<NodeState>;

    static double getListingBucketsInitProgressLimit() { return 0.01; }

    NodeState();
    NodeState(const NodeState &);
    NodeState & operator = (const NodeState &);
    NodeState(NodeState &&) noexcept;
    NodeState & operator = (NodeState &&) noexcept;
    NodeState(const NodeType& nodeType, const State&,
              vespalib::stringref description = "",
              double capacity = 1.0);
    /** Set type if you want to verify that content fit with the given type. */
    explicit NodeState(vespalib::stringref serialized, const NodeType* nodeType = nullptr);
    ~NodeState() override;

    /**
     * Setting prefix to something implies using this function to write a
     * part of the system state. Don't set prefix if you want to be able to
     * recreate the nodestate with NodeState(string) function.
     */
    void serialize(vespalib::asciistream & out, vespalib::stringref prefix = "",
                   bool includeDescription = true) const;

    [[nodiscard]] const State& getState() const { return *_state; }
    [[nodiscard]] vespalib::Double getCapacity() const { return _capacity; }
    [[nodiscard]] uint32_t getMinUsedBits() const { return _minUsedBits; }
    [[nodiscard]] vespalib::Double getInitProgress() const { return _initProgress; }
    [[nodiscard]] const vespalib::string& getDescription() const { return _description; }
    [[nodiscard]] uint64_t getStartTimestamp() const { return _startTimestamp; }

    void setState(const State& state);
    void setCapacity(vespalib::Double capacity);
    void setMinUsedBits(uint32_t usedBits);
    void setInitProgress(vespalib::Double initProgress);
    void setStartTimestamp(uint64_t startTimestamp);
    void setDescription(vespalib::stringref desc) { _description = desc; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    bool operator==(const NodeState& other) const;
    bool operator!=(const NodeState& other) const {
        return !(operator==(other));
    }
    [[nodiscard]] bool similarTo(const NodeState& other) const;

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
