// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/net/tls/impl/openssl_typedefs.h>
#include <chrono>
#include <memory>
#include <vector>

// TODOs
//  - add unit testing
//  - extend interfaces (separate PublicKey etc)
//  - hide all OpenSSL details from header
//  - move to appropriate new namespace/directory somewhere under vespalib

namespace vespalib::net::tls::impl {

class PrivateKey {
public:
    enum class Type {
        EC,
        RSA // TODO implement support..!
    };
private:
    EvpPkeyPtr _pkey;
    Type _type;
public:
    PrivateKey(EvpPkeyPtr pkey, Type type)
            : _pkey(std::move(pkey)),
              _type(type)
    {}

    ::EVP_PKEY* native_key() noexcept { return _pkey.get(); }
    const ::EVP_PKEY* native_key() const noexcept { return _pkey.get(); }

    Type type() const noexcept { return _type; }
    vespalib::string private_to_pem() const;

    static std::shared_ptr<PrivateKey> generate_p256_ec_key();
};


class X509Certificate {
    X509Ptr _cert;
public:
    explicit X509Certificate(X509Ptr cert) : _cert(std::move(cert)) {}

    ::X509* native_cert() noexcept { return _cert.get(); }
    const ::X509* native_cert() const noexcept { return _cert.get(); }

    struct DistinguishedName {
        vespalib::string _country;             // "C"
        vespalib::string _state;               // "ST"
        vespalib::string _locality;            // "L"
        vespalib::string _organization;        // "O"
        vespalib::string _organizational_unit; // "OU"
        // Should only be 1 entry in normal certs, but X509 supports more and
        // we want to be able to test this edge case.
        std::vector<vespalib::string> _common_names; // "CN"

        DistinguishedName();
        DistinguishedName(const DistinguishedName&);
        DistinguishedName& operator=(const DistinguishedName&);
        // TODO make these noexcept once vespalib::string has noexcept move.. or move at all!
        DistinguishedName(DistinguishedName&&);
        DistinguishedName& operator=(DistinguishedName&&);
        ~DistinguishedName();

        // TODO could add rvalue overloads as well...
        DistinguishedName& country(vespalib::stringref c)      { _country = c; return *this; }
        DistinguishedName& state(vespalib::stringref st)       { _state = st; return *this; }
        DistinguishedName& locality(vespalib::stringref l)     { _locality = l; return *this; }
        DistinguishedName& organization(vespalib::stringref o) { _organization = o; return *this; }
        DistinguishedName& organizational_unit(vespalib::stringref ou) {
            _organizational_unit = ou;
            return *this;
        }
        DistinguishedName& add_common_name(vespalib::stringref cn) {
            _common_names.emplace_back(cn);
            return *this;
        }
    };

    struct SubjectInfo {
        DistinguishedName dn;
        std::vector<vespalib::string> subject_alt_names;

        SubjectInfo() = default;
        explicit SubjectInfo(DistinguishedName dn_)
                : dn(std::move(dn_)),
                  subject_alt_names()
        {}

        SubjectInfo& add_subject_alt_name(vespalib::string san) {
            subject_alt_names.emplace_back(std::move(san));
            return *this;
        }
    };

    struct Params {
        Params();
        ~Params();

        SubjectInfo subject_info;
        // TODO make public key, but private key has both and this is currently just for testing.
        std::shared_ptr<PrivateKey> subject_key;
        std::shared_ptr<X509Certificate> issuer; // May be nullptr for self-signed certs
        std::shared_ptr<PrivateKey> issuer_key;
        std::chrono::seconds valid_for = std::chrono::hours(24);
        bool is_ca = false;

        static Params self_signed(SubjectInfo subject, std::shared_ptr<PrivateKey> key);
        // TODO only need _public_ key from subject, but this is simplified
        static Params issued_by(SubjectInfo subject,
                                std::shared_ptr<PrivateKey> subject_key,
                                std::shared_ptr<X509Certificate> issuer,
                                std::shared_ptr<PrivateKey> issuer_key);
    };

    // Generates an X509 certificate using a SHA-256 digest
    static std::shared_ptr<X509Certificate> generate_from(Params params);
    vespalib::string to_pem() const;
};

struct CertKeyWrapper {
    std::shared_ptr<X509Certificate> cert;
    std::shared_ptr<PrivateKey> key;

    CertKeyWrapper(std::shared_ptr<X509Certificate> cert_,
                   std::shared_ptr<PrivateKey> key_);
    ~CertKeyWrapper();
};

}
