// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <deque>
#include <memory>
#include <vector>

namespace search::memoryindex {

class DocumentInverter;
class DocumentInverterContext;

/*
 * Class containing the document inverters used by a memory index.
 */
class DocumentInverterCollection {
    DocumentInverterContext&                       _context;
    std::vector<std::unique_ptr<DocumentInverter>> _free_inverters;
    std::deque<std::unique_ptr<DocumentInverter>>  _inflight_inverters;
    std::unique_ptr<DocumentInverter>              _active_inverter;
    uint32_t                                       _num_inverters;
    uint32_t                                       _max_inverters;
public:
    DocumentInverterCollection(DocumentInverterContext& context, uint32_t max_inverters);
    ~DocumentInverterCollection();
    DocumentInverter& get_active_inverter() noexcept { return *_active_inverter; }
    void switch_active_inverter();
    uint32_t get_num_inverters() const noexcept { return _num_inverters; }
    uint32_t get_max_inverters() const noexcept { return _max_inverters; }
};

}
