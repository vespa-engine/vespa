// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa cert command
// Author: mpolden
package cmd

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

var (
	noApplicationPackage bool
	overwriteCertificate bool
)

func init() {
	certCmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate and private key")
	certCmd.Flags().BoolVarP(&noApplicationPackage, "no-add", "N", false, "Do not add certificate to an application package")
	certCmd.MarkPersistentFlagRequired(applicationFlag)

	deprecatedCertCmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate and private key")
	deprecatedCertCmd.MarkPersistentFlagRequired(applicationFlag)

	certCmd.AddCommand(certAddCmd)
	certAddCmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate")
	certAddCmd.MarkPersistentFlagRequired(applicationFlag)
}

var certCmd = &cobra.Command{
	Use:   "cert",
	Short: "Create a new private key and self-signed certificate for Vespa Cloud deployment",
	Long: `Create a new private key and self-signed certificate for Vespa Cloud deployment.

The private key and certificate will be stored in the Vespa CLI home directory
(see 'vespa help config'). Other commands will then automatically load the
certificate as necessary.

The certificate will be added to your application package specified as an
argument to this command (default '.'), unless the '--no-add' is set.

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
application-specific key.`,
	Example: `$ vespa auth cert -a my-tenant.my-app.my-instance
$ vespa auth cert -a my-tenant.my-app.my-instance path/to/application/package`,
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.MaximumNArgs(1),
	RunE:              doCert,
}

var deprecatedCertCmd = &cobra.Command{
	Use:   "cert",
	Short: "Create a new private key and self-signed certificate for Vespa Cloud deployment",
	Long: `Create a new private key and self-signed certificate for Vespa Cloud deployment.

The private key and certificate will be stored in the Vespa CLI home directory
(see 'vespa help config'). Other commands will then automatically load the
certificate as necessary.

The certificate will be added to your application package specified as an
argument to this command (default '.').

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
application-specific key.`,
	Example:           "$ vespa cert -a my-tenant.my-app.my-instance",
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.MaximumNArgs(1),
	Deprecated:        "use 'vespa auth cert' instead",
	Hidden:            true,
	RunE:              doCert,
}

var certAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add certificate to application package",
	Long: `Add an existing self-signed certificate for Vespa Cloud deployment to your application package.

The certificate will be looked for in the Vespa CLI home directory (see 'vespa
help config') by default.

The location of the application package can be specified as an argument to this
command (default '.').`,
	Example: `$ vespa auth cert add -a my-tenant.my-app.my-instance
$ vespa auth cert add -a my-tenant.my-app.my-instance path/to/application/package`,
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.MinimumNArgs(1),
	RunE:              doCertAdd,
}

func doCert(_ *cobra.Command, args []string) error {
	app, err := getApplication()
	if err != nil {
		return err
	}
	var pkg vespa.ApplicationPackage
	if !noApplicationPackage {
		pkg, err = vespa.FindApplicationPackage(applicationSource(args), false)
		if err != nil {
			return err
		}
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
		if !noApplicationPackage {
			if pkg.HasCertificate() {
				return errHint(fmt.Errorf("application package %s already contains a certificate", pkg.Path), hint)
			}
		}
		if util.PathExists(privateKeyFile) {
			return errHint(fmt.Errorf("private key %s already exists", color.Cyan(privateKeyFile)), hint)
		}
		if util.PathExists(certificateFile) {
			return errHint(fmt.Errorf("certificate %s already exists", color.Cyan(certificateFile)), hint)
		}
	}
	if !noApplicationPackage {
		if pkg.IsZip() {
			hint := "Try running 'mvn clean' before 'vespa auth cert', and then 'mvn package'"
			return errHint(fmt.Errorf("cannot add certificate to compressed application package %s", pkg.Path), hint)
		}
	}

	keyPair, err := vespa.CreateKeyPair()
	if err != nil {
		return err
	}
	var pkgCertificateFile string
	if !noApplicationPackage {
		pkgCertificateFile = filepath.Join(pkg.Path, "security", "clients.pem")
		if err := os.MkdirAll(filepath.Dir(pkgCertificateFile), 0755); err != nil {
			return fmt.Errorf("could not create security directory: %w", err)
		}
		if err := keyPair.WriteCertificateFile(pkgCertificateFile, overwriteCertificate); err != nil {
			return fmt.Errorf("could not write certificate to application package: %w", err)
		}
	}
	if err := keyPair.WriteCertificateFile(certificateFile, overwriteCertificate); err != nil {
		return fmt.Errorf("could not write certificate: %w", err)
	}
	if err := keyPair.WritePrivateKeyFile(privateKeyFile, overwriteCertificate); err != nil {
		return fmt.Errorf("could not write private key: %w", err)
	}
	if !noApplicationPackage {
		printSuccess("Certificate written to ", color.Cyan(pkgCertificateFile))
	}
	printSuccess("Certificate written to ", color.Cyan(certificateFile))
	printSuccess("Private key written to ", color.Cyan(privateKeyFile))
	return nil
}

func doCertAdd(_ *cobra.Command, args []string) error {
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
	certificateFile, err := cfg.CertificatePath(app)
	if err != nil {
		return err
	}

	if pkg.IsZip() {
		hint := "Try running 'mvn clean' before 'vespa auth cert add', and then 'mvn package'"
		return errHint(fmt.Errorf("unable to add certificate to compressed application package: %s", pkg.Path), hint)
	}

	pkgCertificateFile := filepath.Join(pkg.Path, "security", "clients.pem")
	if err := os.MkdirAll(filepath.Dir(pkgCertificateFile), 0755); err != nil {
		return fmt.Errorf("could not create security directory: %w", err)
	}
	src, err := os.Open(certificateFile)
	if errors.Is(err, os.ErrNotExist) {
		return errHint(fmt.Errorf("there is not key pair generated for application '%s'", app), "Try running 'vespa auth cert' to generate it")
	} else if err != nil {
		return fmt.Errorf("could not open certificate file: %w", err)
	}
	defer src.Close()
	flags := os.O_CREATE | os.O_RDWR
	if overwriteCertificate {
		flags |= os.O_TRUNC
	} else {
		flags |= os.O_EXCL
	}
	dst, err := os.OpenFile(pkgCertificateFile, flags, 0755)
	if errors.Is(err, os.ErrExist) {
		return errHint(fmt.Errorf("application package %s already contains a certificate", pkg.Path), "Use -f flag to force overwriting")
	} else if err != nil {
		return fmt.Errorf("could not open application certificate file for writing: %w", err)
	}
	if _, err := io.Copy(dst, src); err != nil {
		return fmt.Errorf("could not copy certificate file to application: %w", err)
	}

	printSuccess("Certificate written to ", color.Cyan(pkgCertificateFile))
	return nil
}
