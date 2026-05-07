// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handle.h"
#include "termfieldmatchdata.h"
#include <vespa/vespalib/util/typed_data_layout.h>

#include <memory>
#include <vector>

namespace search::fef {

using MatchDataDomain = vespalib::tdl::Domain<TermFieldMatchData>;
using MatchDataBase = vespalib::tdl::Data<MatchDataDomain>;

/**
 * An object of this class is used to store all basic data and derived
 * features for a single hit.
 **/
class MatchData : public MatchDataBase {
private:
    double                          _termwise_limit;

public:
    /**
     * Convenience typedef for an auto-pointer to this class.
     **/
    using UP = vespalib::tdl::Layout<MatchDataDomain, MatchData>::DataUP;

    /**
     * The MatchData constructor is not intended to be called directly
     * and is protected with a DataKey that will be supplied by the
     * layout planner (MatchDataLayout).
     **/
    MatchData(vespalib::tdl::DataKey key);

    /**
     * Reset this match data in such a way that it can be re-used with
     * either the same search iterator tree or with a new search
     * iterator tree where the only difference in interaction with the
     * match data is which terms are unpacked. Note that this will
     * reset some properties, but not all. Use with caution.
     **/
    void soft_reset();

    MatchData(const MatchData& rhs) = delete;
    MatchData& operator=(const MatchData& rhs) = delete;

    /**
     * A number in the range [0,1] indicating how much of the corpus
     * the query must match for termwise evaluation to be enabled. 1
     * means never allowed. 0 means always allowed. The initial value
     * is 1 (never). This value is used when creating a search
     * (queryeval::Blueprint::createSearch).
     **/
    double get_termwise_limit() const { return _termwise_limit; }
    void set_termwise_limit(double value) { _termwise_limit = value; }

    /**
     * Obtain the number of term fields allocated in this match data
     * structure.
     *
     * @return number of term fields allocated
     **/
    uint32_t getNumTermFields() const { return all_of<TermFieldMatchData>().size(); }

    /**
     * Resolve a term field handle into a pointer to the actual data.
     *
     * @return term field match data
     * @param handle term field handle
     **/
    TermFieldMatchData* resolveTermField(TermFieldHandle handle) { return &all_of<TermFieldMatchData>()[handle]; }

    /**
     * Resolve a term field handle into a pointer to the actual data.
     *
     * @return term field match data
     * @param handle term field handle
     **/
    const TermFieldMatchData* resolveTermField(TermFieldHandle handle) const { return &all_of<TermFieldMatchData>()[handle]; }

    static MatchData::UP makeTestInstance(uint32_t numTermFields, uint32_t fieldIdLimit);
};

} // namespace search::fef
