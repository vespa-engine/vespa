// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa cert command
// Author: mpolden
package cmd

import (
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newCertCmd(cli *CLI) *cobra.Command {
	var (
		skipApplicationPackage      bool
		overwriteCertificate        bool
		newPrivateKeyAndCertificate bool
		pruneOldCertificates        bool
	)
	cmd := &cobra.Command{
		Use:   "cert",
		Short: "Create a new self-signed certificate for authentication with Vespa Cloud data plane",
		Long: `Create a new self-signed certificate for authentication with Vespa Cloud data plane.

The private key and certificate will be stored in the Vespa CLI home directory
(see 'vespa help config'). Other commands will then automatically load the
certificate as necessary. The certificate will be added to your application
package specified as an argument to this command (default '.').

It's possible to override the private key and certificate used through
environment variables. This can be useful in continuous integration systems.

It's also possible override the CA certificate which can be useful when using
self-signed certificates with a self-hosted Vespa service.
See https://docs.vespa.ai/en/security/mtls.html for more
information.

Example of setting the CA certificate, certificate and key in-line:

    export VESPA_CLI_DATA_PLANE_CA_CERT="my CA cert"
    export VESPA_CLI_DATA_PLANE_CERT="my cert"
    export VESPA_CLI_DATA_PLANE_KEY="my private key"

Example of loading CA certificate, certificate and key from custom paths:

    export VESPA_CLI_DATA_PLANE_CA_CERT_FILE=/path/to/cacert
    export VESPA_CLI_DATA_PLANE_CERT_FILE=/path/to/cert
    export VESPA_CLI_DATA_PLANE_KEY_FILE=/path/to/key

Example of disabling verification of the server's certificate chain and
hostname:

    export VESPA_CLI_DATA_PLANE_TRUST_ALL=true

Note that when overriding key pair through environment variables, that key pair
will always be used for all applications. It's not possible to specify an
application-specific key.

See https://docs.vespa.ai/en/security/guide.html for more details.`,
		Example: `$ vespa auth cert
$ vespa auth cert -a my-tenant.my-app.my-instance
$ vespa auth cert -a my-tenant.my-app.my-instance path/to/application/package`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if pruneOldCertificates {
				return doPruneOldCertificates(cli, overwriteCertificate, skipApplicationPackage, args)
			}
			return doCert(cli, overwriteCertificate, skipApplicationPackage, newPrivateKeyAndCertificate, args)
		},
	}
	cmd.Flags().BoolVarP(&overwriteCertificate, "force", "f", false, "Force overwrite of existing certificate and private key")
	cmd.Flags().BoolVar(&newPrivateKeyAndCertificate, "new-key", false, "Appends a new certificate if certificate already exists. Useful for rotating credentials")
	cmd.Flags().BoolVar(&pruneOldCertificates, "prune-old", false, "Remove all but the newest certificate from the certificate file. Useful after completing credential rotation")
	cmd.MarkFlagsMutuallyExclusive("new-key", "prune-old")
	// TODO(mpolden): Stop adding certificate to application package and remove this flag
	cmd.Flags().BoolVarP(&skipApplicationPackage, "no-add", "N", false, "Do not add certificate to the application package")
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

func doCert(cli *CLI, overwriteCertificate, skipApplicationPackage bool, newKeyAndCertificate bool, args []string) error {
	targetType, err := cli.targetType(cloudTargetOnly)
	if err != nil {
		return err
	}
	app, err := cli.config.application()
	if err != nil {
		return err
	}
	privateKeyFile, err := cli.config.privateKeyPath(app, targetType.name)
	if err != nil {
		return err
	}
	certificateFile, err := cli.config.certificatePath(app, targetType.name)
	if err != nil {
		return err
	}

	if !overwriteCertificate && !newKeyAndCertificate {
		hint := "Use -f flag to force overwriting of certificate, or use --new-key flag to rotate certificates"
		if ioutil.Exists(privateKeyFile.path) {
			return errHint(fmt.Errorf("private key '%s' already exists", color.CyanString(privateKeyFile.path)), hint)
		}
		if ioutil.Exists(certificateFile.path) {
			return errHint(fmt.Errorf("certificate '%s' already exists", color.CyanString(certificateFile.path)), hint)
		}
	}

	if newKeyAndCertificate {
		oldPrivateKeyFile, err := cli.config.oldPrivateKeyPath(app, targetType.name)
		if err == nil && ioutil.Exists(oldPrivateKeyFile.path) {
			return errHint(fmt.Errorf("backup of private key already exists at %s", color.CyanString(oldPrivateKeyFile.path)),
				"If you still want to rotate your private key and certificate, remove the old private key first. Documentation for rotation help: "+color.GreenString("https://docs.vespa.ai/en/security/guide.html"))
		}

		if !ioutil.Exists(certificateFile.path) {
			cli.printWarning("No certificate file exists. A new certificate will be created.")
		}
		if !ioutil.Exists(privateKeyFile.path) {
			cli.printWarning("No private key file exists. A new private key will be created.")
		}
		if !overwriteCertificate {
			ok, err := cli.confirm("This will create a backup of your existing private key and add a new certificate. Continue?", false)
			if err != nil {
				return err
			}
			if !ok {
				return nil
			}
		}
		backupKeyPath, err := cli.config.backupPrivateKey(app, targetType.name)
		if err != nil {
			if !errors.Is(err, os.ErrNotExist) {
				return fmt.Errorf("could not back up private key: %w", err)
			}
		} else {
			cli.printInfo("Private key backup created at ", backupKeyPath)
		}
	}

	keyPair, err := vespa.CreateKeyPair()
	if err != nil {
		return err
	}
	if err := keyPair.WriteCertificateFile(certificateFile.path, overwriteCertificate, newKeyAndCertificate); err != nil {
		return fmt.Errorf("could not write certificate: %w", err)
	}
	if err := keyPair.WritePrivateKeyFile(privateKeyFile.path, overwriteCertificate || newKeyAndCertificate); err != nil {
		return fmt.Errorf("could not write private key: %w", err)
	}
	cli.printSuccess("Certificate written to ", color.CyanString("'"+certificateFile.path+"'"))
	cli.printSuccess("Private key written to ", color.CyanString("'"+privateKeyFile.path+"'"))
	if newKeyAndCertificate {
		cli.printSuccess("Next step: deploy with 'vespa prod deploy' and then remove unused certificates with 'vespa auth cert --prune-old'. See ", color.GreenString("https://docs.vespa.ai/en/security/guide.html"))
	}
	if !skipApplicationPackage {
		return doCertAdd(cli, overwriteCertificate, args)
	}
	return nil
}

func doPruneOldCertificates(cli *CLI, force, skipApplicationPackage bool, args []string) error {
	targetType, err := cli.targetType(cloudTargetOnly)
	if err != nil {
		return err
	}
	app, err := cli.config.application()
	if err != nil {
		return err
	}
	certificateFile, err := cli.config.certificatePath(app, targetType.name)
	if err != nil {
		return err
	}
	if !ioutil.Exists(certificateFile.path) {
		return errHint(fmt.Errorf("no certificate found at '%s'", color.CyanString(certificateFile.path)),
			"Run 'vespa auth cert' first to create an initial certificate")
	}
	certificateData, err := os.ReadFile(certificateFile.path)
	if err != nil {
		return fmt.Errorf("could not read certificate file: %w", err)
	}
	certs, err := vespa.ParseCertificates(certificateData)
	if err != nil {
		return fmt.Errorf("could not parse certificates: %w", err)
	}
	if len(certs) == 1 {
		cli.printInfo("Only one certificate is present, nothing to remove.")
		return nil
	}
	privateKeyFile, err := cli.config.privateKeyPath(app, targetType.name)
	if err != nil {
		return err
	}
	privateKeyData, err := os.ReadFile(privateKeyFile.path)
	if err != nil {
		return fmt.Errorf("could not read private key file: %w", err)
	}
	// Old key backup is optional — used only to label certs.
	oldPrivateKeyFile, _ := cli.config.oldPrivateKeyPath(app, targetType.name)
	oldPrivateKeyData, _ := os.ReadFile(oldPrivateKeyFile.path)

	// Classify every certificate in the file.
	var currentCerts, oldCerts, unknownCerts []*x509.Certificate
	for _, cert := range certs {
		certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: cert.Raw})
		if _, err := tls.X509KeyPair(certPEM, privateKeyData); err == nil {
			currentCerts = append(currentCerts, cert)
			continue
		}
		if len(oldPrivateKeyData) > 0 {
			if _, err := tls.X509KeyPair(certPEM, oldPrivateKeyData); err == nil {
				oldCerts = append(oldCerts, cert)
				continue
			}
		}
		unknownCerts = append(unknownCerts, cert)
	}

	if len(currentCerts) == 0 {
		return errHint(
			fmt.Errorf("no certificate in '%s' matches the current private key", color.CyanString(certificateFile.path)),
			"Ensure the private key at '"+privateKeyFile.path+"' is correct, or re-create the certificate with 'vespa auth cert -f'",
		)
	}
	certInfo := func(cert *x509.Certificate) string {
		return fmt.Sprintf("CN=%s, issued %s, expires %s", cert.Subject.CommonName,
			cert.NotBefore.Format(time.RFC3339), cert.NotAfter.Format(time.RFC3339))
	}

	removeSet := make(map[*x509.Certificate]bool)

	if len(currentCerts) > 1 {
		cli.printWarning(fmt.Sprintf("%d certificates match the current private key", len(currentCerts)))
		newestCurrent := currentCerts[0]
		for _, cert := range currentCerts[1:] {
			if cert.NotAfter.After(newestCurrent.NotAfter) {
				newestCurrent = cert
			}
		}
		if force {
			for _, cert := range currentCerts {
				if cert != newestCurrent {
					removeSet[cert] = true
				}
			}
		} else {
			ok, err := cli.confirm(fmt.Sprintf(
				"Remove %d extra certificate(s) matching the current key, keeping the one expiring %s?",
				len(currentCerts)-1, newestCurrent.NotAfter.Format(time.RFC3339),
			), false)
			if err != nil {
				return err
			}
			if ok {
				for _, cert := range currentCerts {
					if cert != newestCurrent {
						removeSet[cert] = true
					}
				}
			}
		}
	}
	for _, cert := range oldCerts {
		if force {
			removeSet[cert] = true
		} else {
			cli.printInfo("This certificate is associated with the private key in ", oldPrivateKeyFile.path)
			ok, err := cli.confirm(fmt.Sprintf("Remove certificate %s?", certInfo(cert)), false)
			if err != nil {
				return err
			}
			if ok {
				removeSet[cert] = true
			}
		}
	}
	for _, cert := range unknownCerts {
		cli.printWarning("This certificate is not associated with any of your saved private keys")
		if force {
			removeSet[cert] = true
		} else {
			ok, err := cli.confirm(fmt.Sprintf("Remove certificate %s?", certInfo(cert)), false)
			if err != nil {
				return err
			}
			if ok {
				removeSet[cert] = true
			}
		}
	}

	if len(removeSet) == 0 {
		cli.printInfo("No certificates removed.")
		return nil
	}

	// Write all certs not selected for removal, preserving original order.
	var keepPEM []byte
	for _, cert := range certs {
		if !removeSet[cert] {
			keepPEM = append(keepPEM, pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: cert.Raw})...)
		}
	}
	if err := ioutil.AtomicWriteFile(certificateFile.path, keepPEM); err != nil {
		return fmt.Errorf("could not prune certificate file: %w", err)
	}
	cli.printSuccess("Pruned certificate file ", color.CyanString("'"+certificateFile.path+"'"))
	if ioutil.Exists(oldPrivateKeyFile.path) {
		cli.printInfo("Next step: deploy the application again, then you can safely remove ", color.CyanString("'"+oldPrivateKeyFile.path+"'"))
	} else {
		cli.printInfo("Next step: deploy the application again.")
	}
	if !skipApplicationPackage {
		return doCertAdd(cli, force, args)
	}
	return nil
}

