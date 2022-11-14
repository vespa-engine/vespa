// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale.awsnodes;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.yahoo.config.provision.NodeResources.Architecture.arm64;
import static com.yahoo.config.provision.NodeResources.Architecture.x86_64;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;

/**
 * Returns the information about all AWS node types supported on Vespa Cloud as of 2022-10-31.
 *
 * @author bratseth
 */
public class AwsNodeTypes {

    private static final List<VespaFlavor> hostFlavors =
        List.of(new VespaFlavor("t3_nano", 0.5, 0.5, 0.5, 0.45, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("t3_micro", 0.5, 0.5, 1.0, 0.94, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("t3_small", 0.5, 0.5, 2.0, 1.8, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("t3_medium", 0.5, 0.5, 4.0, 3.6, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("t3_large", 0.5, 0.5, 8.0, 7.5, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("t3_xlarge", 0.5, 0.5, 16.0, 15.1, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("t3_2xlarge", 0.5, 0.5, 32.0, 30.5, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("m5_large", 2.0, 2.0, 8.0, 7.3, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("m5d_large", 2.0, 2.0, 8.0, 7.3, 75.0, 10.0, fast, local, x86_64),
                new VespaFlavor("m5_xlarge", 4.0, 4.0, 16.0, 15.1, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("m5d_xlarge", 4.0, 4.0, 16.0, 15.1, 150.0, 10.0, fast, local, x86_64),
                new VespaFlavor("m5_2xlarge", 8.0, 8.0, 32.0, 30.5, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("m5d_2xlarge", 8.0, 8.0, 32.0, 30.5, 300.0, 10.0, fast, local, x86_64),
                new VespaFlavor("m5_4xlarge", 16.0, 16.0, 64.0, 61.3, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("m5d_4xlarge", 16.0, 16.0, 64.0, 61.3, 600.0, 10.0, fast, local, x86_64),
                new VespaFlavor("m5_8xlarge", 32.0, 32.0, 128.0, 123.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("m5d_8xlarge", 32.0, 32.0, 128.0, 123.0, 1200.0, 10.0, fast, local, x86_64),
                new VespaFlavor("m5_12xlarge", 48.0, 48.0, 192.0, 185.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("m5d_12xlarge", 48.0, 48.0, 192.0, 185.0, 1800.0, 10.0, fast, local, x86_64),
                new VespaFlavor("m5_16xlarge", 64.0, 64.0, 256.0, 246.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("m5d_16xlarge", 64.0, 64.0, 256.0, 246.0, 2400.0, 10.0, fast, local, x86_64),
                new VespaFlavor("m5_24xlarge", 96.0, 96.0, 384.0, 370.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("m5d_24xlarge", 96.0, 96.0, 384.0, 370.0, 3600.0, 10.0, fast, local, x86_64),
                new VespaFlavor("m6g_large", 2.0, 2.0, 8.0, 7.3, 16384.0, 10.0, fast, remote, arm64),
                new VespaFlavor("m6g_xlarge", 4.0, 4.0, 16.0, 15.1, 16384.0, 10.0, fast, remote, arm64),
                new VespaFlavor("m6g_2xlarge", 8.0, 8.0, 32.0, 30.5, 16384.0, 10.0, fast, remote, arm64),
                new VespaFlavor("m6g_4xlarge", 16.0, 16.0, 64.0, 61.3, 16384.0, 10.0, fast, remote, arm64),
                new VespaFlavor("m6g_8xlarge", 32.0, 32.0, 128.0, 123.0, 16384.0, 10.0, fast, remote, arm64),
                new VespaFlavor("m6g_12xlarge", 48.0, 48.0, 192.0, 185.0, 16384.0, 10.0, fast, remote, arm64),
                new VespaFlavor("m6g_16xlarge", 64.0, 64.0, 256.0, 246.0, 16384.0, 10.0, fast, remote, arm64),
                new VespaFlavor("c5_large", 2.0, 2.0, 4.0, 3.5, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("c5d_large", 2.0, 2.0, 4.0, 3.5, 50.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c5_xlarge", 4.0, 4.0, 8.0, 7.3, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("c5d_xlarge", 4.0, 4.0, 8.0, 7.3, 100.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c5_2xlarge", 8.0, 8.0, 16.0, 15.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("c5d_2xlarge", 8.0, 8.0, 16.0, 15.0, 200.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c5_4xlarge", 16.0, 16.0, 32.0, 30.3, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("c5d_4xlarge", 16.0, 16.0, 32.0, 30.3, 400.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c5_9xlarge", 36.0, 36.0, 72.0, 68.5, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("c5d_9xlarge", 36.0, 36.0, 72.0, 68.5, 900.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c5_12xlarge", 48.0, 48.0, 96.0, 92.2, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("c5d_12xlarge", 48.0, 48.0, 96.0, 92.2, 1800.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c5_18xlarge", 72.0, 72.0, 144.0, 137.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("c5d_18xlarge", 72.0, 72.0, 144.0, 137.0, 1800.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c5_24xlarge", 96.0, 96.0, 192.0, 185.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("c5d_24xlarge", 96.0, 96.0, 192.0, 185.0, 3600.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c5ad_8xlarge", 32.0, 32.0, 64.0, 62.0, 1200.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c6gd_12xlarge", 48.0, 48.0, 96.0, 93.0, 2850.0, 10.0, fast, local, arm64),
                new VespaFlavor("c6gd_16xlarge", 64.0, 64.0, 128.0, 125.0, 3800.0, 10.0, fast, local, arm64),
                new VespaFlavor("c6id_16xlarge", 64.0, 64.0, 128.0, 123.0, 3800.0, 10.0, fast, local, x86_64),
                new VespaFlavor("c7g_16xlarge", 64.0, 64.0, 128.0, 125.0, 16384.0, 10.0, fast, remote, arm64),
                new VespaFlavor("r5_large", 2.0, 2.0, 16.0, 15.2, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("r5d_large", 2.0, 2.0, 16.0, 15.2, 75.0, 10.0, fast, local, x86_64),
                new VespaFlavor("r5_xlarge", 4.0, 4.0, 32.0, 30.8, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("r5d_xlarge", 4.0, 4.0, 32.0, 30.8, 150.0, 10.0, fast, local, x86_64),
                new VespaFlavor("r5_2xlarge", 8.0, 8.0, 64.0, 62.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("r5d_2xlarge", 8.0, 8.0, 64.0, 62.0, 300.0, 10.0, fast, local, x86_64),
                new VespaFlavor("r5_4xlarge", 16.0, 16.0, 128.0, 124.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("r5d_4xlarge", 16.0, 16.0, 128.0, 124.0, 600.0, 10.0, fast, local, x86_64),
                new VespaFlavor("r5_8xlarge", 32.0, 32.0, 256.0, 249.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("r5d_8xlarge", 32.0, 32.0, 256.0, 249.0, 1200.0, 10.0, fast, local, x86_64),
                new VespaFlavor("r5_12xlarge", 48.0, 48.0, 384.0, 374.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("r5d_12xlarge", 48.0, 48.0, 384.0, 374.0, 1800.0, 10.0, fast, local, x86_64),
                new VespaFlavor("r5_16xlarge", 64.0, 64.0, 512.0, 498.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("r5d_16xlarge", 64.0, 64.0, 512.0, 498.0, 2400.0, 10.0, fast, local, x86_64),
                new VespaFlavor("r5_24xlarge", 96.0, 96.0, 768.0, 748.0, 16384.0, 10.0, fast, remote, x86_64),
                new VespaFlavor("r5d_24xlarge", 96.0, 96.0, 768.0, 748.0, 3600.0, 10.0, fast, local, x86_64),
                new VespaFlavor("x1_16xlarge", 64.0, 64.0, 976.0, 946.0, 1920.0, 10.0, fast, local, x86_64),
                new VespaFlavor("x1_32xlarge", 128.0, 128.0, 1952.0, 1893.0, 3840.0, 10.0, fast, local, x86_64),
                new VespaFlavor("x1e_xlarge", 4.0, 4.0, 122.0, 118.0, 120.0, 10.0, fast, local, x86_64),
                new VespaFlavor("x1e_2xlarge", 8.0, 8.0, 244.0, 237.0, 240.0, 10.0, fast, local, x86_64),
                new VespaFlavor("x1e_4xlarge", 16.0, 16.0, 488.0, 474.0, 480.0, 10.0, fast, local, x86_64),
                new VespaFlavor("x1e_8xlarge", 32.0, 32.0, 976.0, 946.0, 960.0, 10.0, fast, local, x86_64),
                new VespaFlavor("x1e_16xlarge", 64.0, 64.0, 1952.0, 1893.0, 1920.0, 10.0, fast, local, x86_64),
                new VespaFlavor("x1e_32xlarge", 128.0, 128.0, 3904.0, 3786.0, 3840.0, 10.0, fast, local, x86_64),
                new VespaFlavor("z1d_6xlarge", 24.0, 24.0, 192.0, 185.0, 900.0, 10.0, fast, local, x86_64),
                new VespaFlavor("i4i_large", 2.0, 2.0, 16.0, 15.1, 468.0, 10.0, fast, local, x86_64),
                new VespaFlavor("i4i_xlarge", 4.0, 4.0, 32.0, 30.5, 937.0, 10.0, fast, local, x86_64),
                new VespaFlavor("i4i_2xlarge", 8.0, 8.0, 64.0, 61.3, 1875.0, 10.0, fast, local, x86_64),
                new VespaFlavor("i4i_4xlarge", 16.0, 16.0, 128.0, 123.0, 3750.0, 10.0, fast, local, x86_64),
                new VespaFlavor("i4i_8xlarge", 32.0, 32.0, 256.0, 246.0, 7500.0, 10.0, fast, local, x86_64),
                new VespaFlavor("i4i_16xlarge", 64.0, 64.0, 512.0, 498.0, 15000.0, 10.0, fast, local, x86_64),
                new VespaFlavor("i4i_32xlarge", 128.0, 128.0, 1024.0, 996.0, 30000.0, 10.0, fast, local, x86_64));

    public static List<VespaFlavor> asVespaFlavors() { return sorted(hostFlavors); }

    public static List<Flavor> asFlavors() {
        return sorted(hostFlavors).stream().map(f -> toFlavor(f)).toList();
    }

    private static List<VespaFlavor> sorted(List<VespaFlavor> flavors) {
        return flavors.stream().sorted(Comparator.comparing(n -> n.realResources().storageType() == NodeResources.StorageType.local ? 0 : 1)).toList();
    }

    private static Flavor toFlavor(VespaFlavor vespaFlavor) {
        return new Flavor(vespaFlavor.name(),
                          vespaFlavor.realResources(),
                          Optional.empty(),
                          Flavor.Type.VIRTUAL_MACHINE,
                          true,
                          (int)(vespaFlavor.advertisedResources().cost()*24*30));
    }

}
