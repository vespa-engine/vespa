// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <memory>
#include <mutex>
#include <atomic>

namespace search::queryeval {
    class Searchable;
    class IRequestContext;
    class SearchIterator;
    class Blueprint;
}
namespace search::fef { class MatchData; }

namespace proton::matching {

class RangeQueryLocator;

/**
 * This class is responsible for creating attribute-based search
 * iterators that are used to limit the search space. Each search
 * thread wants a separate search iterator, but the blueprint is
 * shared between threads. All threads should request the same number
 * of hits, so this class just lets the first thread requesting a
 * search decide the number of hits in the underlying blueprint.
 **/
class AttributeLimiter
{
public:
    enum DiversityCutoffStrategy { LOOSE, STRICT};
    AttributeLimiter(const RangeQueryLocator & _rangeQueryLocator,
                     search::queryeval::Searchable &searchable_attributes,
                     const search::queryeval::IRequestContext & requestContext,
                     const vespalib::string &attribute_name, bool descending,
                     const vespalib::string &diversity_attribute,
                     double diversityCutoffFactor,
                     DiversityCutoffStrategy diversityCutoffStrategy);
    ~AttributeLimiter();
    std::unique_ptr<search::queryeval::SearchIterator> create_search(size_t want_hits, size_t max_group_size, bool strictSearch);
    bool was_used() const;
    ssize_t getEstimatedHits() const;
    static DiversityCutoffStrategy toDiversityCutoffStrategy(vespalib::stringref strategy);
private:
    search::queryeval::Searchable                      & _searchable_attributes;
    const search::queryeval::IRequestContext           & _requestContext;
    const RangeQueryLocator                            & _rangeQueryLocator;
    vespalib::string                                     _attribute_name;
    bool                                                 _descending;
    vespalib::string                                     _diversity_attribute;
    std::mutex                                           _lock;
    std::vector<std::unique_ptr<search::fef::MatchData>> _match_datas;
    std::unique_ptr<search::queryeval::Blueprint>        _blueprint;
    std::atomic<ssize_t>                                 _estimatedHits;
    double                                               _diversityCutoffFactor;
    DiversityCutoffStrategy                              _diversityCutoffStrategy;
};

}
