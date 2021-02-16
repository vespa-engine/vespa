// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <util/timer.h>
#include <httpclient/httpclient.h>
#include <util/filereader.h>
#include <util/clientstatus.h>
#include <vespa/vespalib/crypto/crypto_exception.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/util/size_literals.h>
#include "client.h"
#include "fbench.h"
#include <cstring>
#include <cmath>
#include <csignal>
#include <cinttypes>
#include <cstdlib>

namespace {

std::string maybe_load(const std::string &file_name, bool &failed) {
    std::string content;
    if (!file_name.empty()) {
        vespalib::MappedFileInput file(file_name);
        if (file.valid()) {
            content = std::string(file.get().data, file.get().size);
        } else {
            fprintf(stderr, "could not load file: '%s'\n", file_name.c_str());
            failed = true;
        }
    }
    return content;
}

}

sig_atomic_t exitSignal = 0;

FBench::FBench()
    : _crypto_engine(),
      _clients(),
      _ignoreCount(0),
      _cycle(0),
      _filenamePattern(NULL),
      _outputPattern(NULL),
      _byteLimit(0),
      _restartLimit(0),
      _maxLineSize(0),
      _keepAlive(true),
      _usePostMode(false),
      _headerBenchmarkdataCoverage(false),
      _seconds(60),
      _singleQueryFile(false)
{
}

FBench::~FBench()
{
    _clients.clear();
    free(_filenamePattern);
    free(_outputPattern);
}

bool
FBench::init_crypto_engine(const std::string &ca_certs_file_name,
                           const std::string &cert_chain_file_name,
                           const std::string &private_key_file_name,
                           bool allow_default_tls)
{
    if (ca_certs_file_name.empty() &&
        cert_chain_file_name.empty() &&
        private_key_file_name.empty())
    {
        if (allow_default_tls) {
            _crypto_engine = vespalib::CryptoEngine::get_default();
        } else {
            _crypto_engine = std::make_shared<vespalib::NullCryptoEngine>();
        }
        return true;
    }
    if (ca_certs_file_name.empty()) {
        fprintf(stderr, "CA certificate required; specify with -T\n");
        return false;
    }
    if (cert_chain_file_name.empty() != private_key_file_name.empty()) {
        fprintf(stderr, "both client certificate AND client private key required; specify with -C and -K\n");
        return false;
    }
    bool load_failed = false;
    auto ts_builder = vespalib::net::tls::TransportSecurityOptions::Params().
            ca_certs_pem(maybe_load(ca_certs_file_name, load_failed)).
            cert_chain_pem(maybe_load(cert_chain_file_name, load_failed)).
            private_key_pem(maybe_load(private_key_file_name, load_failed)).
            authorized_peers(vespalib::net::tls::AuthorizedPeers::allow_all_authenticated()).
            disable_hostname_validation(true); // TODO configurable or default false!
    vespalib::net::tls::TransportSecurityOptions tls_opts(std::move(ts_builder));
    if (load_failed) {
        fprintf(stderr, "failed to load transport security options\n");
        return false;
    }
    try {
        _crypto_engine = std::make_shared<vespalib::TlsCryptoEngine>(tls_opts);
    } catch (vespalib::crypto::CryptoException &e) {
        fprintf(stderr, "%s\n", e.what());
        return false;
    }
    return true;
}

