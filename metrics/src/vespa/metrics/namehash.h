// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class metrics::NameHash
 * \ingroup metrics
 *
 * \brief Simple class to enable string reference counting to work better.
 *
 * When creating metrics, it is easy to use const char references from code,
 * for instance having a for loop setting up metrics for each thread, this will
 * not actually generate ref counted strings, but rather unique strings.
 *
 * Also, with ref counted strings, it is easy to screw it up if you access the
 * string in a way requiring copy.
 *
 * This class is used to just keep a set of strings, and having a class for
 * users to input their strings and get the "master" string with that content.
 *
 * Metrics use this after having registered metrics, to ensure we dont keep more
 * copies of non-unique strings than needed.
 */
#pragma once

#include "memoryconsumption.h"

namespace metrics {

class NameSet;

class NameHash {
    std::unique_ptr<NameSet> _hash;
    uint32_t _unifiedCounter;
    uint32_t _checkedCounter;

public:
    NameHash(const NameHash &) = delete;
    NameHash & operator = (const NameHash &) = delete;
    NameHash();
    ~NameHash();

    void updateName(std::string& name);

    uint32_t getUnifiedStringCount() const { return _unifiedCounter; }
    uint32_t getCheckedStringCount() const { return _checkedCounter; }
    void resetCounts() { _unifiedCounter = 0; _checkedCounter = 0; }
    void addMemoryUsage(MemoryConsumption& mc) const;
};

} // metrics
