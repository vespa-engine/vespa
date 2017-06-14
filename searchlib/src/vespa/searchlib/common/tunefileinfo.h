// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <sys/mman.h>
#include <linux/mman.h>
#include <fcntl.h>
#include <memory>

namespace search {

class TuneFileSeqRead
{
public:
    enum TuneControl {
        NORMAL,
        DIRECTIO
    };

private:

    TuneControl _tuneControl;

public:
    TuneFileSeqRead() : _tuneControl(NORMAL) { }
    void setWantNormal() { _tuneControl = NORMAL; }
    void setWantDirectIO() { _tuneControl = DIRECTIO; }
    bool getWantDirectIO() const { return _tuneControl == DIRECTIO; }

    template <typename Config>
    void setFromConfig(const enum Config::Io &config) {
        switch (config) {
        case Config::NORMAL:
            _tuneControl = NORMAL;
            break;
        case Config::DIRECTIO:
            _tuneControl = DIRECTIO;
            break;
        default:
            _tuneControl = NORMAL;
            break;
        }
    }

    bool operator==(const TuneFileSeqRead &rhs) const {
        return _tuneControl == rhs._tuneControl;
    }

    bool operator!=(const TuneFileSeqRead &rhs) const {
        return _tuneControl != rhs._tuneControl;
    }
};


class TuneFileSeqWrite
{
public:
    enum TuneControl {
        NORMAL,
        OSYNC,
        DIRECTIO
    };

private:

    TuneControl _tuneControl;

public:
    TuneFileSeqWrite() : _tuneControl(NORMAL) { }
    void setWantNormal() { _tuneControl = NORMAL; }
    void setWantSyncWrites() { _tuneControl = OSYNC; }
    void setWantDirectIO() { _tuneControl = DIRECTIO; }
    bool getWantDirectIO() const { return _tuneControl == DIRECTIO; }
    bool getWantSyncWrites() const { return _tuneControl == OSYNC; }

    template <typename Config>
    void setFromConfig(const enum Config::Io &config) {
        switch (config) {
        case Config::NORMAL:
            _tuneControl = NORMAL;
            break;
        case Config::OSYNC:
            _tuneControl = OSYNC;
            break;
        case Config::DIRECTIO:
            _tuneControl = DIRECTIO;
            break;
        default:
            _tuneControl = NORMAL;
            break;
        }
    }

    bool operator==(const TuneFileSeqWrite &rhs) const { return _tuneControl == rhs._tuneControl; }
    bool operator!=(const TuneFileSeqWrite &rhs) const { return _tuneControl != rhs._tuneControl; }
};


class TuneFileRandRead
{
public:
    enum TuneControl { NORMAL, DIRECTIO, MMAP };
private:
    TuneControl _tuneControl;
    int         _mmapFlags;
    int         _advise;
public:
    TuneFileRandRead()
        : _tuneControl(NORMAL),
          _mmapFlags(0),
          _advise(0)
    { }

    void setMemoryMapFlags(int flags) { _mmapFlags = flags; }
    void setAdvise(int advise)        { _advise = advise; }
    void setWantMemoryMap() { _tuneControl = MMAP; }
    void setWantDirectIO()  { _tuneControl = DIRECTIO; }
    void setWantNormal()    { _tuneControl = NORMAL; }
    bool getWantDirectIO()   const { return _tuneControl == DIRECTIO; }
    bool getWantMemoryMap()  const { return _tuneControl == MMAP; }
    int  getMemoryMapFlags() const { return _mmapFlags; }
    int  getAdvise()         const { return _advise; }

    template <typename TuneControlConfig, typename MMapConfig>
    void
    setFromConfig(const enum TuneControlConfig::Io & tuneControlConfig, const MMapConfig & mmapFlags) {
        switch ( tuneControlConfig) {
        case TuneControlConfig::NORMAL:   _tuneControl = NORMAL; break;
        case TuneControlConfig::DIRECTIO: _tuneControl = DIRECTIO; break;
        case TuneControlConfig::MMAP:     _tuneControl = MMAP; break;
        default:                          _tuneControl = NORMAL; break;
        }
        for (size_t i(0), m(mmapFlags.options.size()); i < m; i++) {
            switch (mmapFlags.options[i]) {
            case MMapConfig::MLOCK:    _mmapFlags |= MAP_LOCKED; break;
            case MMapConfig::POPULATE: _mmapFlags |= MAP_POPULATE; break;
            case MMapConfig::HUGETLB:  _mmapFlags |= MAP_HUGETLB; break;
            }
        }
        switch (mmapFlags.advise) {
        case MMapConfig::NORMAL:     setAdvise(POSIX_FADV_NORMAL); break;
        case MMapConfig::RANDOM:     setAdvise(POSIX_FADV_RANDOM); break;
        case MMapConfig::SEQUENTIAL: setAdvise(POSIX_FADV_SEQUENTIAL); break;
        }
    }

