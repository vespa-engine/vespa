// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Helpers used by multiple sub-commands.
// Author: mpolden

package cmd

import (
	"crypto/tls"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/vespa"
)

var exitFunc = os.Exit // To allow overriding Exit in tests

func printErrHint(err error, hints ...string) {
	printErr(nil, err.Error())
	for _, hint := range hints {
		log.Print(color.Cyan("Hint: "), hint)
	}
	exitFunc(1)
}

func printErr(err error, msg ...interface{}) {
	if len(msg) > 0 {
		log.Print(color.Red("Error: "), fmt.Sprint(msg...))
	}
	if err != nil {
		log.Print(color.Yellow(err))
	}
	exitFunc(1)
}

func printSuccess(msg ...interface{}) {
	log.Print(color.Green("Success: "), fmt.Sprint(msg...))
}

func readAPIKey(tenant string) []byte {
	configDir := configDir("")
	apiKeyPath := filepath.Join(configDir, tenant+".api-key.pem")
	key, err := os.ReadFile(apiKeyPath)
	if err != nil {
		printErrHint(err, "Deployment to cloud requires an API key. Try 'vespa api-key'")
	}
	return key
}

func deploymentFromArgs() vespa.Deployment {
	zone, err := vespa.ZoneFromString(zoneArg)
	if err != nil {
		printErrHint(err, "Zone format is <env>.<region>")
	}
	app, err := vespa.ApplicationFromString(getApplication())
	if err != nil {
		printErrHint(err, "Application format is <tenant>.<app>.<instance>")
	}
	return vespa.Deployment{Application: app, Zone: zone}
}

func applicationSource(args []string) string {
	if len(args) > 0 {
		return args[0]
	}
	return "."
}

func getApplication() string {
	app, err := getOption(applicationFlag)
	if err != nil {
		printErr(err, "A valid application must be specified")
	}
	return app
}

func getTargetType() string {
	target, err := getOption(targetFlag)
	if err != nil {
		printErr(err, "A valid target must be specified")
	}
	return target
}

func getService(service string) *vespa.Service {
	t := getTarget()
	timeout := time.Duration(waitSecsArg) * time.Second
	if timeout > 0 {
		log.Printf("Waiting up to %d %s for service discovery to complete ...", color.Cyan(waitSecsArg), color.Cyan("seconds"))
	}
	if err := t.DiscoverServices(timeout); err != nil {
		printErr(err, "Failed to discover services")
	}
	s, err := t.Service(service)
	if err != nil {
		printErr(err, "Invalid service")
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
		apiKey := readAPIKey(deployment.Application.Tenant)
		configDir := configDir(deployment.Application.String())
		privateKeyFile := filepath.Join(configDir, "data-plane-private-key.pem")
		certificateFile := filepath.Join(configDir, "data-plane-public-cert.pem")
		kp, err := tls.LoadX509KeyPair(certificateFile, privateKeyFile)
		if err != nil {
			printErr(err, "Could not read key pair")
		}
		return vespa.CloudTarget(deployment, kp, apiKey)
	}
	printErrHint(fmt.Errorf("Invalid target: %s", targetType), "Valid targets are 'local', 'cloud' or an URL")
	return nil
}

func waitForService(service string) {
	s := getService(service)
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
