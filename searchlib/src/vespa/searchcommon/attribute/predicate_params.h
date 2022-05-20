// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "persistent_predicate_params.h"

namespace search::attribute {

/*
 * Parameters for predicate attributes.
 */
class PredicateParams : public PersistentPredicateParams
{
    double _dense_posting_list_threshold;
public:
    PredicateParams()
        : PersistentPredicateParams(),
          _dense_posting_list_threshold(0.4)
    {
    }

    double dense_posting_list_threshold() const { return _dense_posting_list_threshold; }
    void setDensePostingListThreshold(double v)  { _dense_posting_list_threshold = v; }
    bool operator==(const PredicateParams &rhs) const {
        return (PersistentPredicateParams::operator==(rhs) &&
                (_dense_posting_list_threshold == rhs._dense_posting_list_threshold));
    }
};

}
