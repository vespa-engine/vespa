// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        { }
        vespalib::LazyFile::UP createFile(const std::string& fileName) const override;

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
            int writeOpsBeforeFailure);

    off_t write(const void *buf, size_t bufsize, off_t offset) override;
    size_t read(void *buf, size_t bufsize, off_t offset) const override;
};

} // ns memfile
} // ns storage

