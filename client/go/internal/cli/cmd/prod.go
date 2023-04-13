// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"
	"strings"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/xml"
)

func newProdCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "prod",
		Short: "Deploy an application package to production in Vespa Cloud",
		Long: `Deploy an application package to production in Vespa Cloud.

Configure and deploy your application package to production in Vespa Cloud.`,
		Example: `$ vespa prod init
$ vespa prod submit`,
		DisableAutoGenTag: true,
		SilenceUsage:      false,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return fmt.Errorf("invalid command: %s", args[0])
		},
	}
}

func newProdInitCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:   "init",
		Short: "Modify service.xml and deployment.xml for production deployment",
		Long: `Modify service.xml and deployment.xml for production deployment.

Only basic deployment configuration is available through this command. For
advanced configuration see the relevant Vespa Cloud documentation and make
changes to deployment.xml and services.xml directly.

Reference:
https://cloud.vespa.ai/en/reference/services
https://cloud.vespa.ai/en/reference/deployment`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			pkg, err := cli.applicationPackageFrom(args, false)
			if err != nil {
				return err
			}
			if pkg.IsZip() {
				return errHint(fmt.Errorf("cannot modify compressed application package %s", pkg.Path),
					"Try running 'mvn clean' and run this command again")
			}

			deploymentXML, err := readDeploymentXML(pkg)
			if err != nil {
				return fmt.Errorf("could not read deployment.xml: %w", err)
			}
			servicesXML, err := readServicesXML(pkg)
			if err != nil {
				return fmt.Errorf("a services.xml declaring your cluster(s) must exist: %w", err)
			}
			target, err := cli.target(targetOptions{noCertificate: true})
			if err != nil {
				return err
			}

			fmt.Fprint(cli.Stdout, "This will modify any existing ", color.YellowString("deployment.xml"), " and ", color.YellowString("services.xml"),
				"!\nBefore modification a backup of the original file will be created.\n\n")
			fmt.Fprint(cli.Stdout, "A default value is suggested (shown inside brackets) based on\nthe files' existing contents. Press enter to use it.\n\n")
			fmt.Fprint(cli.Stdout, "Abort the configuration at any time by pressing Ctrl-C. The\nfiles will remain untouched.\n\n")
			fmt.Fprint(cli.Stdout, "See this guide for sizing a Vespa deployment:\n", color.GreenString("https://docs.vespa.ai/en/performance/sizing-search.html\n\n"))
			r := bufio.NewReader(cli.Stdin)
			deploymentXML, err = updateRegions(cli, r, deploymentXML, target.Deployment().System)
			if err != nil {
				return err
			}
			servicesXML, err = updateNodes(cli, r, servicesXML)
			if err != nil {
				return err
			}

			fmt.Fprintln(cli.Stdout)
			if err := writeWithBackup(cli.Stdout, pkg, "deployment.xml", deploymentXML.String()); err != nil {
				return err
			}
			if err := writeWithBackup(cli.Stdout, pkg, "services.xml", servicesXML.String()); err != nil {
				return err
			}
			return nil
		},
	}
}

func newProdSubmitCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:   "submit",
		Short: "Submit an application for production deployment",
		Long: `Submit an application for production deployment.

This commands uploads an application package to Vespa Cloud and deploys it to
the production zones specified in deployment.xml.

Nodes are allocated to the application according to resources specified in
services.xml.

For more information about production deployments in Vespa Cloud see:
https://cloud.vespa.ai/en/production-deployment
https://cloud.vespa.ai/en/automated-deployments`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Example: `$ mvn package # when adding custom Java components
$ vespa prod submit`,
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := cli.target(targetOptions{noCertificate: true})
			if err != nil {
				return err
			}
			if target.Type() != vespa.TargetCloud {
				// TODO: Add support for hosted
				return fmt.Errorf("prod submit does not support %s target", target.Type())
			}
			pkg, err := cli.applicationPackageFrom(args, true)
			if err != nil {
				return err
			}
			if !pkg.HasDeployment() {
				return errHint(fmt.Errorf("no deployment.xml found"), "Try creating one with vespa prod init")
			}
			if err := verifyTests(cli, pkg); err != nil {
				return err
			}
			opts, err := cli.createDeploymentOptions(pkg, target)
			if err != nil {
				return err
			}
			if err := vespa.Submit(opts); err != nil {
				return fmt.Errorf("could not submit application for deployment: %w", err)
			} else {
				cli.printSuccess("Submitted ", color.CyanString(pkg.Path), " for deployment")
				log.Printf("See %s for deployment progress\n", color.CyanString(fmt.Sprintf("%s/tenant/%s/application/%s/prod/deployment",
					opts.Target.Deployment().System.ConsoleURL, opts.Target.Deployment().Application.Tenant, opts.Target.Deployment().Application.Application)))
			}
			return nil
		},
	}
}

