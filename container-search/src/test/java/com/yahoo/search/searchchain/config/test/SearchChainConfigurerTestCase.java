// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.config.test;

import com.yahoo.config.search.IntConfig;
import com.yahoo.config.search.StringConfig;
import com.yahoo.container.core.config.HandlersConfigurerDi;
import com.yahoo.container.core.config.testutil.HandlersConfigurerTestWrapper;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.ExecutionFactory;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.SearcherRegistry;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;

/**
 * @author bratseth
 * @author gjoranv
 */
public class SearchChainConfigurerTestCase {

    private static final Random random = new Random(1);
    private static final String topCfgDir = System.getProperty("java.io.tmpdir") + File.separator +
            "SearchChainConfigurerTestCase" + File.separator;

    private static final String testDir = "src/test/java/com/yahoo/search/searchchain/config/test/";

    public void cleanup(File cfgDir) {
        if (cfgDir.exists()) {
            for (File f : cfgDir.listFiles()) {
                f.delete();
            }
            cfgDir.delete();
        }
    }

    @BeforeClass
    public static void createDefaultComponentsConfigs() throws IOException {
        createComponentsConfig(testDir + "chains.cfg", testDir + "handlers.cfg", testDir + "components.cfg");
    }

    @AfterClass
    public static void removeDefaultComponentsConfigs() {
        new File(testDir + "components.cfg").delete();
    }

    private SearchChainRegistry getSearchChainRegistryFrom(HandlersConfigurerTestWrapper configurer) {
        return ((SearchHandler)configurer.getRequestHandlerRegistry().
                getComponent("com.yahoo.search.handler.SearchHandler")).getSearchChainRegistry();
    }

    @Test
    public synchronized void testConfiguration() {
        HandlersConfigurerTestWrapper configurer = new HandlersConfigurerTestWrapper("dir:" + testDir);

        SearchChain simple=getSearchChainRegistryFrom(configurer).getComponent("simple");
        assertNotNull(simple);
        assertThat(getSearcherNumbers(simple), is(Arrays.asList(1, 2, 3)));

        SearchChain child1=getSearchChainRegistryFrom(configurer).getComponent("child:1");
        assertThat(getSearcherNumbers(child1), is(Arrays.asList(1, 2, 4, 5, 7, 8)));

        SearchChain child2=getSearchChainRegistryFrom(configurer).getComponent("child");
        assertThat(getSearcherNumbers(child2), is(Arrays.asList(3, 6, 7, 9)));

        // Verify successful loading of an explicitly declared searcher that takes no user-defined configs.
        //assertNotNull(SearchChainRegistry.get().getSearcherRegistry().getComponent
        //        ("com.yahoo.search.searchchain.config.test.SearchChainConfigurerTestCase$DeclaredTestSearcher"));
        configurer.shutdown();
    }

    private List<Integer> getSearcherNumbers(SearchChain chain) {
        List<Integer> numbers = new ArrayList<>();
        for (int i=0; i<chain.searchers().size(); i++) {
            String prefix=TestSearcher.class.getName();
            assertTrue(chain.searchers().get(i).getId().getName().startsWith(prefix));
            int value = Integer.parseInt(chain.searchers().get(i).getId().getName().substring(prefix.length()));
            numbers.add(value);
        }
        Collections.sort(numbers);
        return numbers;
    }

