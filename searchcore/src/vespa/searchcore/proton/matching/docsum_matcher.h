// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vector>
#include <memory>

namespace proton::matching {

class MatchToolsFactory;
class SearchSession;

/**
 * Used to perform additional matching related to a docsum
 * request. Note that external objects must be kept alive by the one
 * using this class.
 **/
class DocsumMatcher
{
private:
    using FeatureSet = search::FeatureSet;
    using MatchingElementsFields = search::MatchingElementsFields;
    using MatchingElements = search::MatchingElements;

    std::shared_ptr<SearchSession>     _from_session;
    std::unique_ptr<MatchToolsFactory> _from_mtf;
    MatchToolsFactory    *_mtf;
    std::vector<uint32_t> _docs;

public:
    DocsumMatcher();
    DocsumMatcher(std::shared_ptr<SearchSession> session, std::vector<uint32_t> docs);
    DocsumMatcher(std::unique_ptr<MatchToolsFactory> mtf, std::vector<uint32_t> docs);
    ~DocsumMatcher();

    using UP = std::unique_ptr<DocsumMatcher>;

    FeatureSet::UP get_summary_features() const;
    FeatureSet::UP get_rank_features() const;
    MatchingElements::UP get_matching_elements(const MatchingElementsFields &fields) const;
};

}
