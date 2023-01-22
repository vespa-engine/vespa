// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <fstream>
#include <atomic>
#include <thread>
#include <vespa/vespalib/net/crypto_engine.h>

#define FBENCH_DELIMITER "\n[--xxyyzz--FBENCH_MAGIC_DELIMITER--zzyyxx--]\n"

/**
 * This struct contains arguments used to control a single client.
 * Each client runs in a separate thread. This struct do not own the
 * strings it references.
 **/
struct ClientArguments
{
    /**
     * Sequential number identifying this client.
     **/
    int         _myNum;

    /**
     * Pattern that combined with the client number will become the name
     * of the file containing the urls this client should request.
     **/
    std::string _filenamePattern;

    /**
     * Pattern that combined with the client number will become the name
     * of the file this client should dump url content to. If this
     * pattern is set to NULL no output file is generated.
     **/
    std::string _outputPattern;

    /**
     * The server the client should fetch urls from.
     **/
    const char *_hostname;

    /**
     * The server port where the webserver is running.
     **/
    int         _port;

    /**
     * The minimum number of milliseconds between two requests from this
     * client.
     **/
    long        _cycle;

    /**
     * Number of milliseconds to wait before making the first request.
     * This will be different for different clients and helps distribute
     * the requests.
     **/
    long        _delay;

    /**
     * Number of requests that should be made before we start logging
     * response times. This is included so fbench startup slugginess
     * will not affect the benchmark results.
     **/
    int         _ignoreCount;

    /**
     * Minimum number of bytes allowed in a response for a request to be
     * successful. If a response contains fewer bytes than this number,
     * the request will be logged as a failure even if no errors
     * occurred.
     **/
    int         _byteLimit;

    /**
     * Number of times this client is allowed to re-use the urls in the
     * input query file.
     **/
    int         _restartLimit;

    /**
     * Max line size in the input query data. Longer lines than this
     * will be skipped.
     **/
    int         _maxLineSize;

    /**
     * Indicate wether keep-alive connections should be enabled for this
     * client.
     **/
    bool        _keepAlive;

    /**
     * Indicate wether POST content should be Base64 decoded before
     * sending it
     **/
    bool        _base64Decode;

    /** Whether we should use POST in requests */
    bool        _usePostMode;

    /**
     * Indicate whether to add benchmark data coverage headers
     **/
    bool        _headerBenchmarkdataCoverage;

    uint64_t    _queryfileOffset;
    uint64_t    _queryfileEndOffset;
    bool        _singleQueryFile;
    std::string _queryStringToAppend;
    std::string _extraHeaders;
    std::string _authority;

    ClientArguments(int myNum,
                    const std::string & filenamePattern,
                    const std::string & outputPattern,
                    const char *hostname, int port,
                    long cycle, long delay,
                    int ignoreCount, int byteLimit,
                    int restartLimit, int maxLineSize,
                    bool keepAlive, bool base64Decode,
                    bool headerBenchmarkdataCoverage,
                    uint64_t queryfileOffset, uint64_t queryfileEndOffset, bool singleQueryFile,
                    const std::string & queryStringToAppend, const std::string & extraHeaders,
                    const std::string &authority, bool postMode)
        : _myNum(myNum),
          _filenamePattern(filenamePattern),
          _outputPattern(outputPattern),
          _hostname(hostname),
          _port(port),
          _cycle(cycle),
          _delay(delay),
          _ignoreCount(ignoreCount),
          _byteLimit(byteLimit),
          _restartLimit(restartLimit),
          _maxLineSize(maxLineSize),
          _keepAlive(keepAlive),
          _base64Decode(base64Decode),
          _usePostMode(postMode),
          _headerBenchmarkdataCoverage(headerBenchmarkdataCoverage),
          _queryfileOffset(queryfileOffset),
          _queryfileEndOffset(queryfileEndOffset),
          _singleQueryFile(singleQueryFile),
          _queryStringToAppend(queryStringToAppend),
          _extraHeaders(extraHeaders),
          _authority(authority)
    {
    }

private:
    ClientArguments(const ClientArguments &);
    ClientArguments &operator=(const ClientArguments &);
};


class Timer;
class HTTPClient;
class FileReader;
struct ClientStatus;
/**
 * This class implements a single test client. The clients are run in
 * separate threads to simulate several simultanious users. The
 * operation of a client is controlled through an instance of the
 * @ref ClientArguments class.
 **/
class Client
{
private:
    std::unique_ptr<ClientArguments> _args;
    std::unique_ptr<ClientStatus>    _status;
    std::unique_ptr<Timer>           _reqTimer;
    std::unique_ptr<Timer>           _cycleTimer;
    std::unique_ptr<Timer>           _masterTimer;
    std::unique_ptr<HTTPClient>      _http;
    std::unique_ptr<FileReader>      _reader;
    std::unique_ptr<std::ofstream>   _output;
    int                              _linebufsize;
    std::unique_ptr<char[]>          _linebuf;
    std::atomic<bool>                _stop;
    std::atomic<bool>                _done;
    std::thread                      _thread;

    static void runMe(Client * client);
    void run();

public:
    using UP = std::unique_ptr<Client>;
    /**
     * The client arguments given to this method becomes the
     * responsibility of the client.
     **/
    Client(vespalib::CryptoEngine::SP engine, std::unique_ptr<ClientArguments> args);
    Client(const Client &) = delete;
    Client &operator=(const Client &) = delete;

    /**
     * Delete objects owned by this client, including the client arguments.
     **/
    ~Client();

    /**
     * @return A struct containing status info for this client.
     **/
    const ClientStatus & GetStatus() { return *_status; }
    void start();
    void stop();
    bool done();
    void join();
};

