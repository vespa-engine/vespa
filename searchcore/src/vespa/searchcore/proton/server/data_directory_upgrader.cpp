// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_directory_upgrader.h"

#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <iostream>
#include <sys/stat.h>

namespace proton {

namespace {

vespalib::string UPGRADE_SOURCE_FILE = "data-directory-upgrade-source.txt";
vespalib::string DOWNGRADE_SCRIPT_FILE = "data-directory-downgrade.sh";

bool
isValidDir(const vespalib::string &dir, char prefix)
{
    if (dir.empty() || dir[0] != prefix) {
        return false;
    }
    vespalib::asciistream stream(dir.substr(1));
    uint32_t number = 0;
    try {
        stream >> number;
    } catch (const vespalib::IllegalArgumentException &) {
        return false;
    }
    return true;
}

bool
isRowDir(const vespalib::string &dir)
{
    return isValidDir(dir, 'r');
}

bool
isColumnDir(const vespalib::string &dir)
{
    return isValidDir(dir, 'c');
}

vespalib::string
createDirString(const DataDirectoryUpgrader::RowColDirs &dirs)
{
    vespalib::asciistream result;
    bool first = true;
    for (const auto &dir : dirs) {
        if (!first) {
            result << ", ";
        }
        result << "'" << dir.dir() << "'";
        first = false;
    }
    return result.str();
}

void
writeUpgradeFile(const vespalib::string &srcDir,
                 const vespalib::string &dstDir)
{
    vespalib::File file(dstDir + "/" + UPGRADE_SOURCE_FILE);
    file.open(vespalib::File::CREATE);
    file.write(&srcDir[0], srcDir.size(), 0);
    file.close();
}

void
writeDowngradeScript(const vespalib::string &scanDir,
                     const vespalib::string &dstDir,
                     const DataDirectoryUpgrader::RowColDir &rowColDir)
{
    vespalib::asciistream script;
    vespalib::string fullRowDir = scanDir + "/" + rowColDir.row();
    vespalib::string fullRowColDir = scanDir + "/" + rowColDir.dir();
    script << "#!/bin/sh\n\n";
    script << "mkdir " << fullRowDir << " || exit 1\n";
    script << "chown yahoo " << fullRowDir << "\n";
    script << "mv " << dstDir << " " << fullRowColDir << "\n";
    script << "rm " << fullRowColDir << "/" << UPGRADE_SOURCE_FILE << "\n";
    script << "rm " << fullRowColDir << "/" << DOWNGRADE_SCRIPT_FILE << "\n";
    vespalib::string fileName = dstDir + "/" + DOWNGRADE_SCRIPT_FILE;
    vespalib::File file(fileName);
    file.open(vespalib::File::CREATE);
    file.write(script.c_str(), script.size(), 0);
    file.close();
    chmod(fileName.c_str(), 0755);
}

}


DataDirectoryUpgrader::RowColDir::RowColDir(const vespalib::string &row_,
                                            const vespalib::string &col_)
    : _row(row_),
      _col(col_)
{
}

DataDirectoryUpgrader::RowColDir::~RowColDir() { }

DataDirectoryUpgrader::ScanResult::ScanResult()
    : _rowColDirs(),
      _destDirExisting(false)
{
}

DataDirectoryUpgrader::UpgradeResult::UpgradeResult(const Status status,
                                                    const vespalib::string &desc)
    : _status(status),
      _desc(desc)
{
}

DataDirectoryUpgrader::DataDirectoryUpgrader(const vespalib::string &scanDir,
                                             const vespalib::string &destDir)
    : _scanDir(scanDir),
      _destDir(destDir)
{
}

DataDirectoryUpgrader::~DataDirectoryUpgrader() {}

DataDirectoryUpgrader::ScanResult
DataDirectoryUpgrader::scan() const
{
    ScanResult result;
    try {
        vespalib::DirectoryList dirs = listDirectory(_scanDir);
        for (const auto &dir : dirs) {
            if (isRowDir(dir)) {
                vespalib::DirectoryList subDirs = listDirectory(_scanDir + "/" + dir);
                for (const auto &subDir : subDirs) {
                    if (isColumnDir(subDir)) {
                        result.addDir(RowColDir(dir, subDir));
                    }
                }
            }
        }
    } catch (const vespalib::IoException &) {
        // Scan dir does not exists
    }
    try {
        if (vespalib::stat(_destDir).get() != NULL) {
            result.setDestDirExisting(true);
        }
    } catch (const vespalib::IoException &) {}
    std::sort(result.getRowColDirs().begin(), result.getRowColDirs().end());
    return result;
}

DataDirectoryUpgrader::UpgradeResult
DataDirectoryUpgrader::upgrade(const ScanResult &scanResult) const
{
    if (scanResult.isDestDirExisting()) {
        return UpgradeResult(IGNORE,
                             vespalib::make_string("Destination directory '%s' is already existing",
                                                   _destDir.c_str()));
    }
    const RowColDirs &rowColDirs = scanResult.getRowColDirs();
    if (rowColDirs.empty()) {
        return UpgradeResult(IGNORE, "No directory to upgrade");
    }
    if (rowColDirs.size() > 1) {
        return UpgradeResult(ERROR,
                             vespalib::make_string("Can only upgrade a single directory, was asked to upgrade %zu (%s)",
                                                   rowColDirs.size(), createDirString(rowColDirs).c_str()));
    }
    const vespalib::string src = _scanDir + "/" + rowColDirs[0].dir();
    const vespalib::string &dst = _destDir;
    try {
        if (!vespalib::rename(src, dst)) {
            return UpgradeResult(ERROR,
                                 vespalib::make_string("Failed to rename directory '%s' to '%s'",
                                                       src.c_str(), dst.c_str()));
        }
        const vespalib::string rmDir = _scanDir + "/" + rowColDirs[0].row();
        if (!vespalib::rmdir(rmDir, false)) {
            return UpgradeResult(ERROR,
                                 vespalib::make_string("Failed to remove empty directory '%s'",
                                                       rmDir.c_str()));
        }
        writeUpgradeFile(src, dst);
        writeDowngradeScript(_scanDir, dst, rowColDirs[0]);
    } catch (const vespalib::IoException &ex) {
        return UpgradeResult(ERROR,
                             vespalib::make_string("Got exception during data directory upgrade from '%s' to '%s': %s",
                                                   src.c_str(), dst.c_str(), ex.what()));
    }
    return UpgradeResult(COMPLETE,
                         vespalib::make_string("Moved data from '%s' to '%s'",
                                               src.c_str(), dst.c_str()));
}

} // namespace proton
