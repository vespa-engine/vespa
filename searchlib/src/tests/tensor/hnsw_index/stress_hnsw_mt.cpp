// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <fcntl.h>
#include <cstdio>
#include <unistd.h>
#include <chrono>
#include <cstdlib>
#include <future>
#include <vector>

#include <vespa/eval/eval/typed_cells.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/tensor/distance_functions.h>
#include <vespa/searchlib/tensor/doc_vector_access.h>
#include <vespa/searchlib/tensor/hnsw_index.h>
#include <vespa/searchlib/tensor/inv_log_level_generator.h>
#include <vespa/searchlib/tensor/random_level_generator.h>
#include <vespa/searchlib/tensor/vector_bundle.h>
#include <vespa/vespalib/data/input.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/data/simple_buffer.h>

#include <vespa/log/log.h>
LOG_SETUP("stress_hnsw_mt");

using namespace search::tensor;
using namespace vespalib::slime;
using search::BitVector;
using vespalib::eval::CellType;
using vespalib::eval::ValueType;
using vespalib::GenerationHandler;
using vespalib::MemoryUsage;
using vespalib::Slime;

#define NUM_DIMS 128
#define NUM_POSSIBLE_V 1000000
#define NUM_POSSIBLE_DOCS 30000
#define NUM_OPS 1000000

namespace {

SubspaceType subspace_type(ValueType::make_type(CellType::FLOAT, {{"dims", NUM_DIMS }}));

}

class RndGen {
private:
    std::mt19937_64 urng;
    std::uniform_real_distribution<double> uf;
public:
    RndGen() : urng(0x1234deadbeef5678uLL), uf(0.0, 1.0) {}

    double nextUniform() {
        return uf(urng);
    }
};

using ConstVectorRef = vespalib::ConstArrayRef<float>;

struct MallocPointVector {
    float v[NUM_DIMS];
    operator ConstVectorRef() const { return ConstVectorRef(v, NUM_DIMS); }
};
static MallocPointVector *aligned_alloc_pv(size_t num) {
    size_t num_bytes = num * sizeof(MallocPointVector);
    double mega_bytes = num_bytes / double(1_Mi);
    fprintf(stderr, "allocate %.2f MB of vectors\n", mega_bytes);
    char *mem = (char *)malloc(num_bytes + 512);
    mem += 512;
    size_t val = (size_t)mem;
    size_t unalign = val % 512;
    mem -= unalign;
    return reinterpret_cast<MallocPointVector *>(mem);
}

void read_vector_file(MallocPointVector *p) {
    std::string data_set = "sift";
    std::string data_dir = ".";
    char *home = getenv("HOME");
    if (home) {
        data_dir = home;
        data_dir += "/" + data_set;
    }
    std::string fn = data_dir + "/" + data_set + "_base.fvecs";
    int fd = open(fn.c_str(), O_RDONLY);
    if (fd < 0) {
        perror(fn.c_str());
        std::_Exit(1);
    }
    int d;
    size_t rv;
    fprintf(stderr, "reading %u vectors from %s\n", NUM_POSSIBLE_V, fn.c_str());
    for (uint32_t i = 0; i < NUM_POSSIBLE_V; ++i) {
        rv = read(fd, &d, 4);
        ASSERT_EQ(rv, 4u);
        ASSERT_EQ(d, NUM_DIMS);
        rv = read(fd, &p[i].v, NUM_DIMS*sizeof(float));
        ASSERT_EQ(rv, sizeof(MallocPointVector));
    }
    close(fd);
    fprintf(stderr, "reading %u vectors OK\n", NUM_POSSIBLE_V);
}

class MyDocVectorStore : public DocVectorAccess {
private:
    MallocPointVector *_vectors;
public:
    MyDocVectorStore() {
        _vectors = aligned_alloc_pv(NUM_POSSIBLE_DOCS);
    }
    MyDocVectorStore& set(uint32_t docid, ConstVectorRef vec) {
        assert(docid < NUM_POSSIBLE_DOCS);
        memcpy(&_vectors[docid], vec.cbegin(), sizeof(MallocPointVector));
        return *this;
    }
    vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const override {
        assert(docid < NUM_POSSIBLE_DOCS);
        (void) subspace;
        ConstVectorRef ref(_vectors[docid]);
        return vespalib::eval::TypedCells(ref);
    }
    VectorBundle get_vectors(uint32_t docid) const override {
        assert(docid < NUM_POSSIBLE_DOCS);
        ConstVectorRef ref(_vectors[docid]);
        assert(subspace_type.size() == ref.size());
        return VectorBundle(ref.data(), 1, subspace_type);
    }
};

using FloatSqEuclideanDistance = SquaredEuclideanDistanceHW<float>;

