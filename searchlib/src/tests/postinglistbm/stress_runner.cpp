// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stress_runner.h"

#include <vespa/fastos/thread.h>
#include <vespa/searchlib/test/fakedata/fake_match_loop.h>
#include <vespa/searchlib/test/fakedata/fakeposting.h>
#include <vespa/searchlib/test/fakedata/fakeword.h>
#include <vespa/searchlib/test/fakedata/fakewordset.h>
#include <vespa/searchlib/test/fakedata/fpfactory.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/time.h>
#include <condition_variable>
#include <mutex>
#include <vector>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".stress_runner");

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::queryeval::SearchIterator;
using namespace search::fakedata;

namespace postinglistbm {

class StressWorker;
using StressWorkerUP = std::unique_ptr<StressWorker>;

class StressMaster {
private:
    StressMaster(const StressMaster &);

    StressMaster &operator=(const StressMaster &);

    vespalib::Rand48 &_rnd;
    uint32_t _numDocs;
    std::vector<std::string> _postingTypes;
    StressRunner::OperatorType _operatorType;
    uint32_t _loops;
    uint32_t _skipCommonPairsRate;
    uint32_t _stride;
    bool _unpack;

    FastOS_ThreadPool *_threadPool;
    std::vector<StressWorkerUP> _workers;
    uint32_t _workersDone;

    FakeWordSet &_wordSet;

    std::vector<std::vector<FakePosting::SP> > _postings;

    std::mutex              _taskLock;
    std::condition_variable _taskCond;
    uint32_t _taskIdx;
    uint32_t _numTasks;

public:
    using Task = std::pair<FakePosting *, FakePosting *>;

private:
    std::vector<Task> _tasks;

public:
    StressMaster(vespalib::Rand48 &rnd,
                 FakeWordSet &wordSet,
                 const std::vector<std::string> &postingType,
                 StressRunner::OperatorType operatorType,
                 uint32_t loops,
                 uint32_t skipCommonPairsRate,
                 uint32_t numTasks,
                 uint32_t stride,
                 bool unpack);

    ~StressMaster();
    void run();
    void makePostingsHelper(FPFactory *postingFactory,
                            const std::string &postingFormat,
                            bool validate, bool verbose);
    void dropPostings();
    void dropTasks();
    void resetTasks();  // Prepare for rerun
    void setupTasks(uint32_t numTasks);
    Task *getTask();
    uint32_t getNumDocs() const { return _numDocs; }
    bool getUnpack() const { return _unpack; }
    double runWorkers(const std::string &postingFormat);
};

class StressWorker : public FastOS_Runnable {
protected:
    StressMaster& _master;
    uint32_t _id;

    virtual void run_task(const FakePosting& f1, const FakePosting& f2, uint32_t doc_id_limit, bool unpack) = 0;

public:
    StressWorker(const StressWorker&) = delete;
    StressWorker& operator=(const StressWorker&) = delete;

    StressWorker(StressMaster& master, uint32_t id);
    virtual ~StressWorker();

