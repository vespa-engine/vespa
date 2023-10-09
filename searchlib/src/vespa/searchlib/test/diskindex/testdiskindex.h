// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/diskindex/diskindex.h>

namespace search {
namespace diskindex {

class TestDiskIndex {
private:
    void buildIndex(const std::string &dir, bool directio,
                    bool fieldEmpty, bool docEmpty, bool wordEmpty);
protected:
    index::Schema _schema;
    std::unique_ptr<DiskIndex> _index;

public:
    TestDiskIndex();
    ~TestDiskIndex();
    DiskIndex & getIndex() { return *_index; }
    void buildSchema();
    void openIndex(const std::string &dir, bool directio, bool readmmap,
                   bool fieldEmpty, bool docEmpty, bool wordEmpty);
};

}
}
