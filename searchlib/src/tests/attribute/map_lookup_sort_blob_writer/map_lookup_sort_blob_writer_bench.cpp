// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
// Microbenchmark for the map lookup sort blob writer (layer 1 of the
// performance-test seam in agents/research/sort-map-value.md).
//
// Reports nanoseconds per document for the complete writer next to an ordinary
// scalar sort blob writer of the *same* value type, so the incremental cost of
// the key lookup is what is visible. Also counts allocations inside the
// per-document loop.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/make_sort_blob_writer.h>
#include <vespa/searchlib/attribute/map_lookup_sort_blob_writer.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/vespalib/util/benchmark_timer.h>

#include <algorithm>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <new>
#include <numeric>
#include <random>
#include <string>
#include <vector>

using search::AttributeFactory;
using search::AttributeVector;
using search::FloatingPointAttribute;
using search::IntegerAttribute;
using search::StringAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::ISortBlobWriter;
using search::attribute::make_map_lookup_sort_blob_writer;
using search::attribute::make_sort_blob_writer;
using search::common::FieldSortSpec;
using search::common::sortspec::MissingPolicy;
using search::common::sortspec::SortOrder;
using vespalib::BenchmarkTimer;

namespace {

std::atomic<size_t> allocation_count{0};

} // namespace

// Counts every heap allocation so the per-document loop can be asserted to be allocation free.
void* operator new(size_t size) {
    allocation_count.fetch_add(1, std::memory_order_relaxed);
    void* p = std::malloc(size == 0 ? 1 : size);
    if (p == nullptr) {
        throw std::bad_alloc();
    }
    return p;
}

void* operator new[](size_t size) {
    return ::operator new(size);
}

void operator delete(void* p) noexcept {
    std::free(p);
}
void operator delete[](void* p) noexcept {
    std::free(p);
}
void operator delete(void* p, size_t) noexcept {
    std::free(p);
}
void operator delete[](void* p, size_t) noexcept {
    std::free(p);
}

