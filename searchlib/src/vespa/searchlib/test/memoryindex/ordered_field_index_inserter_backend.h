// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <sstream>

namespace search::index { class DocIdAndFeatures; }

namespace search::memoryindex::test {

/*
 * Backend for test version of ordered field index inserter that creates
 * a string representation used to validate correct use of ordered field index inserter.
 */
class OrderedFieldIndexInserterBackend {
    std::stringstream _ss;
    bool _first;
    bool _verbose;
    bool _show_interleaved_features;
    void addComma();
public:
    OrderedFieldIndexInserterBackend();
    ~OrderedFieldIndexInserterBackend();
    void setNextWord(const vespalib::stringref word);
    void add(uint32_t docId, const index::DocIdAndFeatures &features);
    void remove(uint32_t docId);
    void rewind(uint32_t field_id);
    std::string toStr() const;
    void reset();
    void setVerbose() { _verbose = true; }
    void set_show_interleaved_features() { _show_interleaved_features = true; }
};

}
