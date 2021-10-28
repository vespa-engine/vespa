// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

int verify_top_k(const TopK &perfect, const TopK &result, uint32_t sk, uint32_t qid) {
    int recall = perfect.recall(result);
    EXPECT_TRUE(recall > 40);
    double sum_error = 0.0;
    double c_factor = 1.0;
    for (size_t i = 0; i < result.K; ++i) {
        double factor = (result.hits[i].distance / perfect.hits[i].distance);
        if (factor < 0.99 || factor > 25) {
            fprintf(stderr, "hit[%zu] got distance %.3f, expected %.3f\n",
                    i, result.hits[i].distance, perfect.hits[i].distance);
        }
        sum_error += factor;
        c_factor = std::max(c_factor, factor);
    }
    EXPECT_TRUE(c_factor < 1.5);
    fprintf(stderr, "quality sk=%u: query %u: recall %d  c2-factor %.3f  avg c2: %.3f\n",
            sk, qid, recall, c_factor, sum_error / result.K);
    return recall;
}

int verify_nns_quality(uint32_t sk, NNS_API &nns, uint32_t qid) {
    TopK perfect = bruteforceResults[qid];
    TopK result = find_with_nns(sk, nns, qid);
    return verify_top_k(perfect, result, sk, qid);
}
