#include <thread>
#include <vector>
#include <atomic>
#include <fstream>
#include <sstream>
#include <chrono>
#include <iostream>
#include <stdlib.h>

using namespace std::literals;
using myclock = std::chrono::steady_clock;
using namespace std::chrono;

class Task {
public:
    Task(size_t threadId, size_t loopWork, size_t targetLatency, size_t sleepMS) :
        _threadId(threadId),
        _loopWork(loopWork),
        _targetLatency(targetLatency),
        _sleep(sleepMS),
        _stopped(std::make_unique<std::atomic<bool>>(false)),
        _thread()
    { }
    ~Task() {
        _thread->join();
    }
    void start() {
        _thread = std::make_unique<std::thread>([&]() { run(); });
    }
    void stop() {
        *_stopped = true;
    }
    Task(Task && rhs) = default;
    Task & operator = (Task && rhs) = default;
    static size_t calibrate();
private:
    void run() __attribute__((noinline));
    static double loop(size_t loopWork) __attribute__((noinline));
    size_t       _threadId;
    size_t       _loopWork;
    milliseconds _targetLatency;
    milliseconds _sleep;
    std::unique_ptr<std::atomic<bool>> _stopped;
    std::unique_ptr<std::thread> _thread;
};

void
Task::run() {
    std::ostringstream fileName;
    fileName << "cpumonitor-" << _threadId << ".log" << std::ends;
    std::ofstream log(fileName.str());

    for (size_t iteration(0); !(*_stopped); iteration++) {
        myclock::time_point start = myclock::now();
        double result = loop(_loopWork);
        myclock::time_point end = myclock::now();
        milliseconds latency = duration_cast<milliseconds>(end - start);
        milliseconds since = duration_cast<milliseconds>(end.time_since_epoch());
        log << iteration << " " << since.count() << " " << latency.count() << " " << result << std::endl;
        if (latency > 2*_targetLatency) {
            printf("OBS: %zu %zu %2.2f\n", _threadId, since.count(), double(latency.count())/_targetLatency.count());
        }
        if (_sleep.count() > 0) {
            std::this_thread::sleep_for(_sleep);
        }
    }
}

size_t
Task::calibrate() {
    constexpr size_t ONE_M = 1000000;
    myclock::time_point start = myclock::now();
    myclock::time_point end = start + 1s;
    size_t iterations(0);
    for (; myclock::now() < end; iterations++) {
        loop(ONE_M);
    }
    return (iterations*ONE_M)/1000;
}

double
Task::loop(size_t loopWork) {
    double result = drand48();
    for (size_t i(0); i < loopWork; i++) {
        result = (result + i)/ (i + 1);
    }
    return result;
}

int main(int argc, char * argv[]) {
    size_t numThreads(1);
    size_t runTime(60);
    size_t resolutionMS(100);
    size_t sleepMS(0);

    if (argc > 1) {
        numThreads = strtoul(argv[1], nullptr, 0);
    } else {
        std::cerr << argv[0] << " <num-threads> <seconds-to-run> <target-latency> <sleep-time>";
    }
    if (argc > 2) {
        runTime = strtoul(argv[2], nullptr, 0);
    }
    if (argc > 3) {
        resolutionMS = strtoul(argv[3], nullptr, 0);
    }
    if (argc > 4) {
        sleepMS = strtoul(argv[4], nullptr, 0);
    }
    size_t warmupCalibration = Task::calibrate();
    size_t calibratedPayload = Task::calibrate();
    std::cout << "Starting " << numThreads << " running for " << runTime << " seconds." << std::endl;
    std::cout << "Target latency for each subtask is " << resolutionMS << " milliseconds with calibrated payload of " << calibratedPayload << ". Warmup was " << warmupCalibration << std::endl;
    std::cout << "Logs are written to 'cpumonitor-<threadid>.log" << std::endl;
    std::vector<Task> tasks;
    tasks.reserve(numThreads);
    for (size_t threadId(0); threadId < numThreads; threadId++) {
        tasks.emplace_back(threadId, resolutionMS*calibratedPayload, resolutionMS, sleepMS);
    }
    for (Task & task : tasks) {
        task.start();
    }
    myclock::time_point start = myclock::now();
    myclock::time_point end = start + seconds(runTime);
    std::cout << start.time_since_epoch().count() << " Started" << std::endl;
    while (myclock::now() < end) {
        std::this_thread::sleep_for(1s);
    }
    std::cout << myclock::now().time_since_epoch().count() << " Stopping" << std::endl;
    for (Task & task : tasks) {
        task.stop();
    }
    tasks.clear();
    std::cout << myclock::now().time_since_epoch().count() << " Done" << std::endl;

    return 0;
}
