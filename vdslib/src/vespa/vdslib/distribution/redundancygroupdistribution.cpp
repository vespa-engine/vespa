// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "redundancygroupdistribution.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <boost/lexical_cast.hpp>
#include <algorithm>

namespace storage::lib {

namespace {
    void verifyLegal(vespalib::StringTokenizer& st,
                     vespalib::stringref serialized)
    {
        // First, verify sanity of the serialized string
        uint32_t firstAsterisk = st.size();
        for (uint32_t i=0; i<st.size(); ++i) {
            if (i > firstAsterisk) {
                if (st[i] != "*") {
                    throw vespalib::IllegalArgumentException(
                            "Illegal distribution spec \"" + serialized + "\". "
                            "Asterisk specifications must be tailing the "
                            "specification.", VESPA_STRLOC);
                }
                continue;
            }
            if (i < firstAsterisk && st[i] == "*") {
                firstAsterisk = i;
                continue;
            }
            uint32_t number = atoi(vespalib::string(st[i]).c_str());
            if (number <= 0 || number >= 256) {
                throw vespalib::IllegalArgumentException(
                    "Illegal distribution spec \"" + serialized + "\". "
                    "Copy counts must be in the range 1-255.", VESPA_STRLOC);
            }
            for (vespalib::StringTokenizer::Token::const_iterator it
                    = st[i].begin(); it != st[i].end(); ++it)
            {
                if (*it < '0' || *it > '9') {
                    throw vespalib::IllegalArgumentException(
                        "Illegal distribution spec \"" + serialized + "\". "
                        "Token isn't asterisk or number.", VESPA_STRLOC);
                }
            }
        }
    }

    std::vector<uint16_t> parse(vespalib::stringref serialized) {
        std::vector<uint16_t> result;
        if (serialized == "") return result;
        vespalib::StringTokenizer st(serialized, "|");
        verifyLegal(st, serialized);
        for (vespalib::StringTokenizer::Iterator it = st.begin();
             it != st.end(); ++it)
        {
            if (*it == "*") {
                result.push_back(0);
            } else {
                result.push_back(boost::lexical_cast<uint16_t>(*it));
            }
        }
        return result;
    }
}

RedundancyGroupDistribution::RedundancyGroupDistribution(
        vespalib::stringref serialized)
    : _values(parse(serialized))
{
}

RedundancyGroupDistribution::RedundancyGroupDistribution(
        const RedundancyGroupDistribution& spec,
        uint16_t redundancy)
{
    uint16_t firstAsterisk = spec.getFirstAsteriskIndex();
    // If redundancy is less than the group size, we only get one copy
    // in redundancy groups.
    if (redundancy <= spec.size()) {
        _values = std::vector<uint16_t>(redundancy, 1);
        return;
    }
    // If not we will have one copy at least for every wanted group.
    _values = std::vector<uint16_t>(spec.size(), 1);
    redundancy -= spec.size();
    // Distribute extra copies to non-asterisk entries first
    redundancy = divideSpecifiedCopies(0, firstAsterisk, redundancy, spec._values);
    // Distribute remaining copies to asterisk entries
    divideSpecifiedCopies(firstAsterisk, spec.size(), redundancy, spec._values);
    // Lastly sort, so the most copies will end up first in ideal state
    std::sort(_values.begin(), _values.end());
    std::reverse(_values.begin(), _values.end());
    assert(_values.front() >= _values.back());
}

RedundancyGroupDistribution::~RedundancyGroupDistribution() = default;

void
RedundancyGroupDistribution::print(std::ostream& out,
                                   bool, const std::string&) const
{
    for (uint32_t i=0; i<_values.size(); ++i) {
        if (i != 0) out << '|';
        if (_values[i] == 0) {
            out << '*';
        } else {
            out << _values[i];
        }
    }
}

uint16_t
RedundancyGroupDistribution::getFirstAsteriskIndex() const
{
    if (_values.empty() || _values.back() != 0) {
        throw vespalib::IllegalArgumentException(
                "Invalid spec given. No asterisk entries found.",
                VESPA_STRLOC);
    }
    uint16_t firstAsterisk = _values.size() - 1;
    while (firstAsterisk > 0 && _values[firstAsterisk - 1] == 0) {
        --firstAsterisk;
    }
    return firstAsterisk;
}

uint16_t
RedundancyGroupDistribution::divideSpecifiedCopies(
            uint16_t start, uint16_t end,
            uint16_t redundancy, const std::vector<uint16_t>& maxValues)
{
    uint16_t lastRedundancy = redundancy;
    while (redundancy > 0) {
        for (uint16_t i=start; i<end && redundancy > 0; ++i) {
            if (maxValues[i] == 0 || _values[i] < maxValues[i]) {
                ++_values[i];
                --redundancy;
            }
        }
        if (redundancy == lastRedundancy) break;
        lastRedundancy = redundancy;
    }
    return redundancy;
}

}
