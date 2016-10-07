#include <thread>
#include <vector>
#include <atomic>
#include <fstream>
#include <sstream>
#include <chrono>
#include <iostream>
#include <stdlib.h>

using namespace std::literals;
using clock = std::steady_clock;

class Task {
public:
    Task(size_t threadId, size_t loopWork) :
        _threadId(threadId),
        _loopWork(loopWork),
        _stopped(std::make_unique<std::atomic<bool>>(false)),
        _thread([&]() { run(); })
    { }
    ~Task() {
        _thread.join();
    }
    void stop() {
        *_stopped = true;
    }
    Task(Task && rhs) = default;
    Task & operator = (Task && rhs) = default;
private:
    void run() __attribute__((noinline));
    static double loop(size_t loopWork) __attribute__((noinline));
    size_t _threadId;
    size_t _loopWork;
    std::unique_ptr<std::atomic<bool>> _stopped;
    std::thread _thread;
};

void
Task::run() {
    std::ostringstream fileName;
    fileName << "cpumonitor-" << _threadId << ".log" << std::ends;
    std::ofstream log(fileName.str());

    for (size_t iteration(0); !(*_stopped); iteration++) {
        clock::time_point start = clock::now();
        double result = loop(_loopWork);
        clock::time_point stop = clock::now();
        log << iteration << " " << stop << " " << (stop - end) << result << std::endl;
    }
}

double
Task::loop(size_t loopWork) {
    double result = drand48();
    for (size_t i(0); i < loopWork; loopWork++) {
        result = (result + i)/ (i + 1);
    }
    return result;
}

int main(int argc, char * argv[]) {
    size_t numThreads(1);
    size_t runTime(60);
    size_t resolutionMS(100);

    if (argc > 1) {
        numThreads = strtoul(argv[1], nullptr, 0);
    }
    if (argc > 2) {
        runTime = strtoul(argv[2], nullptr, 0);
    }
    if (argc > 3) {
        resolutionMS = strtoul(argv[3], nullptr, 0);
    }
    std::cout << "Starting " << numThreads << " running for " << runTime << " seconds. Target latency for each subtask is " << resolutionMS << " milliseconds" << std::endl;
    std::vector<Task> tasks;
    tasks.reserve(numThreads);
    for (size_t threadId(0); threadId < numThreads; threadId++) {
        tasks.emplace_back(threadId, resolutionMS*1000000);
    }
    clock::time_point start = clock::now();
    clock::time_point end = start + std::chrono::seconds(runTime);
    std::cout << start << " Started" << std::endl;
    while (clock::now() < end) {
        std::this_thread::sleep_for(1s);
    }
    std::cout << clock::now() << " Stopping" << std::endl;
    for (Task & task : tasks) {
        task.stop();
    }
    tasks.clear();
    std::cout << clock::now() << " Done" << std::endl;

    return 0;
}