void
FBench::InitBenchmark(int numClients, int ignoreCount, int cycle,
                      const char *filenamePattern, const char *outputPattern,
                      int byteLimit, int restartLimit, int maxLineSize,
                      bool keepAlive, bool base64Decode,
                      bool headerBenchmarkdataCoverage, int seconds,
                      bool singleQueryFile, const std::string & queryStringToAppend, const std::string & extraHeaders,
                      const std::string &authority, bool postMode)
{
    _clients.resize(numClients);
    _ignoreCount     = ignoreCount;
    _cycle           = cycle;

    free(_filenamePattern);
    _filenamePattern = strdup(filenamePattern);
    free(_outputPattern);
    _outputPattern   = (outputPattern == NULL) ?
                       NULL : strdup(outputPattern);
    _queryStringToAppend = queryStringToAppend;
    _extraHeaders    = extraHeaders;
    _authority       = authority;
    _byteLimit       = byteLimit;
    _restartLimit    = restartLimit;
    _maxLineSize     = maxLineSize;
    _keepAlive       = keepAlive;
    _base64Decode    = base64Decode;
    _usePostMode     = postMode;
    _headerBenchmarkdataCoverage = headerBenchmarkdataCoverage;
    _seconds = seconds;
    _singleQueryFile = singleQueryFile;
}

void
FBench::CreateClients()
{
    int spread = (_cycle > 1) ? _cycle : 1;

    int i(0);
    for(auto & client : _clients) {
        uint64_t off_beg = 0;
        uint64_t off_end = 0;
        if (_singleQueryFile) {
            off_beg = _queryfileOffset[i];
            off_end = _queryfileOffset[i+1];
        }
        client = std::make_unique<Client>(_crypto_engine,
            new ClientArguments(i, _clients.size(), _filenamePattern,
                                _outputPattern, _hostnames[i % _hostnames.size()].c_str(),
                                _ports[i % _ports.size()], _cycle,
                                random() % spread, _ignoreCount,
                                _byteLimit, _restartLimit, _maxLineSize,
                                _keepAlive, _base64Decode,
                                _headerBenchmarkdataCoverage,
                                off_beg, off_end,
                                _singleQueryFile, _queryStringToAppend, _extraHeaders, _authority, _usePostMode));
        ++i;
    }
}

bool
FBench::ClientsDone()
{
    bool done(true);
    for (auto & client : _clients) {
        if ( ! client->done() ) {
            return false;
        }
    }
    return done;
}

void
FBench::StartClients()
{
    printf("Starting clients...\n");
    for (auto & client : _clients) {
        client->start();
    }
}

void
FBench::StopClients()
{
    printf("Stopping clients");
    for (auto & client : _clients) {
        client->stop();
    }
    printf("\nClients stopped.\n");
    for (auto & client : _clients) {
        client->join();
    }
    printf("\nClients Joined.\n");
}

namespace {

const char *
approx(double latency, const ClientStatus & status) {
    return (latency > (status._timetable.size() / status._timetableResolution - 1))
           ? "ms (approx)"
           : "ms";
}

std::string
fmtPercentile(double percentile) {
    char buf[32];
    if (percentile <= 99.0) {
        snprintf(buf, sizeof(buf), "%2d  ", int(percentile));
    } else {
        snprintf(buf, sizeof(buf), "%2.1f", percentile);
    }
    return buf;
}

}