template <typename IndexType>
class Stressor : public ::testing::Test {
private:
    struct LoadedVectors {
        MallocPointVector *pv_storage;
        void load() {
            pv_storage = aligned_alloc_pv(size());
            read_vector_file(pv_storage);
        }
        size_t size() const { return NUM_POSSIBLE_V; }
        vespalib::ConstArrayRef<float> operator[] (size_t i) {
            return pv_storage[i];
        }
    } loaded_vectors;
public:
    BitVector::UP in_progress;
    std::mutex in_progress_lock;
    BitVector::UP existing_ids;
    RndGen rng;
    MyDocVectorStore vectors;
    GenerationHandler gen_handler;
    std::unique_ptr<IndexType> index;
    vespalib::BlockingThreadStackExecutor multi_prepare_workers;
    vespalib::BlockingThreadStackExecutor write_thread;

    using PrepUP = std::unique_ptr<PrepareResult>;
    using ReadGuard = GenerationHandler::Guard;
    using PrepareFuture = std::future<PrepUP>;

    // union of data required by tasks
    struct TaskBase : vespalib::Executor::Task {
        Stressor &parent;
        uint32_t docid;
        ConstVectorRef vec;
        PrepareFuture prepare_future;
        ReadGuard read_guard;

        TaskBase(Stressor &p, uint32_t d, ConstVectorRef v, PrepareFuture f, ReadGuard g)
            : parent(p), docid(d), vec(v), prepare_future(std::move(f)), read_guard(g)
        {}
        TaskBase(Stressor &p, uint32_t d, ConstVectorRef v, ReadGuard g) // prepare add
            : TaskBase(p, d, v, PrepareFuture(), g) {}
        TaskBase(Stressor &p, uint32_t d, ConstVectorRef v, PrepareFuture r) // complete add+update
            : TaskBase(p, d, v, std::move(r), ReadGuard()) {}
        TaskBase(Stressor &p, uint32_t d) // complete remove
            : TaskBase(p, d, ConstVectorRef(), PrepareFuture(), ReadGuard()) {}

        ~TaskBase() {}
    };

    struct PrepareAddTask  : TaskBase {
        using TaskBase::TaskBase;
        using TaskBase::docid;
        using TaskBase::parent;
        using TaskBase::read_guard;
        using TaskBase::vec;
        std::promise<PrepUP> result_promise;
        auto get_result_future() {
            return result_promise.get_future();
        }
        void run() override {
            assert(subspace_type.size() == vec.size());
            VectorBundle v(vec.data(), 1, subspace_type);
            auto up = parent.index->prepare_add_document(docid, v, read_guard);
            result_promise.set_value(std::move(up));
        }
    };

    struct CompleteAddTask : TaskBase {
        using TaskBase::TaskBase;
        using TaskBase::docid;
        using TaskBase::parent;
        using TaskBase::prepare_future;
        using TaskBase::vec;
        void run() override {
            auto prepare_result = prepare_future.get();
            parent.vectors.set(docid, vec);
            parent.index->complete_add_document(docid, std::move(prepare_result));
            parent.existing_ids->setBit(docid);
            parent.commit(docid);
        }
    };

    struct CompleteRemoveTask : TaskBase {
        using TaskBase::TaskBase;
        using TaskBase::docid;
        using TaskBase::parent;
        void run() override {
            parent.index->remove_document(docid);
            parent.existing_ids->clearBit(docid);
            parent.commit(docid);
        }
    };

    struct CompleteUpdateTask : TaskBase {
        using TaskBase::TaskBase;
        using TaskBase::docid;
        using TaskBase::parent;
        using TaskBase::prepare_future;
        using TaskBase::vec;
        void run() override {
            auto prepare_result = prepare_future.get();
            parent.index->remove_document(docid);
            parent.vectors.set(docid, vec);
            parent.index->complete_add_document(docid, std::move(prepare_result));
            EXPECT_EQ(parent.existing_ids->testBit(docid), true);
            parent.commit(docid);
        }
    };

    Stressor()
        : loaded_vectors(),
          in_progress(BitVector::create(NUM_POSSIBLE_DOCS)),
          existing_ids(BitVector::create(NUM_POSSIBLE_DOCS)),
          rng(),
          vectors(),
          gen_handler(),
          index(),
          multi_prepare_workers(10, 50),
          write_thread(1, 500)
    {
        loaded_vectors.load();
    }

    ~Stressor() {}

    auto dff() {
        return search::tensor::make_distance_function_factory(
                search::attribute::DistanceMetric::Euclidean,
                vespalib::eval::CellType::FLOAT);
    }

