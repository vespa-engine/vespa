// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "client.h"
#include <util/timer.h>
#include <util/clientstatus.h>
#include <httpclient/httpclient.h>
#include <util/filereader.h>
#include <cassert>
#include <cstring>
#include <iostream>
#include <vespa/vespalib/encoding/base64.h>

using namespace vespalib;

Client::Client(vespalib::CryptoEngine::SP engine, ClientArguments *args)
    : _args(args),
      _status(new ClientStatus()),
      _reqTimer(new Timer()),
      _cycleTimer(new Timer()),
      _masterTimer(new Timer()),
      _http(new HTTPClient(std::move(engine), _args->_hostname, _args->_port,
                           _args->_keepAlive, _args->_headerBenchmarkdataCoverage,
                           _args->_extraHeaders, _args->_authority)),
      _reader(new FileReader()),
      _output(),
      _linebufsize(args->_maxLineSize),
      _linebuf(new char[_linebufsize]),
      _stop(false),
      _done(false),
      _thread()
{
    assert(args != NULL);
    _cycleTimer->SetMax(_args->_cycle);
}

Client::~Client()
{
    delete [] _linebuf;
}

void Client::runMe(Client * me) {
    me->run();
}


class UrlReader {
    FileReader &_reader;
    const ClientArguments &_args;
    int _restarts;
    int _contentbufsize;
    int _leftOversLen;
    char *_contentbuf;
    const char *_leftOvers;
public:
    UrlReader(FileReader& reader, const ClientArguments &args)
        : _reader(reader), _args(args), _restarts(0),
          _contentbufsize(0), _leftOversLen(0),
          _contentbuf(0), _leftOvers(0)
    {
        if (_args._usePostMode) {
            _contentbufsize = 16 * _args._maxLineSize;
            _contentbuf = new char[_contentbufsize];
        }
    }
    bool reset();
    int findUrl(char *buf, int buflen);
    int nextUrl(char *buf, int buflen);
    int nextContent();
    const char *content() const { return _contentbuf; }
    ~UrlReader() { delete [] _contentbuf; }
};

bool UrlReader::reset()
{
    if (_restarts == _args._restartLimit) {
        return false;
    } else if (_args._restartLimit > 0) {
        _restarts++;
    }
    _reader.Reset();
    // Start reading from offset
    if (_args._singleQueryFile) {
        _reader.SetFilePos(_args._queryfileOffset);
    }
    return true;
}

int UrlReader::findUrl(char *buf, int buflen)
{
    while (true) {
        if ( _args._singleQueryFile && _reader.GetFilePos() >= _args._queryfileEndOffset ) {
            // reached logical EOF
            return -1;
        }
        int ll = _reader.ReadLine(buf, buflen);
        if (ll < 0) {
            // reached physical EOF
            return ll;
        }
        if (ll > 0) {
            if (buf[0] == '/' || !_args._usePostMode) {
                // found URL
                return ll;
            }
        }
    }
}

int UrlReader::nextUrl(char *buf, int buflen)
{
    if (_leftOvers) {
        if ( _args._usePostMode && _args._singleQueryFile && _reader.GetFilePos() >= _args._queryfileEndOffset ) {
            // reached logical EOF
            _leftOvers = NULL;
            return -1;
        }
        int sz = std::min(_leftOversLen, buflen-1);
        strncpy(buf, _leftOvers, sz);
        buf[sz] = '\0';
        _leftOvers = NULL;
        return _leftOversLen;
    }
    int ll = findUrl(buf, buflen);
    if (ll > 0) {
        return ll;
    }
    if (reset()) {
        // try again
        ll = findUrl(buf, buflen);
    }
    return ll;
}

int UrlReader::nextContent()
{
    char *buf = _contentbuf;
    int totLen = 0;
    // make sure we don't chop leftover URL
    while (totLen + _args._maxLineSize < _contentbufsize) {
       // allow space for newline:
       int room = _contentbufsize - totLen - 1;
       int len = _reader.ReadLine(buf, room);
       if (len < 0) {
           // reached EOF
           break;
       }
       len = std::min(len, room);
       if (len > 0 && buf[0] == '/') {
           // reached next URL
           _leftOvers = buf;
           _leftOversLen = len;
           break;
       }
       buf += len;
       totLen += len;
       *buf++ = '\n';
       totLen++;
    }
    // ignore last newline
    return (totLen > 0) ? totLen-1 : 0;
}


