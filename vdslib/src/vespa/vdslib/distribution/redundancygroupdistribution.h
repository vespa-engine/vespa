// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Helper class to represent the redundancy arrays from config, dividing 
 * copies between groups, like 2|1|*.
 *
 * All asterisk entries must be given last. There must be an asterisk in the end.
 */
#pragma once

#include <vespa/document/util/printable.h>
#include <vector>
#include <vespa/vespalib/stllike/string.h>

namespace storage::lib {

class RedundancyGroupDistribution : public document::Printable {
    std::vector<uint16_t> _values;

public:
    RedundancyGroupDistribution() noexcept {}
    /**
     * Create a group distribution spec from the serialized version.
     * Asterisk entries are represented as zero.
     */
    RedundancyGroupDistribution(vespalib::stringref serialized);
    /**
     * Create a group distribution for a given redundancy. Will fail if there
     * are no asterisk entries in spec. Should prefer to use every copy before
     * allowing more copies for one group.
     */
    RedundancyGroupDistribution(const RedundancyGroupDistribution& spec,
                                uint16_t redundancy);

    ~RedundancyGroupDistribution() override;

    uint16_t size() const { return _values.size(); }
    uint16_t operator[](uint16_t i) const { return _values[i]; }

    bool operator==(const RedundancyGroupDistribution& o) const
        { return (_values == o._values); }

    void print(std::ostream&, bool verbose, const std::string& indent) const override;

private:
    uint16_t getFirstAsteriskIndex() const;
    uint16_t divideSpecifiedCopies(
            uint16_t start, uint16_t end,
            uint16_t redundancy, const std::vector<uint16_t>& maxValues);
};

}