    bool operator==(const TuneFileRandRead &rhs) const {
        return (_tuneControl == rhs._tuneControl) && (_mmapFlags == rhs._mmapFlags);
    }

    bool operator!=(const TuneFileRandRead &rhs) const {
        return (_tuneControl != rhs._tuneControl) && (_mmapFlags == rhs._mmapFlags);
    }
};


/**
 * Controls file access for indexed fields, word list and dictionary
 * during memory dump and fusion.
 */
class TuneFileIndexing
{
public:
    TuneFileSeqRead _read;
    TuneFileSeqWrite _write;

    TuneFileIndexing() : _read(), _write() {}

    TuneFileIndexing(const TuneFileSeqRead &r, const TuneFileSeqWrite &w) : _read(r), _write(w) { }

    bool operator==(const TuneFileIndexing &rhs) const {
        return _read == rhs._read && _write == rhs._write;
    }

    bool operator!=(const TuneFileIndexing &rhs) const {
        return _read != rhs._read || _write != rhs._write;
    }
};

/**
 * Controls file access for indexed fields and dictionary during
 * search.
 */
class TuneFileSearch
{
public:
    TuneFileRandRead _read;

    TuneFileSearch() : _read() { }
    TuneFileSearch(const TuneFileRandRead &r) : _read(r) { }
    bool operator==(const TuneFileSearch &rhs) const { return _read == rhs._read; }
    bool operator!=(const TuneFileSearch &rhs) const { return _read != rhs._read; }
};


/**
 * Controls file access for indexed fields and dictionary during
 * memory dump, fusion and search.
 */
class TuneFileIndexManager
{
public:
    TuneFileIndexing _indexing;
    TuneFileSearch   _search;

    TuneFileIndexManager() : _indexing(), _search() { }

    bool operator==(const TuneFileIndexManager &rhs) const {
        return _indexing == rhs._indexing && _search == rhs._search;
    }

    bool operator!=(const TuneFileIndexManager &rhs) const {
        return _indexing != rhs._indexing || _search != rhs._search;
    }
};


/**
 * Controls file access for writing attributes to disk.
 */
class TuneFileAttributes
{
public:
    TuneFileSeqWrite _write;

    TuneFileAttributes() : _write() { }

    bool operator==(const TuneFileAttributes &rhs) const {
        return _write == rhs._write;
    }

    bool operator!=(const TuneFileAttributes &rhs) const {
        return _write != rhs._write;
    }
};


/**
 * Controls file access for summaries (docstore).
 */
class TuneFileSummary
{
public:
    TuneFileSeqRead  _seqRead;
    TuneFileSeqWrite _write;
    TuneFileRandRead _randRead;

    TuneFileSummary() : _seqRead(), _write(), _randRead() { }

    bool operator==(const TuneFileSummary &rhs) const {
        return _seqRead == rhs._seqRead &&
                 _write == rhs._write &&
              _randRead == rhs._randRead;
    }

    bool operator!=(const TuneFileSummary &rhs) const {
        return _seqRead != rhs._seqRead ||
                 _write != rhs._write ||
              _randRead != rhs._randRead;
    }
};


/**
 * Controls file access for document db, i.e. "everything".
 */
class TuneFileDocumentDB
{
public:
    typedef std::shared_ptr<TuneFileDocumentDB> SP;

    TuneFileIndexManager _index;
    TuneFileAttributes _attr;
    TuneFileSummary _summary;

    TuneFileDocumentDB() : _index(), _attr(), _summary() { }

    bool operator==(const TuneFileDocumentDB &rhs) const {
        return _index == rhs._index &&
                _attr == rhs._attr &&
                _summary == rhs._summary;
    }

    bool operator!=(const TuneFileDocumentDB &rhs) const {
        return _index != rhs._index ||
                _attr != rhs._attr ||
                _summary != rhs._summary;
    }
};

}
