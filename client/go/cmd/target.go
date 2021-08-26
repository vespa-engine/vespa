// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Models a target for Vespa commands
// author: bratseth

package cmd

import (
	"log"
	"strings"
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

func deployTarget() string {
	return getTarget(deployContext).deploy
}

func queryTarget() string {
	return getTarget(queryContext).query
}

func documentTarget() string {
	return getTarget(documentContext).document
}

func getTarget(targetContext context) *target {
	if strings.HasPrefix(targetArgument, "http") {
		// TODO: Add default ports if missing
		switch targetContext {
		case deployContext:
			return &target{
				deploy: targetArgument,
			}
		case queryContext:
			return &target{
				query: targetArgument,
			}
		case documentContext:
			return &target{
				document: targetArgument,
			}
		}
	}

	// Otherwise, target is a name

	if targetArgument == "" || targetArgument == "local" {
		return &target{
			deploy:   "http://127.0.0.1:19071",
			query:    "http://127.0.0.1:8080",
			document: "http://127.0.0.1:8080",
		}
	}

	if targetArgument == "cloud" {
		return nil // TODO
	}

	log.Printf("Unknown target '%s': Use %s, %s or an URL", color.Red(targetArgument), color.Cyan("local"), color.Cyan("cloud"))
	return nil
}
