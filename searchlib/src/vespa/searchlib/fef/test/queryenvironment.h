// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/location.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include "indexenvironment.h"

namespace search {
namespace fef {
namespace test {

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
    Location                    _location;
    search::attribute::IAttributeContext::UP _attrCtx;

public:
    /**
     * Constructs a new query environment.
     *
     * @param indexEnv The index environment of this.
     */
    QueryEnvironment(IndexEnvironment *indexEnv = NULL);
    ~QueryEnvironment();

    // Inherit doc from IQueryEnvironment.
    virtual const Properties &getProperties() const { return _properties; }

    // Inherit doc from IQueryEnvironment.
    virtual uint32_t getNumTerms() const { return _terms.size(); }

    // Inherit doc from IQueryEnvironment.
    virtual const ITermData *getTerm(uint32_t idx) const { return idx < _terms.size() ? &_terms[idx] : NULL; }

    // Inherit doc from IQueryEnvironment.
    virtual const Location & getLocation() const { return _location; }

    // Inherit doc from IQueryEnvironment.
    virtual const search::attribute::IAttributeContext &getAttributeContext() const { return *_attrCtx; }

    // Inherit doc from IQueryEnvironment.
    virtual const IIndexEnvironment &getIndexEnvironment() const { assert(_indexEnv != NULL); return *_indexEnv; }

    /** Returns a reference to the index environment of this. */
    IndexEnvironment *getIndexEnv() { return _indexEnv; }

    /** Returns a const reference to the index environment of this. */
    const IndexEnvironment *getIndexEnv() const { return _indexEnv; }

    /** Sets the index environment of this. */
    QueryEnvironment &setIndexEnv(IndexEnvironment *indexEnv) {
        _indexEnv = indexEnv;
        _attrCtx = ((indexEnv == NULL) ? search::attribute::IAttributeContext::UP() : 
                    indexEnv->getAttributeManager().createContext());
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
    Location & getLocation() { return _location; }
};

} // namespace test
} // namespace fef
} // namespace search