namespace {

constexpr uint32_t    num_docs = 50000; // benchmarked docids are 1 .. num_docs-1
constexpr const char* absent_key = "key_present_in_corpus_but_not_in_these_documents";
constexpr const char* unknown_key = "key_not_in_the_corpus_at_all";
constexpr double      budget_s = 0.25;

std::string map_key(uint32_t i) {
    char buf[32];
    snprintf(buf, sizeof(buf), "map_key_%06u", i);
    return buf;
}

std::string value_string(std::mt19937& rnd, size_t len) {
    static const char alphabet[] = "abcdefghijklmnopqrstuvwxyz";
    std::string       s;
    s.reserve(len);
    // Shared prefix so comparisons do not all resolve on the first byte.
    for (size_t i = 0; i < len; ++i) {
        s.push_back((i < len / 2) ? 'a' : alphabet[rnd() % 26]);
    }
    return s;
}

std::shared_ptr<AttributeVector> new_attribute(const std::string& name, BasicType basic_type,
                                                CollectionType collection_type, bool fast_search) {
    Config cfg(basic_type, collection_type);
    cfg.setFastSearch(fast_search);
    auto attr = AttributeFactory::createAttribute(name, cfg);
    attr->addReservedDoc();
    while (attr->getNumDocs() <= num_docs) {
        uint32_t docid = 0;
        attr->addDoc(docid);
    }
    return attr;
}

// Keys map_key(0) .. map_key(m-1) in every document. The last document also has
// absent_key so it is in the global enum dictionary without being in any
// benchmarked document.
std::shared_ptr<AttributeVector> build_keys(uint32_t m) {
    auto  attr = new_attribute("m.key", BasicType::STRING, CollectionType::ARRAY, false);
    auto& sa = dynamic_cast<StringAttribute&>(*attr);
    for (uint32_t docid = 1; docid <= num_docs; ++docid) {
        for (uint32_t i = 0; i < m; ++i) {
            sa.append(docid, map_key(i), 1);
        }
    }
    sa.append(num_docs, absent_key, 1);
    attr->commit();
    return attr;
}

std::shared_ptr<AttributeVector> build_int_values(uint32_t m, bool fast_search) {
    auto         attr = new_attribute("m.value", BasicType::INT64, CollectionType::ARRAY, fast_search);
    auto&        ia = dynamic_cast<IntegerAttribute&>(*attr);
    std::mt19937 rnd(42);
    for (uint32_t docid = 1; docid <= num_docs; ++docid) {
        for (uint32_t i = 0; i < m; ++i) {
            ia.append(docid, static_cast<int64_t>(rnd()), 1);
        }
    }
    attr->commit();
    return attr;
}

std::shared_ptr<AttributeVector> build_double_values(uint32_t m) {
    auto         attr = new_attribute("m.value", BasicType::DOUBLE, CollectionType::ARRAY, false);
    auto&        fa = dynamic_cast<FloatingPointAttribute&>(*attr);
    std::mt19937 rnd(42);
    for (uint32_t docid = 1; docid <= num_docs; ++docid) {
        for (uint32_t i = 0; i < m; ++i) {
            fa.append(docid, rnd() / 1000.0, 1);
        }
    }
    attr->commit();
    return attr;
}

std::shared_ptr<AttributeVector> build_string_values(uint32_t m, size_t len) {
    auto         attr = new_attribute("m.value", BasicType::STRING, CollectionType::ARRAY, false);
    auto&        sa = dynamic_cast<StringAttribute&>(*attr);
    std::mt19937 rnd(42);
    for (uint32_t docid = 1; docid <= num_docs; ++docid) {
        for (uint32_t i = 0; i < m; ++i) {
            sa.append(docid, value_string(rnd, len), 1);
        }
    }
    attr->commit();
    return attr;
}

std::shared_ptr<AttributeVector> build_single_int_values() {
    auto         attr = new_attribute("scalar", BasicType::INT64, CollectionType::SINGLE, false);
    auto&        ia = dynamic_cast<IntegerAttribute&>(*attr);
    std::mt19937 rnd(42);
    for (uint32_t docid = 1; docid <= num_docs; ++docid) {
        ia.update(docid, static_cast<int64_t>(rnd()));
    }
    attr->commit();
    return attr;
}

std::shared_ptr<AttributeVector> build_single_double_values() {
    auto         attr = new_attribute("scalar", BasicType::DOUBLE, CollectionType::SINGLE, false);
    auto&        fa = dynamic_cast<FloatingPointAttribute&>(*attr);
    std::mt19937 rnd(42);
    for (uint32_t docid = 1; docid <= num_docs; ++docid) {
        fa.update(docid, rnd() / 1000.0);
    }
    attr->commit();
    return attr;
}

std::shared_ptr<AttributeVector> build_single_string_values(size_t len) {
    auto         attr = new_attribute("scalar", BasicType::STRING, CollectionType::SINGLE, false);
    auto&        sa = dynamic_cast<StringAttribute&>(*attr);
    std::mt19937 rnd(42);
    for (uint32_t docid = 1; docid <= num_docs; ++docid) {
        sa.update(docid, value_string(rnd, len));
    }
    attr->commit();
    return attr;
}

FieldSortSpec map_spec(const std::string& key) {
    return FieldSortSpec("m", SortOrder::ASCENDING, {}, MissingPolicy::DEFAULT, "", false, key);
}

FieldSortSpec scalar_spec() {
    return FieldSortSpec("scalar", SortOrder::ASCENDING, {}, MissingPolicy::DEFAULT, "");
}

std::vector<uint32_t> docid_order(bool shuffled) {
    std::vector<uint32_t> ids(num_docs - 1);
    std::iota(ids.begin(), ids.end(), 1u);
    if (shuffled) {
        std::mt19937 rnd(7);
        std::shuffle(ids.begin(), ids.end(), rnd);
    }
    return ids;
}

struct Result {
    double ns_per_doc;
    size_t allocations;
};

Result run(ISortBlobWriter& writer, const std::vector<uint32_t>& ids, std::vector<unsigned char>& buf) {
    // Warm up: grow the buffer until every document fits, and let any read view
    // scratch vector reach its steady state. First touch growth is deliberately
    // outside the counted region.
    for (auto docid : ids) {
        while (writer.write(docid, buf.data(), buf.size()) < 0) {
            buf.resize(buf.size() * 2 + 16);
        }
    }
    // A hand rolled timing loop rather than BenchmarkTimer::benchmark(), whose
    // std::function wrapper allocates and would hide the loop's own behaviour.
    BenchmarkTimer timer(budget_s);
    size_t         before = allocation_count.load(std::memory_order_relaxed);
    while (timer.has_budget()) {
        timer.before();
        for (auto docid : ids) {
            writer.write(docid, buf.data(), buf.size());
        }
        timer.after();
    }
    size_t after = allocation_count.load(std::memory_order_relaxed);
    return {timer.min_time() * 1e9 / ids.size(), after - before};
}

void report(const char* value_type, uint32_t m, const char* key_case, const char* order, const Result& r) {
    printf("%-8s %5u %-9s %-10s %9.2f %10zu\n", value_type, m, key_case, order, r.ns_per_doc, r.allocations);
    fflush(stdout);
}

struct KeyCase {
    const char* name;
    std::string key;
};

std::vector<KeyCase> key_cases(uint32_t m) {
    return {{"first", map_key(0)},
            {"middle", map_key(m / 2)},
            {"last", map_key(m - 1)},
            {"absent", absent_key},
            {"unknown", unknown_key}};
}

void bench_type(const char* value_type, const std::shared_ptr<AttributeVector>& keys,
                const std::shared_ptr<AttributeVector>& values, uint32_t m) {
    std::vector<unsigned char> buf(64);
    for (bool shuffled : {false, true}) {
        auto ids = docid_order(shuffled);
        for (const auto& kc : key_cases(m)) {
            auto writer = make_map_lookup_sort_blob_writer(*keys, *values, map_spec(kc.key));
            if (!writer) {
                printf("FAILED to create writer for %s\n", value_type);
                return;
            }
            report(value_type, m, kc.name, shuffled ? "shuffled" : "seq", run(*writer, ids, buf));
        }
    }
}

void bench_scalar(const char* value_type, const std::shared_ptr<AttributeVector>& values) {
    std::vector<unsigned char> buf(64);
    for (bool shuffled : {false, true}) {
        auto ids = docid_order(shuffled);
        auto writer = make_sort_blob_writer(values.get(), scalar_spec());
        if (!writer) {
            printf("FAILED to create scalar writer for %s\n", value_type);
            return;
        }
        report(value_type, 0, "scalar", shuffled ? "shuffled" : "seq", run(*writer, ids, buf));
    }
}

} // namespace

