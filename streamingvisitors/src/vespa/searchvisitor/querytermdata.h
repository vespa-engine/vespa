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

class QueryTermDataFactory final : public search::streaming::QueryNodeResultFactory {
public:
    using Normalizing = search::Normalizing;
    using QueryNormalization = search::QueryNormalization;
    QueryTermDataFactory(const  QueryNormalization * normalization) noexcept : _normalization(normalization) {}
    std::unique_ptr<search::streaming::QueryNodeResultBase> create() const override {
        return std::make_unique<QueryTermData>();
    }
    Normalizing normalizing_mode(std::string_view index) const noexcept override {
        return _normalization ? _normalization->normalizing_mode(index) : Normalizing::LOWERCASE_AND_FOLD;
    }
    bool allow_float_terms_rewrite(std::string_view index ) const noexcept override {
        return _normalization && _normalization->is_text_matching(index);
    }
private:
    const QueryNormalization * _normalization;
};


} // namespace streaming

