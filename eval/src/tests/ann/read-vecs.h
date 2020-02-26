// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

void read_queries(std::string fn) {
    int fd = open(fn.c_str(), O_RDONLY);
    ASSERT_TRUE(fd > 0);
    int d;
    size_t rv;
    fprintf(stderr, "reading %u queries from %s\n", NUM_Q, fn.c_str());
    for (uint32_t qid = 0; qid < NUM_Q; ++qid) {
        rv = read(fd, &d, 4);
        ASSERT_EQUAL(rv, 4u);
        ASSERT_EQUAL(d, NUM_DIMS);
        rv = read(fd, &generatedQueries[qid].v, NUM_DIMS*sizeof(float));
        ASSERT_EQUAL(rv, sizeof(PointVector));
    }
    close(fd);
}

void read_docs(std::string fn) {
    int fd = open(fn.c_str(), O_RDONLY);
    ASSERT_TRUE(fd > 0);
    int d;
    size_t rv;
    fprintf(stderr, "reading %u doc vectors from %s\n", NUM_DOCS, fn.c_str());
    for (uint32_t docid = 0; docid < NUM_DOCS; ++docid) {
        rv = read(fd, &d, 4);
        ASSERT_EQUAL(rv, 4u);
        ASSERT_EQUAL(d, NUM_DIMS);
        rv = read(fd, &generatedDocs[docid].v, NUM_DIMS*sizeof(float));
        ASSERT_EQUAL(rv, sizeof(PointVector));
    }
    close(fd);
}

void read_data(const std::string& dir, const std::string& data_set) {
    fprintf(stderr, "read data set '%s' from directory '%s'\n", data_set.c_str(), dir.c_str());
    TimePoint bef = std::chrono::steady_clock::now();
    read_queries(dir + "/" + data_set + "_query.fvecs");
    TimePoint aft = std::chrono::steady_clock::now();
    fprintf(stderr, "read queries: %.3f ms\n", to_ms(aft - bef));
    bef = std::chrono::steady_clock::now();
    read_docs(dir + "/" + data_set + "_base.fvecs");
    aft = std::chrono::steady_clock::now();
    fprintf(stderr, "read docs: %.3f ms\n", to_ms(aft - bef));
}
