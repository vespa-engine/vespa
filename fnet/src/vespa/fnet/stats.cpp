// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stats.h"

#include <vespa/log/log.h>
LOG_SETUP(".fnet");

FNET_StatCounters::FNET_StatCounters()
    : _eventLoopCnt(0),
      _eventCnt(0),
      _ioEventCnt(0),
      _packetReadCnt(0),
      _packetWriteCnt(0),
      _dataReadCnt(0),
      _dataWriteCnt(0)
{
}


FNET_StatCounters::~FNET_StatCounters()
{
}


void
FNET_StatCounters::Clear()
{
    _eventLoopCnt   = 0;
    _eventCnt       = 0;
    _ioEventCnt     = 0;
    _packetReadCnt  = 0;
    _packetWriteCnt = 0;
    _dataReadCnt    = 0;
    _dataWriteCnt   = 0;
}

//-----------------------------------------------

FNET_Stats::FNET_Stats()
    : _eventLoopRate(0),
      _eventRate(0),
      _ioEventRate(0),
      _packetReadRate(0),
      _packetWriteRate(0),
      _dataReadRate(0),
      _dataWriteRate(0)
{
}


FNET_Stats::~FNET_Stats()
{
}


void
FNET_Stats::Update(FNET_StatCounters *count, double secs)
{
    _eventLoopRate = (float)(FNET_STATS_OLD_FACTOR * _eventLoopRate
                             + (FNET_STATS_NEW_FACTOR
                                     * ((double)count->_eventLoopCnt / secs)));
    _eventRate = (float)(FNET_STATS_OLD_FACTOR * _eventRate
                         + (FNET_STATS_NEW_FACTOR
                            * ((double)count->_eventCnt / secs)));
    _ioEventRate = (float)(FNET_STATS_OLD_FACTOR * _ioEventRate
                           + (FNET_STATS_NEW_FACTOR
                              * ((double)count->_ioEventCnt / secs)));

    _packetReadRate = (float)(FNET_STATS_OLD_FACTOR * _packetReadRate
                              + (FNET_STATS_NEW_FACTOR
                                      * ((double)count->_packetReadCnt / secs)));
    _packetWriteRate = (float)(FNET_STATS_OLD_FACTOR * _packetWriteRate
                               + (FNET_STATS_NEW_FACTOR
                                       * ((double)count->_packetWriteCnt / secs)));

    _dataReadRate = (float)(FNET_STATS_OLD_FACTOR * _dataReadRate
                            + (FNET_STATS_NEW_FACTOR
                               * ((double)count->_dataReadCnt / (1000.0 * secs))));
    _dataWriteRate = (float)(FNET_STATS_OLD_FACTOR * _dataWriteRate
                             + (FNET_STATS_NEW_FACTOR
                                     * ((double)count->_dataWriteCnt / (1000.0 * secs))));
}


void
FNET_Stats::Log()
{
    LOG(info, "events[/s][loop/int/io][%.1f/%.1f/%.1f] "
        "packets[/s][r/w][%.1f/%.1f] "
        "data[kB/s][r/w][%.2f/%.2f]",
        _eventLoopRate,
        _eventRate,
        _ioEventRate,
        _packetReadRate,
        _packetWriteRate,
        _dataReadRate,
        _dataWriteRate);
}
