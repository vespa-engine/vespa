// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/mattn/go-colorable"
	"github.com/mattn/go-isatty"
	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
	"github.com/vespa-engine/vespa/client/go/internal/cli/build"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/version"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const (
	applicationFlag = "application"
	instanceFlag    = "instance"
	clusterFlag     = "cluster"
	zoneFlag        = "zone"
	targetFlag      = "target"
	waitFlag        = "wait"
	colorFlag       = "color"
	quietFlag       = "quiet"
)

// CLI holds the Vespa CLI command tree, configuration and dependencies.
type CLI struct {
	// Environment holds the process environment.
	Environment map[string]string
	Stdin       io.ReadWriter
	Stdout      io.Writer
	Stderr      io.Writer

	cmd     *cobra.Command
	config  *Config
	version version.Version

	httpClient util.HTTPClient
	exec       executor
	isTerminal func() bool
	spinner    func(w io.Writer, message string, fn func() error) error
}

// ErrCLI is an error returned to the user. It wraps an exit status, a regular error and optional hints for resolving
// the error.
type ErrCLI struct {
	Status int
	quiet  bool
	hints  []string
	error
}

type targetOptions struct {
	// logLevel sets the log level to use for this target. If empty, it defaults to "info".
	logLevel string
	// noCertificate declares that no client certificate should be required when using this target.
	noCertificate bool
}

// errHint creates a new CLI error, with optional hints that will be printed after the error
func errHint(err error, hints ...string) ErrCLI { return ErrCLI{Status: 1, hints: hints, error: err} }

type executor interface {
	LookPath(name string) (string, error)
	Run(name string, args ...string) ([]byte, error)
}

type execSubprocess struct{}

func (c *execSubprocess) LookPath(name string) (string, error) { return exec.LookPath(name) }
func (c *execSubprocess) Run(name string, args ...string) ([]byte, error) {
	return exec.Command(name, args...).Output()
}

// New creates the Vespa CLI, writing output to stdout and stderr, and reading environment variables from environment.
func New(stdout, stderr io.Writer, environment []string) (*CLI, error) {
	cmd := &cobra.Command{
		Use:   "vespa command-name",
		Short: "The command-line tool for Vespa.ai",
		Long: `The command-line tool for Vespa.ai.

Use it on Vespa instances running locally, remotely or in the cloud.
Prefer web service API's to this in production.

Vespa documentation: https://docs.vespa.ai

For detailed description of flags and configuration, see 'vespa help config'.
`,
		DisableAutoGenTag: true,
		SilenceErrors:     true, // We have our own error printing
		SilenceUsage:      false,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return fmt.Errorf("invalid command: %s", args[0])
		},
	}
	env := make(map[string]string)
	for _, entry := range environment {
		parts := strings.SplitN(entry, "=", 2)
		env[parts[0]] = parts[1]
	}
	version, err := version.Parse(build.Version)
	if err != nil {
		return nil, err
	}
	cli := CLI{
		Environment: env,
		Stdin:       os.Stdin,
		Stdout:      stdout,
		Stderr:      stderr,

		version:    version,
		cmd:        cmd,
		httpClient: util.CreateClient(time.Second * 10),
		exec:       &execSubprocess{},
	}
	cli.isTerminal = func() bool { return isTerminal(cli.Stdout) && isTerminal(cli.Stderr) }
	if err := cli.loadConfig(); err != nil {
		return nil, err
	}
	cli.configureSpinner()
	cli.configureCommands()
	cmd.PersistentPreRunE = cli.configureOutput
	return &cli, nil
}

func (c *CLI) loadConfig() error {
	config, err := loadConfig(c.Environment, c.configureFlags())
	if err != nil {
		return err
	}
	c.config = config
	return nil
}

func (c *CLI) configureOutput(cmd *cobra.Command, args []string) error {
	if f, ok := c.Stdout.(*os.File); ok {
		c.Stdout = colorable.NewColorable(f)
	}
	if f, ok := c.Stderr.(*os.File); ok {
		c.Stderr = colorable.NewColorable(f)
	}
	if c.config.isQuiet() {
		c.Stdout = io.Discard
	}
	log.SetFlags(0) // No timestamps
	log.SetOutput(c.Stdout)
	colorValue, _ := c.config.get(colorFlag)
	colorize := false
	switch colorValue {
	case "auto":
		_, nocolor := c.Environment["NO_COLOR"] // https://no-color.org
		colorize = !nocolor && c.isTerminal()
	case "always":
		colorize = true
	case "never":
	default:
		return fmt.Errorf("invalid color option: %s", colorValue)
	}
	color.NoColor = !colorize
	return nil
}

