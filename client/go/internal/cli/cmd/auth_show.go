// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa auth show command
// Author: arnej
package cmd

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/spf13/cobra"
)

func newAuthShowCmd(cli *CLI) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "show",
		Short: "Show authenticated user",
		Long: `Show which user (if any) is authenticated with "auth login"
`,
		Example:           "$ vespa auth show",
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			if _, err := cli.config.application(); err != nil {
				cmd.Flag("application").Value.Set("none.none")
				cmd.Flag("application").Changed = true
			}
			return doAuthShow(cli, args)
		},
	}
	return cmd
}

type userV1 struct {
	IsPublic bool `json:"isPublic"`
	IsCd     bool `json:"isCd"`
	User     struct {
		Email string `json:"email"`
	} `json:"user"`
	Tenants map[string]struct {
		Supported bool     `json:"supported"`
		Roles     []string `json:"roles"`
	} `json:"tenants"`
}

func doAuthShow(cli *CLI, args []string) error {
	target, err := cli.target(targetOptions{supportedType: cloudTargetOnly})
	if err != nil {
		return err
	}
	service, err := target.DeployService()
	if err != nil {
		return err
	}
	url := service.BaseURL + "/user/v1/user"
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return err
	}
	response, err := service.Do(req, time.Second*3)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	dec := json.NewDecoder(response.Body)
	var userResponse userV1
	if err = dec.Decode(&userResponse); err != nil {
		return err
	}
	var output bytes.Buffer
	fmt.Fprintf(&output, "Logged in as: %s\n", userResponse.User.Email)
	for tenant, data := range userResponse.Tenants {
		fmt.Fprintf(&output, "Available tenant: %s\n", tenant)
		for idx, role := range data.Roles {
			if idx == 0 {
				fmt.Fprintf(&output, "    your roles:")
			}
			fmt.Fprintf(&output, " %s", role)
		}
	}
	cli.printSuccess(output.String())
	return nil
}
