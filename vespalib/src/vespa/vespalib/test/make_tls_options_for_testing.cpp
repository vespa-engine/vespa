// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "make_tls_options_for_testing.h"

/*
 * Generated with the following commands:
 *
 * openssl ecparam -name prime256v1 -genkey -noout -out ca.key
 *
 * openssl req -new -x509 -nodes -key ca.key \
 *    -sha256 -out ca.pem \
 *    -subj '/C=US/L=LooneyVille/O=ACME/OU=ACME test CA/CN=acme.example.com' \
 *    -days 10000
 *
 * openssl ecparam -name prime256v1 -genkey -noout -out host.key
 *
 * openssl req -new -key host.key -out host.csr \
 *    -subj '/C=US/L=LooneyVille/O=Wile. E. Coyote, Ltd./CN=wile.example.com' \
 *    -sha256
 *
 * openssl x509 -req -in host.csr \
 *   -CA ca.pem \
 *   -CAkey ca.key \
 *   -CAcreateserial \
 *   -out host.pem \
 *   -days 10000 \
 *   -sha256
 *
 * TODO generate keypairs and certs at test-time to avoid any hard-coding
 * There certs are valid until 2046, so that buys us some time..!
 */

// ca.pem
constexpr const char* ca_pem = R"(-----BEGIN CERTIFICATE-----
MIIBuDCCAV4CCQDpVjQIixTxvDAKBggqhkjOPQQDAjBkMQswCQYDVQQGEwJVUzEU
MBIGA1UEBwwLTG9vbmV5VmlsbGUxDTALBgNVBAoMBEFDTUUxFTATBgNVBAsMDEFD
TUUgdGVzdCBDQTEZMBcGA1UEAwwQYWNtZS5leGFtcGxlLmNvbTAeFw0xODA4MzEx
MDU3NDVaFw00NjAxMTYxMDU3NDVaMGQxCzAJBgNVBAYTAlVTMRQwEgYDVQQHDAtM
b29uZXlWaWxsZTENMAsGA1UECgwEQUNNRTEVMBMGA1UECwwMQUNNRSB0ZXN0IENB
MRkwFwYDVQQDDBBhY21lLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0D
AQcDQgAE1L7IzCN5pbyVnBATIHieuxq+hf9kWyn5yfjkXMhD52T5ITz1huq4nbiN
YtRoRP7XmipI60R/uiCHzERcsVz4rDAKBggqhkjOPQQDAgNIADBFAiEA6wmZDBca
y0aJ6ABtjbjx/vlmVDxdkaSZSgO8h2CkvIECIFktCkbZhDFfSvbqUScPOGuwkdGQ
L/EW2Bxp+1BPcYoZ
-----END CERTIFICATE-----)";

// host.pem
constexpr const char* cert_pem = R"(-----BEGIN CERTIFICATE-----
MIIBsTCCAVgCCQD6GfDh0ltpsjAKBggqhkjOPQQDAjBkMQswCQYDVQQGEwJVUzEU
MBIGA1UEBwwLTG9vbmV5VmlsbGUxDTALBgNVBAoMBEFDTUUxFTATBgNVBAsMDEFD
TUUgdGVzdCBDQTEZMBcGA1UEAwwQYWNtZS5leGFtcGxlLmNvbTAeFw0xODA4MzEx
MDU3NDVaFw00NjAxMTYxMDU3NDVaMF4xCzAJBgNVBAYTAlVTMRQwEgYDVQQHDAtM
b29uZXlWaWxsZTEeMBwGA1UECgwVV2lsZS4gRS4gQ295b3RlLCBMdGQuMRkwFwYD
VQQDDBB3aWxlLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE
e+Y4hxt66em0STviGUj6ZDbxzoLoubXWRml8JDFrEc2S2433KWw2npxYSKVCyo3a
/Vo33V8/H0WgOXioKEZJxDAKBggqhkjOPQQDAgNHADBEAiAN+87hQuGv3z0Ja2BV
b8PHq2vp3BJHjeMuxWu4BFPn0QIgYlvIHikspgGatXRNMZ1gPC0oCccsJFcie+Cw
zL06UPI=
-----END CERTIFICATE-----)";

// host.key
constexpr const char* key_pem = R"(-----BEGIN EC PRIVATE KEY-----
MHcCAQEEID6di2PFYn8hPrxPbkFDGkSqF+K8L520In7nx3g0jwzOoAoGCCqGSM49
AwEHoUQDQgAEe+Y4hxt66em0STviGUj6ZDbxzoLoubXWRml8JDFrEc2S2433KWw2
npxYSKVCyo3a/Vo33V8/H0WgOXioKEZJxA==
-----END EC PRIVATE KEY-----)";

namespace vespalib::test {

SocketSpec local_spec("tcp/localhost:123");

vespalib::net::tls::TransportSecurityOptions make_tls_options_for_testing() {
    return vespalib::net::tls::TransportSecurityOptions(ca_pem, cert_pem, key_pem);
}

} // namespace vespalib::test