func (c *CLI) configureFlags() map[string]*pflag.Flag {
	var (
		target      string
		application string
		instance    string
		cluster     string
		zone        string
		waitSecs    int
		color       string
		quiet       bool
	)
	c.cmd.PersistentFlags().StringVarP(&target, targetFlag, "t", "local", `The target platform to use. Must be "local", "cloud", "hosted" or an URL`)
	c.cmd.PersistentFlags().StringVarP(&application, applicationFlag, "a", "", "The application to use")
	c.cmd.PersistentFlags().StringVarP(&instance, instanceFlag, "i", "", "The instance of the application to use")
	c.cmd.PersistentFlags().StringVarP(&cluster, clusterFlag, "C", "", "The container cluster to use. This is only required for applications with multiple clusters")
	c.cmd.PersistentFlags().StringVarP(&zone, zoneFlag, "z", "", "The zone to use. This defaults to a dev zone")
	c.cmd.PersistentFlags().IntVarP(&waitSecs, waitFlag, "w", 0, "Number of seconds to wait for a service to become ready")
	c.cmd.PersistentFlags().StringVarP(&color, colorFlag, "c", "auto", `Whether to use colors in output. Must be "auto", "never", or "always"`)
	c.cmd.PersistentFlags().BoolVarP(&quiet, quietFlag, "q", false, "Print only errors")
	flags := make(map[string]*pflag.Flag)
	c.cmd.PersistentFlags().VisitAll(func(flag *pflag.Flag) {
		flags[flag.Name] = flag
	})
	return flags
}

func (c *CLI) configureSpinner() {
	// Explicitly disable spinner for Screwdriver. It emulates a tty but
	// \r result in a newline, and output gets truncated.
	_, screwdriver := c.Environment["SCREWDRIVER"]
	if c.config.isQuiet() || !c.isTerminal() || screwdriver {
		c.spinner = func(w io.Writer, message string, fn func() error) error {
			return fn()
		}
	} else {
		c.spinner = util.Spinner
	}
}

func (c *CLI) configureCommands() {
	rootCmd := c.cmd
	authCmd := newAuthCmd()
	certCmd := newCertCmd(c)
	configCmd := newConfigCmd()
	documentCmd := newDocumentCmd(c)
	prodCmd := newProdCmd()
	statusCmd := newStatusCmd(c)
	certCmd.AddCommand(newCertAddCmd(c))            // auth cert add
	authCmd.AddCommand(certCmd)                     // auth cert
	authCmd.AddCommand(newAPIKeyCmd(c))             // auth api-key
	authCmd.AddCommand(newLoginCmd(c))              // auth login
	authCmd.AddCommand(newLogoutCmd(c))             // auth logout
	rootCmd.AddCommand(authCmd)                     // auth
	rootCmd.AddCommand(newCloneCmd(c))              // clone
	configCmd.AddCommand(newConfigGetCmd(c))        // config get
	configCmd.AddCommand(newConfigSetCmd(c))        // config set
	configCmd.AddCommand(newConfigUnsetCmd(c))      // config unset
	rootCmd.AddCommand(configCmd)                   // config
	rootCmd.AddCommand(newCurlCmd(c))               // curl
	rootCmd.AddCommand(newDeployCmd(c))             // deploy
	rootCmd.AddCommand(newPrepareCmd(c))            // prepare
	rootCmd.AddCommand(newActivateCmd(c))           // activate
	documentCmd.AddCommand(newDocumentPutCmd(c))    // document put
	documentCmd.AddCommand(newDocumentUpdateCmd(c)) // document update
	documentCmd.AddCommand(newDocumentRemoveCmd(c)) // document remove
	documentCmd.AddCommand(newDocumentGetCmd(c))    // document get
	rootCmd.AddCommand(documentCmd)                 // document
	rootCmd.AddCommand(newLogCmd(c))                // log
	rootCmd.AddCommand(newManCmd(c))                // man
	prodCmd.AddCommand(newProdInitCmd(c))           // prod init
	prodCmd.AddCommand(newProdSubmitCmd(c))         // prod submit
	rootCmd.AddCommand(prodCmd)                     // prod
	rootCmd.AddCommand(newQueryCmd(c))              // query
	statusCmd.AddCommand(newStatusQueryCmd(c))      // status query
	statusCmd.AddCommand(newStatusDocumentCmd(c))   // status document
	statusCmd.AddCommand(newStatusDeployCmd(c))     // status deploy
	rootCmd.AddCommand(statusCmd)                   // status
	rootCmd.AddCommand(newTestCmd(c))               // test
	rootCmd.AddCommand(newVersionCmd(c))            // version
	rootCmd.AddCommand(newVisitCmd(c))              // visit
}

func (c *CLI) printErr(err error, hints ...string) {
	fmt.Fprintln(c.Stderr, color.RedString("Error:"), err)
	for _, hint := range hints {
		fmt.Fprintln(c.Stderr, color.CyanString("Hint:"), hint)
	}
}

