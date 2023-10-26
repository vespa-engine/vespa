// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "request.h"

namespace vbench {

Request::Request()
    : _url(),
      _server(),
      _scheduledTime(),
      _status(STATUS_OK),
      _startTime(),
      _endTime(),
      _size(0)
{
}

void
Request::handleHeader(const string &name, const string &value)
{
    _headers.handleHeader(name, value);
}

void
Request::handleContent(const Memory &data)
{
    _size += data.size;
}

void
Request::handleFailure(const string &)
{
    _status = STATUS_FAILED;
}

string
Request::toString() const
{
    string str;
    str += "Request {\n";
    str += strfmt("  url: %s\n", _url.c_str());
    str += strfmt("  server.host: %s\n", _server.host.c_str());
    str += strfmt("  server.port: %d\n", _server.port);
    str += strfmt("  scheduledTime: %g\n", _scheduledTime);
    str += strfmt("  status: %s\n",
                  ((_status == STATUS_OK) ? "OK"
                   : (_status == STATUS_DROPPED) ? "DROPPED"
                   : (_status == STATUS_FAILED) ? "FAILED"
                   : "UNKNOWN"));
    str += strfmt("  startTime: %g\n", _startTime);
    str += strfmt("  endTime: %g\n", _endTime);
    str += strfmt("  latency: %g\n", latency());
    str += strfmt("  size: %zu\n", _size);
    str += _headers.toString();
    str += "}\n";
    return str;
}

} // namespace vbench
