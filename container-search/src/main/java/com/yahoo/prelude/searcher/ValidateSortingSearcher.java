// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yahoo.prelude.querytransform.NormalizingSearcher.ACCENT_REMOVAL;


/**
 * Check sorting specification makes sense to the search cluster before
 * passing it on to the backend.
 *
 * @author Steinar Knutsen
 */
@Before(PhaseNames.BACKEND)
@After(ACCENT_REMOVAL)
public class ValidateSortingSearcher extends Searcher {

    private Map<String, AttributesConfig.Attribute> attributeNames = null;
    private String clusterName = "";
    private final QrSearchersConfig.Searchcluster.Indexingmode.Enum indexingMode;

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    private Map<String, AttributesConfig.Attribute> getAttributeNames() {
        return attributeNames;
    }

    public void setAttributeNames(Map<String, AttributesConfig.Attribute> attributeNames) {
        this.attributeNames = attributeNames;
    }

    public void initAttributeNames(AttributesConfig config) {
        HashMap<String, AttributesConfig.Attribute> attributes = new HashMap<>(config.attribute().size());

        for (AttributesConfig.Attribute attr : config.attribute()) {
            if (AttributesConfig.Attribute.Collectiontype.SINGLE != attr.collectiontype()) {
                continue; // cannot sort on multivalue attributes
            }
            attributes.put(attr.name(), attr);
        }
        setAttributeNames(attributes);
    }

    public ValidateSortingSearcher(QrSearchersConfig qrsConfig, ClusterConfig clusterConfig,
                                   AttributesConfig attributesConfig)
    {
        initAttributeNames(attributesConfig);
        setClusterName(qrsConfig.searchcluster(clusterConfig.clusterId()).name());
        indexingMode = qrsConfig.searchcluster(clusterConfig.clusterId()).indexingmode();
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (indexingMode != QrSearchersConfig.Searchcluster.Indexingmode.STREAMING) {
            ErrorMessage e = validate(query);
            if (e != null) {
                Result r = new Result(query);
                r.hits().addError(e);
                return r;
            }
        }
        return execution.search(query);
    }

    private static Sorting.UcaSorter.Strength config2Strength(AttributesConfig.Attribute.Sortstrength.Enum s) {
        if(s == AttributesConfig.Attribute.Sortstrength.PRIMARY) {
            return Sorting.UcaSorter.Strength.PRIMARY;
        } else if(s == AttributesConfig.Attribute.Sortstrength.SECONDARY) {
            return Sorting.UcaSorter.Strength.SECONDARY;
        } else if(s == AttributesConfig.Attribute.Sortstrength.TERTIARY) {
            return Sorting.UcaSorter.Strength.TERTIARY;
        } else if(s == AttributesConfig.Attribute.Sortstrength.QUATERNARY) {
            return Sorting.UcaSorter.Strength.QUATERNARY;
        } else if(s == AttributesConfig.Attribute.Sortstrength.IDENTICAL) {
            return Sorting.UcaSorter.Strength.IDENTICAL;
        }
        return Sorting.UcaSorter.Strength.PRIMARY;
    }
    private ErrorMessage validate(Query query) {
        Sorting sorting = query.getRanking().getSorting();
        List<Sorting.FieldOrder> l = (sorting != null) ? sorting.fieldOrders() : null;

        if (l == null) {
            return null;
        }
        Map<String, AttributesConfig.Attribute> names = getAttributeNames();
        if (names == null) {
            return null;
        }

        String queryLocale = null;
        if (query.getModel().getLocale() != null) {
            queryLocale = query.getModel().getLocale().toString();
        }

        for (Sorting.FieldOrder f : l) {
            String name = f.getFieldName();
            if ("[rank]".equals(name) || "[docid]".equals(name)) {
                // built-in constants - ok
            } else if ("[relevancy]".equals(name)) {
                // built-in constant '[relevancy]' must map to '[rank]'
                f.getSorter().setName("[rank]");
            } else if (names.containsKey(name)) {
                AttributesConfig.Attribute attrConfig = names.get(name);
                if (attrConfig != null) {
                    if (f.getSortOrder() == Sorting.Order.UNDEFINED) {
                        f.setAscending(attrConfig.sortascending());
                    }
                    if (f.getSorter().getClass().equals(Sorting.AttributeSorter.class)) {
                        // This indicates that it shall use default.
                        if ((attrConfig.datatype() == AttributesConfig.Attribute.Datatype.STRING)) {
                            if (attrConfig.sortfunction() == AttributesConfig.Attribute.Sortfunction.UCA) {
                                String locale = attrConfig.sortlocale();
                                if (locale == null || locale.isEmpty()) {
                                    locale = queryLocale;
                                }
                                // can only use UcaSorter if we have knowledge about wanted locale
                                if (locale != null) {
                                    f.setSorter(new Sorting.UcaSorter(name, locale, Sorting.UcaSorter.Strength.UNDEFINED));
                                } else {
                                    // wanted UCA but no locale known, so use lowercase as fallback
                                    f.setSorter(new Sorting.LowerCaseSorter(name));
                                }
                            } else if (attrConfig.sortfunction() == AttributesConfig.Attribute.Sortfunction.LOWERCASE) {
                                f.setSorter(new Sorting.LowerCaseSorter(name));
                            } else if (attrConfig.sortfunction() == AttributesConfig.Attribute.Sortfunction.RAW) {
                                f.setSorter(new Sorting.RawSorter(name));
                            } else {
                                // default if no config found for this string attribute
                                f.setSorter(new Sorting.LowerCaseSorter(name));
                            }
                        }
                    }
                    if (f.getSorter() instanceof Sorting.UcaSorter) {
                        Sorting.UcaSorter sorter = (Sorting.UcaSorter) f.getSorter();
                        String locale = sorter.getLocale();

                        if (locale == null || locale.isEmpty()) {
                            // first fallback
                            locale = attrConfig.sortlocale();
                        }
                        if (locale == null || locale.isEmpty()) {
                            // second fallback
                            locale = queryLocale;
                        }
                        // final fallback
                        if (locale == null || locale.isEmpty()) {
                            locale = "en_US";
                        }

                        Sorting.UcaSorter.Strength strength = sorter.getStrength();
                        if (sorter.getStrength() == Sorting.UcaSorter.Strength.UNDEFINED) {
                            strength = config2Strength(attrConfig.sortstrength());
                        }
                        if ((sorter.getStrength() == Sorting.UcaSorter.Strength.UNDEFINED) || (sorter.getLocale() == null) || sorter.getLocale().isEmpty()) {
                            sorter.setLocale(locale, strength);
                        }
                    }
                } else {
                    return ErrorMessage.createInvalidQueryParameter("The cluster " + getClusterName() + " has attribute config for field: " + name);
                }
            } else {
                return ErrorMessage.createInvalidQueryParameter("The cluster " + getClusterName() + " has no sortable attribute named: " + name);
            }
        }
        return null;
    }
}
