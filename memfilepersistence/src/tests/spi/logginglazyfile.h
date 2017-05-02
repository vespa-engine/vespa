// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/io/fileutil.h>
#include <sstream>

namespace storage::memfile {

class LoggingLazyFile : public vespalib::LazyFile {
public:
    class Factory : public Environment::LazyFileFactory {
    public:
        vespalib::LazyFile::UP createFile(const std::string& fileName) const override {
            return vespalib::LazyFile::UP(
                    new LoggingLazyFile(fileName, vespalib::File::DIRECTIO));
        }
    };

    enum OpType {
        READ = 0,
        WRITE
    };

    struct Entry {
        OpType opType;
        size_t bufsize;
        off_t offset;

        std::string toString() const {
            std::ostringstream ost;
            ost << (opType == READ ? "Reading " : "Writing ")
                << bufsize
                << " bytes at "
                << offset;
            return ost.str();
        }
    };

    mutable std::vector<Entry> operations;

    LoggingLazyFile(const std::string& filename, int flags)
        : LazyFile(filename, flags) {};

    size_t getOperationCount() const {
        return operations.size();
    }

    off_t write(const void *buf, size_t bufsize, off_t offset) override {
        Entry e;
        e.opType = WRITE;
        e.bufsize = bufsize;
        e.offset = offset;

        operations.push_back(e);

        return vespalib::LazyFile::write(buf, bufsize, offset);
    }

    size_t read(void *buf, size_t bufsize, off_t offset) const override {
        Entry e;
        e.opType = READ;
        e.bufsize = bufsize;
        e.offset = offset;

        operations.push_back(e);

        return vespalib::LazyFile::read(buf, bufsize, offset);
    }

    std::string toString() const {
        std::ostringstream ost;
        for (uint32_t i = 0; i < operations.size(); i++) {
            ost << operations[i].toString() << "\n";
        }

        return ost.str();
    }

};

}
