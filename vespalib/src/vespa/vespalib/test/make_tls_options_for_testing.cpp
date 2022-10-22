// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "make_tls_options_for_testing.h"
#include "peer_policy_utils.h"
#include <vespa/vespalib/crypto/private_key.h>
#include <vespa/vespalib/crypto/x509_certificate.h>

namespace {

using namespace vespalib::crypto;
using namespace vespalib::net::tls;

struct TransientCryptoCredentials {
    CertKeyWrapper root_ca;
    CertKeyWrapper host_creds;
    vespalib::net::tls::TransportSecurityOptions cached_transport_options;
    vespalib::net::tls::TransportSecurityOptions cached_constrained_transport_options;

    TransientCryptoCredentials();
    ~TransientCryptoCredentials();

    static CertKeyWrapper make_root_ca() {
        auto dn = X509Certificate::DistinguishedName()
                .country("US").state("CA").locality("Sunnyvale")
                .organization("ACME, Inc.")
                .organizational_unit("ACME Root CA")
                .add_common_name("acme.example.com");
        auto subject = X509Certificate::SubjectInfo(std::move(dn));
        auto key = PrivateKey::generate_p256_ec_key();
        auto params = X509Certificate::Params::self_signed(std::move(subject), key);
        auto cert = X509Certificate::generate_from(std::move(params));
        return {std::move(cert), std::move(key)};
    }

    static CertKeyWrapper make_host_creds(const CertKeyWrapper& root_ca_creds,
                                          const vespalib::string& extra_san_entry) {
        auto dn = X509Certificate::DistinguishedName()
                .country("US").state("CA").locality("Sunnyvale")
                .organization("Wile E. Coyote, Ltd.")
                .organizational_unit("Unit Testing and Anvil Dropping Division")
                .add_common_name("localhost"); // Should technically not be needed, but including it anyway.
        auto subject = X509Certificate::SubjectInfo(std::move(dn));
        subject.add_subject_alt_name("DNS:localhost")
               .add_subject_alt_name(extra_san_entry);
        auto key = PrivateKey::generate_p256_ec_key();
        auto params = X509Certificate::Params::issued_by(std::move(subject), key, root_ca_creds.cert, root_ca_creds.key);
        params.valid_for = std::chrono::hours(1);
        auto cert = X509Certificate::generate_from(std::move(params));
        return {std::move(cert), std::move(key)};
    }

    static const TransientCryptoCredentials& instance();
};

TransientCryptoCredentials::TransientCryptoCredentials()
    : root_ca(make_root_ca()),
      host_creds(make_host_creds(root_ca, "DNS:anvils.example")),
      cached_transport_options(vespalib::net::tls::TransportSecurityOptions::Params().
            ca_certs_pem(root_ca.cert->to_pem()).
            cert_chain_pem(host_creds.cert->to_pem()).
            private_key_pem(host_creds.key->private_to_pem()).
            authorized_peers(vespalib::net::tls::AuthorizedPeers::allow_all_authenticated())),
      cached_constrained_transport_options(vespalib::net::tls::TransportSecurityOptions::Params().
            ca_certs_pem(root_ca.cert->to_pem()).
            cert_chain_pem(host_creds.cert->to_pem()).
            private_key_pem(host_creds.key->private_to_pem()).
            authorized_peers(authorized_peers({policy_with({required_san_dns("anvils.example")},
                                                           CapabilitySet::telemetry())})))
{}

TransientCryptoCredentials::~TransientCryptoCredentials() = default;

const TransientCryptoCredentials& TransientCryptoCredentials::instance() {
    static TransientCryptoCredentials test_creds;
    return test_creds;
}

}

namespace vespalib::test {

SocketSpec make_local_spec() {
    return SocketSpec("tcp/localhost:123");
}

vespalib::net::tls::TransportSecurityOptions make_tls_options_for_testing() {
    return TransientCryptoCredentials::instance().cached_transport_options;
}

vespalib::net::tls::TransportSecurityOptions make_telemetry_only_capability_tls_options_for_testing() {
    return TransientCryptoCredentials::instance().cached_constrained_transport_options;
}

} // namespace vespalib::test
