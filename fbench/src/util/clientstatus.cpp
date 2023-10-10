// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "clientstatus.h"
#include <cstring>
#include <cmath>


ClientStatus::ClientStatus()
    : _error(false),
      _errorMsg(),
      _skipCnt(0),
      _failCnt(0),
      _overtimeCnt(0),
      _totalTime(0),
      _realTime(0),
      _requestCnt(0),
      _timetableResolution(10),
      _timetable(10240 * _timetableResolution, 0),
      _higherCnt(0),
      _minTime(0),
      _maxTime(0),
      _reuseCnt(0),
      _zeroHitQueries(0),
      _requestStatusDistribution()
{
}

ClientStatus::~ClientStatus() = default;

void
ClientStatus::SetError(const char *errorMsg)
{
    _error = true;
    _errorMsg = errorMsg;
}

void
ClientStatus::ResponseTime(double ms)
{
    if (ms < 0) return; // should never happen.
    if (ms > _maxTime)
        _maxTime = ms;
    if (ms < _minTime || _requestCnt == 0)
        _minTime = ms;
    _totalTime += ms;

    size_t t = (size_t)(ms * _timetableResolution + 0.5);
    if (t >= _timetable.size())
        _higherCnt++;
    else
        _timetable[t]++;
    _requestCnt++;
}

void
ClientStatus::AddRequestStatus(uint32_t status)
{
    auto it = _requestStatusDistribution.find(status);

    if (it != _requestStatusDistribution.end())
        it->second++;
    else
        _requestStatusDistribution[status] = 1;
}

void
ClientStatus::Merge(const ClientStatus & status)
{
    if (_timetable.size() != status._timetable.size()) {
        printf("ClientStatus::Merge() : incompatible data structures!\n");
        return;
    }

    if (_maxTime < status._maxTime)
        _maxTime = status._maxTime;
    if ((_requestCnt == 0) ||
        (_minTime > status._minTime && status._requestCnt > 0))
        _minTime = status._minTime;
    _skipCnt += status._skipCnt;
    _failCnt += status._failCnt;
    _overtimeCnt += status._overtimeCnt;
    _totalTime += status._totalTime;
    _realTime += status._realTime;
    _requestCnt += status._requestCnt;
    for (size_t i = 0; i < _timetable.size(); i++)
        _timetable[i] += status._timetable[i];
    _higherCnt += status._higherCnt;
    _reuseCnt += status._reuseCnt;
    _zeroHitQueries += status._zeroHitQueries;

    for (const auto& entry : status._requestStatusDistribution) {
        auto it = _requestStatusDistribution.find(entry.first);
        if (it != _requestStatusDistribution.end())
            it->second += entry.second;
        else
            _requestStatusDistribution[entry.first] = entry.second;
    }
}

double
ClientStatus::GetMin()
{
    return _minTime;
}

double
ClientStatus::GetMax()
{
    return _maxTime;
}

double
ClientStatus::GetAverage()
{
    return (_requestCnt == 0) ?
        0 : _totalTime / ((double)_requestCnt);
}

double
ClientStatus::GetPercentile(double percent)
{
    if (percent < 0.0) percent = 0.0;
    if (percent > 100.0) percent = 100.0;

    double target = ((double)(_requestCnt - 1)) * (percent / 100.0);
    long       t1 = (long)std::floor(target);
    long       t2 = (long)std::ceil(target);
    double      k = std::ceil(target) - target;
    int        i1 = 0;
    int        i2 = 0;
    long      cnt = 0;
    double   val1 = 0;
    double   val2 = 0;

    cnt = _timetable[0];
    while (cnt <= t1) {
        if (i1 + 1 < int(_timetable.size())) {
            cnt += _timetable[++i1];
        } else {
            i1 = -1;
            break;
        }
    }
    i2 = i1;
    if (i1 >= 0) {
        val1 = i1;
        while (cnt <= t2) {
            if (i2 + 1 < int(_timetable.size())) {
                cnt += _timetable[++i2];
            } else {
                i2 = -1;
                break;
            }
        }
    } else {
        if (_higherCnt < 2) {
            val1 = _maxTime * _timetableResolution;
        } else {
            // use uniform distribution for approximation
            val1 = (((double)(t1 - (_requestCnt - _higherCnt))) / ((double)(_higherCnt - 1)))
                   * (_maxTime * _timetableResolution - ((double)_timetable.size())) + ((double)_timetable.size());
        }
    }
    if (i2 >= 0) {
        val2 = i2;
    } else {
        if (_higherCnt < 2) {
            val2 = _maxTime * _timetableResolution;
        } else {
            // use uniform distribution for approximation
            val2 = (((double)(t2 - (_requestCnt - _higherCnt))) / ((double)(_higherCnt - 1)))
                   * (_maxTime * _timetableResolution - ((double)_timetable.size())) + ((double)_timetable.size());
        }
    }
    return (k * val1 + (1 - k) * val2) / _timetableResolution;
}
