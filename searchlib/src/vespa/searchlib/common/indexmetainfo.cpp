// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexmetainfo.h"
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/guard.h>
#include <cassert>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".indexmetainfo");

namespace {

class Parser {
private:
    vespalib::string      _name;
    vespalib::FilePointer _file;
    uint32_t              _line;
    char                  _buf[2048];
    bool                  _error;
    vespalib::string      _lastKey;
    vespalib::string      _lastValue;
    uint32_t              _lastIdx;
    bool                  _matched;

public:
    Parser(const vespalib::string &name)
        : _name(name),
          _file(fopen(name.c_str(), "r")),
          _line(0),
          _buf(),
          _error(false),
          _lastKey(),
          _lastValue(),
          _lastIdx(0),
          _matched(true)
    {
        _error = !_file.valid();
    }
    bool openFailed() {
        LOG(warning, "could not open file for reading: %s", _name.c_str());
        _error = true;
        return false;
    }
    bool illegalLine() {
        LOG(warning, "%s:%d: illegal line: %s", _name.c_str(), _line, _buf);
        _error = true;
        return false;
    }
    bool illegalArrayKey() {
        LOG(warning, "%s:%d: illegal array key '%s'(value='%s')",
            _name.c_str(), _line, _lastKey.c_str(), _lastValue.c_str());
        _error = true;
        return false;
    }
    bool illegalValue() {
        LOG(warning, "%s:%d: illegal value for '%s': %s",
            _name.c_str(), _line, _lastKey.c_str(), _lastValue.c_str());
        _error = true;
        return false;
    }
    bool unknown() {
        LOG(warning, "%s:%d: unknown key '%s'(value='%s')",
            _name.c_str(), _line, _lastKey.c_str(), _lastValue.c_str());
        _error = true;
        return false;
    }
    bool status() const { return !_error; }
    bool next() {
        if (_error) {
            return false;
        }
        if (!_matched) {
            return unknown();
        }
        if (!_file.valid()) {
            return openFailed();
        }
        if (fgets(_buf, sizeof(_buf), _file) == nullptr) {
            return false; // EOF
        }
        ++_line;
        uint32_t len = strlen(_buf);
        if (len > 0 && _buf[len - 1] == '\n') {
            _buf[--len] = '\0';
        }
        char *split = strchr(_buf, '=');
        if (split == nullptr || (split - _buf) == 0) {
            return illegalLine();
        }
        _lastKey = vespalib::string(_buf, split - _buf);
        _lastValue = vespalib::string(split + 1, (_buf + len) - (split + 1));
        _matched = false;
        return true;
    }
    const vespalib::string key() const { return _lastKey; }
    const vespalib::string value() const { return _lastValue; }
    void parseBool(const vespalib::string &k, bool &v) {
        if (!_matched && !_error && _lastKey == k) {
            _matched = true;
            if (_lastValue == "true") {
                v = true;
            } else if (_lastValue == "false") {
                v = false;
            } else {
                illegalValue();
            }
        }
    }
    void parseString(const vespalib::string &k, vespalib::string &v) {
        if (!_matched && !_error && _lastKey == k) {
            _matched = true;
            v = _lastValue;
        }
    }
    void parseInt64(const vespalib::string &k, uint64_t &v) {
        if (!_matched && !_error && _lastKey == k) {
            _matched = true;
            char *end = nullptr;
            uint64_t val = strtoull(_lastValue.c_str(), &end, 10);
            if (end == nullptr || *end != '\0' ||
                val == static_cast<uint64_t>(-1)) {
                illegalValue();
                return;
            }
            v = val;
        }
    }
    bool parseArray(const vespalib::string &name, uint32_t size) {
        if (_matched || _error
            || _lastKey.length() < name.length() + 1
            || strncmp(_lastKey.c_str(), name.c_str(), name.length()) != 0
            || _lastKey[name.length()] != '.')
        {
            return false;
        }
        vespalib::string::size_type dot2 = _lastKey.find('.', name.length() + 1);
        if (dot2 == vespalib::string::npos) {
            return illegalArrayKey();
        }
        char *end = nullptr;
        const char *pt = _lastKey.c_str() + name.length() + 1;
        uint32_t val = strtoul(pt, &end, 10);
        if (end == nullptr || end == pt || *end != '.'
            || val > size || size > val + 1)
        {
            return illegalArrayKey();
        }
        _lastIdx = val;
        _lastKey = _lastKey.substr(dot2 + 1);
        return true;
    }
    uint32_t idx() const { return _lastIdx; }
};

} // namespace <unnamed>

