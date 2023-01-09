package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.util.List;
import java.util.Map;

/**
 * @author gjoranv
 */
public class BundleTestUtil {

    public static final FileReference BUNDLE_1_REF = new FileReference("bundle-1");
    public static final Bundle BUNDLE_1 = new TestBundle(BUNDLE_1_REF.value());
    public static final FileReference BUNDLE_2_REF = new FileReference("bundle-2");
    public static final Bundle BUNDLE_2 = new TestBundle(BUNDLE_2_REF.value());

    public static Map<String, Bundle> testBundles() {
        return Map.of(BUNDLE_1_REF.value(), BUNDLE_1,
                      BUNDLE_2_REF.value(), BUNDLE_2);
    }

    public static class TestBundleInstaller extends FileAcquirerBundleInstaller {

        TestBundleInstaller(FileAcquirer fileAcquirer) {
            super(fileAcquirer);
        }

        @Override
        public List<Bundle> installBundles(FileReference reference, Osgi osgi) {
            return osgi.install(reference.value());
        }

    }

}
