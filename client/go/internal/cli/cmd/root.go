// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/url"
	"os"
	"os/exec"
	"strings"
	"time"

	"github.com/briandowns/spinner"
	"github.com/fatih/color"
	"github.com/mattn/go-colorable"
	"github.com/mattn/go-isatty"
	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
	"github.com/vespa-engine/vespa/client/go/internal/build"
	"github.com/vespa-engine/vespa/client/go/internal/cli/auth/auth0"
	"github.com/vespa-engine/vespa/client/go/internal/cli/auth/zts"
	"github.com/vespa-engine/vespa/client/go/internal/httputil"
	"github.com/vespa-engine/vespa/client/go/internal/version"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const (
	applicationFlag = "application"
	instanceFlag    = "instance"
	clusterFlag     = "cluster"
	zoneFlag        = "zone"
	targetFlag      = "target"
	colorFlag       = "color"
	quietFlag       = "quiet"

	anyTarget = iota
	localTargetOnly
	cloudTargetOnly
)

// CLI holds the Vespa CLI command tree, configuration and dependencies.
type CLI struct {
	// Environment holds the process environment.
	Environment map[string]string
	Stdin       io.ReadWriter
	Stdout      io.Writer
	Stderr      io.Writer

	exec       executor
	isTerminal func() bool
	spinner    func(w io.Writer, message string, fn func() error) error

	now           func() time.Time
	retryInterval time.Duration
	waitTimeout   *time.Duration

	cmd     *cobra.Command
	config  *Config
	version version.Version

	httpClient        httputil.Client
	httpClientFactory func(timeout time.Duration) httputil.Client
	auth0Factory      auth0Factory
	ztsFactory        ztsFactory
}

// ErrCLI is an error returned to the user. It wraps an exit status, a regular error and optional hints for resolving
// the error.
type ErrCLI struct {
	Status int
	warn   bool
	quiet  bool
	hints  []string
	error
}

type targetOptions struct {
	// logLevel sets the log level to use for this target. If empty, it defaults to "info".
	logLevel string
	// noCertificate declares that no client certificate should be required when using this target.
	noCertificate bool
	// supportedType specifies what type of target to allow.
	supportedType int
}

