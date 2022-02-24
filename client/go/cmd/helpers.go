// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Helpers used by multiple sub-commands.
// Author: mpolden

package cmd

import (
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

func deploymentFromArgs() (vespa.Deployment, error) {
	zone, err := vespa.ZoneFromString(zoneArg)
	if err != nil {
		return vespa.Deployment{}, err
	}
	app, err := getApplication()
	if err != nil {
		return vespa.Deployment{}, err
	}
	return vespa.Deployment{Application: app, Zone: zone}, nil
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
	app, err := cfg.Get(applicationFlag)
	if err != nil {
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
	target, err := cfg.Get(targetFlag)
	if err != nil {
		return "", fmt.Errorf("invalid target: %w", err)
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

func getSystem() string { return os.Getenv("VESPA_CLI_CLOUD_SYSTEM") }

func getSystemName() string {
	if getSystem() == "publiccd" {
		return "publiccd"
	}
	return "public"
}

func getConsoleURL() string {
	if getSystem() == "publiccd" {
		return "https://console-cd.vespa.oath.cloud"
	}
	return "https://console.vespa.oath.cloud"

}

func getApiURL() string {
	if getSystem() == "publiccd" {
		return "https://api.vespa-external-cd.aws.oath.cloud:4443"
	}
	return "https://api.vespa-external.aws.oath.cloud:4443"
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
	case "local":
		return vespa.LocalTarget(), nil
	case "cloud":
		cfg, err := LoadConfig()
		if err != nil {
			return nil, err
		}
		deployment, err := deploymentFromArgs()
		if err != nil {
			return nil, err
		}
		endpoints, err := getEndpointsFromEnv()
		if err != nil {
			return nil, err
		}

		var apiKey []byte = nil
		if cfg.UseAPIKey(deployment.Application.Tenant) {
			apiKey, err = cfg.ReadAPIKey(deployment.Application.Tenant)
			if err != nil {
				return nil, err
			}
		}
		kp, err := cfg.X509KeyPair(deployment.Application)
		if err != nil {
			return nil, errHint(err, "Deployment to cloud requires a certificate. Try 'vespa auth cert'")
		}

		return vespa.CloudTarget(
			getApiURL(),
			deployment,
			apiKey,
			vespa.TLSOptions{
				KeyPair:         kp.KeyPair,
				CertificateFile: kp.CertificateFile,
				PrivateKeyFile:  kp.PrivateKeyFile,
			},
			vespa.LogOptions{
				Writer: stdout,
				Level:  vespa.LogLevel(logLevelArg),
			},
			cfg.AuthConfigPath(),
			getSystemName(),
			endpoints,
		), nil
	}
	return nil, errHint(fmt.Errorf("invalid target: %s", targetType), "Valid targets are 'local', 'cloud' or an URL")
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

func getDeploymentOpts(cfg *Config, pkg vespa.ApplicationPackage, target vespa.Target) (vespa.DeploymentOpts, error) {
	opts := vespa.DeploymentOpts{ApplicationPackage: pkg, Target: target}
	if opts.IsCloud() {
		deployment, err := deploymentFromArgs()
		if err != nil {
			return vespa.DeploymentOpts{}, err
		}
		if !opts.ApplicationPackage.HasCertificate() {
			hint := "Try 'vespa auth cert'"
			return vespa.DeploymentOpts{}, errHint(fmt.Errorf("missing certificate in application package"), "Applications in Vespa Cloud require a certificate", hint)
		}
		if cfg.UseAPIKey(deployment.Application.Tenant) {
			opts.APIKey, err = cfg.ReadAPIKey(deployment.Application.Tenant)
			if err != nil {
				return vespa.DeploymentOpts{}, err
			}
		}
		opts.Deployment = deployment
	}
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
