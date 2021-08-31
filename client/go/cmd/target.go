// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Models a target for Vespa commands
// author: bratseth

package cmd

import (
	"log"
	"strings"

	"github.com/spf13/cobra"
)

const (
	applicationFlag = "application"
	targetFlag      = "target"
	cloudApi        = "https://api.vespa-external.aws.oath.cloud:4443"
)

var (
	targetArg      string
	applicationArg string
)

type target struct {
	deploy   string
	query    string
	document string
}

type context int32

const (
	deployContext   context = 0
	queryContext    context = 1
	documentContext context = 2
)

func addTargetFlag(command *cobra.Command) {
	command.PersistentFlags().StringVarP(&targetArg, targetFlag, "t", "local", "The name or URL of the recipient of this command")
	bindFlagToConfig(targetFlag, command)
}

func addApplicationFlag(command *cobra.Command) {
	command.PersistentFlags().StringVarP(&applicationArg, applicationFlag, "a", "", "The application name to use for deployment")
	bindFlagToConfig(applicationFlag, command)
}

func deployTarget() string {
	return getTarget(deployContext).deploy
}

func queryTarget() string {
	return getTarget(queryContext).query
}

func documentTarget() string {
	return getTarget(documentContext).document
}

func getApplication() string {
	app, err := getOption(applicationFlag)
	if err != nil {
		log.Fatalf("a valid application must be specified")
	}
	return app
}

func getTargetType() string {
	target, err := getOption(targetFlag)
	if err != nil {
		log.Fatalf("a valid target must be specified")
	}
	return target
}

func getTarget(targetContext context) *target {
	targetValue := getTargetType()
	if strings.HasPrefix(targetValue, "http") {
		// TODO: Add default ports if missing
		switch targetContext {
		case deployContext:
			return &target{
				deploy: targetValue,
			}
		case queryContext:
			return &target{
				query: targetValue,
			}
		case documentContext:
			return &target{
				document: targetValue,
			}
		}
	}

	// Otherwise, target is a name

	if targetValue == "" || targetValue == "local" {
		return &target{
			deploy:   "http://127.0.0.1:19071",
			query:    "http://127.0.0.1:8080",
			document: "http://127.0.0.1:8080",
		}
	}

	if targetValue == "cloud" {
		return &target{deploy: cloudApi}
	}

	log.Printf("Unknown target '%s': Use %s, %s or an URL", color.Red(targetValue), color.Cyan("local"), color.Cyan("cloud"))
	return nil
}
