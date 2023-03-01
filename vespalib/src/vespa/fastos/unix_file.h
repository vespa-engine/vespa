// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
******************************************************************************
* @author  Oivind H. Danielsen
* @date    Creation date: 2000-01-18
* @file
* Class definitions for FastOS_UNIX_File and FastOS_UNIX_DirectoryScan.
*****************************************************************************/

#pragma once

#include <vespa/fastos/file.h>

/**
 * This is the generic UNIX implementation of @ref FastOS_FileInterface.
 */
class FastOS_UNIX_File : public FastOS_FileInterface
{
public:
    using FastOS_FileInterface::ReadBuf;
private:
    FastOS_UNIX_File(const FastOS_UNIX_File&);
    FastOS_UNIX_File& operator=(const FastOS_UNIX_File&);

protected:
    void  *_mmapbase;
    size_t _mmaplen;
    int    _filedes;
    int    _mmapFlags;
    bool   _mmapEnabled;

    static unsigned int CalcAccessFlags(unsigned int openFlags);

public:
    static bool Rename (const char *currentFileName, const char *newFileName);
    bool Rename (const char *newFileName) override {
        return FastOS_FileInterface::Rename(newFileName);
    }

    static bool Stat(const char *filename, FastOS_StatInfo *statInfo);

    static std::string getCurrentDirectory();

    static bool SetCurrentDirectory (const char *pathName);
    static int GetMaximumFilenameLength (const char *pathName);
    static int GetMaximumPathLength (const char *pathName);

    FastOS_UNIX_File(const char *filename=nullptr)
        : FastOS_FileInterface(filename),
          _mmapbase(nullptr),
          _mmaplen(0),
          _filedes(-1),
          _mmapFlags(0),
          _mmapEnabled(false)
    { }

    void ReadBuf(void *buffer, size_t length, int64_t readOffset) override;
    [[nodiscard]] ssize_t Read(void *buffer, size_t len) override;
    [[nodiscard]] ssize_t Write2(const void *buffer, size_t len) override;
    bool Open(unsigned int openFlags, const char *filename) override;
    [[nodiscard]] bool Close() override;
    bool IsOpened() const override { return _filedes >= 0; }

    void enableMemoryMap(int flags) override {
        _mmapEnabled = true;
        _mmapFlags = flags;
    }

    void *MemoryMapPtr(int64_t position) const override {
        if (_mmapbase != nullptr) {
            if (position < int64_t(_mmaplen)) {
                return static_cast<void *>(static_cast<char *>(_mmapbase) + position);
            } else {  // This is an indication that the file size has changed and a remap/reopen must be done.
                return nullptr;
            }
        } else {
            return nullptr;
        }
    }

    bool IsMemoryMapped() const override { return _mmapbase != nullptr; }
    bool SetPosition(int64_t desiredPosition) override;
    int64_t GetPosition() override;
    int64_t GetSize() override;
    time_t GetModificationTime() override;
    bool Delete() override;
    [[nodiscard]] bool Sync() override;
    bool SetSize(int64_t newSize) override;
    void dropFromCache() const override;

    static bool Delete(const char *filename);
    static int GetLastOSError();
    static Error TranslateError(const int osError);
    static std::string getErrorString(const int osError);
    static int64_t GetFreeDiskSpace (const char *path);
    static int count_open_files();
};

#include <dirent.h>
/**
 * This is the generic UNIX implementation of @ref FastOS_DirectoryScan.
 */
class FastOS_UNIX_DirectoryScan : public FastOS_DirectoryScanInterface
{
private:
    FastOS_UNIX_DirectoryScan(const FastOS_UNIX_DirectoryScan&);
    FastOS_UNIX_DirectoryScan& operator=(const FastOS_UNIX_DirectoryScan&);

    bool _statRun;
    bool _isDirectory;
    bool _isRegular;
    char *_statName;
    char *_statFilenameP;

    void DoStat();

protected:
    DIR *_dir;
    struct dirent *_dp;

public:
    FastOS_UNIX_DirectoryScan(const char *searchPath);
    ~FastOS_UNIX_DirectoryScan();

    bool ReadNext() override;
    bool IsDirectory() override;
    bool IsRegular() override;
    const char *GetName() override;
    bool IsValidScan() const override;
};