    void init() {
        uint32_t m = 16;
        index = std::make_unique<IndexType>(vectors, dff(),
                                            std::make_unique<InvLogLevelGenerator>(m),
                                            HnswIndexConfig(2*m, m, 200, 10, true));
    }
    size_t get_rnd(size_t size) {
        return rng.nextUniform() * size;
    }
    void add_document(uint32_t docid) {
        size_t vec_num = get_rnd(loaded_vectors.size());
        ConstVectorRef vec = loaded_vectors[vec_num];
        auto guard = take_read_guard();
        auto prepare_task = std::make_unique<PrepareAddTask>(*this, docid, vec, guard);
        auto complete_task = std::make_unique<CompleteAddTask>(*this, docid, vec, prepare_task->get_result_future());
        auto r = multi_prepare_workers.execute(std::move(prepare_task));
        ASSERT_EQ(r.get(), nullptr);
        r = write_thread.execute(std::move(complete_task));
        ASSERT_EQ(r.get(), nullptr);
    }
    void remove_document(uint32_t docid) {
        auto task = std::make_unique<CompleteRemoveTask>(*this, docid);
        auto r = write_thread.execute(std::move(task));
        ASSERT_EQ(r.get(), nullptr);
    }
    void update_document(uint32_t docid) {
        size_t vec_num = get_rnd(loaded_vectors.size());
        ConstVectorRef vec = loaded_vectors[vec_num];
        auto guard = take_read_guard();
        auto prepare_task = std::make_unique<PrepareAddTask>(*this, docid, vec, guard);
        auto complete_task = std::make_unique<CompleteUpdateTask>(*this, docid, vec, prepare_task->get_result_future());
        auto r = multi_prepare_workers.execute(std::move(prepare_task));
        ASSERT_EQ(r.get(), nullptr);
        r = write_thread.execute(std::move(complete_task));
        ASSERT_EQ(r.get(), nullptr);
    }
    void commit(uint32_t docid) {
        index->assign_generation(gen_handler.getCurrentGeneration());
        gen_handler.incGeneration();
        index->reclaim_memory(gen_handler.get_oldest_used_generation());
        std::lock_guard<std::mutex> guard(in_progress_lock);
        in_progress->clearBit(docid);
        // printf("commit: %u\n", docid);
    }
    void gen_operation() {
        uint32_t docid = get_rnd(NUM_POSSIBLE_DOCS);
        {
            std::lock_guard<std::mutex> guard(in_progress_lock);
            while (in_progress->testBit(docid)) {
                docid = get_rnd(NUM_POSSIBLE_DOCS);
            }
            in_progress->setBit(docid);
        }
        if (existing_ids->testBit(docid)) {
            if (get_rnd(100) < 70) {
                // printf("start remove op: %u\n", docid);
                remove_document(docid);
            } else {
                // printf("start update op: %u\n", docid);
                update_document(docid);
            }
        } else {
            // printf("start add op: %u\n", docid);
            add_document(docid);
        }
    }
    GenerationHandler::Guard take_read_guard() {
        return gen_handler.takeGuard();
    }
    MemoryUsage memory_usage() const {
        return index->memory_usage();
    }
    uint32_t count_in_progress() {
        std::lock_guard<std::mutex> guard(in_progress_lock);
        in_progress->invalidateCachedCount();
        return in_progress->countTrueBits();
    }
    std::string json_state() {
        Slime actualSlime;
        SlimeInserter inserter(actualSlime);
        index->get_state(inserter);
        vespalib::SimpleBuffer buf;
        vespalib::slime::JsonFormat::encode(actualSlime, buf, false);
        return buf.get().make_string();
    }
};

using StressorTypes = ::testing::Types<HnswIndex<HnswIndexType::SINGLE>>;

TYPED_TEST_SUITE(Stressor, StressorTypes);

TYPED_TEST(Stressor, stress)
{
    this->init();
    for (int i = 0; i < NUM_OPS; ++i) {
        this->gen_operation();
        if (i % 1000 == 0) {
            uint32_t cnt = this->count_in_progress();
            fprintf(stderr, "generating operations %d / %d; in progress: %u ops\n",
                    i, NUM_OPS, cnt);
            auto r = this->write_thread.execute(vespalib::makeLambdaTask([&]() {
                        EXPECT_TRUE(this->index->check_link_symmetry());
            }));
            EXPECT_EQ(r.get(), nullptr);
        }
    }
    fprintf(stderr, "waiting for queued operations...\n");
    this->multi_prepare_workers.sync();
    this->write_thread.sync();
    EXPECT_EQ(this->count_in_progress(), 0);
    EXPECT_TRUE(this->index->check_link_symmetry());
    fprintf(stderr, "HNSW index state after test:\n%s\n", this->json_state().c_str());
    this->existing_ids->invalidateCachedCount();
    fprintf(stderr, "Expected valid nodes: %u\n", this->existing_ids->countTrueBits());
    fprintf(stderr, "all done.\n");
}

GTEST_MAIN_RUN_ALL_TESTS()
