// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "x509_certificate.h"
#include "openssl_crypto_impl.h"

namespace vespalib::crypto {

X509Certificate::DistinguishedName::DistinguishedName() noexcept= default;
X509Certificate::DistinguishedName::DistinguishedName(const DistinguishedName&) = default;
X509Certificate::DistinguishedName::DistinguishedName(DistinguishedName&&) noexcept = default;
X509Certificate::DistinguishedName& X509Certificate::DistinguishedName::operator=(DistinguishedName&&) noexcept = default;
X509Certificate::DistinguishedName::~DistinguishedName() = default;

X509Certificate::Params::Params() noexcept= default;
X509Certificate::Params::~Params() = default;

X509Certificate::Params::Params(const Params&) = default;
X509Certificate::Params::Params(Params&&) noexcept = default;
X509Certificate::Params& X509Certificate::Params::operator=(Params&&) noexcept = default;

X509Certificate::Params
X509Certificate::Params::self_signed(SubjectInfo subject,
                                     std::shared_ptr<PrivateKey> key)
{
    Params params;
    params.subject_info = std::move(subject);
    params.subject_key = key;
    params.issuer_key = std::move(key); // self-signed, subject == issuer
    params.is_ca = true;
    return params;
}

X509Certificate::Params
X509Certificate::Params::issued_by(SubjectInfo subject,
                                   std::shared_ptr<PrivateKey> subject_key,
                                   std::shared_ptr<X509Certificate> issuer,
                                   std::shared_ptr<PrivateKey> issuer_key)
{
    Params params;
    params.subject_info = std::move(subject);
    params.issuer = std::move(issuer);
    params.subject_key = std::move(subject_key);
    params.issuer_key = std::move(issuer_key);
    params.is_ca = false; // By default, caller can change for intermediate CAs
    return params;
}

X509Certificate::SubjectInfo::SubjectInfo(DistinguishedName dn_) noexcept
    : dn(std::move(dn_)),
      subject_alt_names()
{}
X509Certificate::SubjectInfo::SubjectInfo() noexcept = default;
X509Certificate::SubjectInfo::SubjectInfo(const SubjectInfo &) = default;
X509Certificate::SubjectInfo::SubjectInfo(SubjectInfo &&) noexcept = default;
X509Certificate::SubjectInfo & X509Certificate::SubjectInfo::operator=(SubjectInfo &&) noexcept = default;
X509Certificate::SubjectInfo::~SubjectInfo() = default;

std::shared_ptr<X509Certificate> X509Certificate::generate_from(Params params) {
    return openssl_impl::X509CertificateImpl::generate_openssl_x509_from(std::move(params));
}

CertKeyWrapper::CertKeyWrapper(std::shared_ptr<X509Certificate> cert_,
                               std::shared_ptr<PrivateKey> key_)
    : cert(std::move(cert_)),
      key(std::move(key_))
{}

CertKeyWrapper::CertKeyWrapper(CertKeyWrapper &&) noexcept = default;
CertKeyWrapper & CertKeyWrapper::operator=(CertKeyWrapper &&) noexcept = default;
CertKeyWrapper::~CertKeyWrapper() = default;

}
