// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <benchmark/benchmark.h>

#include <atomic>
#include <memory>
#include <vector>

/**
 * TaggedPtr - Pointer with tag to avoid ABA problem in lock-free data structures.
 * See http://en.wikipedia.org/wiki/ABA_problem for details.
 */
struct TaggedPtr {
    TaggedPtr() noexcept : _ptr(nullptr), _tag(0) {}
    TaggedPtr(void* ptr, size_t tag) noexcept : _ptr(ptr), _tag(tag) {}
    void*  _ptr;
    size_t _tag;
};

/* Simple node for lock-free linked list  */
class Node {
public:
    Node() noexcept : _next(nullptr) {}
    Node* _next;
};

using AtomicHeadPtr = std::atomic<TaggedPtr>;

/**
 * Atomically link a node into the head of a lock-free stack.
 * Uses compare-and-swap with tagged pointers to avoid ABA problem.
 */
void linkIn(AtomicHeadPtr& head, Node* node) noexcept {
    TaggedPtr oldHead = head.load(std::memory_order_relaxed);
    TaggedPtr newHead(node, oldHead._tag + 1);
    node->_next = static_cast<Node*>(oldHead._ptr);

    // linkIn/linkOut performs a release/acquire pair
    while (!head.compare_exchange_weak(oldHead, newHead, std::memory_order_release, std::memory_order_relaxed)) {
        newHead._tag = oldHead._tag + 1;
        node->_next = static_cast<Node*>(oldHead._ptr);
    }
}

/**
 * Atomically unlink a node from the head of a lock-free stack.
 * Returns nullptr if the stack is empty.
 */
Node* linkOut(AtomicHeadPtr& head) noexcept {
    TaggedPtr oldHead = head.load(std::memory_order_relaxed);
    Node*     node = static_cast<Node*>(oldHead._ptr);

    if (node == nullptr) {
        return nullptr;
    }

    TaggedPtr newHead(node->_next, oldHead._tag + 1);

    // linkIn/linkOut performs a release/acquire pair
    while (!head.compare_exchange_weak(oldHead, newHead, std::memory_order_acquire, std::memory_order_relaxed)) {
        node = static_cast<Node*>(oldHead._ptr);
        if (node == nullptr) {
            return nullptr;
        }
        newHead._ptr = node->_next;
        newHead._tag = oldHead._tag + 1;
    }

    node->_next = nullptr;
    return node;
}

/**
 * Benchmark: Multiple threads pushing nodes (linkIn contention)
 */
static void BM_LinkIn_Contention(benchmark::State& state) {
    AtomicHeadPtr head;
    head.store(TaggedPtr(nullptr, 0));

    // Each thread gets its own nodes to push
    std::vector<Node> nodes(1000);

    for (auto _ : state) {
        for (size_t i = 0; i < nodes.size(); ++i) {
            linkIn(head, &nodes[i]);
        }
    }

    // Report operations per second
    state.SetItemsProcessed(state.iterations() * nodes.size());
}

/**
 * Benchmark: Multiple threads popping nodes (linkOut contention)
 */
static void BM_LinkOut_Contention(benchmark::State& state) {
    AtomicHeadPtr head;
    head.store(TaggedPtr(nullptr, 0));

    // Pre-populate the stack before timing
    std::vector<Node> nodes(100000);
    for (auto& node : nodes) {
        linkIn(head, &node);
    }

    for (auto _ : state) {
        Node* node = linkOut(head);
        if (node == nullptr) {
            // Stack is empty, refill it
            state.PauseTiming();
            for (auto& n : nodes) {
                linkIn(head, &n);
            }
            state.ResumeTiming();
        }
    }

    state.SetItemsProcessed(state.iterations());
}

/**
 * Benchmark: Mixed workload - threads both pushing and popping
 */
static void BM_LinkInOut_Mixed(benchmark::State& state) {
    AtomicHeadPtr head;
    head.store(TaggedPtr(nullptr, 0));

    // Pre-populate with some nodes
    std::vector<Node> nodes(10000);
    for (size_t i = 0; i < nodes.size() / 2; ++i) {
        linkIn(head, &nodes[i]);
    }

    size_t node_idx = nodes.size() / 2;

    for (auto _ : state) {
        // Alternate between push and pop
        if (state.iterations() % 2 == 0) {
            if (node_idx < nodes.size()) {
                linkIn(head, &nodes[node_idx++]);
            }
        } else {
            Node* node = linkOut(head);
            if (node != nullptr) {
                // Could reuse the node
            }
        }
    }

    state.SetItemsProcessed(state.iterations());
}

// Register benchmarks with different thread counts
BENCHMARK(BM_LinkIn_Contention)->ThreadRange(1, 16);
BENCHMARK(BM_LinkOut_Contention)->ThreadRange(1, 16);
BENCHMARK(BM_LinkInOut_Mixed)->ThreadRange(1, 16);

BENCHMARK_MAIN();
