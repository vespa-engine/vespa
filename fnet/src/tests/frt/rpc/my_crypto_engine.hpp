// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespalib::CryptoEngine::SP my_crypto_engine() {
    const char *env_str = getenv("CRYPTOENGINE");
    if (!env_str) {
        fprintf(stderr, "crypto engine: default\n");
        return vespalib::CryptoEngine::get_default();
    }
    std::string engine(env_str);
    if (engine == "xor") {
        fprintf(stderr, "crypto engine: xor\n");
        return std::make_shared<vespalib::XorCryptoEngine>();
    }
    TEST_FATAL(("invalid crypto engine: " + engine).c_str());
    abort();
}