func writeWithBackup(stdout io.Writer, pkg vespa.ApplicationPackage, filename, contents string) error {
	dst := filepath.Join(pkg.Path, filename)
	if util.PathExists(dst) {
		data, err := os.ReadFile(dst)
		if err != nil {
			return err
		}
		if bytes.Equal(data, []byte(contents)) {
			fmt.Fprintf(stdout, "Not writing %s: File is unchanged\n", color.YellowString(filename))
			return nil
		}
		renamed := false
		for i := 1; i <= 1000; i++ {
			bak := fmt.Sprintf("%s.%d.bak", dst, i)
			if !util.PathExists(bak) {
				fmt.Fprintf(stdout, "Backing up existing %s to %s\n", color.YellowString(filename), color.YellowString(bak))
				if err := os.Rename(dst, bak); err != nil {
					return err
				}
				renamed = true
				break
			}
		}
		if !renamed {
			return fmt.Errorf("could not find an unused backup name for %s", dst)
		}
	}
	fmt.Fprintf(stdout, "Writing %s\n", color.GreenString(dst))
	return os.WriteFile(dst, []byte(contents), 0644)
}

func updateRegions(cli *CLI, stdin *bufio.Reader, deploymentXML xml.Deployment, system vespa.System) (xml.Deployment, error) {
	regions, err := promptRegions(cli, stdin, deploymentXML, system)
	if err != nil {
		return xml.Deployment{}, err
	}
	parts := strings.Split(regions, ",")
	regionElements := xml.Regions(parts...)
	if err := deploymentXML.Replace("prod", "region", regionElements); err != nil {
		return xml.Deployment{}, fmt.Errorf("could not update region elements in deployment.xml: %w", err)
	}
	// TODO: Some sample apps come with production <test> elements, but not necessarily working production tests, we
	//       therefore remove <test> elements here.
	//       This can be improved by supporting <test> elements in xml package and allow specifying testing as part of
	//       region prompt, e.g. region1;test,region2
	if err := deploymentXML.Replace("prod", "test", nil); err != nil {
		return xml.Deployment{}, fmt.Errorf("could not remove test elements in deployment.xml: %w", err)
	}
	return deploymentXML, nil
}

func promptRegions(cli *CLI, stdin *bufio.Reader, deploymentXML xml.Deployment, system vespa.System) (string, error) {
	fmt.Fprintln(cli.Stdout, color.CyanString("> Deployment regions"))
	fmt.Fprintf(cli.Stdout, "Documentation: %s\n", color.GreenString("https://cloud.vespa.ai/en/reference/zones"))
	fmt.Fprintf(cli.Stdout, "Example: %s\n\n", color.YellowString("aws-us-east-1c,aws-us-west-2a"))
	var currentRegions []string
	for _, r := range deploymentXML.Prod.Regions {
		currentRegions = append(currentRegions, r.Name)
	}
	if len(deploymentXML.Instance) > 0 {
		for _, r := range deploymentXML.Instance[0].Prod.Regions {
			currentRegions = append(currentRegions, r.Name)
		}
	}
	validator := func(input string) error {
		regions := strings.Split(input, ",")
		for _, r := range regions {
			if !xml.IsProdRegion(r, system) {
				return fmt.Errorf("invalid region %s", r)
			}
		}
		return nil
	}
	return prompt(cli, stdin, "Which regions do you wish to deploy in?", strings.Join(currentRegions, ","), validator)
}

func updateNodes(cli *CLI, r *bufio.Reader, servicesXML xml.Services) (xml.Services, error) {
	for _, c := range servicesXML.Container {
		nodes, err := promptNodes(cli, r, c.ID, c.Nodes)
		if err != nil {
			return xml.Services{}, err
		}
		if err := servicesXML.Replace("container#"+c.ID, "nodes", nodes); err != nil {
			return xml.Services{}, err
		}
	}
	for _, c := range servicesXML.Content {
		nodes, err := promptNodes(cli, r, c.ID, c.Nodes)
		if err != nil {
			return xml.Services{}, err
		}
		if err := servicesXML.Replace("content#"+c.ID, "nodes", nodes); err != nil {
			return xml.Services{}, err
		}
	}
	return servicesXML, nil
}

func promptNodes(cli *CLI, r *bufio.Reader, clusterID string, defaultValue xml.Nodes) (xml.Nodes, error) {
	count, err := promptNodeCount(cli, r, clusterID, defaultValue.Count)
	if err != nil {
		return xml.Nodes{}, err
	}
	const autoSpec = "auto"
	defaultSpec := autoSpec
	resources := defaultValue.Resources
	if resources != nil {
		defaultSpec = defaultValue.Resources.String()
	}
	spec, err := promptResources(cli, r, clusterID, defaultSpec)
	if err != nil {
		return xml.Nodes{}, err
	}
	if spec == autoSpec {
		resources = nil
	} else {
		r, err := xml.ParseResources(spec)
		if err != nil {
			return xml.Nodes{}, err // Should not happen as resources have already been validated
		}
		resources = &r
	}
	return xml.Nodes{Count: count, Resources: resources}, nil
}

