// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <util/timer.h>
#include <httpclient/httpclient.h>
#include <util/filereader.h>
#include "client.h"

Client::Client(ClientArguments *args)
    : _args(args),
      _status(new ClientStatus()),
      _reqTimer(new Timer()),
      _cycleTimer(new Timer()),
      _masterTimer(new Timer()),
      _http(new HTTPClient(_args->_hostname, _args->_port,
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
        _output->write(FBENCH_DELIMITER + 1, strlen(FBENCH_DELIMITER) - 1);

    if (_args->_ignoreCount == 0)
        _masterTimer->Start();

    // Start reading from offset
    if ( _args->_singleQueryFile )
        _reader->SetFilePos(_args->_queryfileOffset);

    // run queries
    while (!_stop) {

        _cycleTimer->Start();

        linelen = _reader->ReadLine(_linebuf, _linebufsize);

        // Read maximum to _queryfileOffsetEnd
        if ( _args->_singleQueryFile && _reader->GetBufPos() >= _args->_queryfileBytes ) {
            _reader->SetFilePos(_args->_queryfileOffset);
        }

        if (linelen < 0) {
            _reader->Reset();
            // Start reading from offset
            if ( _args->_singleQueryFile ) {
                _reader->SetFilePos(_args->_queryfileOffset);
            }

            linelen = _reader->ReadLine(_linebuf, _linebufsize);
            if (linelen < 0) {
                fprintf(stderr, "Client %d: ERROR: could not read any lines from '%s'\n",
                        _args->_myNum, inputFilename);
                _status->SetError("Could not read any lines from query file.");
                break;
            }
            if (_args->_restartLimit == 0) {
                break;
            } else if (_args->_restartLimit > 0) {
                _args->_restartLimit--;
            }
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
            _reqTimer->Start();
            auto fetch_status = _http->Fetch(_linebuf, _output.get());
            _reqTimer->Stop();
            _status->AddRequestStatus(fetch_status.RequestStatus());
            if (fetch_status.Ok() && fetch_status.TotalHitCount() == 0)
                ++_status->_zeroHitQueries;
            if (_output) {
                if (!fetch_status.Ok()) {
                    _output->write("\nFBENCH: URL FETCH FAILED!\n",
                                          strlen("\nFBENCH: URL FETCH FAILED!\n"));
                    _output->write(FBENCH_DELIMITER + 1, strlen(FBENCH_DELIMITER) - 1);
                } else {
                    sprintf(timestr, "\nTIME USED: %0.4f s\n",
                            _reqTimer->GetTimespan() / 1000.0);
                    _output->write(timestr, strlen(timestr));
                    _output->write(FBENCH_DELIMITER + 1, strlen(FBENCH_DELIMITER) - 1);
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
