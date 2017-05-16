// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace proton {

/**
 * Class used to upgrade a row column directory /rX/cY to an elastic directory /nZ
 * where Z is the distribution key for that search node.
 */
class DataDirectoryUpgrader
{
public:
    class RowColDir
    {
    private:
        vespalib::string _row;
        vespalib::string _col;

    public:
        RowColDir(const vespalib::string &row_, const vespalib::string &col_);
        ~RowColDir();
        const vespalib::string &row() const { return _row; }
        const vespalib::string &col() const { return _col; }
        vespalib::string dir() const { return row() + "/" + col(); }
        bool operator< (const RowColDir &rhs) const { return dir() < rhs.dir(); }
    };
    typedef std::vector<RowColDir> RowColDirs;

    class ScanResult
    {
    private:
        RowColDirs  _rowColDirs;
        bool        _destDirExisting;

    public:
        ScanResult();
        void addDir(const RowColDir &dir) {
            _rowColDirs.push_back(dir);
        }
        RowColDirs &getRowColDirs() { return _rowColDirs; }
        const RowColDirs &getRowColDirs() const { return _rowColDirs; }
        void setDestDirExisting(bool val) { _destDirExisting = val; }
        bool isDestDirExisting() const { return _destDirExisting; }
    };

    enum Status
    {
        IGNORE,
        COMPLETE,
        ERROR
    };

    class UpgradeResult
    {
    private:
        const Status _status;
        const vespalib::string _desc;

    public:
        UpgradeResult(const Status status, const vespalib::string &desc);
        Status getStatus() const { return _status; }
        const vespalib::string &getDesc() const { return _desc; }
    };

private:
    const vespalib::string _scanDir;
    const vespalib::string _destDir;

public:
    DataDirectoryUpgrader(const vespalib::string &scanDir, const vespalib::string &destDir);
    ~DataDirectoryUpgrader();
    ScanResult scan() const;
    UpgradeResult upgrade(const ScanResult &scanResult) const;
};

} // namespace proton

