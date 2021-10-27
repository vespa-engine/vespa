// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vsm/vsm/i_matching_elements_filler.h>

namespace search::streaming { class Query; }
namespace vdslib { class SearchResult; }
namespace vsm {
class FieldIdTSearcherMap;
class StorageDocument;
}

namespace streaming {

class HitCollector;

/*
 * Class for filling matching elements structure for streaming search
 * based on query and struct field mapper.
 */
class MatchingElementsFiller : public vsm::IMatchingElementsFiller {
    vsm::FieldIdTSearcherMap& _field_searcher_map;
    search::streaming::Query& _query;
    HitCollector&             _hit_collector;
    vdslib::SearchResult&     _search_result;

public:
    MatchingElementsFiller(vsm::FieldIdTSearcherMap& field_searcher_map, search::streaming::Query& query,
                           HitCollector& hit_collector, vdslib::SearchResult& search_result);
    virtual ~MatchingElementsFiller();
    std::unique_ptr<search::MatchingElements> fill_matching_elements(const search::MatchingElementsFields& fields) override;
};
    
}
