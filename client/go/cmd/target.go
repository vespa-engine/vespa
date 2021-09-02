// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Models a target for Vespa commands
// author: bratseth

package cmd

import (
	"crypto/tls"
	"fmt"
	"log"
	"path/filepath"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/vespa"
)

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
		log.Printf("Waiting %d %s for service discovery to complete ...", color.Cyan(waitSecsArg), color.Cyan("seconds"))
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
