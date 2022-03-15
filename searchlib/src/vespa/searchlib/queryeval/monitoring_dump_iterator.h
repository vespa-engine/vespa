// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "monitoring_search_iterator.h"

namespace search::queryeval {

/**
 * Search iterator that dumps the search stats of the underlying
 * monitoring search iterator upon destruction.
 */
class MonitoringDumpIterator : public SearchIterator
{
private:
    MonitoringSearchIterator::UP _search;

public:
    MonitoringDumpIterator(MonitoringSearchIterator::UP iterator);
    ~MonitoringDumpIterator();

    // Overrides SearchIterator
    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docId) override;
    Trinary is_strict() const override { return _search->is_strict(); }
    void initRange(uint32_t beginid, uint32_t endid) override {
        _search->initRange(beginid, endid);
        SearchIterator::initRange(_search->getDocId()+1, _search->getEndId());
    }
};

}
