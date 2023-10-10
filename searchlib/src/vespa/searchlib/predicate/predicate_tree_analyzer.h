// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tree_crumbs.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <map>

namespace search::predicate {

/**
 * Analyzes a predicate tree, in the form of a slime object, to find
 * the value for min_feature (the minimum number of features required
 * to find a match), and a map of sizes that is used when assigning
 * intervals.
 */
class PredicateTreeAnalyzer {
    std::map<std::string, int> _key_counts;
    std::map<std::string, int> _size_map;
    int _min_feature;
    bool _has_not;

    bool _negated;
    TreeCrumbs _crumbs;
    int _size;

    // Fills _key_counts, _size_map, and _has_not.
    void traverseTree(const vespalib::slime::Inspector &in);
    float findMinFeature(const vespalib::slime::Inspector &in);

public:
    PredicateTreeAnalyzer(const vespalib::slime::Inspector &in);
    ~PredicateTreeAnalyzer();

    int getMinFeature() const { return _min_feature; }
    int getSize() const { return _size; }
    const std::map<std::string, int> &getSizeMap() const { return _size_map; }
};

}
