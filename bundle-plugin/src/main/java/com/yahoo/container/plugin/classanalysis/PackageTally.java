// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import com.google.common.collect.Sets;
import com.yahoo.container.plugin.util.Maps;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class PackageTally {
    private final Map<String, Optional<ExportPackageAnnotation>> definedPackagesMap;
    private final Set<String> referencedPackagesUnfiltered;

    public PackageTally(Map<String, Optional<ExportPackageAnnotation>> definedPackagesMap, Set<String> referencedPackagesUnfiltered) {
        this.definedPackagesMap = definedPackagesMap;
        this.referencedPackagesUnfiltered = referencedPackagesUnfiltered;
    }

    public Set<String> definedPackages() {
        return definedPackagesMap.keySet();
    }

    public Set<String> referencedPackages() {
        return Sets.difference(referencedPackagesUnfiltered, definedPackages());
    }

    public Map<String, ExportPackageAnnotation> exportedPackages() {
        Map<String, ExportPackageAnnotation> ret = new HashMap<>();
        definedPackagesMap.forEach((k, v) -> {
            v.ifPresent(annotation -> ret.put(k, annotation));
        });
        return ret;
    }

    /**
     * Represents the classes for two package tallies that are deployed as a single unit.
     * <p>
     * ExportPackageAnnotations from this has precedence over the other.
     */
    public PackageTally combine(PackageTally other) {
        Map<String, Optional<ExportPackageAnnotation>> map = Maps.combine(this.definedPackagesMap, other.definedPackagesMap,
                (l, r) -> l.isPresent() ? l : r);
        Set<String> referencedPkgs = new HashSet<>(this.referencedPackagesUnfiltered);
        referencedPkgs.addAll(other.referencedPackagesUnfiltered);

        return new PackageTally(map, referencedPkgs);
    }

    public static PackageTally combine(Collection<PackageTally> packageTallies) {
        Map<String, Optional<ExportPackageAnnotation>> map = new HashMap<>();
        Set<String> referencedPkgs = new HashSet<>();

        for (PackageTally pt : packageTallies) {
            pt.definedPackagesMap.forEach((k, v) -> map.merge(k, v, (l, r) -> l.isPresent() ? l : r));
            referencedPkgs.addAll(pt.referencedPackagesUnfiltered);
        }
        return new PackageTally(map, referencedPkgs);
    }

    public static PackageTally fromAnalyzedClassFiles(Collection<ClassFileMetaData> analyzedClassFiles) {
        Map<String, Optional<ExportPackageAnnotation>> map = new HashMap<>();
        Set<String> referencedPkgs = new HashSet<>();

        for (ClassFileMetaData metaData : analyzedClassFiles) {
            String packageName = Packages.packageName(metaData.getName());
            map.merge(packageName, metaData.getExportPackage(), (l, r) -> l.isPresent() ? l : r);
            metaData.getReferencedClasses().forEach(className -> referencedPkgs.add(Packages.packageName(className)));
        }
        return new PackageTally(map, referencedPkgs);
    }
}
