// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nns.h"
#include <iostream>
#include "/git/hnswlib/hnswlib/hnswlib.h"

class HnswWrapNns : public NNS<float>
{
private:
    using Implementation = hnswlib::HierarchicalNSW<float>;
    hnswlib::L2Space _l2space;
    Implementation _hnsw;

public:
    HnswWrapNns(uint32_t numDims, const DocVectorAccess<float> &dva)
        : NNS(numDims, dva),
          _l2space(numDims),
          _hnsw(&_l2space, 2500000, 16, 200)
    {
    }

    ~HnswWrapNns() {}

    void addDoc(uint32_t docid) override {
        Vector vector = _dva.get(docid);
        _hnsw.addPoint(vector.cbegin(), docid);
    }

    void removeDoc(uint32_t docid) override {
        _hnsw.markDelete(docid);
    }

    std::vector<NnsHit> topK(uint32_t k, Vector vector, uint32_t search_k) override {
        std::vector<NnsHit> reversed;
        _hnsw.setEf(search_k);
        auto priQ = _hnsw.searchKnn(vector.cbegin(), k);
        while (! priQ.empty()) {
            auto pair = priQ.top();
            reversed.emplace_back(pair.second, SqDist(pair.first));
            priQ.pop();
        }
        std::vector<NnsHit> result;
        while (result.size() < k && !reversed.empty()) {
            result.push_back(reversed.back());
            reversed.pop_back();
        }
        return result;
    }

    std::vector<NnsHit> topKfilter(uint32_t k, Vector vector, uint32_t search_k, const BitVector &skipDocIds) override {
        std::vector<NnsHit> reversed;
        uint32_t adjusted_k = k+4;
        uint32_t adjusted_sk = search_k+4;
        for (int retry = 0; (retry < 5) && (reversed.size() < k); ++retry) {
            reversed.clear();
            _hnsw.setEf(adjusted_sk);
            auto priQ = _hnsw.searchKnn(vector.cbegin(), adjusted_k);
            while (! priQ.empty()) {
                auto pair = priQ.top();
                if (! skipDocIds.isSet(pair.second)) {
                    reversed.emplace_back(pair.second, SqDist(pair.first));
                }
                priQ.pop();
            }
            double got = 1 + reversed.size();
            double factor = 1.25 * k / got;
            adjusted_k *= factor;
            adjusted_sk *= factor;
        }
        std::vector<NnsHit> result;
        while (result.size() < k && !reversed.empty()) {
            result.push_back(reversed.back());
            reversed.pop_back();
        }
        return result;
    }
};

std::unique_ptr<NNS<float>>
make_hnsw_wrap(uint32_t numDims, const DocVectorAccess<float> &dva)
{
    NNS<float> *p = new HnswWrapNns(numDims, dva);
    return std::unique_ptr<NNS<float>>(p);
}
