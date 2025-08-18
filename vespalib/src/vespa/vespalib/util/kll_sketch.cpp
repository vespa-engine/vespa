// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "kll_sketch.h"

#include <DataSketches/kll_sketch.hpp>

namespace vespalib {

struct KLLSketch::Impl {
    datasketches::kll_sketch<double> _sketch;
};

KLLSketch::KLLSketch()
    : _impl(std::make_unique<Impl>())
{
}

KLLSketch::~KLLSketch() = default;

KLLSketch::KLLSketch(const KLLSketch& other) {
    _impl = std::make_unique<Impl>(*other._impl);
}

KLLSketch& KLLSketch::operator=(const KLLSketch& other) {
    _impl = std::make_unique<Impl>(*other._impl);
    return *this;
}

void KLLSketch::update(double item) {
    _impl->_sketch.update(item);
}

void KLLSketch::merge(const KLLSketch& other) {
    _impl->_sketch.merge(other._impl->_sketch);
}


bool KLLSketch::is_empty() const {
    return _impl->_sketch.is_empty();
}


double KLLSketch::get_quantile(double rank) const {
    return _impl->_sketch.get_quantile(rank);
}

}
