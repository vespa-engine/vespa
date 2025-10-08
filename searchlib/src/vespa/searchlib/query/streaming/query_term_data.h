// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "querynoderesultbase.h"
#include <vespa/searchlib/fef/simpletermdata.h>

namespace search::streaming {

/**
 * This class keeps data for a query term that is used by the ranking framework.
 **/
class QueryTermData : public QueryNodeResultBase
{
private:
    search::fef::SimpleTermData   _termData;
public:
    QueryTermData * clone() const override { return new QueryTermData(); }
    search::fef::SimpleTermData &getTermData() noexcept { return _termData; }
    const search::fef::SimpleTermData &getTermData() const noexcept { return _termData; }
};

class QueryTermDataFactory final : public QueryNodeResultFactory {
public:
    using Normalizing = search::Normalizing;
    using QueryNormalization = search::QueryNormalization;
    QueryTermDataFactory(const  QueryNormalization * normalization,
                         const search::queryeval::IElementGapInspector* element_gap_inspector) noexcept
        : _normalization(normalization),
          _element_gap_inspector(element_gap_inspector)
    {}
    ~QueryTermDataFactory() override;
    std::unique_ptr<QueryNodeResultBase> create() const override {
        return std::make_unique<QueryTermData>();
    }
    Normalizing normalizing_mode(std::string_view index) const noexcept override {
        return _normalization ? _normalization->normalizing_mode(index) : Normalizing::LOWERCASE_AND_FOLD;
    }
    bool allow_float_terms_rewrite(std::string_view index ) const noexcept override {
        return _normalization && _normalization->is_text_matching(index);
    }
    const search::queryeval::IElementGapInspector& get_element_gap_inspector() const noexcept override;
private:
    const QueryNormalization * _normalization;
    const search::queryeval::IElementGapInspector* _element_gap_inspector;
};

}
