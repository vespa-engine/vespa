// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"errors"
	"fmt"
	"strings"

	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const (
	applicationFlag = "application"
	instanceFlag    = "instance"
	clusterFlag     = "cluster"
	zoneFlag        = "zone"
	targetFlag      = "target"
)

// TargetFlags represents the set of flags used for targeting commands.
// This provides a reusable way to add target-related flags to commands that need them.
type TargetFlags struct {
	target      string
	application string
	instance    string
	cluster     string
	zone        string
	cli         *CLI // Reference to CLI for config integration
}

// NewTargetFlags creates a new TargetFlags instance with default values.
func NewTargetFlags() *TargetFlags {
	return &TargetFlags{
		target: "local", // default target
	}
}

// NewTargetFlagsWithCLI creates a new TargetFlags instance with CLI integration for config fallback.
func NewTargetFlagsWithCLI(cli *CLI) *TargetFlags {
	return &TargetFlags{
		target: "local", // default target
		cli:    cli,
	}
}

// AddFlags adds all cloud interaction flags to the given command.
func (cf *TargetFlags) AddFlags(cmd *cobra.Command) {
	cf.AddTargetFlag(cmd)
	cf.AddApplicationFlag(cmd)
	cf.AddInstanceFlag(cmd)
	cf.AddClusterFlag(cmd)
	cf.AddZoneFlag(cmd)
}

// AddTargetFlag adds only the target flag to the given command.
func (cf *TargetFlags) AddTargetFlag(cmd *cobra.Command) {
	cmd.Flags().StringVarP(&cf.target, targetFlag, "t", "local", `The target platform to use. Must be "local", "cloud", "hosted" or an URL`)
}

// AddApplicationFlag adds only the application flag to the given command.
func (cf *TargetFlags) AddApplicationFlag(cmd *cobra.Command) {
	cmd.Flags().StringVarP(&cf.application, applicationFlag, "a", "", `The application to use (cloud only). Format "tenant.application.instance" - instance is optional`)
}

// AddInstanceFlag adds only the instance flag to the given command.
func (cf *TargetFlags) AddInstanceFlag(cmd *cobra.Command) {
	cmd.Flags().StringVarP(&cf.instance, instanceFlag, "i", "", "The instance of the application to use (cloud only)")
}

// AddClusterFlag adds only the cluster flag to the given command.
func (cf *TargetFlags) AddClusterFlag(cmd *cobra.Command) {
	cmd.Flags().StringVarP(&cf.cluster, clusterFlag, "C", "", "The container cluster to use. This is only required for applications with multiple clusters")
}

// AddZoneFlag adds only the zone flag to the given command.
func (cf *TargetFlags) AddZoneFlag(cmd *cobra.Command) {
	cmd.Flags().StringVarP(&cf.zone, zoneFlag, "z", "", "The zone to use. This defaults to a dev zone (cloud only)")
}

// RequireApplicationFlag marks the application flag as required for the given command.
func (cf *TargetFlags) RequireApplicationFlag(cmd *cobra.Command) error {
	return cmd.MarkFlagRequired(applicationFlag)
}

// RequireTargetFlag marks the target flag as required for the given command.
func (cf *TargetFlags) RequireTargetFlag(cmd *cobra.Command) error {
	return cmd.MarkFlagRequired(targetFlag)
}

// RequireZoneFlag marks the zone flag as required for the given command.
func (cf *TargetFlags) RequireZoneFlag(cmd *cobra.Command) error {
	return cmd.MarkFlagRequired(zoneFlag)
}

// Target returns the configured target value, falling back to config if not set via flag.
func (cf *TargetFlags) Target() string {
	// If CLI config is available, check if flag was set explicitly, otherwise use config fallback
	if cf.cli != nil {
		if cf.target != "" && cf.target != "local" {
			return cf.target
		}
		if configValue, _ := cf.cli.config.get(targetFlag); configValue != "" {
			return configValue
		}
	}
	return cf.target
}

// Application returns the configured application value, falling back to config if not set via flag.
func (cf *TargetFlags) Application() string {
	// If CLI config is available, check if flag was set explicitly, otherwise use config fallback
	if cf.cli != nil {
		if cf.application != "" {
			return cf.application
		}
		if configValue, _ := cf.cli.config.get(applicationFlag); configValue != "" {
			return configValue
		}
	}
	return cf.application
}

// Instance returns the configured instance value, falling back to config if not set via flag.
func (cf *TargetFlags) Instance() string {
	// If CLI config is available, check if flag was set explicitly, otherwise use config fallback
	if cf.cli != nil {
		if cf.instance != "" {
			return cf.instance
		}
		if configValue, _ := cf.cli.config.get(instanceFlag); configValue != "" {
			return configValue
		}
	}
	return cf.instance
}

// Cluster returns the configured cluster value, falling back to config if not set via flag.
func (cf *TargetFlags) Cluster() string {
	// If CLI config is available, check if flag was set explicitly, otherwise use config fallback
	if cf.cli != nil {
		if cf.cluster != "" {
			return cf.cluster
		}
		if configValue, _ := cf.cli.config.get(clusterFlag); configValue != "" {
			return configValue
		}
	}
	return cf.cluster
}

// Zone returns the configured zone value, falling back to config if not set via flag.
func (cf *TargetFlags) Zone() string {
	// If CLI config is available, check if flag was set explicitly, otherwise use config fallback
	if cf.cli != nil {
		if cf.zone != "" {
			return cf.zone
		}
		if configValue, _ := cf.cli.config.get(zoneFlag); configValue != "" {
			return configValue
		}
	}
	return cf.zone
}

