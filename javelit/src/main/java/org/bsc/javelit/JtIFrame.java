package org.bsc.javelit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.javelit.core.JtComponent;
import io.javelit.core.JtComponentBuilder;
import io.javelit.core.JtContainer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class JtIFrame extends JtComponent<Void> {

    private static final Mustache registerTemplate;
    private static final Mustache renderTemplate;

    static {
        MustacheFactory mf = new DefaultMustacheFactory();
        registerTemplate = mf.compile("IFrame.register.html.mustache");
        renderTemplate = mf.compile("IFrame.render.html.mustache");
    }

    public static class Builder extends JtComponentBuilder<Void, JtIFrame, JtIFrame.Builder> {

        private URI uri;
        private String height;

        public Builder height(@NonNull String height) {
            this.height = requireNonNull(height, "height is null");
            return this;
        }
        public Builder height(int height) {
            this.height = "%dpx".formatted(height);
            return this;
        }

        public Builder uri(@NonNull URI uri) {
            this.uri = uri;
            return this;
        }

        public Builder uri(@NonNull URL url)  {
            try {
                this.uri = url.toURI();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
            return this;
        }

        public Builder uri(@NonNull String url) {
            try {
                return uri(new URL(url));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public JtIFrame build() {
            return new JtIFrame(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final URI uri;
    final String height;

    protected JtIFrame(@NonNull Builder builder) {
        super(builder, null, null, JtContainer.MAIN);
        this.uri = builder.uri;
        this.height = ofNullable(builder.height).orElse("100%");
    }

    public String uriAsString() {
        return uri.toString();
    }

    @Override
    protected String register() {
        final StringWriter writer = new StringWriter();
        registerTemplate.execute(writer, this);
        return writer.toString();
    }

    @Override
    protected String render() {
        final StringWriter writer = new StringWriter();
        renderTemplate.execute(writer, this);
        return writer.toString();
    }

    @Override
    protected TypeReference<Void> getTypeReference() {
        return new TypeReference<>() {};
    }
}
