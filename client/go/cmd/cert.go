// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa cert command
// Author: mpolden
package cmd

import (
	"log"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/vespa"
)

var overwriteCertificate bool

func init() {
	certCmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate and private key")
	rootCmd.AddCommand(certCmd)
}

var certCmd = &cobra.Command{
	Use:   "cert",
	Short: "Creates a new private key and self-signed certificate",
	Long: `Applications in Vespa Cloud are required to secure their data plane with mutual TLS. ` +
		`This command creates a self-signed certificate suitable for development purposes. ` +
		`See https://cloud.vespa.ai/en/security-model for more information on the Vespa Cloud security model.`,
	Run: func(cmd *cobra.Command, args []string) {
		var path string
		if len(args) > 0 {
			path = args[0]
		} else {
			var err error
			path, err = os.Getwd()
			fatalIfErr(err)
		}

		pkg, err := vespa.FindApplicationPackage(path)
		fatalIfErr(err)
		if pkg.HasCertificate() && !overwriteCertificate {
			log.Print("Certificate already exists. Use -f option to recreate")
			return
		}

		// TODO: Consider writing key pair inside ~/.vespa/<app-name>/ instead so that vespa document commands can easily
		// locate key pair
		securityDir := filepath.Join(pkg.Path, "security")
		privateKeyFile := filepath.Join(path, "data-plane-private-key.pem")
		certificateFile := filepath.Join(path, "data-plane-public-cert.pem")
		pkgCertificateFile := filepath.Join(securityDir, "clients.pem")

		keyPair, err := vespa.CreateKeyPair()
		fatalIfErr(err)

		err = os.MkdirAll(securityDir, 0755)
		fatalIfErr(err)
		err = keyPair.WriteCertificateFile(pkgCertificateFile, overwriteCertificate)
		fatalIfErr(err)
		err = keyPair.WriteCertificateFile(certificateFile, overwriteCertificate)
		fatalIfErr(err)
		err = keyPair.WritePrivateKeyFile(privateKeyFile, overwriteCertificate)
		fatalIfErr(err)

		// TODO: Just use log package, which has Printf
		log.Printf("Certificate written to %s", color.Green(pkgCertificateFile))
		log.Printf("Certificate written to %s", color.Green(certificateFile))
		log.Printf("Private key written to %s", color.Green(privateKeyFile))
	},
}

func fatalIfErr(err error) {
	if err != nil {
		log.Fatal(err)
	}
}
