// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "openssl_crypto_impl.h"
#include <vespa/vespalib/crypto/crypto_exception.h>
#include <cassert>
#include <openssl/bn.h>
#include <openssl/rand.h>
#include <openssl/x509v3.h>

namespace vespalib::crypto::openssl_impl {

namespace {

struct EvpPkeyCtxDeleter {
    void operator()(::EVP_PKEY_CTX* ctx) const noexcept {
        ::EVP_PKEY_CTX_free(ctx);
    }
};

using EvpPkeyCtxPtr = std::unique_ptr<::EVP_PKEY_CTX, EvpPkeyCtxDeleter>;

struct BignumDeleter {
    void operator()(::BIGNUM* bn) const noexcept {
        ::BN_free(bn);
    }
};

using BignumPtr = std::unique_ptr<::BIGNUM, BignumDeleter>;

struct Asn1IntegerDeleter {
    void operator()(::ASN1_INTEGER* i) const noexcept {
        ::ASN1_INTEGER_free(i);
    }
};

using Asn1IntegerPtr = std::unique_ptr<::ASN1_INTEGER, Asn1IntegerDeleter>;

struct X509ExtensionDeleter {
    void operator()(X509_EXTENSION* ext) const noexcept {
        ::X509_EXTENSION_free(ext);
    }
};

using X509ExtensionPtr = std::unique_ptr<::X509_EXTENSION, X509ExtensionDeleter>;

vespalib::string bio_to_string(BIO& bio) {
    int written = BIO_pending(&bio);
    assert(written >= 0);
    vespalib::string pem_str(written, '\0');
    if (::BIO_read(&bio, &pem_str[0], written) != written) {
        throw CryptoException("BIO_read did not copy all PEM data");
    }
    return pem_str;
}

BioPtr new_memory_bio() {
    BioPtr bio(::BIO_new(::BIO_s_mem()));
    if (!bio) {
        throw CryptoException("BIO_new(BIO_s_mem())");
    }
    return bio;
}

} // anonymous namespace

vespalib::string
PrivateKeyImpl::private_to_pem() const {
    BioPtr bio = new_memory_bio();
    // TODO this API is const-broken even on 1.1.1, revisit in the future...
    auto* mutable_pkey = const_cast<::EVP_PKEY*>(_pkey.get());
    if (::PEM_write_bio_PrivateKey(bio.get(), mutable_pkey, nullptr, nullptr,
                                   0, nullptr, nullptr) != 1) {
        throw CryptoException("PEM_write_bio_PrivateKey");
    }
    return bio_to_string(*bio);
}

std::shared_ptr<PrivateKeyImpl>
PrivateKeyImpl::generate_openssl_p256_ec_key() {
    // We first have to generate an EVP context for the keygen _parameters_...
    EvpPkeyCtxPtr params_ctx(::EVP_PKEY_CTX_new_id(EVP_PKEY_EC, nullptr));
    if (!params_ctx) {
        throw CryptoException("EVP_PKEY_CTX_new_id");
    }
    if (::EVP_PKEY_paramgen_init(params_ctx.get()) != 1) {
        throw CryptoException("EVP_PKEY_paramgen_init");
    }
    // Set EC keygen parameters to use prime256v1, which is the same as P-256
    if (EVP_PKEY_CTX_set_ec_paramgen_curve_nid(params_ctx.get(), NID_X9_62_prime256v1) <= 0) {
        throw CryptoException("EVP_PKEY_CTX_set_ec_paramgen_curve_nid");
    }
#if (OPENSSL_VERSION_NUMBER >= 0x10100000L)
    // Must tag _explicitly_ as a named curve or many won't accept our pretty keys.
    // If we don't do this, explicit curve parameters are included with the key,
    // and this is not widely supported nor needed since we're generating a key on
    // a standardized curve.
    if (EVP_PKEY_CTX_set_ec_param_enc(params_ctx.get(), OPENSSL_EC_NAMED_CURVE) <= 0) {
        throw CryptoException("EVP_PKEY_CTX_set_ec_param_enc");
    }
#endif
    // Note: despite being an EVP_PKEY this is not an actual key, just key parameters!
    ::EVP_PKEY* params_raw = nullptr;
    if (::EVP_PKEY_paramgen(params_ctx.get(), &params_raw) != 1) {
        throw CryptoException("EVP_PKEY_paramgen");
    }
    EvpPkeyPtr params(params_raw);
    // Now we can create a context for the proper key generation
    EvpPkeyCtxPtr key_ctx(::EVP_PKEY_CTX_new(params.get(), nullptr));
    if (!params_ctx) {
        throw CryptoException("EVP_PKEY_CTX_new");
    }
    if (::EVP_PKEY_keygen_init(key_ctx.get()) != 1) {
        throw CryptoException("EVP_PKEY_keygen_init");
    }
    // Finally, it's time to generate the key pair itself.
    ::EVP_PKEY* pkey_raw = nullptr;
    if (::EVP_PKEY_keygen(key_ctx.get(), &pkey_raw) != 1) {
        throw CryptoException("EVP_PKEY_keygen");
    }
    EvpPkeyPtr generated_key(pkey_raw);
#if (OPENSSL_VERSION_NUMBER < 0x10100000L)
    // On OpenSSL versions prior to 1.1.0, we must set the named curve ASN1 flag
    // directly on the EC_KEY, as the EVP_PKEY wrapper doesn't exist (this is a
    // half truth, as it exists on 1.0.2 stable, but not necessarily on all 1.0.2
    // versions, and certainly not on 1.0.1).
    EcKeyPtr ec_key(::EVP_PKEY_get1_EC_KEY(generated_key.get())); // Bumps ref count, needs free
    if (!ec_key) {
        throw CryptoException("EVP_PKEY_get1_EC_KEY");
    }
    ::EC_KEY_set_asn1_flag(ec_key.get(), OPENSSL_EC_NAMED_CURVE);
#endif
    return std::make_shared<PrivateKeyImpl>(std::move(generated_key), Type::EC);
}

namespace {

void assign_random_positive_serial_number(::X509& cert) {
    /*
     * From RFC3280, section 4.1.2.2:
     * "The serial number MUST be a positive integer assigned by the CA to
     *  each certificate.  It MUST be unique for each certificate issued by a
     *  given CA (i.e., the issuer name and serial number identify a unique
     *  certificate).  CAs MUST force the serialNumber to be a non-negative
     *  integer.
     *  Given the uniqueness requirements above, serial numbers can be
     *  expected to contain long integers.  Certificate users MUST be able to
     *  handle serialNumber values up to 20 octets. (...)"
     */
    unsigned char rand_buf[20];
    // Could also have used BN_rand() for this, but RAND_bytes is just as simple
    // for our purposes.
    if (::RAND_bytes(rand_buf, sizeof(rand_buf)) != 1) {
        throw CryptoException("RAND_bytes");
    }
    // X509 serial numbers must be positive, so clear the MSB of the
    // bignum-to-be. Binary buffer to BIGNUM is interpreted as big endian.
    rand_buf[0] &= 0x7f;
    BignumPtr bn(::BN_bin2bn(rand_buf, sizeof(rand_buf), nullptr));
    if (!bn) {
        throw CryptoException("BN_bin2bn");
    }
    Asn1IntegerPtr bn_as_asn1(::BN_to_ASN1_INTEGER(bn.get(), nullptr));
    if (!bn_as_asn1) {
        throw CryptoException("BN_to_ASN1_INTEGER");
    }
    if (!::X509_set_serialNumber(&cert, bn_as_asn1.get())) { // Makes internal copy of bignum
        throw CryptoException("X509_set_serialNumber");
    }
}

void set_certificate_expires_from_now(::X509& cert, std::chrono::seconds valid_for) {
    if (::X509_gmtime_adj(X509_get_notBefore(&cert), 0) == nullptr) {
        throw CryptoException("X509_gmtime_adj(X509_get_notBefore())");
    }
    if (::X509_gmtime_adj(X509_get_notAfter(&cert), valid_for.count()) == nullptr) {
        throw CryptoException("X509_gmtime_adj(X509_get_notAfter())");
    }
}

void
set_name_entry_if_non_empty(::X509_NAME& name, const char* field, vespalib::stringref entry) {
    if (entry.empty()) {
        return;
    }
    assert(entry.size() <= INT_MAX);
    auto* entry_buf = reinterpret_cast<const unsigned char*>(entry.data());
    auto entry_len  = static_cast<int>(entry.size());
    if (::X509_NAME_add_entry_by_txt(&name, field, MBSTRING_UTF8, entry_buf, entry_len, -1, 0) != 1) {
        throw CryptoException("X509_NAME_add_entry_by_txt");
    }
}

void
assign_subject_distinguished_name(::X509_NAME& name, const X509Certificate::DistinguishedName& dn) {
    set_name_entry_if_non_empty(name, "C",  dn._country);
    set_name_entry_if_non_empty(name, "ST", dn._state);
    set_name_entry_if_non_empty(name, "L",  dn._locality);
    set_name_entry_if_non_empty(name, "O",  dn._organization);
    set_name_entry_if_non_empty(name, "OU", dn._organizational_unit);
    for (auto& cn : dn._common_names) {
        set_name_entry_if_non_empty(name, "CN", cn);
    }
}

// `value` string is taken by value since X509V3_EXT_conf_nid takes in a mutable char*
// and who knows what terrible things it might do to it (we must also ensure null
// termination of the string).
void
add_v3_ext(::X509& subject, ::X509& issuer, int nid, vespalib::string value) {
    // We are now reaching a point where the API we need to use is not properly documented.
    // This functionality is inferred from https://opensource.apple.com/source/OpenSSL/OpenSSL-22/openssl/demos/x509/mkcert.c
    ::X509V3_CTX ctx;
    X509V3_set_ctx_nodb(&ctx);
    // Need to set the certificate(s) so that e.g. subjectKeyIdentifier can find
    // the correct public key to hash.
    ::X509V3_set_ctx(&ctx, &issuer, &subject, /*CSR*/nullptr, /*CRL*/nullptr, /*flags*/0);
    X509ExtensionPtr ext(::X509V3_EXT_conf_nid(nullptr, &ctx, nid, &value[0]));
    if (!ext) {
        throw CryptoException("X509V3_EXT_conf_nid");
    }
    // Makes internal copy of ext.
    // Return value semantics not documented; inferred from reading source...
    if (::X509_add_ext(&subject, ext.get(), -1) != 1) {
        throw CryptoException("X509_add_ext");
    }
}

void
add_any_subject_alternate_names(::X509& subject, ::X509& issuer,
                                     const std::vector<vespalib::string>& sans) {
    // There can only be 1 SAN entry in a valid cert, but it can have multiple
    // logical entries separated by commas in a single string.
    vespalib::string san_csv;
    for (auto& san : sans) {
        if (!san_csv.empty()) {
            san_csv.append(',');
        }
        san_csv.append(san);
    }
    if (!san_csv.empty()) {
        add_v3_ext(subject, issuer, NID_subject_alt_name, std::move(san_csv));
    }
}

} // anonymous namespace

X509CertificateImpl::~X509CertificateImpl() = default;

// Some references:
// https://stackoverflow.com/questions/256405/programmatically-create-x509-certificate-using-openssl
// https://opensource.apple.com/source/OpenSSL/OpenSSL-22/openssl/demos/x509/mkcert.c
std::shared_ptr<X509CertificateImpl>
X509CertificateImpl::generate_openssl_x509_from(Params params) {
    X509Ptr cert(::X509_new());
    if (!cert) {
        throw CryptoException("X509_new");
    }
    // FIXME make this const, currently is not due to OpenSSL API const issues (ugh).
    auto& subject_key_impl = dynamic_cast<PrivateKeyImpl&>(*params.subject_key);
    ::X509_set_version(cert.get(), 2); // 2 actually means v3 :)
    assign_random_positive_serial_number(*cert);
    set_certificate_expires_from_now(*cert, params.valid_for);
    // Internal key copy; does not take ownership
    if (::X509_set_pubkey(cert.get(), subject_key_impl.native_key()) != 1) {
        throw CryptoException("X509_set_pubkey");
    }
    // The "subject" is the target entity the certificate is intended to, well, certify.
    ::X509_NAME* subj_name = ::X509_get_subject_name(cert.get()); // Internal pointer, never null, not owned by us
    assign_subject_distinguished_name(*subj_name, params.subject_info.dn);

    // If our parameters indicate that there is no parent issuer of this certificate, the
    // certificate to generate is by definition a self-signed (root) certificate authority.
    // In this case, the Issuer name and Subject name are identical.
    // If we _do_ have an issuer, we'll record its Subject name as our Issuer name.
    // Note that it's legal to have a self-signed non-CA certificate, though it obviously
    // cannot be used to sign any subordinate certificates.
    auto* issuer_cert_impl = dynamic_cast<X509CertificateImpl*>(params.issuer.get()); // May be nullptr.
    ::X509_NAME* issuer_name = (issuer_cert_impl
                                ? ::X509_get_subject_name(issuer_cert_impl->native_cert())
                                : subj_name);
    if (::X509_set_issuer_name(cert.get(), issuer_name) != 1) { // Makes internal copy
        throw CryptoException("X509_set_issuer_name");
    }
    ::X509& issuer_cert = issuer_cert_impl ? *issuer_cert_impl->native_cert() : *cert;

    const char* basic_constraints = params.is_ca ? "critical,CA:TRUE" : "critical,CA:FALSE";
    const char* key_usage = params.is_ca ? "critical,keyCertSign,digitalSignature"
                                         : "critical,digitalSignature";

    add_v3_ext(*cert, issuer_cert, NID_basic_constraints, basic_constraints);
    add_v3_ext(*cert, issuer_cert, NID_key_usage, key_usage);
    add_v3_ext(*cert, issuer_cert, NID_subject_key_identifier, "hash");
    // For root CAs, authority key id == subject key id
    add_v3_ext(*cert, issuer_cert, NID_authority_key_identifier, "keyid:always");
    add_any_subject_alternate_names(*cert, issuer_cert, params.subject_info.subject_alt_names);

    auto& issuer_key_impl = dynamic_cast<PrivateKeyImpl&>(*params.issuer_key);
    if (::X509_sign(cert.get(), issuer_key_impl.native_key(), ::EVP_sha256()) == 0) {
        throw CryptoException("X509_sign");
    }
    return std::make_shared<X509CertificateImpl>(std::move(cert));
}

vespalib::string
X509CertificateImpl::to_pem() const {
    BioPtr bio = new_memory_bio();
    // TODO this API is const-broken, revisit in the future...
    auto* mutable_cert = const_cast<::X509*>(_cert.get());
    if (::PEM_write_bio_X509(bio.get(), mutable_cert) != 1) {
        throw CryptoException("PEM_write_bio_X509");
    }
    return bio_to_string(*bio);
}


}