   public static abstract class TestSearcher extends Searcher {
        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }
    }
    public static final class TestSearcher1 extends TestSearcher {}
    public static final class TestSearcher2 extends TestSearcher {}
    public static final class TestSearcher3 extends TestSearcher {}
    public static final class TestSearcher4 extends TestSearcher {}
    public static final class TestSearcher5 extends TestSearcher {}
    public static final class TestSearcher6 extends TestSearcher {}
    public static final class TestSearcher7 extends TestSearcher {}
    public static final class TestSearcher8 extends TestSearcher {}
    public static final class TestSearcher9 extends TestSearcher {}
    public static final class DeclaredTestSearcher extends TestSearcher {}

    @Test
    public  void testConfigurableSearcher() {
        HandlersConfigurerTestWrapper configurer=new HandlersConfigurerTestWrapper("dir:" + testDir);

        SearchChain configurable = getSearchChainRegistryFrom(configurer).getComponent("configurable");
        assertNotNull(configurable);

        Searcher s = configurable.searchers().get(0);
        assertThat(s, instanceOf(ConfigurableSearcher.class));
        ConfigurableSearcher searcher = (ConfigurableSearcher)s;
        assertThat("Value from int.cfg file", searcher.intConfig.intVal(), is(7));
        assertThat("Value from string.cfg file", searcher.stringConfig.stringVal(),
                is("com.yahoo.search.searchchain.config.test"));
        configurer.shutdown();
    }

    /**
     * Verifies that only searchers with updated config are re-instantiated after a config update
     * that does not contain any bootstrap configs.
     */
    @Test
    public void testSearcherConfigUpdate() throws IOException {
        File cfgDir = getCfgDir();
        copyFile(testDir + "handlers.cfg", cfgDir +  "/handlers.cfg");
        copyFile(testDir + "qr-search.cfg", cfgDir +  "/qr-search.cfg");
        copyFile(testDir + "qr-searchers.cfg", cfgDir +  "/qr-searchers.cfg");
        copyFile(testDir + "index-info.cfg", cfgDir +  "/index-info.cfg");
        copyFile(testDir + "specialtokens.cfg", cfgDir +  "/specialtokens.cfg");
        copyFile(testDir + "three-searchers.cfg", cfgDir +  "/chains.cfg");
        copyFile(testDir + "container-http.cfg", cfgDir +  "/container-http.cfg");
        createComponentsConfig(testDir + "three-searchers.cfg", testDir + "handlers.cfg", cfgDir +  "/components.cfg");
        printFile(new File(cfgDir + "/int.cfg"), "intVal 16\n");
        printFile(new File(cfgDir + "/string.cfg"), "stringVal \"testSearcherConfigUpdate\"\n");

        HandlersConfigurerTestWrapper configurer = new HandlersConfigurerTestWrapper("dir:" + cfgDir);
        SearcherRegistry searchers = getSearchChainRegistryFrom(configurer).getSearcherRegistry();
        assertThat(searchers.getComponentCount(), is(3));

        IntSearcher intSearcher = (IntSearcher)searchers.getComponent(IntSearcher.class.getName());
        assertThat(intSearcher.intConfig.intVal(), is(16));
        StringSearcher stringSearcher = (StringSearcher)searchers.getComponent(StringSearcher.class.getName());
        DeclaredTestSearcher noConfigSearcher =
                (DeclaredTestSearcher)searchers.getComponent(DeclaredTestSearcher.class.getName());

        // Update int config for IntSearcher,
        printFile(new File(cfgDir + "/int.cfg"), "intVal 17\n");
        configurer.reloadConfig();

        // Registry is rebuilt
        assertThat(getSearchChainRegistryFrom(configurer).getSearcherRegistry(), not(searchers));
        searchers = getSearchChainRegistryFrom(configurer).getSearcherRegistry();
        assertThat(searchers.getComponentCount(), is(3));

        // Searcher with updated config is re-instantiated.
        IntSearcher intSearcher2 = (IntSearcher)searchers.getComponent(IntSearcher.class.getName());
        assertThat(intSearcher2, not(sameInstance(intSearcher)));
        assertThat(intSearcher2.intConfig.intVal(), is(17));

        // Searchers with unchanged config (or that takes no config) are the same as before.
        Searcher s = searchers.getComponent(DeclaredTestSearcher.class.getName());
        assertThat(s, sameInstance(noConfigSearcher));
        s = searchers.getComponent(StringSearcher.class.getName());
        assertThat(s, sameInstance(stringSearcher));

        configurer.shutdown();
        cleanup(cfgDir);
    }

    /**
     * Updates the chains config, while the searcher configs are unchanged.
     * Verifies that a new searcher that was not in the old config is instantiated,
     * and that a searcher that has been removed from the configuration is not in the new registry.
     */
    @Test
    public void testChainsConfigUpdate() throws IOException {
        File cfgDir = getCfgDir();
        copyFile(testDir + "handlers.cfg", cfgDir +  "/handlers.cfg");
        copyFile(testDir + "qr-search.cfg", cfgDir +  "/qr-search.cfg");
        copyFile(testDir + "qr-searchers.cfg", cfgDir +  "/qr-searchers.cfg");
        copyFile(testDir + "index-info.cfg", cfgDir +  "/index-info.cfg");
        copyFile(testDir + "specialtokens.cfg", cfgDir +  "/specialtokens.cfg");
        copyFile(testDir + "chainsConfigUpdate_1.cfg", cfgDir +  "/chains.cfg");
        copyFile(testDir + "container-http.cfg", cfgDir +  "/container-http.cfg");
        createComponentsConfig(testDir + "chainsConfigUpdate_1.cfg", testDir + "handlers.cfg", cfgDir +  "/components.cfg");

        HandlersConfigurerTestWrapper configurer = new HandlersConfigurerTestWrapper("dir:" + cfgDir);

        SearchChainRegistry scReg = getSearchChainRegistryFrom(configurer);
        SearcherRegistry searchers = scReg.getSearcherRegistry();
        assertThat(searchers.getComponentCount(), is(2));
        assertThat(searchers.getComponent(IntSearcher.class.getName()), instanceOf(IntSearcher.class));
        assertThat(searchers.getComponent(StringSearcher.class.getName()), instanceOf(StringSearcher.class));
        assertThat(searchers.getComponent(ConfigurableSearcher.class.getName()), nullValue());
        assertThat(searchers.getComponent(DeclaredTestSearcher.class.getName()), nullValue());

        IntSearcher intSearcher = (IntSearcher)searchers.getComponent(IntSearcher.class.getName());

        // Update chains config
        copyFile(testDir + "chainsConfigUpdate_2.cfg", cfgDir +  "/chains.cfg");
        createComponentsConfig(testDir + "chainsConfigUpdate_2.cfg", testDir + "handlers.cfg", cfgDir +  "/components.cfg");
        configurer.reloadConfig();

        assertThat(getSearchChainRegistryFrom(configurer), not(scReg));

        // In the new registry, the correct searchers are removed and added
        assertThat(getSearchChainRegistryFrom(configurer).getSearcherRegistry(), not(searchers));
        searchers = getSearchChainRegistryFrom(configurer).getSearcherRegistry();
        assertThat(searchers.getComponentCount(), is(3));
        assertThat(searchers.getComponent(IntSearcher.class.getName()), sameInstance(intSearcher));
        assertThat(searchers.getComponent(ConfigurableSearcher.class.getName()), instanceOf(ConfigurableSearcher.class));
        assertThat(searchers.getComponent(DeclaredTestSearcher.class.getName()), instanceOf(DeclaredTestSearcher.class));
        assertThat(searchers.getComponent(StringSearcher.class.getName()), nullValue());
        configurer.shutdown();
        cleanup(cfgDir);
    }

    public static class ConfigurableSearcher extends Searcher {
        IntConfig intConfig;
        StringConfig stringConfig;

        public ConfigurableSearcher(IntConfig intConfig) {
            this.intConfig = intConfig;
        }
        public ConfigurableSearcher(IntConfig intConfig, StringConfig stringConfig) {
            this.intConfig = intConfig;
            this.stringConfig = stringConfig;
        }
        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }
    }

    public static class IntSearcher extends Searcher {
        IntConfig intConfig;
        public IntSearcher(IntConfig intConfig) {
            this.intConfig = intConfig;
        }
        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }
    }

    public static class StringSearcher extends Searcher {
        StringConfig stringConfig;
        public StringSearcher(StringConfig stringConfig) {
            this.stringConfig = stringConfig;
        }
        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }
    }


    //// Helper methods

    public static void printFile(File f, String content) throws IOException {
        OutputStream out = new FileOutputStream(f);
        out.write(content.getBytes());
        out.close();

    }

    /**
     * Copies src file to dst file. If the dst file does not exist, it is created.
     */
    public static void copyFile(String srcName, String dstName) throws IOException {
        InputStream src = new FileInputStream(new File(srcName));
        OutputStream dst = new FileOutputStream(new File(dstName));
        byte[] buf = new byte[1024];
        int len;
        while ((len = src.read(buf)) > 0) {
            dst.write(buf, 0, len);
        }
        src.close();
        dst.close();
    }

    public static File getCfgDir() {
        String token = Long.toHexString(random.nextLong());
        File cfgDir = new File(topCfgDir + File.separator + token + File.separator);
        cfgDir.mkdirs();
        return cfgDir;
    }

    /**
     * Copies the ids from the 'search' array in chains to a 'components' array in a new components file.
     * Also adds the default SearchHandler.
     */
    public static void createComponentsConfig(String chainsFile, String handlersFile, String componentsFile) throws IOException {
        createComponentsConfig(handlersFile, componentsFile, "handler", false);
        createComponentsConfig(chainsFile, componentsFile, "components", true);
    }

    /**
     * Copies the component ids from another config, e.g. 'handlers' to a 'components' array in a new components file,
     * to avoid a manually written 'components' file for tests where the bundle spec is given by the component id.
     *
     * @param configFile  Full path to the original config file, e.g. 'handlers'
     * @param componentsFile  Full path to the new 'components' file
     * @param componentType   The type of component, e.g. 'handler'
     * @param append  'true' will append to an already existing 'componentsFile'
     */
    public static void createComponentsConfig(String configFile,
                                              String componentsFile,
                                              String componentType,
                                              boolean append) throws IOException {
        StringBuilder buf = new StringBuilder();
        String line;
        int i = 0;
        if (append) {
            Pattern p = Pattern.compile("^[a-z]+" + "\\[\\d+\\]\\.id (.+)");
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                                                       new FileInputStream(new File(componentsFile)), StandardCharsets.UTF_8));
            while ((line = reader.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.matches() && !m.group(1).equals(HandlersConfigurerDi.RegistriesHack.class.getName())) {
                    buf.append("components[").append(i).append("].id ").append(m.group(1)).append("\n");
                    i++;
                }
            }
            reader.close();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(configFile)), StandardCharsets.UTF_8));
        Pattern component = Pattern.compile("^" + componentType + "\\[\\d+\\]\\.id (.+)");
        while ((line = reader.readLine()) != null) {
            Matcher m = component.matcher(line);
            if (m.matches()) {
                buf.append("components[").append(i).append("].id ").append(m.group(1)).append("\n");
                i++;
            }
        }
        reader.close();

        buf.append("components[").append(i++).append("].id ").append(HandlersConfigurerDi.RegistriesHack.class.getName()).append("\n");
        if (componentType.equals("components"))
            buf.append("components[").append(i++).append("].id ").append(ExecutionFactory.class.getName()).append("\n");
        buf.insert(0, "components["+i+"]\n");

        Writer writer = new OutputStreamWriter(new FileOutputStream(new File(componentsFile)), StandardCharsets.UTF_8);
        writer.write(buf.toString());
        writer.flush();
        writer.close();
    }

}
