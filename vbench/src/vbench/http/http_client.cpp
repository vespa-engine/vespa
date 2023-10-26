// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "http_client.h"
#include "hex_number.h"
#include <vbench/core/line_reader.h>
#include <vespa/vespalib/data/output_writer.h>

namespace vbench {

using OutputWriter = vespalib::OutputWriter;

HttpClient::~HttpClient() {}

void
HttpClient::writeRequest() {
    OutputWriter dst(_conn->stream(), WRITE_SIZE);
    dst.printf("GET %s HTTP/1.1\r\n", _url.c_str());
    dst.printf("Host: %s\r\n", _conn->server().host.c_str());
    dst.write("User-Agent: vbench\r\n");
    dst.write("X-Yahoo-Vespa-Benchmarkdata: true\r\n");
    dst.write("X-Yahoo-Vespa-Benchmarkdata-Coverage: true\r\n");
    dst.write("\r\n");
}

bool
HttpClient::readStatus()
{
    LineReader reader(_conn->stream());
    if (reader.readLine(_line) && (splitstr(_line, "\t ", _split) >= 2)) {
        if (_split[0] == "HTTP/1.0") {
            _header.version = 0;
        } else if (_split[0] == "HTTP/1.1") {
            _header.version = 1;
        } else {
            _handler.handleFailure(strfmt("unknown HTTP version: '%s'", _split[0].c_str()));
            return false;
        }
        _header.status = atoi(_split[1].c_str());
        if (_header.status != 200) {
            _handler.handleFailure(strfmt("HTTP status not 200: '%s'", _split[1].c_str()));
            return false;
        }
        return true;
    }
    if (_conn->stream().tainted()) {
        _handler.handleFailure(strfmt("Connection error: %s",
                                      _conn->stream().tainted().reason().c_str()));
    } else {
        _handler.handleFailure(strfmt("could not parse HTTP status line: '%s'", _line.c_str()));
    }
    return false;
}

bool
HttpClient::readHeaders()
{
    LineReader reader(_conn->stream());
    while (reader.readLine(_line)) {
        if (_line.empty()) {
            return true;
        }
        if ((_line[0] == ' ') || (_line[0] == '\t')) {
            // ignore continuation headers
        } else if (_line.find("X-Yahoo-Vespa-") == 0) {
            if (splitstr(_line, ":\t ", _split) == 2) {
                _handler.handleHeader(_split[0], _split[1]);
            }
        } else {
            if (splitstr(_line, ":\t ", _split) > 1) {
                if (strcasecmp(_split[0].c_str(), "connection") == 0) {
                    for (size_t i = 1; i < _split.size(); ++i) {
                        if (strcasecmp(_split[i].c_str(), "keep-alive") == 0) {
                            _handler.handleHeader(_split[0], _split[i]);
                            _header.keepAliveGiven = true;
                        } else if (strcasecmp(_split[i].c_str(), "close") == 0) {
                            _handler.handleHeader(_split[0], _split[i]);
                            _header.connectionCloseGiven = true;
                        }
                    }
                } else if (strcasecmp(_split[0].c_str(), "content-length") == 0 &&
                           _split.size() == 2)
                {
                    _handler.handleHeader(_split[0], _split[1]);
                    _header.contentLengthGiven = true;
                    _header.contentLength = atoi(_split[1].c_str());
                } else if (strcasecmp(_split[0].c_str(), "transfer-encoding") == 0 &&
                           strcasecmp(_split[1].c_str(), "chunked") == 0)
                {
                    _handler.handleHeader(_split[0], _split[1]);
                    _header.chunkedEncodingGiven = true;
                }
            }
        }
    }
    _handler.handleFailure("HTTP header did not end in empty line");
    return false;
}

bool
HttpClient::readContent(size_t len) {
    Input &input = _conn->stream();
    while (len > 0) {
        Memory mem = input.obtain();
        mem.size = std::min(len, mem.size);
        if (mem.size == 0) {
            _handler.handleFailure(strfmt("short read: missing %zu bytes", len));
            return false;
        }
        _handler.handleContent(mem);
        input.evict(mem.size);
        len -= mem.size;
    }
    return true;
}

bool
HttpClient::readChunkSize(bool first, size_t &size)
{
    LineReader reader(_conn->stream());
    if (!first && (!reader.readLine(_line) || !_line.empty())) {
        return false;
    }
    if (!reader.readLine(_line)) {
        return false;
    }
    HexNumber hex(_line.c_str());
    size = hex.value();
    return (hex.length() > 0);
}

bool
HttpClient::skipTrailers()
{
    LineReader reader(_conn->stream());
    while (reader.readLine(_line)) {
        if (_line.empty()) {
            return true;
        }
    }
    return false;
}

bool
HttpClient::readContent()
{
    if (_header.contentLengthGiven) {
        return readContent(_header.contentLength);
    } else if (_header.chunkedEncodingGiven) {
        size_t chunkSize = 0;
        for (bool first = true; readChunkSize(first, chunkSize); first = false) {
            if (chunkSize == 0) {
                return skipTrailers();
            }
            if (!readContent(chunkSize)) {
                return false;
            }
        }
        _handler.handleFailure("error reading HTTP chunk size");
        return false;
    } else { // data terminated by eof
        if (serverKeepAlive()) {
            _handler.handleFailure("server indicated keep-alive, "
                                   "but we need eof to terminate data");
            return false;
        }
        Input &input = _conn->stream();
        for (;;) {
            Memory mem = input.obtain();
            if (mem.size == 0) {
                if (_conn->stream().tainted()) {
                    _handler.handleFailure(strfmt("read error: '%s'",
                                                  _conn->stream().tainted().reason().c_str()));
                }
                return true;
            }
            _handler.handleContent(mem);
            input.evict(mem.size);
        }
    }
}

bool
HttpClient::perform(CryptoEngine &crypto)
{
    writeRequest();
    if (!_conn->fresh() && (_conn->stream().obtain().size == 0)) {
        _conn.reset(new HttpConnection(crypto, _conn->server()));
        writeRequest();
    }
    return (readStatus() && readHeaders() && readContent());
}

} // namespace vbench
