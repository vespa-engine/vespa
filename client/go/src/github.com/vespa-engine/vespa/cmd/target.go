// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Models a target for Vespa commands
// author: bratseth

package cmd

import (
    "github.com/vespa-engine/vespa/utils"
    "strings"
)

type Target struct {
    deploy string
    query string
    document string
}

type Context int32
const (
    deployContext Context = 0
    queryContext   Context = 1
    documentContext  Context = 2
)

func getTarget(targetContext Context) *Target {
    if strings.HasPrefix(targetArgument, "http") {
        // TODO: Add default ports if missing
        switch targetContext {
            case deployContext:
                return &Target{
                    deploy: targetArgument,
                }
            case queryContext:
                return &Target{
                    query: targetArgument,
                }
            case documentContext:
                return &Target{
                    document: targetArgument,
                }
        }
    }

    // Otherwise, target is a name

    if targetArgument == "" || targetArgument == "local" {
        return &Target{
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