void
FBench::PrintSummary()
{
    ClientStatus status;

    double maxRate    = 0;
    double actualRate = 0;

    int realNumClients = 0;
    
    int i = 0;
    for (auto & client : _clients) {
        if (client->GetStatus()._error) {
            printf("Client %d: %s => discarding client results.\n",
                   i, client->GetStatus()._errorMsg.c_str());
        } else {
            status.Merge(client->GetStatus());
            ++realNumClients;
        }
        ++i;
    }
    double avg = status.GetAverage();

    maxRate = (avg > 0) ? realNumClients * 1000.0 / avg : 0;
    actualRate = (status._realTime > 0) ?
                 realNumClients * 1000.0 * status._requestCnt / status._realTime : 0;

    if (_keepAlive) {
        printf("*** HTTP keep-alive statistics ***\n");
        printf("connection reuse count -- %" PRIu64 "\n", status._reuseCnt);
    }
    printf("***************** Benchmark Summary *****************\n");
    printf("clients:                %8ld\n", _clients.size());
    printf("ran for:                %8d seconds\n", _seconds);
    printf("cycle time:             %8d ms\n", _cycle);
    printf("lower response limit:   %8d bytes\n", _byteLimit);
    printf("skipped requests:       %8ld\n", status._skipCnt);
    printf("failed requests:        %8ld\n", status._failCnt);
    printf("successful requests:    %8ld\n", status._requestCnt);
    printf("cycles not held:        %8ld\n", status._overtimeCnt);
    printf("minimum response time:  %8.2f ms\n", status._minTime);
    printf("maximum response time:  %8.2f ms\n", status._maxTime);
    printf("average response time:  %8.2f ms\n", status.GetAverage());

    for (double percentile : {25.0, 50.0, 75.0, 90.0, 95.0, 98.0, 99.0, 99.5, 99.6, 99.7, 99.8, 99.9}) {
        double latency = status.GetPercentile(percentile);
        printf("%s percentile:          %8.2f %s\n",
               fmtPercentile(percentile).c_str(), latency, approx(latency, status));
    }

    printf("actual query rate:      %8.2f Q/s\n", actualRate);
    printf("utilization:            %8.2f %%\n",
           (maxRate > 0) ? 100 * (actualRate / maxRate) : 0);
    printf("zero hit queries:       %8ld\n", status._zeroHitQueries);
    printf("http request status breakdown:\n");
    for (const auto& entry : status._requestStatusDistribution)
        printf("  %8u : %8u \n", entry.first, entry.second);
    
    fflush(stdout);
}

void
FBench::Usage()
{
    printf("usage: vespa-fbench [-H extraHeader] [-a queryStringToAppend ] [-n numClients] [-c cycleTime] [-l limit] [-i ignoreCount]\n");
    printf("              [-s seconds] [-q queryFilePattern] [-o outputFilePattern]\n");
    printf("              [-r restartLimit] [-m maxLineSize] [-k] <hostname> <port>\n\n");
    printf(" -H <str> : append extra header to each get request.\n");
    printf(" -A <str> : assign authority.  <str> should be hostname:port format. Overrides Host: header sent.\n");
    printf(" -P       : use POST for requests instead of GET.\n");
    printf(" -a <str> : append string to each query\n");
    printf(" -n <num> : run with <num> parallel clients [10]\n");
    printf(" -c <num> : each client will make a request each <num> milliseconds [1000]\n");
    printf("            ('-1' -> cycle time should be twice the response time)\n");
    printf(" -l <num> : minimum response size for successful requests [0]\n");
    printf(" -i <num> : do not log the <num> first results. -1 means no logging [0]\n");
    printf(" -s <num> : run the test for <num> seconds. -1 means forever [60]\n");
    printf(" -q <str> : pattern defining input query files ['query%%03d.txt']\n");
    printf("            (the pattern is used with sprintf to generate filenames)\n");
    printf(" -o <str> : save query results to output files with the given pattern\n");
    printf("            (default is not saving.)\n");
    printf(" -r <num> : number of times to re-use each query file. -1 means no limit [-1]\n");
    printf(" -m <num> : max line size in input query files [131072].\n");
    printf("            Can not be less than the minimum [1024].\n");
    printf(" -p <num> : print summary every <num> seconds.\n");
    printf(" -k       : disable HTTP keep-alive.\n");
    printf(" -d       : Base64 decode POST request content.\n");
    printf(" -y       : write data on coverage to output file.\n");
    printf(" -z       : use single query file to be distributed between clients.\n");
    printf(" -T <str> : CA certificate file to verify peer against.\n");
    printf(" -C <str> : client certificate file name.\n");
    printf(" -K <str> : client private key file name.\n");
    printf(" -D       : use TLS configuration from environment if T/C/K is not used\n\n");
    printf(" <hostname> : the host you want to benchmark.\n");
    printf(" <port>     : the port to use when contacting the host.\n\n");
    printf("Several hostnames and ports can be listed\n");
    printf("This is distributed in round-robin manner to clients\n");
}

