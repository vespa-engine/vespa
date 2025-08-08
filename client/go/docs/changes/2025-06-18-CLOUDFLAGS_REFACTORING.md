# Target Flags Refactoring

## Overview

This refactoring moves target-related flags from global persistent flags to a modular system where commands can selectively include only the flags they need.

## Before

Previously, the following flags were global persistent flags, meaning they appeared on every command:

- `-a, --application` - The application to use (cloud only)
- `-i, --instance` - The instance of the application to use (cloud only)
- `-C, --cluster` - The container cluster to use
- `-t, --target` - The target platform to use (local, cloud, hosted, or URL)
- `-z, --zone` - The zone to use (cloud only)

## After

These flags are now added selectively to commands that need them using the `TargetFlags` system.

### Truly Global Flags

Only these flags remain global:
- `-c, --color` - Color output control
- `-q, --quiet` - Print only errors
- `--debug` - Debug mode (hidden)

### TargetFlags System

The new `TargetFlags` system provides:

1. **TargetFlags struct** - Holds flag variables and provides methods to add specific flags
2. **Selective flag addition** - Commands can add only the flags they need
3. **Consistent flag definitions** - All target flags use the same definitions across commands

### Usage Patterns

#### Commands that need all target flags
```go
targetFlags := NewTargetFlagsWithCLI(cli)
// ... command setup ...
targetFlags.AddFlags(cmd)  // Adds all target flags
```

#### Commands that need only specific flags
```go
targetFlags := NewTargetFlagsWithCLI(cli)
// ... command setup ...
targetFlags.AddApplicationFlag(cmd)  // Only adds application flag
targetFlags.RequireApplicationFlag(cmd)  // Makes it required
```

### Benefits

1. **Cleaner help output** - Commands only show relevant flags
2. **Better user experience** - Users aren't confused by irrelevant flags
3. **Maintainable** - Consistent flag definitions across commands
4. **Flexible** - Easy to add flags to new commands as needed
5. **Go idiomatic** - Uses composition rather than inheritance

### Examples

- `vespa auth api-key --help` - Only shows application flag (which it requires)
- `vespa deploy --help` - Shows all target flags (since deploy can target any environment)
- `vespa query --help` - Shows all target flags (since queries can target any cluster)
- `vespa --help` - Shows only truly global flags

This approach follows Go's composition patterns and provides a clean, maintainable way to manage command flags that target different environments and services.
