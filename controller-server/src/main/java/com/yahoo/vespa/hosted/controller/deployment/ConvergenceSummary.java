// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import java.util.Objects;

/**
 * Summary of node and service status during a deployment job.
 *
 * @author jonmv
 */
public class ConvergenceSummary {

    private final long nodes;
    private final long down;
    private final long upgradingOs;
    private final long upgradingFirmware;
    private final long needPlatformUpgrade;
    private final long upgradingPlatform;
    private final long needReboot;
    private final long rebooting;
    private final long needRestart;
    private final long restarting;
    private final long services;
    private final long needNewConfig;
    private final long retiring;

    public ConvergenceSummary(long nodes, long down, long upgradingOs, long upgradingFirmware, long needPlatformUpgrade, long upgradingPlatform,
                              long needReboot, long rebooting, long needRestart, long restarting, long services, long needNewConfig, long retiring) {
        this.nodes = nodes;
        this.down = down;
        this.upgradingOs = upgradingOs;
        this.upgradingFirmware = upgradingFirmware;
        this.needPlatformUpgrade = needPlatformUpgrade;
        this.upgradingPlatform = upgradingPlatform;
        this.needReboot = needReboot;
        this.rebooting = rebooting;
        this.needRestart = needRestart;
        this.restarting = restarting;
        this.services = services;
        this.needNewConfig = needNewConfig;
        this.retiring = retiring;
    }

    /** Number of nodes in the application. */
    public long nodes() {
        return nodes;
    }

    /** Number of nodes allowed to be down. */
    public long down() {
        return down;
    }

    /** Number of nodes down for OS upgrade. */
    public long upgradingOs() {
        return upgradingOs;
    }

    /** Number of nodes down for firmware upgrade. */
    public long upgradingFirmware() {
        return upgradingFirmware;
    }

    /** Number of nodes in need of a platform upgrade. */
    public long needPlatformUpgrade() {
        return needPlatformUpgrade;
    }

    /** Number of nodes down for platform upgrade. */
    public long upgradingPlatform() {
        return upgradingPlatform;
    }

    /** Number of nodes in need of a reboot. */
    public long needReboot() {
        return needReboot;
    }

    /** Number of nodes down for reboot. */
    public long rebooting() {
        return rebooting;
    }

    /** Number of nodes in need of a restart. */
    public long needRestart() {
        return needRestart;
    }

    /** Number of nodes down for restart. */
    public long restarting() {
        return restarting;
    }

    /** Number of services in the application. */
    public long services() {
        return services;
    }

    /** Number of services with outdated config generation. */
    public long needNewConfig() {
        return needNewConfig;
    }

    /** Number of nodes that are retiring. */
    public long retiring() {
        return retiring;
    }

    /** Whether the convergence is done. */
    public boolean converged() {
        return     nodes > 0
                && needPlatformUpgrade == 0
                && needReboot == 0
                && needRestart == 0
                && services > 0
                && needNewConfig == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConvergenceSummary that = (ConvergenceSummary) o;
        return nodes == that.nodes &&
               down == that.down &&
               upgradingOs == that.upgradingOs &&
               upgradingFirmware == that.upgradingFirmware &&
               needPlatformUpgrade == that.needPlatformUpgrade &&
               upgradingPlatform == that.upgradingPlatform &&
               needReboot == that.needReboot &&
               rebooting == that.rebooting &&
               needRestart == that.needRestart &&
               restarting == that.restarting &&
               services == that.services &&
               needNewConfig == that.needNewConfig &&
               retiring == that.retiring;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, down, upgradingOs, upgradingFirmware, needPlatformUpgrade, upgradingPlatform, needReboot, rebooting, needRestart, restarting, services, needNewConfig, retiring);
    }

}


