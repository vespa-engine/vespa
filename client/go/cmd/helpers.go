// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Helpers used by multiple sub-commands.
// Author: mpolden

package cmd

import (
	"crypto/tls"
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/vespa"
)

const defaultConsoleURL = "https://console.vespa.oath.cloud"

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
	printErr(nil, err.Error())
	for _, hint := range hints {
		log.Print(color.Cyan("Hint: "), hint)
	}
}

func printErr(err error, msg ...interface{}) {
	if len(msg) > 0 {
		log.Print(color.Red("Error: "), fmt.Sprint(msg...))
	}
	if err != nil {
		log.Print(color.Yellow(err))
	}
}

func printSuccess(msg ...interface{}) {
	log.Print(color.Green("Success: "), fmt.Sprint(msg...))
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

func getService(service string, sessionOrRunID int64) *vespa.Service {
	t := getTarget()
	timeout := time.Duration(waitSecsArg) * time.Second
	if timeout > 0 {
		log.Printf("Waiting up to %d %s for services to become available ...", color.Cyan(waitSecsArg), color.Cyan("seconds"))
	}
	if err := t.DiscoverServices(timeout, sessionOrRunID); err != nil {
		fatalErr(err, "Services unavailable")
	}
	s, err := t.Service(service)
	if err != nil {
		fatalErr(err, "Invalid service")
	}
	return s
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
		deployment := deploymentFromArgs()
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return nil
		}
		apiKey, err := ioutil.ReadFile(cfg.APIKeyPath(deployment.Application.Tenant))
		if err != nil {
			fatalErrHint(err, "Deployment to cloud requires an API key. Try 'vespa api-key'")
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
			fatalErrHint(err, "Deployment to cloud requires a certificate. Try 'vespa cert'")
		}
		return vespa.CloudTarget(deployment, kp, apiKey)
	}
	fatalErrHint(fmt.Errorf("Invalid target: %s", targetType), "Valid targets are 'local', 'cloud' or an URL")
	return nil
}

func waitForService(service string, sessionOrRunID int64) {
	s := getService(service, sessionOrRunID)
	timeout := time.Duration(waitSecsArg) * time.Second
	if timeout > 0 {
		log.Printf("Waiting up to %d %s for service to become ready ...", color.Cyan(waitSecsArg), color.Cyan("seconds"))
	}
	status, err := s.Wait(timeout)
	if status/100 == 2 {
		log.Print(s.Description(), " at ", color.Cyan(s.BaseURL), " is ", color.Green("ready"))
	} else {
		log.Print(s.Description(), " at ", color.Cyan(s.BaseURL), " is ", color.Red("not ready"))
		if err == nil {
			log.Print(color.Yellow(fmt.Sprintf("Status %d", status)))
		} else {
			log.Print(color.Yellow(err))
		}
	}
}
