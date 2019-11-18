// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mergingmanager.h"
#include <map>
#include <vespa/searchlib/aggregation/grouping.h>
#include <vespa/searchlib/aggregation/fs4hit.h>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/nbostream.h>

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

public:
    typedef search::aggregation::FS4Hit FS4Hit;
    PathMangler(uint32_t partBits, uint32_t rowBits, uint32_t partId, uint32_t rowId)
        : _partBits(partBits), _rowBits(rowBits), _partId(partId), _rowId(rowId) {}
    bool check(const vespalib::Identifiable &obj) const override;
    void execute(vespalib::Identifiable &obj) override __attribute__((noinline));
    uint32_t computeNewPath(uint32_t path) const {
        path += _partId;
        if (_rowBits > 0) {
            path = (path << _rowBits) + _rowId;
        }
        return path;
    }
};

bool PathMangler::check(const vespalib::Identifiable &obj) const {
    return (obj.getClass().id() == FS4Hit::classId);
}

void PathMangler::execute(vespalib::Identifiable &obj) {
    FS4Hit &hit = static_cast<search::aggregation::FS4Hit&>(obj);
    hit.setPath(computeNewPath(hit.getPath()));
}

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
MergingManager::addResult(uint32_t partId, uint32_t rowId,
                          const char *groupResult, size_t groupResultLen)
{
    _input.push_back(Entry(partId, rowId, groupResult, groupResultLen));
}

bool MergingManager::needMerge() const
{
    if (_input.size() == 1) {
        PathMangler pathMangler(_partBits, _rowBits, _input[0].partId, _input[0].rowId);
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

typedef std::unique_ptr<Grouping>    UP;
typedef std::map<uint32_t, UP> MAP;
typedef MAP::iterator ITR;

namespace {

void mergeOne(MAP & map, const MergingManager::Entry & input, uint32_t partBits, uint32_t rowBits) __attribute__((noinline));

void mergeOne(MAP & map, const MergingManager::Entry & input, uint32_t partBits, uint32_t rowBits) {
    PathMangler pathMangler(partBits, rowBits, input.partId, input.rowId);
    vespalib::nbostream is(input.data, input.length);
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

void
MergingManager::fullMerge()
{
    MAP map;
    for (size_t i = 0; i < _input.size(); ++i) {
        if ((_input[i].data != NULL) && (_input[i].length > 0)) {
            mergeOne(map, _input[i], _partBits, _rowBits);
        }
    }
    vespalib::nbostream os;
    vespalib::NBOSerializer nos(os);
    nos << (uint32_t)map.size();
    for (auto & entry : map) {
        entry.second->postMerge();
        entry.second->sortById();
        entry.second->serialize(nos);
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
