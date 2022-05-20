// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <limits>

namespace search::attribute {

/*
 * Persistent parameters for predicate attributes.
 */
class PersistentPredicateParams {
    uint32_t _arity;
    int64_t  _lower_bound;
    int64_t  _upper_bound;

public:
    PersistentPredicateParams()
        : _arity(8),
          _lower_bound(std::numeric_limits<int64_t>::min()),
          _upper_bound(std::numeric_limits<int64_t>::max())
    {
    }
    uint32_t arity()                      const { return _arity; }
    int64_t lower_bound()                 const { return _lower_bound; }
    int64_t upper_bound()                 const { return _upper_bound; }
    void setArity(uint32_t v)                    { _arity = v; }
    void setBounds(int64_t lower, int64_t upper) { _lower_bound = lower; _upper_bound = upper; }

    bool operator==(const PersistentPredicateParams &rhs) const {
        return ((_arity == rhs._arity) &&
                (_lower_bound == rhs._lower_bound) &&
                (_upper_bound == rhs._upper_bound));
    }
};

}
