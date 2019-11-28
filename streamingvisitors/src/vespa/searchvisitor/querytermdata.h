// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/query/streaming/querynoderesultbase.h>

namespace storage {

/**
 * This class keeps data for a query term that is used by the ranking framework.
 **/
class QueryTermData : public search::streaming::QueryNodeResultBase
{
private:
    search::fef::SimpleTermData   _termData;
public:
    QueryTermData * clone() const override { return new QueryTermData(); }
    search::fef::SimpleTermData &getTermData() { return _termData; }
};

class QueryTermDataFactory final : public search::streaming::QueryNodeResultFactory {
public:
    std::unique_ptr<search::streaming::QueryNodeResultBase> create() const override {
        return std::make_unique<QueryTermData>();
    }
    bool getRewriteFloatTerms() const override { return true; }
};


} // namespace storage

