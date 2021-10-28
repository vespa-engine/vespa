// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/crypto/private_key.h>
#include <vespa/vespalib/crypto/x509_certificate.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

using namespace ::testing;

namespace vespalib::crypto {

// FIXME these tests are very high level and simple since the current crypto utility API we provide
// is extremely simple and does not support loading PEMs, signing or verifying.

TEST(CryptoTest, generated_p256_ec_private_key_can_be_exported_to_pem_format) {
    auto key = PrivateKey::generate_p256_ec_key();
    auto pem = key->private_to_pem();
    EXPECT_THAT(pem, StartsWith("-----BEGIN PRIVATE KEY-----"));
}

TEST(CryptoTest, generated_x509_certificate_can_be_exported_to_pem_format) {
    auto dn = X509Certificate::DistinguishedName()
            .country("NO").locality("Trondheim")
            .organization("Cool Unit Test Writers")
            .organizational_unit("Only the finest tests, yes")
            .add_common_name("cooltests.example.com");
    auto subject = X509Certificate::SubjectInfo(std::move(dn));
    auto key = PrivateKey::generate_p256_ec_key();
    auto params = X509Certificate::Params::self_signed(std::move(subject), key);
    auto cert = X509Certificate::generate_from(std::move(params));
    auto pem = cert->to_pem();
    EXPECT_THAT(pem, StartsWith("-----BEGIN CERTIFICATE-----"));
}

}

GTEST_MAIN_RUN_ALL_TESTS()