void
FBench::Exit()
{
    StopClients();
    printf("\n");
    PrintSummary();
    std::_Exit(0);
}

int
FBench::Main(int argc, char *argv[])
{
    // parameters with default values.
    int numClients  = 10;
    int cycleTime   = 1000;
    int byteLimit   = 0;
    int ignoreCount = 0;
    int seconds     = 60;
    int maxLineSize = 128_Ki;
    const int minLineSize = 1024;

    const char *queryFilePattern  = "query%03d.txt";
    const char *outputFilePattern = NULL;
    std::string queryStringToAppend;
    std::string extraHeaders;
    std::string ca_certs_file_name; // -T
    std::string cert_chain_file_name; // -C
    std::string private_key_file_name; // -K
    bool allow_default_tls = false; // -D

    int  restartLimit = -1;
    bool keepAlive    = true;
    bool base64Decode = false;
    bool headerBenchmarkdataCoverage = false;
    bool usePostMode = false;

    bool singleQueryFile = false;
    std::string authority;

    int  printInterval = 0;

    // parse options and override defaults.
    int         idx;
    char        opt;
    const char *arg;
    bool        optError;

    idx = 1;
    optError = false;
    while((opt = GetOpt(argc, argv, "H:A:T:C:K:Da:n:c:l:i:s:q:o:r:m:p:kdxyzP", arg, idx)) != -1) {
        switch(opt) {
        case 'A':
            authority = arg;
            break;
        case 'H':
            extraHeaders += std::string(arg) + "\r\n";
            if (strncmp(arg, "Host:", 5) == 0) {
                fprintf(stderr, "Do not override 'Host:' header, use -A option instead\n");
                return -1;
            }
            break;
        case 'T':
            ca_certs_file_name = std::string(arg);
            break;
        case 'C':
            cert_chain_file_name = std::string(arg);
            break;
        case 'K':
            private_key_file_name = std::string(arg);
            break;
        case 'D':
            allow_default_tls = true;
            break;
        case 'a':
            queryStringToAppend = std::string(arg);
            break;
        case 'n':
            numClients = atoi(arg);
            break;
        case 'c':
            cycleTime = atoi(arg);
            break;
        case 'l':
            byteLimit = atoi(arg);
            break;
        case 'i':
            ignoreCount = atoi(arg);
            break;
        case 's':
            seconds = atoi(arg);
            break;
        case 'q':
            queryFilePattern = arg;
            break;
        case 'o':
            outputFilePattern = arg;
            break;
        case 'r':
            restartLimit = atoi(arg);
            break;
        case 'm':
            maxLineSize = atoi(arg);
            if (maxLineSize < minLineSize) {
                maxLineSize = minLineSize;
            }
            break;
        case 'P':
            usePostMode = true;
            break;
        case 'p':
            printInterval = atoi(arg);
            if (printInterval < 0)
                optError = true;
            break;
        case 'k':
            keepAlive = false;
            break;
        case 'd':
            base64Decode = false;
            break;
        case 'x': 
            // consuming x for backwards compability. This turned on header benchmark data
            // but this is now always on. 
            break;
        case 'y':
            headerBenchmarkdataCoverage = true;
            break;
        case 'z':
            singleQueryFile = true;
            break;
        default:
            optError = true;
            break;
        }
    }

    if ( argc < (idx + 2) || optError) {
        Usage();
        return -1;
    }
    // Hostname/port must be in pair
    int args = (argc - idx);
    if (args % 2 != 0) {
        fprintf(stderr, "Not equal number of hostnames and ports\n");
        return -1;
    }

    if (!init_crypto_engine(ca_certs_file_name, cert_chain_file_name, private_key_file_name, allow_default_tls)) {
        fprintf(stderr, "failed to initialize crypto engine\n");
        return -1;
    }

    short hosts = args / 2;

    for (int i=0; i<hosts; ++i)
    {
        _hostnames.push_back(std::string(argv[idx+2*i]));
        int port = atoi(argv[idx+2*i+1]);
        if (port == 0) {
            fprintf(stderr, "Not a valid port:\t%s\n", argv[idx+2*i+1]);
            return -1;
        }
        _ports.push_back(port);
    }

    // Find offset for each client if shared query file
    _queryfileOffset.push_back(0);
    if (singleQueryFile) {
        // Open file to find offsets, with pattern as if client 0
        char filename[1024];
        snprintf(filename, 1024, queryFilePattern, 0);
        queryFilePattern = filename;
        FileReader reader;
        if (!reader.Open(queryFilePattern)) {
            fprintf(stderr, "ERROR: could not open file '%s' [read mode]\n",
                    queryFilePattern);
            return -1;
        }

        uint64_t totalSize = reader.GetFileSize();
        uint64_t perClient = totalSize / numClients;

        for (int i=1; i<numClients; ++i) {
            /** Start each client with some offset, adjusted to next newline
             **/
            FileReader r;
            r.Open(queryFilePattern);
            uint64_t clientOffset = std::max(i*perClient, _queryfileOffset.back() );
            uint64_t newline = r.FindNextLine(clientOffset);
            _queryfileOffset.push_back(newline);
        }

        // Add pos to end of file
        _queryfileOffset.push_back(totalSize);


        // Print offset of clients
        /*
          printf("%6s%14s%15s", "Client", "Offset", "Bytes\n");
          for (unsigned int i =0; i< _queryfileOffset.size()-1; ++i)
          printf("%6d%14ld%14ld\n", i, _queryfileOffset[i], _queryfileOffset[i+1]-_queryfileOffset[i]);
        */
    }

    InitBenchmark(numClients, ignoreCount, cycleTime,
                  queryFilePattern, outputFilePattern,
                  byteLimit, restartLimit, maxLineSize,
                  keepAlive, base64Decode,
                  headerBenchmarkdataCoverage, seconds,
                  singleQueryFile, queryStringToAppend, extraHeaders,
                  authority, usePostMode);

    CreateClients();
    StartClients();

    if (seconds < 0) {
        unsigned int secondCount = 0;
        while (!ClientsDone()) {
            if (exitSignal) {
                _seconds = secondCount;
                Exit();
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(1000));
            if (printInterval != 0 && ++secondCount % printInterval == 0) {
                printf("\nRuntime: %d sec\n", secondCount);
                PrintSummary();
            }
        }
    } else if (seconds > 0) {
        // Timer to compansate for work load on PrintSummary()
        Timer sleepTimer;
        sleepTimer.SetMax(1000);

        for (;seconds > 0 && !ClientsDone(); seconds--) {
            if (exitSignal) {
                _seconds = _seconds - seconds;
                Exit();
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(int(sleepTimer.GetRemaining())));
            sleepTimer.Start();

            if (seconds % 60 == 0) {
                printf("[dummydate]: PROGRESS: vespa-fbench: Seconds left %d\n", seconds);
            }

            if (printInterval != 0 && seconds % printInterval == 0) {
                printf("\nRuntime: %d sec\n", _seconds - seconds);
                PrintSummary();
            }

            sleepTimer.Stop();
        }
    }

    StopClients();
    PrintSummary();
    return 0;
}

void sighandler(int sig)
{
    if (sig == SIGINT) {
        exitSignal = 1;
    }
}

int
main(int argc, char** argv)
{

    struct sigaction act;

    act.sa_handler = sighandler;
    sigemptyset(&act.sa_mask);
    act.sa_flags = 0;

    sigaction(SIGINT, &act, NULL);
    sigaction(SIGPIPE, &act, NULL);

    FBench myApp;
    return myApp.Main(argc, argv);
}
