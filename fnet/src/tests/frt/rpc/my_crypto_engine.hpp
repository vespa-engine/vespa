// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/maybe_tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <vespa/vespalib/test/test_path.h>

vespalib::CryptoEngine::SP my_crypto_engine() {
    const char* env_str = getenv("CRYPTOENGINE");
    if (!env_str) {
        fprintf(stderr, "crypto engine: null\n");
        return std::make_shared<vespalib::NullCryptoEngine>();
    }
    std::string engine(env_str);
    if (engine == "tls") {
        fprintf(stderr, "crypto engine: tls\n");
        return std::make_shared<vespalib::TlsCryptoEngine>(
            vespalib::test::make_telemetry_only_capability_tls_options_for_testing());
    } else if (engine == "tls_maybe_yes") {
        fprintf(stderr, "crypto engine: tls client, mixed server\n");
        auto tls = std::make_shared<vespalib::TlsCryptoEngine>(
            vespalib::test::make_telemetry_only_capability_tls_options_for_testing());
        return std::make_shared<vespalib::MaybeTlsCryptoEngine>(std::move(tls), true);
    } else if (engine == "tls_maybe_no") {
        fprintf(stderr, "crypto engine: null client, mixed server\n");
        auto tls = std::make_shared<vespalib::TlsCryptoEngine>(
            vespalib::test::make_telemetry_only_capability_tls_options_for_testing());
        return std::make_shared<vespalib::MaybeTlsCryptoEngine>(std::move(tls), false);
    }
    ADD_FAILURE() << "invalid crypto engine: " << engine;
    abort();
}
