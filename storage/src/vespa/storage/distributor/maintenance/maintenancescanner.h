// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>

namespace storage {
namespace distributor {

class MaintenanceScanner
{
public:
    virtual ~MaintenanceScanner() {}

    class ScanResult {
        bool _done;
        BucketDatabase::Entry _entry;

    public:
        bool isDone() const { return _done; }
        const BucketDatabase::Entry& getEntry() const { return _entry; }

        static ScanResult createDone() { return ScanResult(true); }
        static ScanResult createNotDone(BucketDatabase::Entry entry) {
            return ScanResult(entry);
        }

    private:
        ScanResult(bool done) : _done(done), _entry() {}
        ScanResult(const BucketDatabase::Entry& e) : _done(false), _entry(e) {}
    };

    virtual ScanResult scanNext() = 0;

    virtual void reset() = 0;
};

}
}

