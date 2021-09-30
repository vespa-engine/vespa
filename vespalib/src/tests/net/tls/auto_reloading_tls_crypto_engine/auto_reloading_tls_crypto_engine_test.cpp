// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/net/tls/auto_reloading_tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/statistics.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/transport_security_options_reading.h>
#include <vespa/vespalib/net/tls/impl/openssl_tls_context_impl.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <openssl/ssl.h>

using namespace vespalib;
using namespace vespalib::net::tls;
using namespace std::chrono_literals;

/*

Keys and certificates used for these tests generated with commands:

openssl ecparam -name prime256v1 -genkey -noout -out ca.key

# test_ca.pem:
openssl req -new -x509 -nodes -key ca.key \
    -sha256 -out test_ca.pem \
    -subj '/C=US/L=LooneyVille/O=ACME/OU=ACME test CA/CN=acme.example.com' \
    -days 10000

openssl ecparam -name prime256v1 -genkey -noout -out test_key.pem

openssl req -new -key test_key.pem -out host1.csr \
    -subj '/C=US/L=LooneyVille/O=Wile. E. Coyote, Ltd./CN=wile.example.com' \
    -sha256

# cert1_pem:
openssl x509 -req -in host1.csr \
    -CA ca.pem \
    -CAkey ca.key \
    -CAcreateserial \
    -out cert1.pem \
    -days 10000 \
    -sha256

openssl req -new -key test_key.pem -out host2.csr \
    -subj '/C=US/L=LooneyVille/O=Wile. E. Coyote, Ltd./CN=wile.example.com' \
    -sha256

# cert2_pem:
openssl x509 -req -in host2.csr \
    -CA ca.pem \
    -CAkey ca.key \
    -CAcreateserial \
    -out cert2.pem \
    -days 10000 \
    -sha256

*/

constexpr const char* cert1_pem = R"(-----BEGIN CERTIFICATE-----
MIIBszCCAVgCCQCXsYrXQWS0bzAKBggqhkjOPQQDAjBkMQswCQYDVQQGEwJVUzEU
MBIGA1UEBwwLTG9vbmV5VmlsbGUxDTALBgNVBAoMBEFDTUUxFTATBgNVBAsMDEFD
TUUgdGVzdCBDQTEZMBcGA1UEAwwQYWNtZS5leGFtcGxlLmNvbTAeFw0xODExMzAx
NDA0MzdaFw00NjA0MTcxNDA0MzdaMF4xCzAJBgNVBAYTAlVTMRQwEgYDVQQHDAtM
b29uZXlWaWxsZTEeMBwGA1UECgwVV2lsZS4gRS4gQ295b3RlLCBMdGQuMRkwFwYD
VQQDDBB3aWxlLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE
cQN3UOKg30+h1EYgAxQukAYgzbx7VmcrOBheD7AaJoTUnaRn9xQ6j0t4eKNa6x/1
K7luNL+AfaJiCQLrbalVoDAKBggqhkjOPQQDAgNJADBGAiEAyzvCt9qJCtY/7Qi1
2Jzb1BTvAPOszeBFRzovMatQSUICIQDuT6cyV3yigoxLZbn5In3Sx+qUPFPCMI8O
X5yKMXNkmQ==
-----END CERTIFICATE-----)";

constexpr const char* cert2_pem = R"(-----BEGIN CERTIFICATE-----
MIIBsjCCAVgCCQCXsYrXQWS0cDAKBggqhkjOPQQDAjBkMQswCQYDVQQGEwJVUzEU
MBIGA1UEBwwLTG9vbmV5VmlsbGUxDTALBgNVBAoMBEFDTUUxFTATBgNVBAsMDEFD
TUUgdGVzdCBDQTEZMBcGA1UEAwwQYWNtZS5leGFtcGxlLmNvbTAeFw0xODExMzAx
NDA0MzdaFw00NjA0MTcxNDA0MzdaMF4xCzAJBgNVBAYTAlVTMRQwEgYDVQQHDAtM
b29uZXlWaWxsZTEeMBwGA1UECgwVV2lsZS4gRS4gQ295b3RlLCBMdGQuMRkwFwYD
VQQDDBB3aWxlLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE
cQN3UOKg30+h1EYgAxQukAYgzbx7VmcrOBheD7AaJoTUnaRn9xQ6j0t4eKNa6x/1
K7luNL+AfaJiCQLrbalVoDAKBggqhkjOPQQDAgNIADBFAiEAluT52NkVdGBRZJxo
PhL9XBnJJfzvG5GKXIK/iZgFuYkCIFLp+SIQ5Nc1+NzrU2ii/mkzCgC4N/nOWu9H
88OP2wnm
-----END CERTIFICATE-----)";

void write_file(vespalib::stringref path, vespalib::stringref data) {
    File f(path);
    f.open(File::CREATE | File::TRUNC);
    f.write(data.data(), data.size(), 0);
}

struct Fixture {
    std::unique_ptr<AutoReloadingTlsCryptoEngine> engine;
    explicit Fixture(AutoReloadingTlsCryptoEngine::TimeInterval reload_interval,
                     AuthorizationMode mode = AuthorizationMode::Enforce) {
        write_file("test_cert.pem", cert1_pem);
        // Must be done after file has been written
        engine = std::make_unique<AutoReloadingTlsCryptoEngine>("test_config.json", mode, reload_interval);
    }

    ~Fixture() {
        engine.reset();
        if (fileExists("test_cert.pem")) {
            unlink("test_cert.pem"); // just crash the test if this throws
        }
    }

    vespalib::string current_cert_chain() const {
        return engine->acquire_current_engine()->tls_context()->transport_security_options().cert_chain_pem();
    }

    AuthorizationMode current_authorization_mode() const {
        return engine->acquire_current_engine()->tls_context()->authorization_mode();
    }
};

TEST_FF("Config reloading transitively loads updated files", Fixture(50ms), TimeBomb(60)) {
    auto current_certs = f1.current_cert_chain();
    ASSERT_EQUAL(cert1_pem, current_certs);

    write_file("test_cert.pem.tmp", cert2_pem);
    rename("test_cert.pem.tmp", "test_cert.pem", false, false); // We expect this to be an atomic rename under the hood

    current_certs = f1.current_cert_chain();
    while (current_certs != cert2_pem) {
        std::this_thread::sleep_for(10ms);
        current_certs = f1.current_cert_chain();
    }
    // If the config is never reloaded, test will go boom.
}

TEST_FF("Shutting down auto-reloading engine immediately stops background thread", Fixture(600s), TimeBomb(60)) {
    // This passes just from not having the TimeBomb blow up.
}

TEST_FF("Authorization mode is propagated to engine", Fixture(50ms, AuthorizationMode::LogOnly), TimeBomb(60)) {
    EXPECT_EQUAL(AuthorizationMode::LogOnly, f1.current_authorization_mode());
}

TEST_FF("Config reload failure increments failure statistic", Fixture(50ms), TimeBomb(60)) {
    auto before = ConfigStatistics::get().snapshot();

    write_file("test_cert.pem.tmp", "Broken file oh no :(");
    rename("test_cert.pem.tmp", "test_cert.pem", false, false);

    while (ConfigStatistics::get().snapshot().subtract(before).failed_config_reloads == 0) {
        std::this_thread::sleep_for(10ms);
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
