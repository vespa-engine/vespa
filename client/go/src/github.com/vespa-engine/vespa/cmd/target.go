// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Models a target for Vespa commands
// author: bratseth

package cmd

import (
    "github.com/vespa-engine/vespa/utils"
    "strings"
)

type target struct {
    deploy string
    query string
    document string
}

type context int32
const (
    deployContext   context = 0
    queryContext    context = 1
    documentContext context = 2
)

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
            deploy: "http://127.0.0.1:19071",
            query: "http://127.0.0.1:8080",
            document: "http://127.0.0.1:8080",
        }
    }

    if targetArgument == "cloud" {
        return nil // TODO
    }

    utils.Error("Unknown target argument '" + targetArgument + ": Use 'local', 'cloud' or an URL")
    return nil
}