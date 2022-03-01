// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Helpers used by multiple sub-commands.
// Author: mpolden

package cmd

import (
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/build"
	"github.com/vespa-engine/vespa/client/go/version"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func printErrHint(err error, hints ...string) {
	fmt.Fprintln(stderr, color.Red("Error:"), err)
	for _, hint := range hints {
		fmt.Fprintln(stderr, color.Cyan("Hint:"), hint)
	}
}

func printSuccess(msg ...interface{}) {
	log.Print(color.Green("Success: "), fmt.Sprint(msg...))
}

func printWarning(msg string, hints ...string) {
	fmt.Fprintln(stderr, color.Yellow("Warning:"), msg)
	for _, hint := range hints {
		fmt.Fprintln(stderr, color.Cyan("Hint:"), hint)
	}
}

func athenzPath(filename string) (string, error) {
	userHome, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(userHome, ".athenz", filename), nil
}

func athenzKeyPair() (tls.Certificate, error) {
	certFile, err := athenzPath("cert")
	if err != nil {
		return tls.Certificate{}, err
	}
	keyFile, err := athenzPath("key")
	if err != nil {
		return tls.Certificate{}, err
	}
	kp, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		return tls.Certificate{}, err
	}
	cert, err := x509.ParseCertificate(kp.Certificate[0])
	if err != nil {
		return tls.Certificate{}, err
	}
	now := time.Now()
	expiredAt := cert.NotAfter
	if expiredAt.Before(now) {
		delta := now.Sub(expiredAt).Truncate(time.Second)
		return tls.Certificate{}, errHint(fmt.Errorf("certificate %s expired at %s (%s ago)", certFile, cert.NotAfter, delta), "Try renewing certificate with 'athenz-user-cert'")
	}
	return kp, nil
}

func vespaCliHome() (string, error) {
	home := os.Getenv("VESPA_CLI_HOME")
	if home == "" {
		userHome, err := os.UserHomeDir()
		if err != nil {
			return "", err
		}
		home = filepath.Join(userHome, ".vespa")
	}
	if err := os.MkdirAll(home, 0700); err != nil {
		return "", err
	}
	return home, nil
}

func vespaCliCacheDir() (string, error) {
	cacheDir := os.Getenv("VESPA_CLI_CACHE_DIR")
	if cacheDir == "" {
		userCacheDir, err := os.UserCacheDir()
		if err != nil {
			return "", err
		}
		cacheDir = filepath.Join(userCacheDir, "vespa")
	}
	if err := os.MkdirAll(cacheDir, 0755); err != nil {
		return "", err
	}
	return cacheDir, nil
}

func deploymentFromArgs(system vespa.System) (vespa.Deployment, error) {
	zone := system.DefaultZone
	var err error
	if zoneArg != "" {
		zone, err = vespa.ZoneFromString(zoneArg)
		if err != nil {
			return vespa.Deployment{}, err
		}
	}
	app, err := getApplication()
	if err != nil {
		return vespa.Deployment{}, err
	}
	return vespa.Deployment{System: system, Application: app, Zone: zone}, nil
}

func applicationSource(args []string) string {
	if len(args) > 0 {
		return args[0]
	}
	return "."
}

func getApplication() (vespa.ApplicationID, error) {
	cfg, err := LoadConfig()
	if err != nil {
		return vespa.ApplicationID{}, err
	}
	app, ok := cfg.Get(applicationFlag)
	if !ok {
		return vespa.ApplicationID{}, errHint(fmt.Errorf("no application specified: %w", err), "Try the --"+applicationFlag+" flag")
	}
	application, err := vespa.ApplicationFromString(app)
	if err != nil {
		return vespa.ApplicationID{}, errHint(err, "application format is <tenant>.<app>.<instance>")
	}
	return application, nil
}

func getTargetType() (string, error) {
	cfg, err := LoadConfig()
	if err != nil {
		return "", err
	}
	target, ok := cfg.Get(targetFlag)
	if !ok {
		return "", fmt.Errorf("target is unset")
	}
	return target, nil
}

func getService(service string, sessionOrRunID int64, cluster string) (*vespa.Service, error) {
	t, err := getTarget()
	if err != nil {
		return nil, err
	}
	timeout := time.Duration(waitSecsArg) * time.Second
	if timeout > 0 {
		log.Printf("Waiting up to %d %s for %s service to become available ...", color.Cyan(waitSecsArg), color.Cyan("seconds"), color.Cyan(service))
	}
	s, err := t.Service(service, timeout, sessionOrRunID, cluster)
	if err != nil {
		return nil, fmt.Errorf("service '%s' is unavailable: %w", service, err)
	}
	return s, nil
}

func getEndpointsOverride() string { return os.Getenv("VESPA_CLI_ENDPOINTS") }

func getSystem(targetType string) (vespa.System, error) {
	name := os.Getenv("VESPA_CLI_CLOUD_SYSTEM")
	if name != "" {
		return vespa.GetSystem(name)
	}
	switch targetType {
	case vespa.TargetHosted:
		return vespa.MainSystem, nil
	case vespa.TargetCloud:
		return vespa.PublicSystem, nil
	}
	return vespa.System{}, fmt.Errorf("no default system found for %s target", targetType)
}

func getTarget() (vespa.Target, error) {
	clientVersion, err := version.Parse(build.Version)
	if err != nil {
		return nil, err
	}
	target, err := createTarget()
	if err != nil {
		return nil, err
	}
	if err := target.CheckVersion(clientVersion); err != nil {
		printErrHint(err, "This is not a fatal error, but this version may not work as expected", "Try 'vespa version' to check for a new version")
	}
	return target, nil
}

