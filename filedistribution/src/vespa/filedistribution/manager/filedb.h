// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vespa/filedistribution/model/filedbmodel.h>

namespace filedistribution {

class FileDB {
    Path _dbPath;
public:
    FileDB(Path dbPath);
    DirectoryGuard::UP getGuard() { return std::make_unique<DirectoryGuard>(_dbPath); }
    /**
     *
     * @param directoryGuard The guard you need to hold in order to prevent someone fidling with your directory.
     * @param original The file top copy
     * @param name The name the file shall have.
     * @return true if it was added, false if it was already present.
     */
    bool add(const DirectoryGuard & directoryGuard, Path original, const std::string& name);
};

} //namespace filedistribution

