// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "indexenvironment.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/location.h>
#include <vespa/searchlib/fef/simpletermdata.h>

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

    const Properties &getProperties() const override { return _properties; }
    uint32_t getNumTerms() const override { return _terms.size(); }
    const ITermData *getTerm(uint32_t idx) const override { return idx < _terms.size() ? &_terms[idx] : NULL; }
    const Location & getLocation() const override { return _location; }
    const search::attribute::IAttributeContext &getAttributeContext() const override { return *_attrCtx; }
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
    Location & getLocation() { return _location; }
};

} // namespace test
} // namespace fef
} // namespace search

