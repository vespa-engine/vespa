// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include "http_connection.h"
#include "http_connection_pool.h"
#include "http_result_handler.h"
#include <vbench/core/socket.h>

namespace vbench {

/**
 * This class handles sequential HTTP requests against a single
 * server.
 **/
class HttpClient
{
public:
    using CryptoEngine = vespalib::CryptoEngine;
private:
    static const size_t WRITE_SIZE = 2000;

    struct HeaderInfo {
        bool connectionCloseGiven;
        bool contentLengthGiven;
        bool chunkedEncodingGiven;
        bool keepAliveGiven;
        uint32_t status;
        uint32_t version;
        size_t contentLength;
        HeaderInfo() : connectionCloseGiven(false), contentLengthGiven(false),
                       chunkedEncodingGiven(false), keepAliveGiven(false),
                       status(0), version(0) {}
    };

    HttpConnection::UP _conn;
    string             _url;
    HttpResultHandler &_handler;
    HeaderInfo         _header;

    // scratch data used for parsing
    string              _line;
    std::vector<string> _split;

    HttpClient(HttpConnection::UP conn, const string &url, HttpResultHandler &handler)
        : _conn(std::move(conn)), _url(url), _handler(handler), _header() {}

    bool serverKeepAlive() const {
        return ((_header.version == 1 && !_header.connectionCloseGiven) ||
                (_header.version == 0 && _header.keepAliveGiven));
    }

    void writeRequest();
    bool readStatus();
    bool readHeaders();
    bool readContent(size_t len);
    bool readChunkSize(bool first, size_t &size);
    bool skipTrailers();
    bool readContent();
    bool perform(CryptoEngine &crypto);

public:
    ~HttpClient();
    static bool fetch(CryptoEngine &crypto, const ServerSpec &server, const string &url,
                      HttpResultHandler &handler)
    {
        HttpClient client(HttpConnection::UP(new HttpConnection(crypto, server)), url, handler);
        return client.perform(crypto);
    }
    static bool fetch(HttpConnectionPool &pool,
                      const ServerSpec &server, const string &url,
                      HttpResultHandler &handler)
    {
        HttpClient client(pool.getConnection(server), url, handler);
        if (client.perform(pool.crypto())) {
            if (client.serverKeepAlive()) {
                pool.putConnection(std::move(client._conn));
            }
            return true;
        }
        return false;
    }
};

} // namespace vbench

