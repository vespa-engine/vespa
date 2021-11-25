// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Helpers used by multiple sub-commands.
// Author: mpolden

package cmd

import (
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/vespa"
)

var exitFunc = os.Exit // To allow overriding Exit in tests

func fatalErrHint(err error, hints ...string) {
	printErrHint(err, hints...)
	exitFunc(1)
}

func fatalErr(err error, msg ...interface{}) {
	printErr(err, msg...)
	exitFunc(1)
}

func printErrHint(err error, hints ...string) {
	if err != nil {
		printErr(nil, err.Error())
	}
	for _, hint := range hints {
		fmt.Fprintln(stderr, color.Cyan("Hint:"), hint)
	}
}

func printErr(err error, msg ...interface{}) {
	if len(msg) > 0 {
		fmt.Fprintln(stderr, color.Red("Error:"), fmt.Sprint(msg...))
	}
	if err != nil {
		fmt.Fprintln(stderr, color.Yellow(err))
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

func deploymentFromArgs() vespa.Deployment {
	zone, err := vespa.ZoneFromString(zoneArg)
	if err != nil {
		fatalErrHint(err, "Zone format is <env>.<region>")
	}
	app := getApplication()
	return vespa.Deployment{Application: app, Zone: zone}
}

func applicationSource(args []string) string {
	if len(args) > 0 {
		return args[0]
	}
	return "."
}

func getApplication() vespa.ApplicationID {
	cfg, err := LoadConfig()
	if err != nil {
		fatalErr(err, "Could not load config")
		return vespa.ApplicationID{}
	}
	app, err := cfg.Get(applicationFlag)
	if err != nil {
		fatalErrHint(err, "No application specified. Try the --"+applicationFlag+" flag")
		return vespa.ApplicationID{}
	}
	application, err := vespa.ApplicationFromString(app)
	if err != nil {
		fatalErrHint(err, "Application format is <tenant>.<app>.<instance>")
		return vespa.ApplicationID{}
	}
	return application
}

func getTargetType() string {
	cfg, err := LoadConfig()
	if err != nil {
		fatalErr(err, "Could not load config")
		return ""
	}
	target, err := cfg.Get(targetFlag)
	if err != nil {
		fatalErr(err, "A valid target must be specified")
	}
	return target
}

func getService(service string, sessionOrRunID int64, cluster string) *vespa.Service {
	t := getTarget()
	timeout := time.Duration(waitSecsArg) * time.Second
	if timeout > 0 {
		log.Printf("Waiting up to %d %s for service to become available ...", color.Cyan(waitSecsArg), color.Cyan("seconds"))
	}
	s, err := t.Service(service, timeout, sessionOrRunID, cluster)
	if err != nil {
		fatalErr(err, "Invalid service: ", service)
	}
	return s
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

func getTarget() vespa.Target {
	targetType := getTargetType()
	if strings.HasPrefix(targetType, "http") {
		return vespa.CustomTarget(targetType)
	}
	switch targetType {
	case "local":
		return vespa.LocalTarget()
	case "cloud":
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return nil
		}
		deployment := deploymentFromArgs()
		endpoints := getEndpointsFromEnv()

		var apiKey []byte = nil
		apiKey, err = ioutil.ReadFile(cfg.APIKeyPath(deployment.Application.Tenant))
		if !vespa.Auth0AccessTokenEnabled() && endpoints == nil {
			if err != nil {
				fatalErrHint(err, "Deployment to cloud requires an API key. Try 'vespa api-key'")
			}
		}
		privateKeyFile, err := cfg.PrivateKeyPath(deployment.Application)
		if err != nil {
			fatalErr(err)
			return nil
		}
		certificateFile, err := cfg.CertificatePath(deployment.Application)
		if err != nil {
			fatalErr(err)
			return nil
		}
		kp, err := tls.LoadX509KeyPair(certificateFile, privateKeyFile)
		if err != nil {
			var msg string
			if vespa.Auth0AccessTokenEnabled() {
				msg = "Deployment to cloud requires a certificate. Try 'vespa auth cert'"
			} else {
				msg = "Deployment to cloud requires a certificate. Try 'vespa cert'"
			}
			fatalErrHint(err, msg)
		}
		var cloudAuth string
		if vespa.Auth0AccessTokenEnabled() {
			cloudAuth, err = cfg.Get(cloudAuthFlag)
			if err != nil {
				if apiKey != nil {
					cloudAuth = "api-key"
				} else {
					cloudAuth = "access-token"
				}
			}
		} else {
			cloudAuth = ""
		}

		return vespa.CloudTarget(getApiURL(), deployment, apiKey,
			vespa.TLSOptions{
				KeyPair:         kp,
				CertificateFile: certificateFile,
				PrivateKeyFile:  privateKeyFile,
			},
			vespa.LogOptions{
				Writer: stdout,
				Level:  vespa.LogLevel(logLevelArg),
			},
			cfg.AuthConfigPath(),
			getSystemName(),
			cloudAuth,
			endpoints)
	}
	fatalErrHint(fmt.Errorf("Invalid target: %s", targetType), "Valid targets are 'local', 'cloud' or an URL")
	return nil
}

func waitForService(service string, sessionOrRunID int64) {
	s := getService(service, sessionOrRunID, "")
	timeout := time.Duration(waitSecsArg) * time.Second
	if timeout > 0 {
		log.Printf("Waiting up to %d %s for service to become ready ...", color.Cyan(waitSecsArg), color.Cyan("seconds"))
	}
	status, err := s.Wait(timeout)
	if status/100 == 2 {
		log.Print(s.Description(), " at ", color.Cyan(s.BaseURL), " is ", color.Green("ready"))
	} else {
		if err == nil {
			err = fmt.Errorf("Status %d", status)
		}
		fatalErr(err, s.Description(), " at ", color.Cyan(s.BaseURL), " is ", color.Red("not ready"))
	}
}

func getDeploymentOpts(cfg *Config, pkg vespa.ApplicationPackage, target vespa.Target) vespa.DeploymentOpts {
	opts := vespa.DeploymentOpts{ApplicationPackage: pkg, Target: target}
	if opts.IsCloud() {
		deployment := deploymentFromArgs()
		if !opts.ApplicationPackage.HasCertificate() {
			var msg string
			if vespa.Auth0AccessTokenEnabled() {
				msg = "Try 'vespa auth cert'"
			} else {
				msg = "Try 'vespa cert'"
			}
			fatalErrHint(fmt.Errorf("Missing certificate in application package"), "Applications in Vespa Cloud require a certificate", msg)
			return opts
		}
		var err error
		opts.APIKey, err = cfg.ReadAPIKey(deployment.Application.Tenant)
		if !vespa.Auth0AccessTokenEnabled() {
			if err != nil {
				fatalErrHint(err, "Deployment to cloud requires an API key. Try 'vespa api-key'")
				return opts
			}
		}
		opts.Deployment = deployment
	}
	return opts
}

func getEndpointsFromEnv() map[string]string {
	endpointsString := getEndpointsOverride()
	if endpointsString == "" {
		return nil
	}

	var endpoints endpoints
	urlsByCluster := make(map[string]string)
	if err := json.Unmarshal([]byte(endpointsString), &endpoints); err != nil {
		fatalErrHint(err, "Endpoints must be valid JSON")
	}
	if len(endpoints.Endpoints) == 0 {
		fatalErr(fmt.Errorf("endpoints must be non-empty"))
	}
	for _, endpoint := range endpoints.Endpoints {
		urlsByCluster[endpoint.Cluster] = endpoint.URL
	}
	return urlsByCluster
}

type endpoints struct {
	Endpoints []endpoint `json:"endpoints"`
}

type endpoint struct {
	Cluster string `json:"cluster"`
	URL     string `json:"url"`
}
