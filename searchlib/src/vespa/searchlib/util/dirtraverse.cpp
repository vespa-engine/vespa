// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dirtraverse.h"
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fastos/file.h>
#include <cassert>
#include <cstring>

namespace search {

extern "C" {
static int cmpname(const void *av, const void *bv)
{
    const DirectoryTraverse::Name *const a =
        *(const DirectoryTraverse::Name *const *) av;
    const DirectoryTraverse::Name *const b =
        *(const DirectoryTraverse::Name *const *) bv;
    return strcmp(a->_name, b->_name);
}
}

DirectoryTraverse::Name::Name(const char *name)
    : _name(nullptr),
      _next(nullptr)
{
    _name = strdup(name);
}
DirectoryTraverse::Name::~Name() { free(_name); }

DirectoryTraverse::Name *
DirectoryTraverse::Name::sort(Name *head, int count)
{
    Name *nl;
    Name **names;
    int i;

    names = new Name *[count];
    i = 0;
    for(nl = head; nl != nullptr; nl = nl->_next)
        names[i++] = nl;
    assert(i == count);
    qsort(names, count, sizeof(Name *), cmpname);
    for (i = 0; i < count; i++) {
        if (i + 1 < count)
            names[i]->_next = names[i + 1];
        else
            names[i]->_next = nullptr;
    }
    head = names[0];
    delete [] names;
    return head;
}


void
DirectoryTraverse::QueueDir(const char *name)
{
    Name *n = new Name(name);
    if (_dirTail == nullptr)
        _dirHead = n;
    else
        _dirTail->_next = n;
    _dirTail = n;
}


void
DirectoryTraverse::PushDir(const char *name)
{
    Name *n = new Name(name);
    n->_next = _pdirHead;
    _pdirHead = n;
}


void
DirectoryTraverse::PushRemoveDir(const char *name)
{
    Name *n = new Name(name);
    n->_next = _rdirHead;
    _rdirHead = n;
}


void
DirectoryTraverse::PushPushedDirs()
{
    Name *n;
    while (_pdirHead != nullptr) {
        n = _pdirHead;
        _pdirHead = n->_next;
        n->_next = _dirHead;
        _dirHead = n;
        if (_dirTail == nullptr)
            _dirTail = n;
    }
}


DirectoryTraverse::Name *
DirectoryTraverse::UnQueueDir()
{
    Name *n;
    PushPushedDirs();
    if (_dirHead == nullptr)
        return nullptr;
    n = _dirHead;
    _dirHead = n->_next;
    n->_next = nullptr;
    if (_dirHead == nullptr)
        _dirTail = nullptr;
    return n;
}

DirectoryTraverse::Name *
DirectoryTraverse::UnQueueName()
{
    Name *n;
    if (_nameHead == nullptr)
        return nullptr;
    n = _nameHead;
    _nameHead = n->_next;
    n->_next = nullptr;
    _nameCount--;
    return n;
}


void
DirectoryTraverse::ScanSingleDir()
{
    assert(_nameHead == nullptr);
    assert(_nameCount == 0);
    delete _curDir;
    free(_fullDirName);
    _fullDirName = nullptr;
    _curDir = UnQueueDir();
    if (_curDir == nullptr)
        return;
    _fullDirName = (char *) malloc(strlen(_baseDir) + 1 +
                                   strlen(_curDir->_name) + 1);
    strcpy(_fullDirName, _baseDir);
    if (_curDir->_name[0] != '\0') {
        strcat(_fullDirName, "/");
        strcat(_fullDirName, _curDir->_name);
    }
    FastOS_DirectoryScan *dirscan = new FastOS_DirectoryScan(_fullDirName);
    while (dirscan->ReadNext()) {
        const char *name = dirscan->GetName();
        if (strcmp(name, ".") == 0 ||
            strcmp(name, "..") == 0)
            continue;
        Name *nl = new Name(name);
        nl->_next = _nameHead;
        _nameHead = nl;
        _nameCount++;
    }
    if (_nameCount > 1)
        _nameHead = _nameHead->sort(_nameHead, _nameCount);
    delete dirscan;
}


bool
DirectoryTraverse::NextName()
{
    delete _curName;
    _curName = nullptr;
    while (_nameHead == nullptr && (_dirHead != nullptr || _pdirHead != nullptr))
        ScanSingleDir();
    if (_nameHead == nullptr)
        return false;
    _curName = UnQueueName();
    free(_fullName);
    _fullName = (char *) malloc(strlen(_fullDirName) + 1 +
                                strlen(_curName->_name) + 1);
    strcpy(_fullName, _fullDirName);
    _relName = _fullName + strlen(_baseDir) + 1;
    strcat(_fullName, "/");
    strcat(_fullName, _curName->_name);
    return true;
}


bool
DirectoryTraverse::NextRemoveDir()
{
    Name *curName;

    delete _curName;
    _curName = nullptr;
    if (_rdirHead == nullptr)
        return false;
    curName = _rdirHead;
    _rdirHead = curName->_next;
    free(_fullName);
    _fullName = (char *) malloc(strlen(_baseDir) + 1 +
                                strlen(curName->_name) + 1);
    strcpy(_fullName, _baseDir);
    _relName = _fullName + strlen(_baseDir) + 1;
    strcat(_fullName, "/");
    strcat(_fullName, curName->_name);
    delete curName;
    return true;
}


bool
DirectoryTraverse::RemoveTree()
{
    FastOS_StatInfo statInfo;

    while (NextName()) {
        const char *relname = GetRelName();
        const char *fullname = GetFullName();
        if (FastOS_File::Stat(fullname, &statInfo)) {
            if (statInfo._isDirectory) {
                PushDir(relname);
                PushRemoveDir(relname);
            } else {
                FastOS_File::Delete(fullname);
            }
        }
    }
    while (NextRemoveDir()) {
        const char *fullname = GetFullName();
        FastOS_File::RemoveDirectory(fullname);
    }
    FastOS_File::RemoveDirectory(_baseDir);
    return true;
}

uint64_t
DirectoryTraverse::GetTreeSize()
{
    FastOS_StatInfo statInfo;
    uint64_t size = 0;
    const uint64_t blockSize = 4_Ki;

    while (NextName()) {
        const char *relname = GetRelName();
        const char *fullname = GetFullName();
        if (FastOS_File::Stat(fullname, &statInfo)) {
            uint64_t adjSize = ((statInfo._size + blockSize - 1) / blockSize) * blockSize;
            size += adjSize;
            if (statInfo._isDirectory) {
                PushDir(relname);
            }
        }
    }
    return size;
}

DirectoryTraverse::DirectoryTraverse(const char *baseDir)
    : _baseDir(nullptr),
      _nameHead(nullptr),
      _nameCount(0),
      _dirHead(nullptr),
      _dirTail(nullptr),
      _pdirHead(nullptr),
      _rdirHead(nullptr),
      _curDir(nullptr),
      _curName(nullptr),
      _fullDirName(nullptr),
      _fullName(nullptr),
      _relName(nullptr)
{
    _baseDir = strdup(baseDir);
    QueueDir("");
    ScanSingleDir();
}


DirectoryTraverse::~DirectoryTraverse()
{
    free(_fullDirName);
    free(_fullName);
    free(_baseDir);
    delete _curDir;
    delete _curName;
    PushPushedDirs();
    while (_dirHead != nullptr)
        delete UnQueueDir();
    while (_nameHead != nullptr)
        delete UnQueueName();
    while (_rdirHead != nullptr) {
        Name *n;
        n = _rdirHead;
        _rdirHead = n->_next;
        n->_next = nullptr;
        delete n;
    }
}

} // namespace search
