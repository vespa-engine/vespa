// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

bool reach_with_nns_k(NNS_API &nns, uint32_t docid, uint32_t k) {
    const PointVector &qv = generatedDocs[docid];
    vespalib::ConstArrayRef<float> query(qv.v, NUM_DIMS);
    auto rv = nns.topK(k, query, k);
    if (rv.size() != k) {
        fprintf(stderr, "Result/K=%u from query for %u is %zu hits\n",
                k, docid, rv.size());
        return false;
    }
    if (rv[0].docid != docid) {
      if (rv[0].sq.distance != 0.0)
        fprintf(stderr, "Expected/K=%u to find %u but got %u with sq distance %.3f\n",
                k, docid, rv[0].docid, rv[0].sq.distance);
    }
    return (rv[0].docid == docid || rv[0].sq.distance == 0.0);
}

void quality_nns(NNS_API &nns, std::vector<uint32_t> sk_list) {
    for (uint32_t search_k : sk_list) {
        double sum_recall = 0;
        for (int cnt = 0; cnt < NUM_Q; ++cnt) {
            sum_recall += verify_nns_quality(search_k, nns, cnt);
        }
        fprintf(stderr, "Overall average recall: %.2f\n", sum_recall / NUM_Q);
    }
    for (uint32_t search_k : { 1, 10, 100, 1000 }) {
        TimePoint bef = std::chrono::steady_clock::now();
        uint32_t reached = 0;
        for (uint32_t i = 0; i < NUM_REACH; ++i) {
            uint32_t target = i * (NUM_DOCS / NUM_REACH);
            if (reach_with_nns_k(nns, target, search_k)) ++reached;
        }
        fprintf(stderr, "Could reach %u of %u documents with k=%u\n",
                reached, NUM_REACH, search_k);
        TimePoint aft = std::chrono::steady_clock::now();
        fprintf(stderr, "reach time k=%u: %.3f ms = %.3f ms/q\n",
                search_k, to_ms(aft - bef), to_ms(aft - bef)/NUM_REACH);
        if (reached == NUM_REACH) break;
    }
}