// GetTarget creates a vespa.Target using the TargetFlags configuration and CLI target options.
// This is equivalent to calling cli.target() but uses TargetFlags values instead of global flags.
func (cf *TargetFlags) GetTarget(supportedType int) (vespa.Target, error) {
	return cf.GetTargetWithOptions(targetOptions{supportedType: supportedType})
}

// GetTargetWithOptions creates a vespa.Target using the TargetFlags configuration and specific target options.
func (cf *TargetFlags) GetTargetWithOptions(opts targetOptions) (vespa.Target, error) {
	if cf.cli == nil {
		return nil, fmt.Errorf("TargetFlags not initialized with CLI instance")
	}

	// Create a temporary target type structure
	targetType := targetType{name: cf.Target()}

	// Check if target is a URL
	if strings.HasPrefix(targetType.name, "http://") || strings.HasPrefix(targetType.name, "https://") {
		targetType.url = targetType.name
		var err error
		targetType.name, err = cf.cli.targetFromURL(targetType.url)
		if err != nil {
			return nil, err
		}
	}

	// Check if target type is supported
	if err := validateTargetTypeSupport(targetType.name, opts.supportedType); err != nil {
		return nil, err
	}

	// Create target directly based on type
	var target vespa.Target
	var err error
	switch targetType.name {
	case vespa.TargetLocal, vespa.TargetCustom:
		target, err = cf.cli.createCustomTarget(targetType.name, targetType.url)
	case vespa.TargetCloud, vespa.TargetHosted:
		// For cloud targets, we need to temporarily override config with TargetFlags values
		target, err = cf.createCloudTarget(targetType.name, opts, targetType.url)
	default:
		return nil, errHint(fmt.Errorf("invalid target: %s", targetType), "Valid targets are 'local', 'cloud', 'hosted' or an URL")
	}

	// Perform version compatibility check (same as original cli.target() method)
	if target != nil && err == nil {
		if checkErr := target.CompatibleWith(cf.cli.version); checkErr != nil {
			var authError vespa.AuthError
			if errors.As(checkErr, &authError) {
				return nil, checkErr
			}
			cf.cli.printWarning(checkErr, "This version of CLI may not work as expected", "Try 'vespa version' to check for a new version")
		}
	}

	return target, err
}

// createCloudTarget creates a cloud target using TargetFlags values
func (cf *TargetFlags) createCloudTarget(targetType string, opts targetOptions, customURL string) (vespa.Target, error) {
	// Temporarily override config values for cloud target creation
	originalValues := make(map[string]string)
	configOverrides := map[string]string{
		targetFlag:      cf.Target(),
		applicationFlag: cf.Application(),
		instanceFlag:    cf.Instance(),
		zoneFlag:        cf.Zone(),
	}

	// Store original values and set TargetFlags values
	for key, value := range configOverrides {
		if value != "" {
			originalValues[key], _ = cf.cli.config.get(key)
			_ = cf.cli.config.set(key, value)
		}
	}

	// Create the target
	target, err := cf.cli.createCloudTarget(targetType, opts, customURL)

	// Restore original values
	for key, originalValue := range originalValues {
		if originalValue != "" {
			_ = cf.cli.config.set(key, originalValue)
		} else {
			// If there was no original value, unset it
			_ = cf.cli.config.unset(key)
		}
	}

	return target, err
}

// AddFlagsToConfig adds TargetFlags values to a flag map for config override purposes.
// This allows config commands to use TargetFlags values as overrides.
func (cf *TargetFlags) AddFlagsToConfig(cmd *cobra.Command, flagMap map[string]*pflag.Flag) {
	if flag := cmd.Flags().Lookup(targetFlag); flag != nil {
		flagMap[targetFlag] = flag
	}
	if flag := cmd.Flags().Lookup(applicationFlag); flag != nil {
		flagMap[applicationFlag] = flag
	}
	if flag := cmd.Flags().Lookup(instanceFlag); flag != nil {
		flagMap[instanceFlag] = flag
	}
	if flag := cmd.Flags().Lookup(clusterFlag); flag != nil {
		flagMap[clusterFlag] = flag
	}
	if flag := cmd.Flags().Lookup(zoneFlag); flag != nil {
		flagMap[zoneFlag] = flag
	}
}

// TargetFlagsMixin provides a way to embed TargetFlags in command structs.
// This is useful for commands that need cloud interaction flags.
type TargetFlagsMixin struct {
	TargetFlags *TargetFlags
}

// NewTargetFlagsMixin creates a new TargetFlagsMixin.
func NewTargetFlagsMixin() *TargetFlagsMixin {
	return &TargetFlagsMixin{
		TargetFlags: NewTargetFlags(),
	}
}

// NewTargetFlagsMixinWithCLI creates a new TargetFlagsMixin with CLI integration.
func NewTargetFlagsMixinWithCLI(cli *CLI) *TargetFlagsMixin {
	return &TargetFlagsMixin{
		TargetFlags: NewTargetFlagsWithCLI(cli),
	}
}

// AddAllFlags adds all cloud flags to the command.
func (cfm *TargetFlagsMixin) AddAllFlags(cmd *cobra.Command) {
	cfm.TargetFlags.AddFlags(cmd)
}

// AddCloudTargetFlags adds target, application, instance, and zone flags (common cloud interaction flags).
func (cfm *TargetFlagsMixin) AddCloudTargetFlags(cmd *cobra.Command) {
	cfm.TargetFlags.AddTargetFlag(cmd)
	cfm.TargetFlags.AddApplicationFlag(cmd)
	cfm.TargetFlags.AddInstanceFlag(cmd)
	cfm.TargetFlags.AddZoneFlag(cmd)
}

// AddLocalTargetFlags adds only the target flag (for commands that work with local targets).
func (cfm *TargetFlagsMixin) AddLocalTargetFlags(cmd *cobra.Command) {
	cfm.TargetFlags.AddTargetFlag(cmd)
}
