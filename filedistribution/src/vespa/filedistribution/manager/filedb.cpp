// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filedb.h"

#include <boost/filesystem.hpp>

namespace fs = boost::filesystem;

namespace {

void copyDirectory(fs::path original, fs::path destination)
{
    fs::create_directory(destination);
    for (fs::directory_iterator curr(original), end;
         curr != end;
         ++curr) {

        fs::path destPath = destination / curr->path().filename();
        if ( fs::is_directory(curr->status()) ) {
            copyDirectory(*curr, destPath);
        } else {
            fs::copy_file(*curr, destPath);
        }
    }
}

} //anonymous namespace

namespace filedistribution {

FileDB::FileDB(fs::path dbPath)
    : _dbPath(dbPath) {}


bool
FileDB::add(const DirectoryGuard & directoryGuard, fs::path original, const std::string &name) {
    (void) directoryGuard;
    fs::path finalPath = _dbPath / name;
    fs::path targetPath = _dbPath / (name + ".new");
    if (fs::exists(finalPath) || fs::exists(targetPath)) {
        return false;
    }
    fs::path targetPathTemp = _dbPath / (name + ".tmp");

    if (fs::exists(targetPathTemp)) {
        fs::remove_all(targetPathTemp);
    }

    fs::create_directory(targetPathTemp);
    if (!fs::is_directory(original)) {
        fs::copy_file(original, targetPathTemp / original.filename());
    } else {
        copyDirectory(original, targetPathTemp / original.filename());
    }

    assert(!fs::exists(targetPath));
    fs::rename(targetPathTemp, targetPath);
    return true;
}

}
