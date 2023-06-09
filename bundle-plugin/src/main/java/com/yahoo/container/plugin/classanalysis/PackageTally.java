// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import com.google.common.collect.Sets;
import com.yahoo.container.plugin.util.Maps;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class PackageTally {

    private final Map<String, PackageInfo> definedPackages;
    private final Set<String> referencedPackagesUnfiltered;

    PackageTally(Map<String, PackageInfo> definedPackages, Set<String> referencedPackagesUnfiltered) {
        this.definedPackages = definedPackages;
        this.referencedPackagesUnfiltered = referencedPackagesUnfiltered;
    }

    public Set<String> definedPackages() {
        return definedPackages.keySet();
    }

    public Set<String> referencedPackages() {
        return Sets.difference(referencedPackagesUnfiltered, definedPackages());
    }

    public Map<String, ExportPackageAnnotation> exportedPackages() {
        Map<String, ExportPackageAnnotation> ret = new HashMap<>();
        definedPackages.forEach((pkg, pkgInfo) -> {
            pkgInfo.exportPackage().ifPresent(a -> ret.put(pkg, a));
        });
        return ret;
    }

    public Set<String> publicApiPackages() {
        return definedPackages.values().stream()
                .filter(PackageInfo::isPublicApi)
                .map(PackageInfo::name)
                .collect(Collectors.toSet());
    }

    public Set<String> nonPublicApiExportedPackages() {
        return definedPackages.values().stream()
                .filter(pkgInfo -> pkgInfo.exportPackage().isPresent())
                .filter(pkgInfo -> ! pkgInfo.isPublicApi())
                .map(PackageInfo::name)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the set of packages that is referenced from this tally, but not included in the given set of available packages.
     *
     * @param definedAndExportedPackages Set of available packages (usually all packages defined in the generated bundle's project + all exported packages of dependencies)
     * @return The set of missing packages, that may cause problem when the bundle is deployed in an OSGi container runtime.
     */
    public Set<String> referencedPackagesMissingFrom(Set<String> definedAndExportedPackages) {
        return Sets.difference(referencedPackages(), definedAndExportedPackages).stream()
                .filter(pkg -> !pkg.startsWith("java."))
                .filter(pkg -> !pkg.equals(com.yahoo.api.annotations.PublicApi.class.getPackageName()))
                .collect(Collectors.toSet());
    }

    /**
     * Represents the classes for two package tallies that are deployed as a single unit.
     * <p>
     * ExportPackageAnnotations from this has precedence over the other.
     * TODO: Add unit test and try using Map.merge (as in the functions below). Can't see how Maps.combine is any different.
     */
    public PackageTally combine(PackageTally other) {
        var definedPkgs = Maps.combine(this.definedPackages, other.definedPackages, PackageInfo::hasExportPackageOrElse);
        Set<String> referencedPkgs = new HashSet<>(this.referencedPackagesUnfiltered);
        referencedPkgs.addAll(other.referencedPackagesUnfiltered);

        return new PackageTally(definedPkgs, referencedPkgs);
    }

    public static PackageTally combine(Collection<PackageTally> packageTallies) {
        var definedPkgs = new HashMap<String, PackageInfo>();
        Set<String> referencedPkgs = new HashSet<>();

        for (PackageTally tally : packageTallies) {
            tally.definedPackages.forEach((pkg, info) -> definedPkgs.merge(pkg, info, PackageInfo::hasExportPackageOrElse));
            referencedPkgs.addAll(tally.referencedPackagesUnfiltered);
        }
        return new PackageTally(definedPkgs, referencedPkgs);
    }

    public static PackageTally fromAnalyzedClassFiles(Collection<ClassFileMetaData> analyzedClassFiles) {
        var definedPkgs = new HashMap<String, PackageInfo>();
        var referencedPkgs = new HashSet<String>();

        for (ClassFileMetaData classData : analyzedClassFiles) {
            var pkgName = classData.packageInfo().name();
            definedPkgs.merge(pkgName, classData.packageInfo(), PackageInfo::hasExportPackageOrElse);
            classData.getReferencedClasses().forEach(className -> referencedPkgs.add(Packages.packageName(className)));
        }
        return new PackageTally(definedPkgs, referencedPkgs);
    }

}