void
Client::run()
{
    char inputFilename[1024];
    char outputFilename[1024];
    char timestr[64];
    int  linelen;
    ///   int  reslen;

    std::this_thread::sleep_for(std::chrono::milliseconds(_args->_delay));

    // open query file
    snprintf(inputFilename, 1024, _args->_filenamePattern, _args->_myNum);
    if (!_reader->Open(inputFilename)) {
        printf("Client %d: ERROR: could not open file '%s' [read mode]\n",
               _args->_myNum, inputFilename);
        _status->SetError("Could not open query file.");
        return;
    }
    if (_args->_outputPattern != NULL) {
        snprintf(outputFilename, 1024, _args->_outputPattern, _args->_myNum);
        _output = std::make_unique<std::ofstream>(outputFilename, std::ofstream::out | std::ofstream::binary);
        if (_output->fail()) {
            printf("Client %d: ERROR: could not open file '%s' [write mode]\n",
                   _args->_myNum, outputFilename);
            _status->SetError("Could not open output file.");
            return;
        }
    }
    if (_output)
        _output->write(&FBENCH_DELIMITER[1], strlen(FBENCH_DELIMITER) - 1);

    if (_args->_ignoreCount == 0)
        _masterTimer->Start();

    // Start reading from offset
    if ( _args->_singleQueryFile )
        _reader->SetFilePos(_args->_queryfileOffset);

    UrlReader urlSource(*_reader, *_args);
    size_t urlNumber = 0;

    // run queries
    while (!_stop) {

        _cycleTimer->Start();

        linelen = urlSource.nextUrl(_linebuf, _linebufsize);
        if (linelen > 0) {
            ++urlNumber;
        } else {
            if (urlNumber == 0) {
                fprintf(stderr, "Client %d: ERROR: could not read any lines from '%s'\n",
                        _args->_myNum, inputFilename);
                _status->SetError("Could not read any lines from query file.");
            }
            break;
        }
        if (linelen < _linebufsize) {
            if (_output) {
                _output->write("URL: ", strlen("URL: "));
                _output->write(_linebuf, linelen);
                _output->write("\n\n", 2);
            }
            if (linelen + (int)_args->_queryStringToAppend.length() < _linebufsize) {
                strcat(_linebuf, _args->_queryStringToAppend.c_str());
            }
            int cLen = _args->_usePostMode ? urlSource.nextContent() : 0;
            
            const char* content = urlSource.content();
            std::string base64_decoded;
            if (_args->_usePostMode && _args->_base64Decode) {
                base64_decoded = Base64::decode(content, cLen);
                content = base64_decoded.c_str();
                cLen = base64_decoded.size();
            }
                        
            _reqTimer->Start();
            auto fetch_status = _http->Fetch(_linebuf, _output.get(), _args->_usePostMode, content, cLen);
            _reqTimer->Stop();
            _status->AddRequestStatus(fetch_status.RequestStatus());
            if (fetch_status.Ok() && fetch_status.TotalHitCount() == 0)
                ++_status->_zeroHitQueries;
            if (_output) {
                if (!fetch_status.Ok()) {
                    _output->write("\nFBENCH: URL FETCH FAILED!\n",
                                          strlen("\nFBENCH: URL FETCH FAILED!\n"));
                    _output->write(&FBENCH_DELIMITER[1], strlen(FBENCH_DELIMITER) - 1);
                } else {
                    sprintf(timestr, "\nTIME USED: %0.4f s\n",
                            _reqTimer->GetTimespan() / 1000.0);
                    _output->write(timestr, strlen(timestr));
                    _output->write(&FBENCH_DELIMITER[1], strlen(FBENCH_DELIMITER) - 1);
                }
            }
            if (fetch_status.ResultSize() >= _args->_byteLimit) {
                if (_args->_ignoreCount == 0)
                    _status->ResponseTime(_reqTimer->GetTimespan());
            } else {
                if (_args->_ignoreCount == 0)
                    _status->RequestFailed();
            }
        } else {
            if (_args->_ignoreCount == 0)
                _status->SkippedRequest();
        }
        _cycleTimer->Stop();
        if (_args->_cycle < 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(int(_reqTimer->GetTimespan())));
        } else {
            if (_cycleTimer->GetRemaining() > 0) {
                std::this_thread::sleep_for(std::chrono::milliseconds(int(_cycleTimer->GetRemaining())));
            } else {
                if (_args->_ignoreCount == 0)
                    _status->OverTime();
            }
        }
        if (_args->_ignoreCount > 0) {
            _args->_ignoreCount--;
            if (_args->_ignoreCount == 0)
                _masterTimer->Start();
        }
        // Update current time span to calculate Q/s
        _status->SetRealTime(_masterTimer->GetCurrent());
    }
    _masterTimer->Stop();
    _status->SetRealTime(_masterTimer->GetTimespan());
    _status->SetReuseCount(_http->GetReuseCount());
    printf(".");
    fflush(stdout);
    _done = true;
}

void Client::stop() {
    _stop = true;
}

bool Client::done() {
    return _done;
}

void Client::start() {
    _thread = std::thread(Client::runMe, this);
}

void Client::join() {
    _thread.join();
}
