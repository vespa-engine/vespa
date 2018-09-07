// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>

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
    } else if (engine == "tls") {
        fprintf(stderr, "crypto engine: tls\n");
        return std::make_shared<vespalib::TlsCryptoEngine>(vespalib::test::make_tls_options_for_testing());
    }
    TEST_FATAL(("invalid crypto engine: " + engine).c_str());
    abort();
}