type targetType struct {
	name string
	url  string
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

type auth0Factory func(httpClient httputil.Client, options auth0.Options) (vespa.Authenticator, error)

type ztsFactory func(httpClient httputil.Client, domain, url string) (vespa.Authenticator, error)

// newSpinner writes message to writer w and executes function fn. While fn is running a spinning animation will be
// displayed after message.
func newSpinner(w io.Writer, message string, fn func() error) error {
	s := spinner.New(spinner.CharSets[11], 100*time.Millisecond, spinner.WithWriter(w))
	// Cursor is hidden by default. Hiding cursor requires Stop() to be called to restore cursor (i.e. if the process is
	// interrupted), however we don't want to bother with a signal handler just for this
	s.HideCursor = false
	if err := s.Color("blue", "bold"); err != nil {
		return err
	}
	if !strings.HasSuffix(message, " ") {
		message += " "
	}
	s.Prefix = message
	s.FinalMSG = "\r" + message + "done\n"
	s.Start()
	err := fn()
	if err != nil {
		s.FinalMSG = "\r" + message + "failed\n"
	}
	s.Stop()
	return err
}

// New creates the Vespa CLI, writing output to stdout and stderr, and reading environment variables from environment.
func New(stdout, stderr io.Writer, environment []string) (*CLI, error) {
	cmd := &cobra.Command{
		Use:   "vespa",
		Short: "The command-line tool for Vespa.ai",
		Long: `The command-line tool for Vespa.ai.

Use it on Vespa instances running locally, remotely or in Vespa Cloud.

To get started, see the following quick start guides:

- Local Vespa instance: https://docs.vespa.ai/en/vespa-quick-start.html
- Vespa Cloud: https://cloud.vespa.ai/en/getting-started

The complete Vespa documentation is available at https://docs.vespa.ai.

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
	cmd.CompletionOptions.HiddenDefaultCmd = true // Do not show the 'completion' command in help output
	env := make(map[string]string)
	for _, entry := range environment {
		parts := strings.SplitN(entry, "=", 2)
		env[parts[0]] = parts[1]
	}
	version, err := version.Parse(build.Version)
	if err != nil {
		return nil, err
	}
	httpClientFactory := httputil.NewClient
	cli := CLI{
		Environment: env,
		Stdin:       os.Stdin,
		Stdout:      stdout,
		Stderr:      stderr,

		exec:          &execSubprocess{},
		now:           time.Now,
		retryInterval: 2 * time.Second,

		version: version,
		cmd:     cmd,

		httpClient:        httpClientFactory(time.Second * 10),
		httpClientFactory: httpClientFactory,
		auth0Factory: func(httpClient httputil.Client, options auth0.Options) (vespa.Authenticator, error) {
			return auth0.NewClient(httpClient, options)
		},
		ztsFactory: func(httpClient httputil.Client, domain, url string) (vespa.Authenticator, error) {
			return zts.NewClient(httpClient, domain, url)
		},
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
		color       string
		quiet       bool
	)
	c.cmd.PersistentFlags().StringVarP(&target, targetFlag, "t", "local", `The target platform to use. Must be "local", "cloud", "hosted" or an URL`)
	c.cmd.PersistentFlags().StringVarP(&application, applicationFlag, "a", "", "The application to use (cloud only)")
	c.cmd.PersistentFlags().StringVarP(&instance, instanceFlag, "i", "", "The instance of the application to use (cloud only)")
	c.cmd.PersistentFlags().StringVarP(&cluster, clusterFlag, "C", "", "The container cluster to use. This is only required for applications with multiple clusters")
	c.cmd.PersistentFlags().StringVarP(&zone, zoneFlag, "z", "", "The zone to use. This defaults to a dev zone (cloud only)")
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
		c.spinner = newSpinner
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
	authCmd.AddCommand(newAuthShowCmd(c))           // auth show
	authCmd.AddCommand(newLogoutCmd(c))             // auth logout
	rootCmd.AddCommand(authCmd)                     // auth
	rootCmd.AddCommand(newCloneCmd(c))              // clone
	configCmd.AddCommand(newConfigGetCmd(c))        // config get
	configCmd.AddCommand(newConfigSetCmd(c))        // config set
	configCmd.AddCommand(newConfigUnsetCmd(c))      // config unset
	rootCmd.AddCommand(configCmd)                   // config
	rootCmd.AddCommand(newCurlCmd(c))               // curl
	rootCmd.AddCommand(newDeployCmd(c))             // deploy
	rootCmd.AddCommand(newDestroyCmd(c))            // destroy
	rootCmd.AddCommand(newPrepareCmd(c))            // prepare
	rootCmd.AddCommand(newActivateCmd(c))           // activate
	documentCmd.AddCommand(newDocumentPutCmd(c))    // document put
	documentCmd.AddCommand(newDocumentUpdateCmd(c)) // document update
	documentCmd.AddCommand(newDocumentRemoveCmd(c)) // document remove
	documentCmd.AddCommand(newDocumentGetCmd(c))    // document get
	rootCmd.AddCommand(documentCmd)                 // document
	rootCmd.AddCommand(newLogCmd(c))                // log
	rootCmd.AddCommand(newManCmd(c))                // man
	rootCmd.AddCommand(newGendocCmd(c))             // gendoc
	prodCmd.AddCommand(newProdInitCmd(c))           // prod init
	prodCmd.AddCommand(newProdDeployCmd(c))         // prod deploy
	rootCmd.AddCommand(prodCmd)                     // prod
	rootCmd.AddCommand(newQueryCmd(c))              // query
	statusCmd.AddCommand(newStatusDeployCmd(c))     // status deploy
	statusCmd.AddCommand(newStatusDeploymentCmd(c)) // status deployment
	rootCmd.AddCommand(statusCmd)                   // status
	rootCmd.AddCommand(newTestCmd(c))               // test
	rootCmd.AddCommand(newVersionCmd(c))            // version
	rootCmd.AddCommand(newVisitCmd(c))              // visit
	rootCmd.AddCommand(newFeedCmd(c))               // feed
	rootCmd.AddCommand(newFetchCmd(c))              // fetch
}

func (c *CLI) bindWaitFlag(cmd *cobra.Command, defaultSecs int, value *int) {
	desc := "Number of seconds to wait for service(s) to become ready. 0 to disable"
	if defaultSecs == 0 {
		desc += " (default 0)"
	}
	cmd.PersistentFlags().IntVarP(value, "wait", "w", defaultSecs, desc)
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

func (c *CLI) printInfo(msg ...interface{}) {
	fmt.Fprintln(c.Stderr, fmt.Sprint(msg...))
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

func (c *CLI) confirm(question string, confirmByDefault bool) (bool, error) {
	if !c.isTerminal() {
		return false, fmt.Errorf("terminal is not interactive")
	}
	for {
		var answer string
		choice := "[Y/n]"
		if !confirmByDefault {
			choice = "[y/N]"
		}
		fmt.Fprintf(c.Stdout, "%s %s ", question, choice)
		fmt.Fscanln(c.Stdin, &answer)
		answer = strings.TrimSpace(answer)
		if answer == "" {
			return confirmByDefault, nil
		}
		switch answer {
		case "y", "Y":
			return true, nil
		case "n", "N":
			return false, nil
		default:
			c.printErr(fmt.Errorf("please answer 'y' or 'n'"))
		}
	}
}

func (c *CLI) waiter(timeout time.Duration, cmd *cobra.Command) *Waiter {
	return &Waiter{Timeout: timeout, cli: c, cmd: cmd}
}

// target creates a target according the configuration of this CLI and given opts.
func (c *CLI) target(opts targetOptions) (vespa.Target, error) {
	targetType, err := c.targetType(opts.supportedType)
	if err != nil {
		return nil, err
	}
	var target vespa.Target
	switch targetType.name {
	case vespa.TargetLocal, vespa.TargetCustom:
		target, err = c.createCustomTarget(targetType.name, targetType.url)
	case vespa.TargetCloud, vespa.TargetHosted:
		target, err = c.createCloudTarget(targetType.name, opts, targetType.url)
	default:
		return nil, errHint(fmt.Errorf("invalid target: %s", targetType), "Valid targets are 'local', 'cloud', 'hosted' or an URL")
	}
	if err != nil {
		return nil, err
	}
	if target.IsCloud() && !c.isCloudCI() { // Vespa Cloud always runs an up-to-date version
		if err := target.CompatibleWith(c.version); err != nil {
			c.printWarning(err, "This version of CLI may not work as expected", "Try 'vespa version' to check for a new version")
		}
	}
	return target, nil
}

// targetType resolves the real target type and its custom URL (if any)
func (c *CLI) targetType(targetTypeRestriction int) (targetType, error) {
	v, err := c.config.targetOrURL()
	if err != nil {
		return targetType{}, err
	}
	tt := targetType{name: v}
	if strings.HasPrefix(tt.name, "http://") || strings.HasPrefix(tt.name, "https://") {
		tt.url = tt.name
		tt.name, err = c.targetFromURL(tt.url)
		if err != nil {
			return targetType{}, err
		}
	}
	unsupported := (targetTypeRestriction == cloudTargetOnly && tt.name != vespa.TargetCloud && tt.name != vespa.TargetHosted) ||
		(targetTypeRestriction == localTargetOnly && tt.name != vespa.TargetLocal && tt.name != vespa.TargetCustom)
	if unsupported {
		return targetType{}, fmt.Errorf("command does not support %s target", tt.name)
	}
	return tt, nil
}

func (c *CLI) targetFromURL(customURL string) (string, error) {
	u, err := url.Parse(customURL)
	if err != nil {
		return "", err
	}
	// Check if URL belongs to a cloud target
	for _, cloudTarget := range []string{vespa.TargetHosted, vespa.TargetCloud} {
		system, err := c.system(cloudTarget)
		if err != nil {
			return "", err
		}
		if strings.HasSuffix(u.Hostname(), "."+system.EndpointDomain) {
			return cloudTarget, nil
		}
	}
	return vespa.TargetCustom, nil
}

func (c *CLI) createCustomTarget(targetType, customURL string) (vespa.Target, error) {
	tlsOptions, err := c.config.readTLSOptions(vespa.DefaultApplication, targetType)
	if err != nil {
		return nil, err
	}
	switch targetType {
	case vespa.TargetLocal:
		return vespa.LocalTarget(c.httpClient, tlsOptions, c.retryInterval), nil
	case vespa.TargetCustom:
		return vespa.CustomTarget(c.httpClient, customURL, tlsOptions, c.retryInterval), nil
	default:
		return nil, fmt.Errorf("invalid custom target: %s", targetType)
	}
}

func (c *CLI) cloudApiAuthenticator(deployment vespa.Deployment, system vespa.System) (vespa.Authenticator, error) {
	apiKey, err := c.config.readAPIKey(c, deployment.Application.Tenant)
	if err != nil {
		return nil, err
	}
	if apiKey == nil {
		authConfigPath := c.config.authConfigPath()
		auth0, err := c.auth0Factory(c.httpClient, auth0.Options{ConfigPath: authConfigPath, SystemName: system.Name, SystemURL: system.URL})
		if err != nil {
			return nil, err
		}
		return auth0, nil
	}
	return vespa.NewRequestSigner(deployment.Application.SerializedForm(), apiKey), nil
}

func (c *CLI) createCloudTarget(targetType string, opts targetOptions, customURL string) (vespa.Target, error) {
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
		apiAuth              vespa.Authenticator
		deploymentAuth       vespa.Authenticator
		apiTLSOptions        vespa.TLSOptions
		deploymentTLSOptions vespa.TLSOptions
	)
	switch targetType {
	case vespa.TargetCloud:
		// Only setup API authentication if we're using "cloud" target, and not a direct URL
		if customURL == "" {
			apiAuth, err = c.cloudApiAuthenticator(deployment, system)
			if err != nil {
				return nil, err
			}
		}
		deploymentTLSOptions = vespa.TLSOptions{}
		if !opts.noCertificate {
			kp, err := c.config.readTLSOptions(deployment.Application, targetType)
			if err != nil {
				return nil, errHint(err, "Deployment to cloud requires a certificate", "Try 'vespa auth cert' to create a self-signed certificate")
			}
			deploymentTLSOptions = kp
		}
	case vespa.TargetHosted:
		kp, err := c.config.readTLSOptions(deployment.Application, targetType)
		if err != nil {
			return nil, errHint(err, "Deployment to hosted requires an Athenz certificate", "Try renewing certificate with 'athenz-user-cert'")
		}
		zts, err := c.ztsFactory(c.httpClient, system.AthenzDomain, zts.DefaultURL)
		if err != nil {
			return nil, err
		}
		deploymentAuth = zts
		apiTLSOptions = kp
		deploymentTLSOptions = kp
	default:
		return nil, fmt.Errorf("invalid cloud target: %s", targetType)
	}
	apiOptions := vespa.APIOptions{
		System:     system,
		TLSOptions: apiTLSOptions,
	}
	deploymentOptions := vespa.CloudDeploymentOptions{
		Deployment:  deployment,
		TLSOptions:  deploymentTLSOptions,
		CustomURL:   customURL,
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
	return vespa.CloudTarget(c.httpClient, apiAuth, deploymentAuth, apiOptions, deploymentOptions, logOptions, c.retryInterval)
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
				if cliErr.warn {
					c.printWarning(cliErr, cliErr.hints...)
				} else {
					c.printErr(cliErr, cliErr.hints...)
				}
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
func (c *CLI) applicationPackageFrom(args []string, options vespa.PackageOptions) (vespa.ApplicationPackage, error) {
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
	return vespa.FindApplicationPackage(path, options)
}