func promptNodeCount(cli *CLI, stdin *bufio.Reader, clusterID string, nodeCount string) (string, error) {
	fmt.Fprintln(cli.Stdout, color.CyanString("\n> Node count: "+clusterID+" cluster"))
	fmt.Fprintf(cli.Stdout, "Documentation: %s\n", color.GreenString("https://cloud.vespa.ai/en/reference/services"))
	fmt.Fprintf(cli.Stdout, "Example: %s\nExample: %s\n\n", color.YellowString("4"), color.YellowString("[2,8]"))
	validator := func(input string) error {
		_, _, err := xml.ParseNodeCount(input)
		return err
	}
	return prompt(cli, stdin, fmt.Sprintf("How many nodes should the %s cluster have?", color.CyanString(clusterID)), nodeCount, validator)
}

func promptResources(cli *CLI, stdin *bufio.Reader, clusterID string, resources string) (string, error) {
	fmt.Fprintln(cli.Stdout, color.CyanString("\n> Node resources: "+clusterID+" cluster"))
	fmt.Fprintf(cli.Stdout, "Documentation: %s\n", color.GreenString("https://cloud.vespa.ai/en/reference/services"))
	fmt.Fprintf(cli.Stdout, "Example: %s\nExample: %s\n\n", color.YellowString("auto"), color.YellowString("vcpu=4,memory=8Gb,disk=100Gb"))
	validator := func(input string) error {
		if input == "auto" {
			return nil
		}
		_, err := xml.ParseResources(input)
		return err
	}
	return prompt(cli, stdin, fmt.Sprintf("Which resources should each node in the %s cluster have?", color.CyanString(clusterID)), resources, validator)
}

func readDeploymentXML(pkg vespa.ApplicationPackage) (xml.Deployment, error) {
	f, err := os.Open(filepath.Join(pkg.Path, "deployment.xml"))
	if errors.Is(err, os.ErrNotExist) {
		// Return a default value if there is no current deployment.xml
		return xml.DefaultDeployment, nil
	} else if err != nil {
		return xml.Deployment{}, err
	}
	defer f.Close()
	return xml.ReadDeployment(f)
}

func readServicesXML(pkg vespa.ApplicationPackage) (xml.Services, error) {
	f, err := os.Open(filepath.Join(pkg.Path, "services.xml"))
	if err != nil {
		return xml.Services{}, err
	}
	defer f.Close()
	return xml.ReadServices(f)
}

func prompt(cli *CLI, stdin *bufio.Reader, question, defaultAnswer string, validator func(input string) error) (string, error) {
	var input string
	for input == "" {
		fmt.Fprint(cli.Stdout, question)
		if defaultAnswer != "" {
			fmt.Fprint(cli.Stdout, " [", color.YellowString(defaultAnswer), "]")
		}
		fmt.Fprint(cli.Stdout, " ")

		var err error
		input, err = stdin.ReadString('\n')
		if err != nil {
			return "", err
		}
		input = strings.TrimSpace(input)
		if input == "" {
			input = defaultAnswer
		}

		if err := validator(input); err != nil {
			cli.printErr(err)
			fmt.Fprintln(cli.Stderr)
			input = ""
		}
	}
	return input, nil
}

func verifyTests(cli *CLI, app vespa.ApplicationPackage) error {
	if !app.HasTests() {
		return nil
	}
	// TODO: system-test, staging-setup and staging-test should be required if the application
	//       does not have any Java tests.
	suites := map[string]bool{
		"system-test":     false,
		"staging-setup":   false,
		"staging-test":    false,
		"production-test": false,
	}
	testPath := app.TestPath
	if app.IsZip() {
		path, err := app.Unzip(true)
		if err != nil {
			return err
		}
		defer os.RemoveAll(path)
		testPath = path
	}
	for suite, required := range suites {
		if err := verifyTest(cli, testPath, suite, required); err != nil {
			return err
		}
	}
	return nil
}

func verifyTest(cli *CLI, testsParent string, suite string, required bool) error {
	testDirectory := filepath.Join(testsParent, "tests", suite)
	_, err := os.Stat(testDirectory)
	if err != nil {
		if required {
			if errors.Is(err, os.ErrNotExist) {
				return errHint(fmt.Errorf("no %s tests found: %w", suite, err),
					fmt.Sprintf("No such directory: %s", testDirectory),
					"See https://cloud.vespa.ai/en/reference/testing")
			}
			return errHint(err, "See https://cloud.vespa.ai/en/reference/testing")
		}
		return nil
	}
	_, _, err = runTests(cli, testDirectory, true)
	return err
}
