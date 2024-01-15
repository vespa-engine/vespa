// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/query/streaming/querynoderesultbase.h>

namespace streaming {

/**
 * This class keeps data for a query term that is used by the ranking framework.
 **/
class QueryTermData : public search::streaming::QueryNodeResultBase
{
private:
    search::fef::SimpleTermData   _termData;
public:
    QueryTermData * clone() const override { return new QueryTermData(); }
    search::fef::SimpleTermData &getTermData() noexcept { return _termData; }
};

class SearchMethodInfo {
public:
    using Normalizing = search::streaming::Normalizing;
    virtual ~SearchMethodInfo() = default;
    virtual bool is_text_matching(vespalib::stringref index) const noexcept = 0;
    virtual Normalizing normalizing_mode(vespalib::stringref index) const noexcept = 0;
};

class QueryTermDataFactory final : public search::streaming::QueryNodeResultFactory {
public:
    using Normalizing = search::streaming::Normalizing;
    QueryTermDataFactory(const SearchMethodInfo * searchMethodInfo) noexcept : _searchMethodInfo(searchMethodInfo) {}
    std::unique_ptr<search::streaming::QueryNodeResultBase> create() const override {
        return std::make_unique<QueryTermData>();
    }
    Normalizing normalizing_mode(vespalib::stringref index) const noexcept override {
        return _searchMethodInfo ? _searchMethodInfo->normalizing_mode(index) : Normalizing::LOWERCASE_AND_FOLD;
    }
    bool allow_float_terms_rewrite(vespalib::stringref index ) const noexcept override {
        return _searchMethodInfo && _searchMethodInfo->is_text_matching(index);
    }
private:
    const SearchMethodInfo * _searchMethodInfo;
};


} // namespace streaming

