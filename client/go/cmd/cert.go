// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa cert command
// Author: mpolden
package cmd

import (
	"fmt"
	"log"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/util"
	"github.com/vespa-engine/vespa/vespa"
)

var overwriteCertificate bool

func init() {
	rootCmd.AddCommand(certCmd)
	certCmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate and private key")
	certCmd.MarkPersistentFlagRequired(applicationFlag)
}

var certCmd = &cobra.Command{
	Use:     "cert",
	Short:   "Create a new private key and self-signed certificate for a cloud deployment",
	Example: "$ vespa cert -a my-tenant.my-app.my-instance",
	Args:    cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		var path string
		if len(args) > 0 {
			path = args[0]
		} else {
			var err error
			path, err = os.Getwd()
			fatalIfErr(err)
		}

		app := getApplication()
		pkg, err := vespa.ApplicationPackageFrom(path)
		fatalIfErr(err)
		configDir, err := configDir(app)
		fatalIfErr(err)

		securityDir := filepath.Join(pkg.Path, "security")
		pkgCertificateFile := filepath.Join(securityDir, "clients.pem")
		privateKeyFile := filepath.Join(configDir, "data-plane-private-key.pem")
		certificateFile := filepath.Join(configDir, "data-plane-public-cert.pem")
		if !overwriteCertificate {
			for _, file := range []string{pkgCertificateFile, privateKeyFile, certificateFile} {
				if util.PathExists(file) {
					errorWithHint(fmt.Errorf("Certificate or private key %s already exists", color.Cyan(file)), "Use -f flag to force overwriting")
					return
				}
			}
		}

		keyPair, err := vespa.CreateKeyPair()
		fatalIfErr(err)
		err = os.MkdirAll(configDir, 0755)
		fatalIfErr(err)
		err = os.MkdirAll(securityDir, 0755)
		fatalIfErr(err)
		err = keyPair.WriteCertificateFile(pkgCertificateFile, overwriteCertificate)
		fatalIfErr(err)
		err = keyPair.WriteCertificateFile(certificateFile, overwriteCertificate)
		fatalIfErr(err)
		err = keyPair.WritePrivateKeyFile(privateKeyFile, overwriteCertificate)
		fatalIfErr(err)

		log.Printf("Certificate written to %s", color.Cyan(pkgCertificateFile))
		log.Printf("Certificate written to %s", color.Cyan(certificateFile))
		log.Printf("Private key written to %s", color.Cyan(privateKeyFile))
	},
}

func fatalIfErr(err error) {
	if err != nil {
		log.Fatal(err)
	}
}
