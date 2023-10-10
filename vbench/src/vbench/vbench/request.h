// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vbench/core/string.h>
#include <vbench/http/benchmark_headers.h>
#include <vbench/http/server_spec.h>
#include <vbench/http/http_result_handler.h>
#include <memory>

namespace vbench {

/**
 * Encapsulates all known information about a single query. This
 * object will flow through the system.
 **/
class Request : public HttpResultHandler
{
public:
    using UP = std::unique_ptr<Request>;

    enum Status {
        STATUS_OK      = 0,
        STATUS_DROPPED = 1,
        STATUS_FAILED  = 2
    };

private:
    // parameters to request scheduler
    string     _url;
    ServerSpec _server;
    double     _scheduledTime;

    // results filled in by request scheduler
    Status     _status;
    double     _startTime;
    double     _endTime;
    size_t     _size;

    // benchmark headers from QRS
    BenchmarkHeaders _headers;

public:
    Request();

    //--- parameters

    const string &url() const { return _url; }
    Request &url(const string &value) { _url = value; return *this; }

    const ServerSpec &server() const { return _server; }
    Request &server(const ServerSpec &value) { _server = value; return *this; }

    double scheduledTime() const { return _scheduledTime; }
    Request &scheduledTime(double value) { _scheduledTime = value; return *this; }

    //--- results

    Status status() const { return _status; }
    Request &status(Status value) { _status = value; return *this; }

    double startTime() const { return _startTime; }
    Request &startTime(double value) { _startTime = value; return *this; }

    double endTime() const { return _endTime; }
    Request &endTime(double value) { _endTime = value; return *this; }

    double latency() const { return (_endTime - _startTime); }

    void handleHeader(const string &name, const string &value) override;
    void handleContent(const Memory &data) override;
    void handleFailure(const string &reason) override;

    const BenchmarkHeaders &headers() const { return _headers; }

    string toString() const;
};

} // namespace vbench