    virtual void Run(FastOS_ThreadInterface* thisThread, void* arg) override;
};

class DirectStressWorker : public StressWorker {
private:
    void run_task(const FakePosting& f1, const FakePosting& f2, uint32_t doc_id_limit, bool unpack) override;

public:
    DirectStressWorker(StressMaster& master, uint32_t id);
};

class AndStressWorker : public StressWorker {
private:
    void run_task(const FakePosting& f1, const FakePosting& f2, uint32_t doc_id_limit, bool unpack) override;

public:
    AndStressWorker(StressMaster& master, uint32_t id);
};

class OrStressWorker : public StressWorker {
private:
    void run_task(const FakePosting& f1, const FakePosting& f2, uint32_t doc_id_limit, bool unpack) override;

public:
    OrStressWorker(StressMaster& master, uint32_t id);
};


StressMaster::StressMaster(vespalib::Rand48 &rnd,
                           FakeWordSet &wordSet,
                           const std::vector<std::string> &postingTypes,
                           StressRunner::OperatorType operatorType,
                           uint32_t loops,
                           uint32_t skipCommonPairsRate,
                           uint32_t numTasks,
                           uint32_t stride,
                           bool unpack)
    : _rnd(rnd),
      _numDocs(wordSet.numDocs()),
      _postingTypes(postingTypes),
      _operatorType(operatorType),
      _loops(loops),
      _skipCommonPairsRate(skipCommonPairsRate),
      _stride(stride),
      _unpack(unpack),
      _threadPool(nullptr),
      _workers(),
      _workersDone(0),
      _wordSet(wordSet),
      _postings(FakeWordSet::NUM_WORDCLASSES),
      _taskLock(),
      _taskCond(),
      _taskIdx(0),
      _numTasks(numTasks),
      _tasks()
{
    LOG(info, "StressMaster::StressMaster()");

    _threadPool = new FastOS_ThreadPool(128_Ki, 400);
}

StressMaster::~StressMaster()
{
    LOG(info, "StressMaster::~StressMaster()");

    _threadPool->Close();
    delete _threadPool;
    _threadPool = nullptr;
    _workers.clear();
    dropPostings();
}

void
StressMaster::dropPostings()
{
    for (auto& posting : _postings) {
        posting.clear();
    }
    dropTasks();
}

void
StressMaster::dropTasks()
{
    _tasks.clear();
    _taskIdx = 0;
}

void
StressMaster::resetTasks()
{
    _taskIdx = 0;
}

void
makeSomePostings(FPFactory *postingFactory,
                 const FakeWordSet::FakeWordVector &words,
                 std::vector<FakePosting::SP> &postings,
                 uint32_t stride,
                 bool validate,
                 bool verbose)
{
    for (const auto& word : words) {
        auto posting = postingFactory->make(*word);
        if (validate) {
            TermFieldMatchData md;
            TermFieldMatchDataArray tfmda;
            tfmda.add(&md);

            md.setNeedNormalFeatures(posting->enable_unpack_normal_features());
            md.setNeedInterleavedFeatures(posting->enable_unpack_interleaved_features());
            std::unique_ptr<SearchIterator> iterator(posting->createIterator(tfmda));
            if (posting->hasWordPositions()) {
                if (stride != 0) {
                    word->validate(iterator.get(), tfmda, stride, posting->enable_unpack_normal_features(), posting->has_interleaved_features() && posting->enable_unpack_interleaved_features(), verbose);
                } else {
                    word->validate(iterator.get(), tfmda, posting->enable_unpack_normal_features(), posting->has_interleaved_features() && posting->enable_unpack_interleaved_features(), verbose);
                }
            } else {
                word->validate(iterator.get(), verbose);
            }
        }
        postings.push_back(posting);
    }
}

void
StressMaster::makePostingsHelper(FPFactory *postingFactory,
                                 const std::string &postingFormat,
                                 bool validate, bool verbose)
{
    vespalib::Timer tv;

    postingFactory->setup(_wordSet);
    for (size_t i = 0; i < _wordSet.words().size(); ++i)
        makeSomePostings(postingFactory,
                         _wordSet.words()[i], _postings[i],
                         _stride,
                         validate,
                         verbose);

    LOG(info,
        "StressMaster::makePostingsHelper() elapsed %10.6f s for %s format",
        vespalib::to_s(tv.elapsed()),
        postingFormat.c_str());
}

void
StressMaster::setupTasks(uint32_t numTasks)
{
    uint32_t wordclass1;
    uint32_t wordclass2;
    uint32_t word1idx;
    uint32_t word2idx;

    for (uint32_t i = 0; i < numTasks; ++i) {
        wordclass1 = _rnd.lrand48() % _postings.size();
        wordclass2 = _rnd.lrand48() % _postings.size();
        while (wordclass1 == FakeWordSet::COMMON_WORD &&
               wordclass2 == FakeWordSet::COMMON_WORD &&
               (_rnd.lrand48() % _skipCommonPairsRate) != 0) {
            wordclass1 = _rnd.lrand48() % _postings.size();
            wordclass2 = _rnd.lrand48() % _postings.size();
        }
        word1idx = _rnd.lrand48() % _postings[wordclass1].size();
        word2idx = _rnd.lrand48() % _postings[wordclass2].size();
        FakePosting::SP p1 = _postings[wordclass1][word1idx];
        FakePosting::SP p2 = _postings[wordclass2][word2idx];
        _tasks.push_back(std::make_pair(p1.get(), p2.get()));
    }
}

StressMaster::Task *
StressMaster::getTask()
{
    Task *result = nullptr;
    std::lock_guard<std::mutex> taskGuard(_taskLock);
    if (_taskIdx < _tasks.size()) {
        result = &_tasks[_taskIdx];
        ++_taskIdx;
    } else {
        _workersDone++;
        if (_workersDone == _workers.size()) {
            _taskCond.notify_all();
        }
    }
    return result;
}

void
StressMaster::run()
{
    LOG(info, "StressMaster::run()");

    for (const auto& type : _postingTypes) {
        std::unique_ptr<FPFactory> factory(getFPFactory(type, _wordSet.getSchema()));
        makePostingsHelper(factory.get(), type, true, false);
        setupTasks(_numTasks);
        double totalTime = 0;
        for (uint32_t loop = 0; loop < _loops; ++loop) {
            totalTime += runWorkers(type);
            resetTasks();
        }
        LOG(info, "StressMaster::average run elapsed %10.6f s for workers %s format",
            totalTime / _loops, type.c_str());
        dropPostings();
    }
    std::this_thread::sleep_for(250ms);
}

double
StressMaster::runWorkers(const std::string &postingFormat)
{
    vespalib::Timer tv;

    uint32_t numWorkers = 8;
    for (uint32_t i = 0; i < numWorkers; ++i) {
        if (_operatorType == StressRunner::OperatorType::Direct) {
            _workers.push_back(std::make_unique<DirectStressWorker>(*this, i));
        } else if (_operatorType == StressRunner::OperatorType::And) {
            _workers.push_back(std::make_unique<AndStressWorker>(*this, i));
        } else if (_operatorType == StressRunner::OperatorType::Or) {
            _workers.push_back(std::make_unique<OrStressWorker>(*this, i));
        }
    }

    for (auto& worker : _workers) {
        _threadPool->NewThread(worker.get());
    }

    {
        std::unique_lock<std::mutex> taskGuard(_taskLock);
        while (_workersDone < _workers.size()) {
            _taskCond.wait(taskGuard);
        }
    }

    LOG(info,
        "StressMaster::run() elapsed %10.6f s for workers %s format",
       vespalib::to_s(tv.elapsed()),
        postingFormat.c_str());
    _workers.clear();
    _workersDone = 0;
    return vespalib::to_s(tv.elapsed());
}

StressWorker::StressWorker(StressMaster& master, uint32_t id)
    : _master(master),
      _id(id)
{
}

StressWorker::~StressWorker() = default;

void
StressWorker::Run(FastOS_ThreadInterface* thisThread, void* arg)
{
    (void) thisThread;
    (void) arg;
    LOG(debug, "StressWorker::Run(), id=%u", _id);

    bool unpack = _master.getUnpack();
    for (;;) {
        StressMaster::Task *task = _master.getTask();
        if (task == nullptr) {
            break;
        }
        run_task(*task->first, *task->second, _master.getNumDocs(), unpack);
    }
}

DirectStressWorker::DirectStressWorker(StressMaster& master, uint32_t id)
    : StressWorker(master, id)
{
}

void
DirectStressWorker::run_task(const FakePosting& f1, const FakePosting& f2, uint32_t doc_id_limit, bool unpack)
{
    if (unpack) {
        FakeMatchLoop::direct_posting_scan_with_unpack(f1, doc_id_limit);
        FakeMatchLoop::direct_posting_scan_with_unpack(f2, doc_id_limit);
    } else {
        FakeMatchLoop::direct_posting_scan(f1, doc_id_limit);
        FakeMatchLoop::direct_posting_scan(f2, doc_id_limit);
    }
}

AndStressWorker::AndStressWorker(StressMaster& master, uint32_t id)
    : StressWorker(master, id)
{
}

void
AndStressWorker::run_task(const FakePosting& f1, const FakePosting& f2, uint32_t doc_id_limit, bool unpack)
{
    if (unpack) {
        FakeMatchLoop::and_pair_posting_scan_with_unpack(f1, f2, doc_id_limit);
    } else {
        FakeMatchLoop::and_pair_posting_scan(f1, f2, doc_id_limit);
    }
}

OrStressWorker::OrStressWorker(StressMaster& master, uint32_t id)
    : StressWorker(master, id)
{
}

void
OrStressWorker::run_task(const FakePosting& f1, const FakePosting& f2, uint32_t doc_id_limit, bool unpack)
{
    if (unpack) {
        FakeMatchLoop::or_pair_posting_scan_with_unpack(f1, f2, doc_id_limit);
    } else {
        FakeMatchLoop::or_pair_posting_scan(f1, f2, doc_id_limit);
    }
}

void
StressRunner::run(vespalib::Rand48 &rnd,
                  FakeWordSet &wordSet,
                  const std::vector<std::string> &postingTypes,
                  OperatorType operatorType,
                  uint32_t loops,
                  uint32_t skipCommonPairsRate,
                  uint32_t numTasks,
                  uint32_t stride,
                  bool unpack)
{
    LOG(debug, "StressRunner::run()");
    StressMaster master(rnd,
                        wordSet,
                        postingTypes,
                        operatorType,
                        loops,
                        skipCommonPairsRate,
                        numTasks,
                        stride,
                        unpack);
    master.run();
}

}
