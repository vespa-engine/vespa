// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <map>
#include <vector>
#include <string>

/**
 * This is a helper struct that is used by the @ref Client class to
 * aggregate runtime statistics. It is also used to record warnings
 * and errors.
 **/
struct ClientStatus
{
    /**
     * Indicates wether a fatal error has occurred.
     **/
    bool   _error;

    /**
     * Message explaining the error indicated by _error.
     **/
    std::string  _errorMsg;

    /**
     * The number of requests that has been skipped.
     **/
    long   _skipCnt;

    /**
     * The number of requests that have failed.
     **/
    long   _failCnt;

    /**
     * The number of requests that had response time greater than the
     * cycle time.
     **/
    long   _overtimeCnt;

    /**
     * Total response time for all requests.
     **/
    double _totalTime;

    /**
     * Real time passed. This is used to calculate the actual query
     * rate.
     **/
    double _realTime;

    /**
     * Total number of (successful) requests. Overtime requests are
     * counted with, but not failed or skipped ones.
     **/
    long   _requestCnt;

    /**
     * Resolution of timetable. A resolution of 1 means each entry in
     * the timetable is 1 millisecond. A resolution of 10 means each
     * entry is 1/10th of a millisecond.
     **/
    const int    _timetableResolution;

    /**
     * Table where _timetable[i] is the number of requests with response
     * time in milliseconds (i is multiplied with the resolution).
     **/
    std::vector<int> _timetable;

    /**
     * Number of requests with response time greater than or equal
     * _timetableSize divided by _timetableResolution milliseconds.
     **/
    long   _higherCnt;

    /**
     * The minimum response time measured.
     **/
    double _minTime;

    /**
     * The maximum response time measured.
     **/
    double _maxTime;

    /**
     * Connection reuse count. Tells us how many requests were made
     * without having to open a new connection. If keep-alive is not
     * enabled, this will always be 0.
     **/
    uint64_t _reuseCnt;

    /**
     * The number of zero hit queries
     **/
    long   _zeroHitQueries;

    /**
     * The request status distribution. Key=Status, Value=Count.
     **/
    std::map<uint32_t, uint32_t> _requestStatusDistribution;

    ClientStatus();
    ~ClientStatus();

    /**
     * Notify that an error occurred and set an error message describing
     * the error. The client should only call this method once right
     * before exiting due to a fatal error.
     *
     * @param errorMsg A string explaining the error.
     **/
    void SetError(const char* errorMsg);

    /**
     * Notify that a request was skipped. Long requests (measured in
     * bytes) will be skipped due to intenal buffer limitations. This
     * should happen very rarely.
     **/
    void SkippedRequest() { _skipCnt++; }

    /**
     * Notify that a request failed. This should be called when the
     * client could not establish a connection to the server or a read
     * error occurred while fetching the response.
     **/
    void RequestFailed() { _failCnt++; }

    /**
     * Notify that the cycle time could not be held. This typically
     * indicates that either the server response time is longer than the
     * cycle time or that your thread/socket libraries are unable to
     * handle the number of clients currently running.
     **/
    void OverTime() { _overtimeCnt++; }

    /**
     * This method is used to register response times measured by the
     * client. Response times should only be registered for successful
     * requests.
     *
     * @param ms Response time measured in milliseconds.
     **/
    void ResponseTime(double ms);

    /**
     * Set real time passed while benchmarking.
     *
     * @param ms time passed while benchmarking (in milliseconds)
     **/
    void SetRealTime(double ms) { _realTime = ms; }

    /**
     * Set connection reuse count.
     *
     * @param cnt connection reuse count
     **/
    void SetReuseCount(uint64_t cnt) { _reuseCnt = cnt; }

    /**
     * Add request status to request status distribution.
     *
     * @param status The status to insert
     **/
    void AddRequestStatus(uint32_t status);

    /**
     * Merge the info held by 'status' into the info held by this
     * struct. Note that the error flag and error messages are ignored. If
     * you do not want to use data held by a status struct with an error
     * you should check the error flag before merging.
     *
     * @param status The ClientStatus that should be merged into this one.
     **/
    void Merge(const ClientStatus & status);

    /**
     * @return the minimum response time.
     **/
    double GetMin();

    /**
     * @return the maximum response time.
     **/
    double GetMax();

    /**
     * @return the average response time.
     **/
    double GetAverage();

    /**
     * @return The 50 percent percentile (aka median).
     **/
    double GetMedian() { return GetPercentile(50); }

    /**
     * This method calculates a response time that separates the 'percent'
     * percent fastest requests from the (100 - 'percent') percent slowest
     * requests. A single request may be classified by comparing the
     * request response time with the percentile returned by this
     * method. If the requested percentile lies outside the time table
     * measuring interval, -1 is returned. This indicates that the
     * requested percentile was greater than _timetableSize (divided by
     * resolution) milliseconds.
     *
     * @return the calculated percentile or -1 if it was outside the time table.
     * @param percent percent of requests that should have response time lower
     *        than the percentile to be calculated by this method. Legal values
     *        of this parameter is in the range [0,100].
     **/
    double GetPercentile(double percent);

private:
    ClientStatus(const ClientStatus &);
    ClientStatus &operator=(const ClientStatus &);
};