func (c *CLI) printSuccess(msg ...interface{}) {
	fmt.Fprintln(c.Stdout, color.GreenString("Success:"), fmt.Sprint(msg...))
}

func (c *CLI) printDebug(msg ...interface{}) {
	fmt.Fprintln(c.Stderr, color.CyanString("Debug:"), fmt.Sprint(msg...))
}

func (c *CLI) printWarning(msg interface{}, hints ...string) {
	fmt.Fprintln(c.Stderr, color.YellowString("Warning:"), msg)
	for _, hint := range hints {
		fmt.Fprintln(c.Stderr, color.CyanString("Hint:"), hint)
	}
}

// target creates a target according the configuration of this CLI and given opts.
func (c *CLI) target(opts targetOptions) (vespa.Target, error) {
	target, err := c.createTarget(opts)
	if err != nil {
		return nil, err
	}
	if !c.isCloudCI() { // Vespa Cloud always runs an up-to-date version
		if err := target.CheckVersion(c.version); err != nil {
			c.printWarning(err, "This version may not work as expected", "Try 'vespa version' to check for a new version")
		}
	}
	return target, nil
}

func (c *CLI) createTarget(opts targetOptions) (vespa.Target, error) {
	targetType, err := c.config.targetType()
	if err != nil {
		return nil, err
	}
	if strings.HasPrefix(targetType, "http") {
		return vespa.CustomTarget(c.httpClient, targetType), nil
	}
	switch targetType {
	case vespa.TargetLocal:
		return vespa.LocalTarget(c.httpClient), nil
	case vespa.TargetCloud, vespa.TargetHosted:
		return c.createCloudTarget(targetType, opts)
	}
	return nil, errHint(fmt.Errorf("invalid target: %s", targetType), "Valid targets are 'local', 'cloud', 'hosted' or an URL")
}

func (c *CLI) createCloudTarget(targetType string, opts targetOptions) (vespa.Target, error) {
	system, err := c.system(targetType)
	if err != nil {
		return nil, err
	}
	deployment, err := c.config.deploymentIn(system)
	if err != nil {
		return nil, err
	}
	endpoints, err := c.endpointsFromEnv()
	if err != nil {
		return nil, err
	}
	var (
		apiKey               []byte
		authConfigPath       string
		apiTLSOptions        vespa.TLSOptions
		deploymentTLSOptions vespa.TLSOptions
	)
	switch targetType {
	case vespa.TargetCloud:
		apiKey, err = c.config.readAPIKey(c, system, deployment.Application.Tenant)
		if err != nil {
			return nil, err
		}
		authConfigPath = c.config.authConfigPath()
		deploymentTLSOptions = vespa.TLSOptions{}
		if !opts.noCertificate {
			kp, err := c.config.x509KeyPair(deployment.Application, targetType)
			if err != nil {
				return nil, errHint(err, "Deployment to cloud requires a certificate. Try 'vespa auth cert'")
			}
			deploymentTLSOptions = vespa.TLSOptions{
				KeyPair:         kp.KeyPair,
				CertificateFile: kp.CertificateFile,
				PrivateKeyFile:  kp.PrivateKeyFile,
			}
		}
	case vespa.TargetHosted:
		kp, err := c.config.x509KeyPair(deployment.Application, targetType)
		if err != nil {
			return nil, errHint(err, "Deployment to hosted requires an Athenz certificate", "Try renewing certificate with 'athenz-user-cert'")
		}
		apiTLSOptions = vespa.TLSOptions{
			KeyPair:         kp.KeyPair,
			CertificateFile: kp.CertificateFile,
			PrivateKeyFile:  kp.PrivateKeyFile,
		}
		deploymentTLSOptions = apiTLSOptions
	default:
		return nil, fmt.Errorf("invalid cloud target: %s", targetType)
	}
	apiOptions := vespa.APIOptions{
		System:         system,
		TLSOptions:     apiTLSOptions,
		APIKey:         apiKey,
		AuthConfigPath: authConfigPath,
	}
	deploymentOptions := vespa.CloudDeploymentOptions{
		Deployment:  deployment,
		TLSOptions:  deploymentTLSOptions,
		ClusterURLs: endpoints,
	}
	logLevel := opts.logLevel
	if logLevel == "" {
		logLevel = "info"
	}
	logOptions := vespa.LogOptions{
		Writer: c.Stdout,
		Level:  vespa.LogLevel(logLevel),
	}
	return vespa.CloudTarget(c.httpClient, apiOptions, deploymentOptions, logOptions)
}

