// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::Group
 *
 * Defines a Group object that defines a group of groups/nodes.
 *
 * The "1|*" partitions representation is stored as an array of double,
 * where the star (*) is represented by zero (0).
 * The subgroups and storagenode/distributor indexes are stored in increasing order.
 *
 */
#pragma once

#include "redundancygroupdistribution.h"
#include <vespa/vespalib/objects/floatingpointtype.h>
#include <vespa/vespalib/util/crc.h>
#include <map>
#include <vector>
#include <memory>

namespace vespalib { class asciistream; }

namespace storage::lib {

class Group : public document::Printable
{
public:
    using UP = std::unique_ptr<Group>;
    using Distribution = RedundancyGroupDistribution ;

private:
    vespalib::string           _name;
    uint16_t                   _index;
    uint32_t                   _distributionHash;
    Distribution               _distributionSpec;
    std::vector<Distribution>  _preCalculated;
    vespalib::Double           _capacity;
    std::map<uint16_t, Group*> _subGroups; // Set if branch group
    // Invariant: _nodes is ordered by ascending index value.
    std::vector<uint16_t>      _nodes;     // Set if leaf group
    // Same set of indices as _nodes, but in the order originally given as
    // part of setNodes(), i.e. may not be ordered.
    // TODO(vekterli): this can be removed once model code is guaranteed to
    // output nodes in a well-defined order, i.e. _originalNodes == _nodes.
    std::vector<uint16_t>      _originalNodes;

    void calculateDistributionHashValues(uint32_t parentHash);
    void getConfigHash(vespalib::asciistream & out) const;

public:
        // Create leaf node
    Group(uint16_t index, vespalib::stringref name);
        // Create branch node
    Group(uint16_t index, vespalib::stringref name,
          const Distribution&, uint16_t redundancy);
    virtual ~Group();

    bool isLeafGroup() const { return _nodes.size() > 0; }
    bool operator==(const Group& other) const;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    vespalib::Double getCapacity() const { return _capacity; }
    const vespalib::string & getName() const { return _name; }
    uint16_t getIndex() const { return _index; }
    std::map<uint16_t, Group*>& getSubGroups() { return _subGroups; }
    const std::map<uint16_t, Group*>& getSubGroups() const
        { return _subGroups; }
    const std::vector<uint16_t>& getNodes() const { return _nodes; };
    const Distribution& getDistributionSpec() const
        { return _distributionSpec; }
    const Distribution& getDistribution(uint16_t redundancy) const
        { return _preCalculated[redundancy]; }
    uint32_t getDistributionHash() const { return _distributionHash; }

    void addSubGroup(Group::UP);
    void setCapacity(vespalib::Double capacity);
    void setNodes(const std::vector<uint16_t>& nodes);

    /**
     * Returns the hierarchical group the given node is in.
     */
    const Group* getGroupForNode(uint16_t index) const;

    /**
     * Calculates distribution hashes, used to create unique values for each
     * group to XOR their bucket seeds with. Calculated based on index of itself
     * and parent groups. Call this on the root group to generate all hashes.
     */
    void calculateDistributionHashValues() {
        calculateDistributionHashValues(0x8badf00d);
    }

    /**
     * Get a string uniquely describing the parts of the distribution config
     * that is critical for distribution. Use to match up two different group
     * instances in order to verify if they would generate the same distribution
     */
    vespalib::string getDistributionConfigHash() const;
};

}
