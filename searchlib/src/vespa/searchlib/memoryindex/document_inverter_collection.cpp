// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_inverter_collection.h"
#include "document_inverter.h"
#include <cassert>

namespace search::memoryindex {

DocumentInverterCollection::DocumentInverterCollection(DocumentInverterContext& context, uint32_t max_inverters)
    : _context(context),
      _free_inverters(),
      _inflight_inverters(),
      _active_inverter(std::make_unique<DocumentInverter>(_context)),
      _num_inverters(1),
      _max_inverters(max_inverters)
{
}

DocumentInverterCollection::~DocumentInverterCollection() = default;

void
DocumentInverterCollection::switch_active_inverter()
{
    _inflight_inverters.emplace_back(std::move(_active_inverter));
    while (!_inflight_inverters.empty() && _inflight_inverters.front()->has_zero_ref_count()) {
        _free_inverters.emplace_back(std::move(_inflight_inverters.front()));
        _inflight_inverters.pop_front();
    }
    if (!_free_inverters.empty()) {
        _active_inverter = std::move(_free_inverters.back());
        _free_inverters.pop_back();
        return;
    }
    if (_num_inverters >= _max_inverters) {
        assert(!_inflight_inverters.empty());
        _active_inverter = std::move(_inflight_inverters.front());
        _inflight_inverters.pop_front();
        _active_inverter->wait_for_zero_ref_count();
        return;
    }
    _active_inverter = std::make_unique<DocumentInverter>(_context);
    ++_num_inverters;
}

}