// system returns the appropiate system for the target configured in this CLI.
func (c *CLI) system(targetType string) (vespa.System, error) {
	name := c.Environment["VESPA_CLI_CLOUD_SYSTEM"]
	if name != "" {
		return vespa.GetSystem(name)
	}
	switch targetType {
	case vespa.TargetHosted:
		return vespa.MainSystem, nil
	case vespa.TargetCloud:
		return vespa.PublicSystem, nil
	}
	return vespa.System{}, fmt.Errorf("no default system found for %s target", targetType)
}

// service returns the service of given name located at target. If non-empty, cluster specifies a cluster to query. This
// function blocks according to the wait period configured in this CLI. The parameter sessionOrRunID specifies either
// the session ID (local target) or run ID (cloud target) to wait for.
func (c *CLI) service(target vespa.Target, name string, sessionOrRunID int64, cluster string) (*vespa.Service, error) {
	timeout, err := c.config.timeout()
	if err != nil {
		return nil, err
	}
	if timeout > 0 {
		log.Printf("Waiting up to %s for %s service to become available ...", color.CyanString(timeout.String()), color.CyanString(name))
	}
	s, err := target.Service(name, timeout, sessionOrRunID, cluster)
	if err != nil {
		err := fmt.Errorf("service '%s' is unavailable: %w", name, err)
		if target.IsCloud() {
			return nil, errHint(err, "Confirm that you're communicating with the correct zone and cluster", "The -z option controls the zone", "The -C option controls the cluster")
		}
		return nil, err
	}
	return s, nil
}

func (c *CLI) createDeploymentOptions(pkg vespa.ApplicationPackage, target vespa.Target) (vespa.DeploymentOptions, error) {
	timeout, err := c.config.timeout()
	if err != nil {
		return vespa.DeploymentOptions{}, err
	}
	return vespa.DeploymentOptions{
		ApplicationPackage: pkg,
		Target:             target,
		Timeout:            timeout,
		HTTPClient:         c.httpClient,
	}, nil
}

// isCI returns true if running inside a continuous integration environment.
func (c *CLI) isCI() bool {
	_, ok := c.Environment["CI"]
	return ok
}

// isCloudCI returns true if running inside a Vespa Cloud deployment job.
func (c *CLI) isCloudCI() bool {
	_, ok := c.Environment["VESPA_CLI_CLOUD_CI"]
	return ok
}

func (c *CLI) endpointsFromEnv() (map[string]string, error) {
	endpointsString := c.Environment["VESPA_CLI_ENDPOINTS"]
	if endpointsString == "" {
		return nil, nil
	}
	var endpoints endpoints
	urlsByCluster := make(map[string]string)
	if err := json.Unmarshal([]byte(endpointsString), &endpoints); err != nil {
		return nil, fmt.Errorf("endpoints must be valid json: %w", err)
	}
	if len(endpoints.Endpoints) == 0 {
		return nil, fmt.Errorf("endpoints must be non-empty")
	}
	for _, endpoint := range endpoints.Endpoints {
		urlsByCluster[endpoint.Cluster] = endpoint.URL
	}
	return urlsByCluster, nil
}

// Run executes the CLI with given args. If args is nil, it defaults to os.Args[1:].
func (c *CLI) Run(args ...string) error {
	c.cmd.SetArgs(args)
	err := c.cmd.Execute()
	if err != nil {
		if cliErr, ok := err.(ErrCLI); ok {
			if !cliErr.quiet {
				c.printErr(cliErr, cliErr.hints...)
			}
		} else {
			c.printErr(err)
		}
	}
	return err
}

type endpoints struct {
	Endpoints []endpoint `json:"endpoints"`
}

type endpoint struct {
	Cluster string `json:"cluster"`
	URL     string `json:"url"`
}

func isTerminal(w io.Writer) bool {
	if f, ok := w.(*os.File); ok {
		return isatty.IsTerminal(f.Fd())
	}
	return false
}

// applicationPackageFrom returns an application loaded from args. If args is empty, the application package is loaded
// from the working directory. If requirePackaging is true, the application package is required to be packaged with mvn
// package.
func (c *CLI) applicationPackageFrom(args []string, requirePackaging bool) (vespa.ApplicationPackage, error) {
	path := "."
	if len(args) == 1 {
		path = args[0]
		stat, err := os.Stat(path)
		if err != nil {
			return vespa.ApplicationPackage{}, err
		}
		if stat.IsDir() {
			// Using an explicit application directory, look for local config in that directory too
			if err := c.config.loadLocalConfigFrom(path); err != nil {
				return vespa.ApplicationPackage{}, err
			}
		}
	} else if len(args) > 1 {
		return vespa.ApplicationPackage{}, fmt.Errorf("expected 0 or 1 arguments, got %d", len(args))
	}
	return vespa.FindApplicationPackage(path, requirePackaging)
}