func doCertAdd(cli *CLI, overwriteCertificate bool, args []string) error {
	targetType, err := cli.targetType(cloudTargetOnly)
	if err != nil {
		return err
	}
	app, err := cli.config.application()
	if err != nil {
		return err
	}
	pkg, err := cli.applicationPackageFrom(args, vespa.PackageOptions{})
	if err != nil {
		return err
	}
	if pkg.HasCertificate() && !overwriteCertificate {
		return errHint(fmt.Errorf("application package '%s' already contains a certificate", pkg.Path), "Use -f to force overwriting")
	}
	if pkg.IsZip() {
		return errHint(fmt.Errorf("cannot add certificate to compressed application package: '%s'", pkg.Path),
			"Try running 'mvn clean', then 'vespa auth cert add' and finally 'mvn package'")
	}
	tlsOptions, err := cli.config.readTLSOptions(app, targetType.name)
	if err != nil {
		return err
	}
	return copyCertificate(tlsOptions, cli, pkg)
}

func requireCertificate(force, ignoreZip bool, cli *CLI, target vespa.Target, pkg vespa.ApplicationPackage) error {
	if pkg.IsZip() {
		if ignoreZip {
			cli.printWarning("Cannot verify existence of "+color.CyanString("security/clients.pem")+" since '"+pkg.Path+"' is compressed",
				"Deployment to Vespa Cloud requires either certificate or token authentication",
				"See https://docs.vespa.ai/en/security/guide.html")
			return nil
		} else {
			hint := "Try running 'mvn clean', then 'vespa auth cert add' and finally 'mvn package'"
			return errHint(fmt.Errorf("cannot add certificate to compressed application package: '%s'", pkg.Path), hint)
		}
	}
	tlsOptions, err := cli.config.readTLSOptions(target.Deployment().Application, target.Type())
	if err != nil {
		return err
	}
	if force {
		return copyCertificate(tlsOptions, cli, pkg)
	}
	if pkg.HasCertificate() {
		if cli.isCI() {
			return nil // A matching certificate is not required in CI environments
		}
		if len(tlsOptions.CertificatePEM) == 0 {
			return errHint(fmt.Errorf("no certificate exists for %s", target.Deployment().Application.String()), "Try (re)creating the certificate with 'vespa auth cert'")
		}
		matches, err := pkg.HasMatchingCertificate(tlsOptions.CertificatePEM)
		if err != nil {
			return err
		}
		if !matches {
			return errHint(fmt.Errorf("certificate in %s does not match the stored key pair for %s",
				filepath.Join("security", "clients.pem"),
				target.Deployment().Application.String()),
				"If this application was deployed using a different application ID in the past, the matching key pair may be stored under a different ID in "+cli.config.homeDir,
				"Specify the matching application with --application, or add the current certificate to the package using --add-cert")
		}
		return nil
	}
	if cli.isTerminal() {
		cli.printWarning("Application package does not contain " + color.CyanString("security/clients.pem") + ", which is required for deployments to Vespa Cloud")
		if len(tlsOptions.CertificatePEM) == 0 {
			return errHint(fmt.Errorf("no certificate exists for %s", target.Deployment().Application.String()), "Try (re)creating the certificate with 'vespa auth cert'")
		}

		ok, err := cli.confirm("Do you want to copy existing certificate of application "+color.GreenString(target.Deployment().Application.String())+" into this application package?", true)
		if err != nil {
			return err
		}
		if ok {
			return copyCertificate(tlsOptions, cli, pkg)
		}
	}
	return errHint(fmt.Errorf("Deployment to Vespa Cloud requires either certificate or token authentication"),
		"See https://docs.vespa.ai/en/security/guide.html",
		"Pass --add-cert to use the certificate of the current application")
}

func copyCertificate(tlsOptions vespa.TLSOptions, cli *CLI, pkg vespa.ApplicationPackage) error {
	data, err := os.ReadFile(tlsOptions.CertificateFile)
	if err != nil {
		return errHint(fmt.Errorf("could not read certificate file: %w", err))
	}
	dstPath := filepath.Join(pkg.Path, "security", "clients.pem")
	if err := os.MkdirAll(filepath.Dir(dstPath), 0o755); err != nil {
		return fmt.Errorf("could not create security directory: %w", err)
	}
	err = ioutil.AtomicWriteFile(dstPath, data)
	if err == nil {
		cli.printSuccess("Copied certificate from '", tlsOptions.CertificateFile, "' to '", dstPath, "'")
	}
	return err
}
