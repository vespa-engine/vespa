// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_access_recorder.h"
#include "indexenvironment.h"
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/properties.h>

namespace streaming {

/**
 * Implementation of the feature execution framework
 * query environment API for the search visitor.
 **/
class QueryEnvironment : public search::fef::IQueryEnvironment
{
private:
    const IndexEnvironment                     &_indexEnv;
    const search::fef::Properties              &_properties;
    std::unique_ptr<AttributeAccessRecorder>    _attrCtx;
    std::vector<const search::fef::ITermData *> _queryTerms;
    std::vector<search::common::GeoLocationSpec> _locations;

public:
    using UP = std::unique_ptr<QueryEnvironment>;

    QueryEnvironment(const vespalib::string & location,
                     const IndexEnvironment & indexEnv,
                     const search::fef::Properties & properties,
                     const search::IAttributeManager * attrMgr);
    ~QueryEnvironment() override;

    void addGeoLocation(const vespalib::string &field, const vespalib::string &location);
    const search::fef::Properties & getProperties() const override { return _properties; }
    uint32_t getNumTerms() const override { return _queryTerms.size(); }

    const search::fef::ITermData *getTerm(uint32_t idx) const override {
        if (idx >= _queryTerms.size()) {
            return nullptr;
        }
        return _queryTerms[idx];
    }

    GeoLocationSpecPtrs getAllLocations() const override;
    const search::attribute::IAttributeContext & getAttributeContext() const override { return *_attrCtx; }
    double get_average_field_length(const vespalib::string &) const override { return 100.0; }
    const search::fef::IIndexEnvironment & getIndexEnvironment() const override { return _indexEnv; }
    void addTerm(const search::fef::ITermData *term) { _queryTerms.push_back(term); }

    std::vector<vespalib::string> get_accessed_attributes() const { return _attrCtx->get_accessed_attributes(); }
};

} // namespace streaming

