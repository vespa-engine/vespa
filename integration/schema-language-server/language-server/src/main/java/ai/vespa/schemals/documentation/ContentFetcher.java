package ai.vespa.schemals.documentation;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Node;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.renderer.ResolvedLink;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.html2md.converter.HtmlLinkResolver;
import com.vladsch.flexmark.html2md.converter.HtmlLinkResolverFactory;
import com.vladsch.flexmark.html2md.converter.HtmlNodeConverterContext;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * ContentFetcher
 * Common logic for setting options for HTML -> Markdown converter and link resolving.
 * Override {@link getMarkdownContent} to return a set of key/value pairs, where key gives a markdown file name, and value is markdown content.
 */
public abstract class ContentFetcher {
    protected final static String URL_PREFIX = "https://docs.vespa.ai/";

    String fileUrl;

    ContentFetcher(String relativeFileUrl) {
        this.fileUrl = relativeFileUrl;
    }

    abstract Map<String, String> getMarkdownContent() throws IOException;

    FlexmarkHtmlConverter getHtmlParser() {
        MutableDataSet options = new MutableDataSet()
            .set(FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID, false)
            .set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false)
            .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
            .set(Parser.EXTENSIONS, Collections.singletonList(new HtmlConverterTextExtension(fileUrl)));

        return FlexmarkHtmlConverter.builder(options).build();
    }

    static class CustomLinkResolver implements HtmlLinkResolver {
        String fileUrl;

        public CustomLinkResolver(HtmlNodeConverterContext context, String fileUrl) { 
            this.fileUrl = fileUrl;
        }

        @Override
        public ResolvedLink resolveLink(Node node, HtmlNodeConverterContext context, ResolvedLink link) {
            // convert all links from relative to absolute http url.
            String curr = link.getUrl();

            if (curr.startsWith("http")) 
                return link;

            try {
                return link.withUrl(new URI(URL_PREFIX).resolve(fileUrl).resolve(curr).toString());
            } catch(Exception e) {
            }

            if (curr.startsWith("#"))
                return link.withUrl(URL_PREFIX + fileUrl + curr);

            if (curr.startsWith("."))
                return link.withUrl(URL_PREFIX + fileUrl + curr);

            return link.withUrl(URL_PREFIX + curr);
        }

        static class Factory implements HtmlLinkResolverFactory {
            String fileUrl;

            @Nullable
            @Override
            public Set<Class<?>> getAfterDependents() {
                return null;
            }

            @Nullable
            @Override
            public Set<Class<?>> getBeforeDependents() {
                return null;
            }

            @Override
            public boolean affectsGlobalScope() {
                return false;
            }

            @Override
            public HtmlLinkResolver apply(HtmlNodeConverterContext context) {
                return new CustomLinkResolver(context, this.fileUrl);
            }

            public Factory(String fileUrl) {
                this.fileUrl = fileUrl;
            }
        }
    }

    static class HtmlConverterTextExtension implements FlexmarkHtmlConverter.HtmlConverterExtension {
        private String fileUrl;

        public HtmlConverterTextExtension(String fileUrl) {
            this.fileUrl = fileUrl;
        }

        @Override
        public void rendererOptions(@NotNull MutableDataHolder options) {

        }

        @Override
        public void extend(FlexmarkHtmlConverter.@NotNull Builder builder) {
            builder.linkResolverFactory(new CustomLinkResolver.Factory(this.fileUrl));
        }
    }
}