int main(int, char**) {
    printf("map lookup sort blob writer benchmark, %u documents per pass\n", num_docs - 1);
    printf("%-8s %5s %-9s %-10s %9s %10s\n", "type", "M", "key", "order", "ns/doc", "allocs");

    // Same-type scalar baselines. These are the floor each map row should be read against.
    bench_scalar("int64", build_single_int_values());
    bench_scalar("double", build_single_double_values());
    bench_scalar("string8", build_single_string_values(8));
    bench_scalar("string32", build_single_string_values(32));

    for (uint32_t m : {1u, 4u, 16u, 64u, 256u}) {
        auto keys = build_keys(m);
        bench_type("int64", keys, build_int_values(m, false), m);
        bench_type("double", keys, build_double_values(m), m);
        bench_type("string8", keys, build_string_values(m, 8), m);
    }

    // Selected string length sweep at a fixed map cardinality. The initial sort
    // buffer estimate in FastS_SortSpec::initSortData is 11 bytes for strings.
    {
        constexpr uint32_t m = 16;
        auto               keys = build_keys(m);
        for (size_t len : {4u, 11u, 32u, 128u}) {
            char name[32];
            snprintf(name, sizeof(name), "string%zu", len);
            bench_type(name, keys, build_string_values(m, len), m);
        }
    }

    // fast-search numeric value attribute: its typed read view materializes all
    // M values into a scratch vector before one is selected.
    for (uint32_t m : {4u, 64u}) {
        auto keys = build_keys(m);
        bench_type("i64-fs", keys, build_int_values(m, true), m);
    }
    return 0;
}
