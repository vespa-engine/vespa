// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <tests/spi/memfiletestutils.h>
#include <tests/spi/logginglazyfile.h>

namespace storage {
namespace memfile {

class SimulatedFailureLazyFile : public vespalib::LazyFile
{
    mutable int _readOpsBeforeFailure;
    mutable int _writeOpsBeforeFailure;
public:
    class Factory : public Environment::LazyFileFactory {
    public:
        Factory()
            : _readOpsBeforeFailure(-1),
              _writeOpsBeforeFailure(0)
        {
        }
        vespalib::LazyFile::UP createFile(const std::string& fileName) const {
            return vespalib::LazyFile::UP(
                    new SimulatedFailureLazyFile(fileName,
                            vespalib::File::DIRECTIO,
                            _readOpsBeforeFailure,
                            _writeOpsBeforeFailure));
        }

        void setReadOpsBeforeFailure(int ops) {
            _readOpsBeforeFailure = ops;
        }

        void setWriteOpsBeforeFailure(int ops) {
            _writeOpsBeforeFailure = ops;
        }
    private:
        int _readOpsBeforeFailure;
        int _writeOpsBeforeFailure;
    };

    SimulatedFailureLazyFile(
            const std::string& filename,
            int flags,
            int readOpsBeforeFailure,
            int writeOpsBeforeFailure)
        : LazyFile(filename, flags),
          _readOpsBeforeFailure(readOpsBeforeFailure),
          _writeOpsBeforeFailure(writeOpsBeforeFailure)
    {
    }

    off_t write(const void *buf, size_t bufsize, off_t offset)
    {
        if (_writeOpsBeforeFailure == 0) {
            throw vespalib::IoException(
                    "A simulated I/O write exception was triggered",
                    vespalib::IoException::CORRUPT_DATA, VESPA_STRLOC);
        }
        --_writeOpsBeforeFailure;
        return vespalib::LazyFile::write(buf, bufsize, offset);
    }

    size_t read(void *buf, size_t bufsize, off_t offset) const
    {
        if (_readOpsBeforeFailure == 0) {
            throw vespalib::IoException(
                    "A simulated I/O read exception was triggered",
                    vespalib::IoException::CORRUPT_DATA, VESPA_STRLOC);
        }
        --_readOpsBeforeFailure;
        return vespalib::LazyFile::read(buf, bufsize, offset);
    }
};

} // ns memfile
} // ns storage

