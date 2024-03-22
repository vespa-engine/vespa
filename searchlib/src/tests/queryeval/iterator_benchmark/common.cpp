// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "common.h"
#include <random>
#include <sstream>

using search::attribute::CollectionType;

namespace search::queryeval::test {

vespalib::string
to_string(const Config& attr_config)
{
    std::ostringstream oss;
    auto col_type = attr_config.collectionType();
    auto basic_type = attr_config.basicType();
    if (col_type == CollectionType::SINGLE) {
        oss << basic_type.asString();
    } else {
        oss << col_type.asString() << "<" << basic_type.asString() << ">";
    }
    if (attr_config.fastSearch()) {
        oss << "(fs)";
    }
    return oss.str();
}

vespalib::string
to_string(QueryOperator query_op)
{
    switch (query_op) {
        case QueryOperator::Term: return "Term";
        case QueryOperator::In: return "In";
        case QueryOperator::WeightedSet: return "WeightedSet";
        case QueryOperator::DotProduct: return "DotProduct";
        case QueryOperator::And: return "And";
        case QueryOperator::Or: return "Or";
        case QueryOperator::WeakAnd: return "WeakAnd";
        case QueryOperator::ParallelWeakAnd: return "ParallelWeakAnd";
    }
    return "unknown";
}

namespace {

// TODO: Make seed configurable.
constexpr uint32_t default_seed = 1234;
std::mt19937 gen(default_seed);

}

BitVector::UP
random_docids(uint32_t docid_limit, uint32_t count)
{
    auto res = BitVector::create(docid_limit);
    if ((count + 1) == docid_limit) {
        res->notSelf();
        res->clearBit(0);
        return res;
    }
    uint32_t docids_left = count;
    // Bit 0 is never set since it is reserved as docid 0.
    // All other docids have equal probability to be set.
    for (uint32_t docid = 1; docid < docid_limit; ++docid) {
        std::uniform_int_distribution<uint32_t> distr(0, docid_limit - docid - 1);
        if (distr(gen) < docids_left) {
            res->setBit(docid);
            --docids_left;
        }
    }
    res->invalidateCachedCount();
    assert(res->countTrueBits() == count);
    return res;
}

int32_t
random_int(int32_t a, int32_t b)
{
    std::uniform_int_distribution<int32_t> distr(a, b);
    return distr(gen);
}

}