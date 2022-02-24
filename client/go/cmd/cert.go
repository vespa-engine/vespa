// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa cert command
// Author: mpolden
package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

var overwriteCertificate bool

const longDoc = `Create a new private key and self-signed certificate for Vespa Cloud deployment.

The private key and certificate will be stored in the Vespa CLI home directory
(see 'vespa help config'). Other commands will then automatically load the
certificate as necessary.

It's possible to override the private key and certificate used through
environment variables. This can be useful in continuous integration systems.

* VESPA_CLI_DATA_PLANE_CERT and VESPA_CLI_DATA_PLANE_KEY containing the
  certificate and private key directly:

  export VESPA_CLI_DATA_PLANE_CERT="my cert"
  export VESPA_CLI_DATA_PLANE_KEY="my private key"

* VESPA_CLI_DATA_PLANE_CERT_FILE and VESPA_CLI_DATA_PLANE_KEY_FILE containing
  paths to the certificate and private key:

  export VESPA_CLI_DATA_PLANE_CERT_FILE=/path/to/cert
  export VESPA_CLI_DATA_PLANE_KEY_FILE=/path/to/key

Note that when overriding key pair through environment variables, that key pair
will always be used for all applications. It's not possible to specify an
application-specific key.`

func init() {
	certCmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate and private key")
	certCmd.MarkPersistentFlagRequired(applicationFlag)
	deprecatedCertCmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate and private key")
	deprecatedCertCmd.MarkPersistentFlagRequired(applicationFlag)
}

func certExample() string {
	return "$ vespa auth cert -a my-tenant.my-app.my-instance"
}

var certCmd = &cobra.Command{
	Use:               "cert",
	Short:             "Create a new private key and self-signed certificate for Vespa Cloud deployment",
	Long:              longDoc,
	Example:           certExample(),
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.MaximumNArgs(1),
	RunE:              doCert,
}

var deprecatedCertCmd = &cobra.Command{
	Use:               "cert",
	Short:             "Create a new private key and self-signed certificate for Vespa Cloud deployment",
	Long:              longDoc,
	Example:           "$ vespa cert -a my-tenant.my-app.my-instance",
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.MaximumNArgs(1),
	Deprecated:        "use 'vespa auth cert' instead",
	Hidden:            true,
	RunE:              doCert,
}

func doCert(_ *cobra.Command, args []string) error {
	app, err := getApplication()
	if err != nil {
		return err
	}
	pkg, err := vespa.FindApplicationPackage(applicationSource(args), false)
	if err != nil {
		return err
	}
	cfg, err := LoadConfig()
	if err != nil {
		return err
	}
	privateKeyFile, err := cfg.PrivateKeyPath(app)
	if err != nil {
		return err
	}
	certificateFile, err := cfg.CertificatePath(app)
	if err != nil {
		return err
	}

	if !overwriteCertificate {
		hint := "Use -f flag to force overwriting"
		if pkg.HasCertificate() {
			return errHint(fmt.Errorf("application package %s already contains a certificate", pkg.Path), hint)
		}
		if util.PathExists(privateKeyFile) {
			return errHint(fmt.Errorf("private key %s already exists", color.Cyan(privateKeyFile)), hint)
		}
		if util.PathExists(certificateFile) {
			return errHint(fmt.Errorf("certificate %s already exists", color.Cyan(certificateFile)), hint)
		}
	}
	if pkg.IsZip() {
		hint := "Try running 'mvn clean' before 'vespa auth cert', and then 'mvn package'"
		return errHint(fmt.Errorf("cannot add certificate to compressed application package %s", pkg.Path), hint)
	}

	keyPair, err := vespa.CreateKeyPair()
	if err != nil {
		return err
	}
	pkgCertificateFile := filepath.Join(pkg.Path, "security", "clients.pem")
	if err := os.MkdirAll(filepath.Dir(pkgCertificateFile), 0755); err != nil {
		return fmt.Errorf("could not create security directory: %w", err)
	}
	if err := keyPair.WriteCertificateFile(pkgCertificateFile, overwriteCertificate); err != nil {
		return fmt.Errorf("could not write certificate to application package: %w", err)
	}
	if err := keyPair.WriteCertificateFile(certificateFile, overwriteCertificate); err != nil {
		return fmt.Errorf("could not write certificate: %w", err)
	}
	if err := keyPair.WritePrivateKeyFile(privateKeyFile, overwriteCertificate); err != nil {
		return fmt.Errorf("could not write private key: %w", err)
	}
	printSuccess("Certificate written to ", color.Cyan(pkgCertificateFile))
	printSuccess("Certificate written to ", color.Cyan(certificateFile))
	printSuccess("Private key written to ", color.Cyan(privateKeyFile))
	return nil
}