namespace search {

vespalib::string
IndexMetaInfo::makeFileName(const vespalib::string &baseName)
{
    if (_path.length() == 0 || _path == ".") {
        return baseName;
    } else if (_path[_path.length() - 1] == '/') {
        return vespalib::make_string("%s%s", _path.c_str(), baseName.c_str());
    }
    return vespalib::make_string("%s/%s", _path.c_str(), baseName.c_str());
}


IndexMetaInfo::Snapshot &
IndexMetaInfo::getCreateSnapshot(uint32_t idx)
{
    while (idx >= _snapshots.size()) {
        _snapshots.push_back(Snapshot());
    }
    return _snapshots[idx];
}


IndexMetaInfo::SnapshotList::iterator
IndexMetaInfo::findSnapshot(uint64_t syncToken)
{
    for (SnapItr it = _snapshots.begin(); it != _snapshots.end(); ++it) {
        if (it->syncToken == syncToken) {
            return it;
        }
    }
    return _snapshots.end();
}


IndexMetaInfo::IndexMetaInfo(const vespalib::string &path)
    : _path(path),
      _snapshots()
{
}

IndexMetaInfo::~IndexMetaInfo()  = default;

IndexMetaInfo::Snapshot
IndexMetaInfo::getBestSnapshot() const
{
    int idx = _snapshots.size() - 1;
    while (idx >= 0 && !_snapshots[idx].valid) {
        --idx;
    }
    return (idx >= 0) ? _snapshots[idx] : Snapshot();
}


IndexMetaInfo::Snapshot
IndexMetaInfo::getSnapshot(uint64_t syncToken) const
{
    IndexMetaInfo *self = const_cast<IndexMetaInfo *>(this);
    SnapItr itr = self->findSnapshot(syncToken);
    if (itr == _snapshots.end()) {
        return Snapshot();
    }
    return *itr;
}


bool
IndexMetaInfo::addSnapshot(const Snapshot &snap)
{
    if (snap.dirName.empty()
        || (findSnapshot(snap.syncToken) != _snapshots.end()))
    {
        return false;
    }
    assert(snap.syncToken != uint64_t(-1));
    _snapshots.push_back(snap);
    std::sort(_snapshots.begin(), _snapshots.end());
    return true;
}


bool
IndexMetaInfo::removeSnapshot(uint64_t syncToken)
{
    SnapItr itr = findSnapshot(syncToken);
    if (itr == _snapshots.end()) {
        return false;
    }
    _snapshots.erase(itr);
    return true;
}


bool
IndexMetaInfo::validateSnapshot(uint64_t syncToken)
{
    SnapItr itr = findSnapshot(syncToken);
    if (itr == _snapshots.end()) {
        return false;
    }
    itr->valid = true;
    return true;
}


bool
IndexMetaInfo::invalidateSnapshot(uint64_t syncToken)
{
    SnapItr itr = findSnapshot(syncToken);
    if (itr == _snapshots.end()) {
        return false;
    }
    itr->valid = false;
    return true;
}


void
IndexMetaInfo::clear()
{
    _snapshots.resize(0);
}


bool
IndexMetaInfo::load(const vespalib::string &baseName)
{
    clear();
    Parser parser(makeFileName(baseName));
    while (parser.status() && parser.next()) {
        if (parser.parseArray("snapshot", _snapshots.size())) {
            Snapshot &snap = getCreateSnapshot(parser.idx());
            parser.parseBool("valid", snap.valid);
            parser.parseInt64("syncToken", snap.syncToken);
            parser.parseString("dirName", snap.dirName);
            assert(snap.syncToken != static_cast<uint64_t>(-1));
        }
    }
    std::sort(_snapshots.begin(), _snapshots.end());
    return parser.status();
}


bool
IndexMetaInfo::save(const vespalib::string &baseName)
{
    vespalib::string fileName = makeFileName(baseName);
    vespalib::string newName = fileName + ".new";
    vespalib::unlink(newName);
    vespalib::FilePointer f(fopen(newName.c_str(), "w"));
    if (!f.valid()) {
        LOG(warning, "could not open file for writing: %s", newName.c_str());
        return false;
    }
    for (uint32_t i = 0; i < _snapshots.size(); ++i) {
        Snapshot &snap = _snapshots[i];
        fprintf(f, "snapshot.%d.valid=%s\n", i, snap.valid? "true" : "false");
        fprintf(f, "snapshot.%d.syncToken=%" PRIu64 "\n", i, snap.syncToken);
        fprintf(f, "snapshot.%d.dirName=%s\n", i, snap.dirName.c_str());
    }
    if (ferror(f) != 0) {
        LOG(error, "Could not write to file %s", newName.c_str());
        return false;
    }
    if (fflush(f) != 0) {
        LOG(error, "Could not flush file %s", newName.c_str());
        return false;
    }
    if (fsync(fileno(f)) != 0) {
        LOG(error, "Could not fsync file %s", newName.c_str());
        return false;
    }
    if (fclose(f.release()) != 0) {
        LOG(error, "Could not close file %s", newName.c_str());
        return false;
    }
    if (rename(newName.c_str(), fileName.c_str()) != 0) {
        LOG(warning, "could not rename: %s->%s", newName.c_str(), fileName.c_str());
        return false;
    }
    vespalib::File::sync(vespalib::dirname(fileName));
    return true;
}

} // namespace search
