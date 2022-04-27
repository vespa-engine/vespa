// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/thread.h>
#include <vespa/log/bufferedlogger.h>
#include <array>
#include <iostream>
#include <thread>
#include <chrono>
#include <atomic>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <cstdlib>

using std::string;
using namespace std::chrono_literals;
using namespace std::chrono;

LOG_SETUP(".threadtest");

class FileThread : public FastOS_Runnable
{
    std::atomic<bool> _done;
    string _file;
public:
    FileThread(string file) : _done(false), _file(file) {}
    void Run(FastOS_ThreadInterface *thread, void *arg) override;
    void stop() { _done.store(true, std::memory_order_relaxed); }
};

class LoggerThread : public FastOS_Runnable
{
    std::atomic<bool> _done;
public:
    std::atomic<bool> _useLogBuffer;
    LoggerThread() : _done(false), _useLogBuffer(false) {}
    void Run(FastOS_ThreadInterface *thread, void *arg) override;
    void stop() { _done.store(true, std::memory_order_relaxed); }
};

void
FileThread::Run(FastOS_ThreadInterface *, void *)
{
    unlink(_file.c_str());
    while (!_done.load(std::memory_order_relaxed)) {
        int fd = open(_file.c_str(), O_RDWR | O_CREAT | O_APPEND, 0644);
        if (fd == -1) {
            fprintf(stderr, "open failed: %s\n", strerror(errno));
            std::_Exit(1);
        }
        std::this_thread::sleep_for(5ms);
        struct stat buf;
        fstat(fd, &buf);
        if (buf.st_size != 0) {
            fprintf(stderr, "%s isn't empty anymore\n", _file.c_str());
            std::_Exit(1);
        }
        if (close(fd) != 0) {
            fprintf(stderr, "close of %d failed: %s\n", fd, strerror(errno));
            std::_Exit(1);
        }
    }
}


void
LoggerThread::Run(FastOS_ThreadInterface *, void *)
{
    int counter = 0;
    while (!_done.load(std::memory_order_relaxed)) {
        if (_useLogBuffer.load(std::memory_order_relaxed)) {
            LOGBM(info, "bla bla bla %u", ++counter);
        } else {
            LOG(info, "bla bla bla");
        }
    }
}


class ThreadTester
{
public:
    int _argc = 0;
    char **_argv = nullptr;
    int Main();
    int Entry(int argc, char **argv) {
        _argc = argc;
        _argv = argv;
        return Main();
    }
};

int
ThreadTester::Main()
{
    std::cerr << "Testing that logging is threadsafe. 5 sec test.\n";
    FastOS_ThreadPool pool(128 * 1024);

    const int numWriters = 30;
    const int numLoggers = 10;

    auto writers = std::array<std::unique_ptr<FileThread>, numWriters>();
    auto loggers = std::array<std::unique_ptr<LoggerThread>, numLoggers>();

    for (int i = 0; i < numWriters; i++) {
        char filename[100];
        sprintf(filename, "empty.%d", i);
        writers[i] = std::make_unique<FileThread>(filename);
        pool.NewThread(writers[i].get());
    }
    for (int i = 0; i < numLoggers; i++) {
        loggers[i] = std::make_unique<LoggerThread>();
        pool.NewThread(loggers[i].get());
    }

    steady_clock::time_point start = steady_clock::now();
    // Reduced runtime to half as the test now repeats itself to test with
    // buffering. (To avoid test taking 5 seconds)
    while ((steady_clock::now() - start) < 2.5s) {
        unlink(_argv[1]);
        std::this_thread::sleep_for(1ms);
    }
        // Then set to use logbuffer and continue
    for (int i = 0; i < numLoggers; i++) {
        loggers[i]->_useLogBuffer.store(true, std::memory_order_relaxed);
    }
    start = steady_clock::now();
    while ((steady_clock::now() - start) < 2.5s) {
        unlink(_argv[1]);
        std::this_thread::sleep_for(1ms);
    }

    for (int i = 0; i < numLoggers; i++) {
        loggers[i]->stop();
    }
    for (int i = 0; i < numWriters; i++) {
        writers[i]->stop();
    }

    pool.Close();

    return 0;
}

int main(int argc, char **argv)
{
    ThreadTester app;
    return app.Entry(argc, argv);
}


