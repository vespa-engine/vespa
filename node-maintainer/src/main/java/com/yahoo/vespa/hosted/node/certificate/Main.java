package com.yahoo.vespa.hosted.node.certificate;

import com.yahoo.net.HostName;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author freva
 */
public class Main {


    public static void main(String[] args) throws Exception {
        final String caServerHostname = args[0];
        final Path keyStorePath = Paths.get(args[1]);

        CertificateAuthorityClient caClient = new CertificateAuthorityClient(caServerHostname);
        CertificateRefresher certificateRefresher = new CertificateRefresher(caClient);

        certificateRefresher.refreshCertificate(keyStorePath, HostName.getLocalhost());
    }
}
