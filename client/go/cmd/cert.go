// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa cert command
// Author: mpolden
package cmd

import (
	"fmt"
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
	Short:   "Create a new private key and self-signed certificate for Vespa Cloud deployment",
	Example: "$ vespa cert -a my-tenant.my-app.my-instance",
	Args:    cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		app := getApplication()
		pkg, err := vespa.ApplicationPackageFrom(applicationSource(args))
		if err != nil {
			printErr(err)
			return
		}
		configDir := configDir(app)
		securityDir := filepath.Join(pkg.Path, "security")
		pkgCertificateFile := filepath.Join(securityDir, "clients.pem")
		privateKeyFile := filepath.Join(configDir, "data-plane-private-key.pem")
		certificateFile := filepath.Join(configDir, "data-plane-public-cert.pem")
		if !overwriteCertificate {
			for _, file := range []string{pkgCertificateFile, privateKeyFile, certificateFile} {
				if util.PathExists(file) {
					printErrHint(fmt.Errorf("Certificate or private key %s already exists", color.Cyan(file)), "Use -f flag to force overwriting")
					return
				}
			}
		}

		keyPair, err := vespa.CreateKeyPair()
		if err != nil {
			printErr(err, "Could not create key pair")
			return
		}
		if err := os.MkdirAll(securityDir, 0755); err != nil {
			printErr(err, "Could not create security directory")
			return
		}
		if err := keyPair.WriteCertificateFile(pkgCertificateFile, overwriteCertificate); err != nil {
			printErr(err, "Could not write certificate")
			return
		}
		if err := keyPair.WriteCertificateFile(certificateFile, overwriteCertificate); err != nil {
			printErr(err, "Could not write certificate")
			return
		}
		if err := keyPair.WritePrivateKeyFile(privateKeyFile, overwriteCertificate); err != nil {
			printErr(err, "Could not write private key")
			return
		}
		printSuccess("Certificate written to ", color.Cyan(pkgCertificateFile))
		printSuccess("Certificate written to ", color.Cyan(certificateFile))
		printSuccess("Private key written to ", color.Cyan(privateKeyFile))
	},
}
