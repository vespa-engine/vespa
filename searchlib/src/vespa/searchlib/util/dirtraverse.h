// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search {

class DirectoryTraverse
{
private:
    DirectoryTraverse(const DirectoryTraverse &);
    DirectoryTraverse& operator=(const DirectoryTraverse &);

public:
    class Name
    {
    private:
        Name(const Name &);
        Name& operator=(const Name &);

    public:
        char *_name;
        Name *_next;
        explicit Name(const char *name);
        ~Name();
        static Name *sort(Name *head, int count);
    };
private:
    char *_baseDir;
    Name *_nameHead;
    int _nameCount;
    Name *_dirHead;
    Name *_dirTail;
    Name *_pdirHead;
    Name *_rdirHead;
    Name *_curDir;
    Name *_curName;
    char *_fullDirName;
    char *_fullName;
    char *_relName;
public:
    const char *GetFullName() const { return _fullName; }
    const char *GetRelName() const { return _relName; }
    void QueueDir(const char *name);
    void PushDir(const char *name);
    void PushRemoveDir(const char *name);
    void PushPushedDirs();
    Name *UnQueueDir();
    Name *UnQueueName();
    void ScanSingleDir();
    bool NextName();
    bool NextRemoveDir();
    bool RemoveTree();
    uint64_t GetTreeSize(); // Returns size of directory in bytes
    explicit DirectoryTraverse(const char *baseDir);
    ~DirectoryTraverse();
};

} // namespace search
