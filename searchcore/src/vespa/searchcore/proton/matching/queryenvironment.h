// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/location.h>

namespace search::index { class IFieldLengthInspector; }

namespace proton::matching {

/**
 * Query environment implementation for the proton matching pipeline.
 **/
class QueryEnvironment : public search::fef::IQueryEnvironment
{
private:
    const search::fef::IIndexEnvironment       &_indexEnv;
    const search::attribute::IAttributeContext &_attrContext;
    search::fef::Properties                     _properties;
    std::vector<const search::fef::Location *>  _locations;
    std::vector<const search::fef::ITermData *> _terms;
    const search::index::IFieldLengthInspector &_field_length_inspector;

    QueryEnvironment(const QueryEnvironment &);
    QueryEnvironment &operator=(const QueryEnvironment &);

public:
    /**
     * Set up a new query environment.
     *
     * @param indexEnv index environment; referenced, not copied
     * @param attrContext attribute context; referenced, not copied
     * @param properties properties; copied
     **/
    QueryEnvironment(const search::fef::IIndexEnvironment &indexEnv,
                     const search::attribute::IAttributeContext &attrContext,
                     const search::fef::Properties &properties,
                     const search::index::IFieldLengthInspector &field_length_inspector);

    /**
     * Used to edit the list of terms by the one setting up this query
     * environment.
     *
     * @return modifiable list of terms data pointers
     **/
    std::vector<const search::fef::ITermData *> &terms() { return _terms; }

    /**
     * Used to edit the list of locations by the one setting up this
     * query environment.
     *
     * Initially, only the first location in this list is made
     * available through the IQueryEnvironment interface.
     *
     * @return modifiable list of location data pointers
     **/
    std::vector<const search::fef::Location *> &locations() {
        return _locations;
    }

    // inherited from search::fef::IQueryEnvironment
    const search::fef::Properties &getProperties() const override;

    // inherited from search::fef::IQueryEnvironment
    uint32_t getNumTerms() const override;

    // inherited from search::fef::IQueryEnvironment
    const search::fef::ITermData *getTerm(uint32_t idx) const override;

    // inherited from search::fef::IQueryEnvironment
    const search::fef::Location & getLocation() const override;
    std::vector<const search::fef::Location *> getAllLocations() const override {
        return _locations;
    }

    // inherited from search::fef::IQueryEnvironment
    const search::attribute::IAttributeContext & getAttributeContext() const override;

    double get_average_field_length(const vespalib::string &field_name) const override;

    // inherited from search::fef::IQueryEnvironment
    const search::fef::IIndexEnvironment & getIndexEnvironment() const override;

    ~QueryEnvironment() override;
};

}
