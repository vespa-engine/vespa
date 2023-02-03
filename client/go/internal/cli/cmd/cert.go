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

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newCertCmd(cli *CLI) *cobra.Command {
	var (
		noApplicationPackage bool
		overwriteCertificate bool
	)
	cmd := &cobra.Command{
		Use:   "cert",
		Short: "Create a new private key and self-signed certificate for data-plane access with Vespa Cloud",
		Long: `Create a new private key and self-signed certificate for data-plane access with Vespa Cloud.

The private key and certificate will be stored in the Vespa CLI home directory
(see 'vespa help config'). Other commands will then automatically load the
certificate as necessary. The certificate will be added to your application
package specified as an argument to this command (default '.').

It's possible to override the private key and certificate used through
environment variables. This can be useful in continuous integration systems.

Example of setting the certificate and key in-line:

    export VESPA_CLI_DATA_PLANE_CERT="my cert"
    export VESPA_CLI_DATA_PLANE_KEY="my private key"

Example of loading certificate and key from custom paths:

    export VESPA_CLI_DATA_PLANE_CERT_FILE=/path/to/cert
    export VESPA_CLI_DATA_PLANE_KEY_FILE=/path/to/key

Note that when overriding key pair through environment variables, that key pair
will always be used for all applications. It's not possible to specify an
application-specific key.

Read more in https://cloud.vespa.ai/en/security/guide`,
		Example: `$ vespa auth cert -a my-tenant.my-app.my-instance
$ vespa auth cert -a my-tenant.my-app.my-instance path/to/application/package`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return doCert(cli, overwriteCertificate, noApplicationPackage, args)
		},
	}
	cmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate and private key")
	cmd.Flags().BoolVarP(&noApplicationPackage, "no-add", "N", false, "Do not add certificate to the application package")
	cmd.MarkPersistentFlagRequired(applicationFlag)
	return cmd
}

func newCertAddCmd(cli *CLI) *cobra.Command {
	var overwriteCertificate bool
	cmd := &cobra.Command{
		Use:   "add",
		Short: "Add certificate to application package",
		Long: `Add an existing self-signed certificate for Vespa Cloud deployment to your application package.

The certificate will be loaded from the Vespa CLI home directory (see 'vespa
help config') by default.

The location of the application package can be specified as an argument to this
command (default '.').`,
		Example: `$ vespa auth cert add -a my-tenant.my-app.my-instance
$ vespa auth cert add -a my-tenant.my-app.my-instance path/to/application/package`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return doCertAdd(cli, overwriteCertificate, args)
		},
	}
	cmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate")
	cmd.MarkPersistentFlagRequired(applicationFlag)
	return cmd
}

func doCert(cli *CLI, overwriteCertificate, noApplicationPackage bool, args []string) error {
	app, err := cli.config.application()
	if err != nil {
		return err
	}
	var pkg vespa.ApplicationPackage
	if !noApplicationPackage {
		pkg, err = cli.applicationPackageFrom(args, false)
		if err != nil {
			return err
		}
	}
	targetType, err := cli.config.targetType()
	if err != nil {
		return err
	}
	privateKeyFile, err := cli.config.privateKeyPath(app, targetType)
	if err != nil {
		return err
	}
	certificateFile, err := cli.config.certificatePath(app, targetType)
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
			return errHint(fmt.Errorf("private key %s already exists", color.CyanString(privateKeyFile)), hint)
		}
		if util.PathExists(certificateFile) {
			return errHint(fmt.Errorf("certificate %s already exists", color.CyanString(certificateFile)), hint)
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
		cli.printSuccess("Certificate written to ", color.CyanString(pkgCertificateFile))
	}
	cli.printSuccess("Certificate written to ", color.CyanString(certificateFile))
	cli.printSuccess("Private key written to ", color.CyanString(privateKeyFile))
	return nil
}

func doCertAdd(cli *CLI, overwriteCertificate bool, args []string) error {
	app, err := cli.config.application()
	if err != nil {
		return err
	}
	pkg, err := cli.applicationPackageFrom(args, false)
	if err != nil {
		return err
	}
	targetType, err := cli.config.targetType()
	if err != nil {
		return err
	}
	certificateFile, err := cli.config.certificatePath(app, targetType)
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

	cli.printSuccess("Certificate written to ", color.CyanString(pkgCertificateFile))
	return nil
}