func createTarget() (vespa.Target, error) {
	targetType, err := getTargetType()
	if err != nil {
		return nil, err
	}
	if strings.HasPrefix(targetType, "http") {
		return vespa.CustomTarget(targetType), nil
	}
	switch targetType {
	case vespa.TargetLocal:
		return vespa.LocalTarget(), nil
	case vespa.TargetCloud, vespa.TargetHosted:
		return createCloudTarget(targetType)
	}
	return nil, errHint(fmt.Errorf("invalid target: %s", targetType), "Valid targets are 'local', 'cloud', 'hosted' or an URL")
}

func createCloudTarget(targetType string) (vespa.Target, error) {
	cfg, err := LoadConfig()
	if err != nil {
		return nil, err
	}
	system, err := getSystem(targetType)
	if err != nil {
		return nil, err
	}
	deployment, err := deploymentFromArgs(system)
	if err != nil {
		return nil, err
	}
	endpoints, err := getEndpointsFromEnv()
	if err != nil {
		return nil, err
	}
	var (
		apiKey               []byte
		authConfigPath       string
		apiTLSOptions        vespa.TLSOptions
		deploymentTLSOptions vespa.TLSOptions
	)
	if targetType == vespa.TargetCloud {
		if cfg.UseAPIKey(system, deployment.Application.Tenant) {
			apiKey, err = cfg.ReadAPIKey(deployment.Application.Tenant)
			if err != nil {
				return nil, err
			}
		}
		authConfigPath = cfg.AuthConfigPath()
		kp, err := cfg.X509KeyPair(deployment.Application)
		if err != nil {
			return nil, errHint(err, "Deployment to cloud requires a certificate. Try 'vespa auth cert'")
		}
		deploymentTLSOptions = vespa.TLSOptions{
			KeyPair:         kp.KeyPair,
			CertificateFile: kp.CertificateFile,
			PrivateKeyFile:  kp.PrivateKeyFile,
		}
	} else if targetType == vespa.TargetHosted {
		kp, err := athenzKeyPair()
		if err != nil {
			return nil, err
		}
		apiTLSOptions = vespa.TLSOptions{KeyPair: kp}
		deploymentTLSOptions = apiTLSOptions
	} else {
		return nil, fmt.Errorf("invalid cloud target: %s", targetType)
	}
	apiOptions := vespa.APIOptions{
		System:         system,
		TLSOptions:     apiTLSOptions,
		APIKey:         apiKey,
		AuthConfigPath: authConfigPath,
	}
	deploymentOptions := vespa.CloudDeploymentOptions{
		Deployment:  deployment,
		TLSOptions:  deploymentTLSOptions,
		ClusterURLs: endpoints,
	}
	logOptions := vespa.LogOptions{
		Writer: stdout,
		Level:  vespa.LogLevel(logLevelArg),
	}
	return vespa.CloudTarget(apiOptions, deploymentOptions, logOptions)
}

func waitForService(service string, sessionOrRunID int64) error {
	s, err := getService(service, sessionOrRunID, "")
	if err != nil {
		return err
	}
	timeout := time.Duration(waitSecsArg) * time.Second
	if timeout > 0 {
		log.Printf("Waiting up to %d %s for service to become ready ...", color.Cyan(waitSecsArg), color.Cyan("seconds"))
	}
	status, err := s.Wait(timeout)
	if status/100 == 2 {
		log.Print(s.Description(), " at ", color.Cyan(s.BaseURL), " is ", color.Green("ready"))
	} else {
		if err == nil {
			err = fmt.Errorf("status %d", status)
		}
		return fmt.Errorf("%s at %s is %s: %w", s.Description(), color.Cyan(s.BaseURL), color.Red("not ready"), err)
	}
	return nil
}

func getDeploymentOptions(cfg *Config, pkg vespa.ApplicationPackage, target vespa.Target) (vespa.DeploymentOptions, error) {
	opts := vespa.DeploymentOptions{ApplicationPackage: pkg, Target: target}
	if opts.IsCloud() {
		if target.Type() == vespa.TargetCloud && !opts.ApplicationPackage.HasCertificate() {
			hint := "Try 'vespa auth cert'"
			return vespa.DeploymentOptions{}, errHint(fmt.Errorf("missing certificate in application package"), "Applications in Vespa Cloud require a certificate", hint)
		}
	}
	opts.Timeout = time.Duration(waitSecsArg) * time.Second
	return opts, nil
}

func getEndpointsFromEnv() (map[string]string, error) {
	endpointsString := getEndpointsOverride()
	if endpointsString == "" {
		return nil, nil
	}

	var endpoints endpoints
	urlsByCluster := make(map[string]string)
	if err := json.Unmarshal([]byte(endpointsString), &endpoints); err != nil {
		return nil, fmt.Errorf("endpoints must be valid json: %w", err)
	}
	if len(endpoints.Endpoints) == 0 {
		return nil, fmt.Errorf("endpoints must be non-empty")
	}
	for _, endpoint := range endpoints.Endpoints {
		urlsByCluster[endpoint.Cluster] = endpoint.URL
	}
	return urlsByCluster, nil
}

type endpoints struct {
	Endpoints []endpoint `json:"endpoints"`
}

type endpoint struct {
	Cluster string `json:"cluster"`
	URL     string `json:"url"`
}
