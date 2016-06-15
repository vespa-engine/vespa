// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".mergingmanager");
#include "mergingmanager.h"
#include <map>
#include <vespa/searchlib/aggregation/grouping.h>
#include <vespa/searchlib/aggregation/fs4hit.h>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <vespa/vespalib/objects/objectoperation.h>

namespace search {
namespace grouping {

namespace {

class PathMangler : public vespalib::ObjectPredicate,
                    public vespalib::ObjectOperation
{
private:
    uint32_t _partBits;
    uint32_t _rowBits;
    uint32_t _partId;
    uint32_t _rowId;
    bool     _mld;

public:
    typedef search::aggregation::FS4Hit FS4Hit;
    PathMangler(uint32_t partBits, uint32_t rowBits, uint32_t partId, uint32_t rowId, bool mld)
        : _partBits(partBits), _rowBits(rowBits), _partId(partId), _rowId(rowId), _mld(mld) {}
    virtual bool check(const vespalib::Identifiable &obj) const {
        return (obj.getClass().id() == FS4Hit::classId);
    }
    virtual void execute(vespalib::Identifiable &obj) {
        FS4Hit &hit = static_cast<search::aggregation::FS4Hit&>(obj);
        hit.setPath(computeNewPath(hit.getPath()));
    }
    uint32_t computeNewPath(uint32_t path) const {
        if (_mld) {
            path = (path + 1) << _partBits;
        }
        path += _partId;
        if (_rowBits > 0) {
            path = (path << _rowBits) + _rowId;
        }
        return path;
    }
};

} // namespace search::grouping::<unnamed>

using search::aggregation::Grouping;

//-----------------------------------------------------------------------------

MergingManager::MergingManager(uint32_t partBits, uint32_t rowBits)
    : _partBits(partBits),
      _rowBits(rowBits),
      _input(),
      _result(0),
      _resultLen(0)
{
}

MergingManager::~MergingManager()
{
    free(_result);
}

void
MergingManager::addResult(uint32_t partId, uint32_t rowId, bool mld,
                          const char *groupResult, size_t groupResultLen)
{
    _input.push_back(Entry(partId, rowId, mld, groupResult, groupResultLen));
}

bool MergingManager::needMerge() const
{
    if (_input.size() == 1) {
        PathMangler pathMangler(_partBits, _rowBits,
                                _input[0].partId, _input[0].rowId,
                                _input[0].mld);
        if (pathMangler.computeNewPath(0) == 0) {
            return false;
        }
    }
    return true;
}

void
MergingManager::merge()
{
    if (needMerge()) {
        fullMerge();
    } else {
        _resultLen = _input[0].length;
        _result = (char *) malloc(_resultLen);
        memcpy(_result, _input[0].data, _resultLen);
    }
}

void
MergingManager::fullMerge()
{
    typedef std::unique_ptr<Grouping>    UP;
    typedef std::map<uint32_t, UP> MAP;
    typedef MAP::iterator          ITR;

    MAP map;
    for (size_t i = 0; i < _input.size(); ++i) {
        PathMangler pathMangler(_partBits, _rowBits,
                                _input[i].partId, _input[i].rowId,
                                _input[i].mld);
        if ((_input[i].data != NULL) && (_input[i].length > 0)) {
            vespalib::nbostream is(_input[i].data, _input[i].length);
            vespalib::NBOSerializer nis(is);
            uint32_t cnt = 0;
            nis >> cnt;
            for (uint32_t j = 0; j < cnt; ++j) {
                UP g(new Grouping());
                g->deserialize(nis);
                g->select(pathMangler, pathMangler);
                ITR pos = map.find(g->getId());
                if (pos == map.end()) {
                    map[g->getId()] = std::move(g);
                } else {
                    pos->second->merge(*g);
                }
            }
        }
    }
    vespalib::nbostream os;
    vespalib::NBOSerializer nos(os);
    nos << (uint32_t)map.size();
    ITR end = map.end();
    for (ITR itr = map.begin(); itr != end; ++itr) {
        itr->second->postMerge();
        itr->second->sortById();
        itr->second->serialize(nos);
    }
    _resultLen = os.size();
    _result = (char *) malloc(os.size());
    memcpy(_result, os.c_str(), os.size());
}

size_t
MergingManager::getGroupResultLen() const
{
    return _resultLen;
}

const char *
MergingManager::getGroupResult() const
{
    return _result;
}

char *
MergingManager::stealGroupResult()
{
    char *tmp = _result;
    _result = 0;
    return tmp;
}

//-----------------------------------------------------------------------------

} // namespace search::grouping
} // namespace search
