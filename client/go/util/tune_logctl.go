// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package util

func TuneLogging(serviceName, component, settings string) bool {
	arg := serviceName
	if component != "" {
		arg = serviceName + ":" + component
	}
	_, err := BackTicksIgnoreStderr.Run("vespa-logctl", "-c", arg, settings)
	return err == nil
}
