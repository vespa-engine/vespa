// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handle.h"
#include "termfieldmatchdata.h"
#include <memory>
#include <vector>

namespace search::fef {

/**
 * An object of this class is used to store all basic data and derived
 * features for a single hit.
 **/
class MatchData
{
private:
    std::vector<TermFieldMatchData> _termFields;
    double                          _termwise_limit;

public:
    /**
     * Wrapper for constructor parameters
     **/
    class Params
    {
    private:
        uint32_t _numTermFields;

        friend class ::search::fef::MatchData;
        Params() : _numTermFields(0) {}
    public:
        uint32_t numTermFields() const { return _numTermFields; }
        Params & numTermFields(uint32_t value) {
            _numTermFields = value;
            return *this;
        }
    };
    /**
     * Avoid C++'s most vexing parse problem.
     * (reference: http://www.amazon.com/dp/0201749629/)
     **/
    static Params params() { return Params(); }

    /**
     * Convenience typedef for an auto-pointer to this class.
     **/
    using UP = std::unique_ptr<MatchData>;

    /**
     * Create a new object with the given number of term, attribute, and feature
     * slots.
     *
     * @param numTerms number of term slots
     * @param numAttributes number of attribute slots
     * @param numFeatures number of feature slots
     **/
    explicit MatchData(const Params &cparams);

    /**
     * Reset this match data in such a way that it can be re-used with
     * either the same search iterator tree or with a new search
     * iterator tree where the only difference in interaction with the
     * match data is which terms are unpacked. Note that this will
     * reset some properties, but not all. Use with caution.
     **/
    void soft_reset();

    MatchData(const MatchData &rhs) = delete;
    MatchData & operator=(const MatchData &rhs) = delete;

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
    uint32_t getNumTermFields() const { return _termFields.size(); }

    /**
     * Resolve a term field handle into a pointer to the actual data.
     *
     * @return term field match data
     * @param handle term field handle
     **/
    TermFieldMatchData *resolveTermField(TermFieldHandle handle) { return &_termFields[handle]; }

    /**
     * Resolve a term field handle into a pointer to the actual data.
     *
     * @return term field match data
     * @param handle term field handle
     **/
    const TermFieldMatchData *resolveTermField(TermFieldHandle handle) const { return &_termFields[handle]; }

    static MatchData::UP makeTestInstance(uint32_t numTermFields, uint32_t fieldIdLimit);
};

}
