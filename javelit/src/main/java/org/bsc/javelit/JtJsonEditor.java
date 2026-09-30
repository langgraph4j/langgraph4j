package org.bsc.javelit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.javelit.core.JtComponent;
import io.javelit.core.JtComponentBuilder;

import java.io.StringWriter;

import static java.util.Objects.requireNonNull;

/**
 * A JSON Schema driven editor backed by the Jedison web component library.
 */
public class JtJsonEditor extends JtComponent<String> {

    private static final Mustache REGISTER_TEMPLATE;
    private static final Mustache RENDER_TEMPLATE;

    static {
        MustacheFactory mustacheFactory = new DefaultMustacheFactory();
        REGISTER_TEMPLATE = mustacheFactory.compile("JtJsonEditor.register.html.mustache");
        RENDER_TEMPLATE = mustacheFactory.compile("JtJsonEditor.render.html.mustache");
    }

    public static class Builder extends JtComponentBuilder<String, JtJsonEditor, Builder> {

        private String json;
        private String schema;
        private boolean disabled;

        private Builder() {
        }

        private Builder(String json, String schema) {
            this.json = json;
            this.schema = schema;
        }

        public Builder json(String json) {
            this.json = requireNonNull(json, "json cannot be null");
            return this;
        }

        public Builder schema(String schema) {
            this.schema = requireNonNull(schema, "schema cannot be null");
            return this;
        }

        /**
         * Prevents users from changing the JSON value.
         */
        public Builder disabled(boolean disabled) {
            this.disabled = disabled;
            return this;
        }

        @Override
        public JtJsonEditor build() {
            requireNonNull(json, "json cannot be null");
            requireNonNull(schema, "schema cannot be null");
            return new JtJsonEditor(this);
        }
    }

    /**
     * Creates an editor builder. The supplied strings must contain JSON data and a JSON Schema.
     */
    public static Builder builder(String json, String schema) {
        return new Builder(json, schema);
    }

    /**
     * Creates an editor builder whose JSON value and schema can be configured fluently.
     */
    public static Builder builder() {
        return new Builder();
    }

    private final String json;
    private final String schema;
    private final boolean disabled;

    private JtJsonEditor(Builder builder) {
        super(builder, builder.json, null);
        this.json = builder.json;
        this.schema = builder.schema;
        this.disabled = builder.disabled;
    }

    @SuppressWarnings("unused") // Used by the Mustache template.
    public String getJson() {
        return json;
    }

    @SuppressWarnings("unused") // Used by the Mustache template.
    public String getSchema() {
        return schema;
    }

    @SuppressWarnings("unused") // Used by the Mustache template.
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    protected String register() {
        StringWriter writer = new StringWriter();
        REGISTER_TEMPLATE.execute(writer, this);
        return writer.toString();
    }

    @Override
    protected String render() {
        StringWriter writer = new StringWriter();
        RENDER_TEMPLATE.execute(writer, this);
        return writer.toString();
    }

    @Override
    protected TypeReference<String> getTypeReference() {
        return new TypeReference<>() {
        };
    }
}
