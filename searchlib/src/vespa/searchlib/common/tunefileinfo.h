// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
    TuneFileSeqRead() noexcept : _tuneControl(NORMAL) { }
    void setWantDirectIO() noexcept { _tuneControl = DIRECTIO; }
    bool getWantDirectIO() const noexcept { return _tuneControl == DIRECTIO; }

    template <typename Config>
    void setFromConfig(const enum Config::Io &config) noexcept {
        switch (config) {
        case Config::Io::NORMAL:
            _tuneControl = NORMAL;
            break;
        case Config::Io::DIRECTIO:
            _tuneControl = DIRECTIO;
            break;
        default:
            _tuneControl = NORMAL;
            break;
        }
    }

    bool operator==(const TuneFileSeqRead &rhs) const noexcept {
        return _tuneControl == rhs._tuneControl;
    }

    bool operator!=(const TuneFileSeqRead &rhs) const noexcept {
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
    TuneFileSeqWrite() noexcept : _tuneControl(NORMAL) { }
    void setWantDirectIO() noexcept { _tuneControl = DIRECTIO; }
    bool getWantDirectIO() const noexcept { return _tuneControl == DIRECTIO; }
    bool getWantSyncWrites() const noexcept { return _tuneControl == OSYNC; }

    template <typename Config>
    void setFromConfig(const enum Config::Io &config) noexcept {
        switch (config) {
        case Config::Io::NORMAL:
            _tuneControl = NORMAL;
            break;
        case Config::Io::OSYNC:
            _tuneControl = OSYNC;
            break;
        case Config::Io::DIRECTIO:
            _tuneControl = DIRECTIO;
            break;
        default:
            _tuneControl = NORMAL;
            break;
        }
    }

    bool operator==(const TuneFileSeqWrite &rhs) const noexcept { return _tuneControl == rhs._tuneControl; }
    bool operator!=(const TuneFileSeqWrite &rhs) const noexcept { return _tuneControl != rhs._tuneControl; }
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
    TuneFileRandRead() noexcept
        : _tuneControl(NORMAL),
          _mmapFlags(0),
          _advise(0)
    { }

    void setAdvise(int advise) noexcept { _advise = advise; }
    void setWantMemoryMap() noexcept { _tuneControl = MMAP; }
    void setWantDirectIO()  noexcept { _tuneControl = DIRECTIO; }
    void setWantNormal()    noexcept { _tuneControl = NORMAL; }
    bool getWantDirectIO()   const noexcept { return _tuneControl == DIRECTIO; }
    bool getWantMemoryMap()  const noexcept { return _tuneControl == MMAP; }
    int  getMemoryMapFlags() const noexcept { return _mmapFlags; }
    int  getAdvise()         const noexcept { return _advise; }

    template <typename TuneControlConfig, typename MMapConfig>
    void setFromConfig(const enum TuneControlConfig::Io & tuneControlConfig, const MMapConfig & mmapFlags) noexcept;
    template <typename MMapConfig>
    void setFromMmapConfig(const MMapConfig & mmapFlags) noexcept;

    bool operator==(const TuneFileRandRead &rhs) const noexcept {
        return (_tuneControl == rhs._tuneControl) && (_mmapFlags == rhs._mmapFlags);
    }

    bool operator!=(const TuneFileRandRead &rhs) const noexcept {
        return (_tuneControl != rhs._tuneControl) && (_mmapFlags == rhs._mmapFlags);
    }
    TuneFileRandRead consider_force_memory_map(bool force_memory_map) const noexcept;
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

    TuneFileIndexing() noexcept : _read(), _write() {}

    TuneFileIndexing(const TuneFileSeqRead &r, const TuneFileSeqWrite &w) noexcept : _read(r), _write(w) { }

    bool operator==(const TuneFileIndexing &rhs) const noexcept {
        return _read == rhs._read && _write == rhs._write;
    }

    bool operator!=(const TuneFileIndexing &rhs) const noexcept {
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
    bool _force_memory_map_posting_list;

    TuneFileSearch() noexcept : _read(), _force_memory_map_posting_list(false) { }
    TuneFileSearch(const TuneFileRandRead &r) noexcept : _read(r), _force_memory_map_posting_list(false) { }
    void set_force_memory_map_posting_list(bool value) noexcept { _force_memory_map_posting_list = value; }
    TuneFileRandRead get_tune_file_search_posting_list() const noexcept {
        return _read.consider_force_memory_map(_force_memory_map_posting_list);
    }
    bool operator==(const TuneFileSearch &rhs) const noexcept {
        return _read == rhs._read &&
               _force_memory_map_posting_list == rhs._force_memory_map_posting_list;
    }
    bool operator!=(const TuneFileSearch &rhs) const noexcept { return !operator==(rhs); }
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

    TuneFileIndexManager() noexcept : _indexing(), _search() { }

    bool operator==(const TuneFileIndexManager &rhs) const noexcept {
        return _indexing == rhs._indexing && _search == rhs._search;
    }

    bool operator!=(const TuneFileIndexManager &rhs) const noexcept {
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

    TuneFileAttributes() noexcept : _write() { }

    bool operator==(const TuneFileAttributes &rhs) const noexcept {
        return _write == rhs._write;
    }

    bool operator!=(const TuneFileAttributes &rhs) const noexcept {
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

    TuneFileSummary() noexcept : _seqRead(), _write(), _randRead() { }

    bool operator==(const TuneFileSummary &rhs) const noexcept {
        return _seqRead == rhs._seqRead &&
                 _write == rhs._write &&
              _randRead == rhs._randRead;
    }

    bool operator!=(const TuneFileSummary &rhs) const noexcept {
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
    using SP = std::shared_ptr<TuneFileDocumentDB>;

    TuneFileIndexManager _index;
    TuneFileAttributes _attr;
    TuneFileSummary _summary;

    TuneFileDocumentDB() noexcept : _index(), _attr(), _summary() { }

    bool operator==(const TuneFileDocumentDB &rhs) const noexcept {
        return _index == rhs._index &&
                _attr == rhs._attr &&
                _summary == rhs._summary;
    }

    bool operator!=(const TuneFileDocumentDB &rhs) const noexcept {
        return _index != rhs._index ||
                _attr != rhs._attr ||
                _summary != rhs._summary;
    }
};

}
