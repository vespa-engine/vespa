// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "indexenvironment.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <unordered_map>

namespace search::fef::test {

using search::common::GeoLocationSpec;

/**
 * Implementation of the IQueryEnvironment interface used for testing.
 */
class QueryEnvironment : public IQueryEnvironment
{
private:
    QueryEnvironment(const QueryEnvironment &);             // hide
    QueryEnvironment & operator=(const QueryEnvironment &); // hide

    IndexEnvironment           *_indexEnv;
    std::vector<SimpleTermData> _terms;
    Properties                  _properties;
    std::vector<GeoLocationSpec> _locations;
    search::attribute::IAttributeContext::UP _attrCtx;
    std::unordered_map<std::string, double> _avg_field_lengths;

public:
    /**
     * Constructs a new query environment.
     *
     * @param indexEnv The index environment of this.
     */
    QueryEnvironment(IndexEnvironment *indexEnv = NULL);
    ~QueryEnvironment();

    const Properties &getProperties() const override { return _properties; }
    uint32_t getNumTerms() const override { return _terms.size(); }
    const ITermData *getTerm(uint32_t idx) const override { return idx < _terms.size() ? &_terms[idx] : NULL; }
    GeoLocationSpecPtrs getAllLocations() const override {
        GeoLocationSpecPtrs locations;
        for (const auto & loc : _locations) {
            locations.push_back(&loc);
        }
        return locations;
    }
    const search::attribute::IAttributeContext &getAttributeContext() const override { return *_attrCtx; }
    double get_average_field_length(const vespalib::string& field_name) const override {
        auto itr = _avg_field_lengths.find(field_name);
        if (itr != _avg_field_lengths.end()) {
            return itr->second;
        }
        return 1.0;
    }
    const IIndexEnvironment &getIndexEnvironment() const override { assert(_indexEnv != NULL); return *_indexEnv; }

    /** Returns a reference to the index environment of this. */
    IndexEnvironment *getIndexEnv() { return _indexEnv; }

    /** Returns a const reference to the index environment of this. */
    const IndexEnvironment *getIndexEnv() const { return _indexEnv; }

    /** Sets the index environment of this. */
    QueryEnvironment &setIndexEnv(IndexEnvironment *indexEnv) {
        _indexEnv = indexEnv;
        _attrCtx = ((indexEnv == NULL) ? search::attribute::IAttributeContext::UP() :
                    indexEnv->getAttributeMap().createContext());
        return *this;
    }

    /**
     * Override which attribute manager to use.
     *
     * @param vecMan the manager we want to use
     **/
    void overrideAttributeManager(AttributeManager *vecMan) {
        _attrCtx = ((vecMan == NULL) ? search::attribute::IAttributeContext::UP() : vecMan->createContext());
    }

    /** Returns a reference to the list of term data objects. */
    std::vector<SimpleTermData> &getTerms() { return _terms; }

    /** Returns a const reference to the list of term data objects. */
    const std::vector<SimpleTermData> &getTerms() const { return _terms; }

    /** Returns a reference to the properties of this. */
    Properties & getProperties() { return _properties; }

    /** Returns a reference to the location of this. */
    void addLocation(const GeoLocationSpec &spec) { _locations.push_back(spec); }

    std::unordered_map<std::string, double>& get_avg_field_lengths() { return _avg_field_lengths; }
};

}

