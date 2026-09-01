package org.bsc.javelit;

import java.io.StringWriter;
import java.util.*;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.javelit.core.JtComponent;
import io.javelit.core.JtComponentBuilder;
import jakarta.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class JtDataTable<T> extends JtComponent<Set<Integer>> {

    private static final Mustache registerTemplate;
    private static final Mustache renderTemplate;

    static {
        final MustacheFactory mf = new DefaultMustacheFactory();
        registerTemplate = mf.compile("DataTable.register.html.mustache");
        renderTemplate = mf.compile("DataTable.render.html.mustache");
    }

    public static class Builder<T> extends JtComponentBuilder<Set<Integer>, JtDataTable<T>, Builder<T>> {

        List<T> data;
        Map<String, Function<T,String>> columns = new LinkedHashMap<>();


        private Builder(final List<T> data) {
            this.data = data;
        }

        public Builder<T> column(String name, Function<T,String> column) {
            this.columns.put(name, column);
            return this;
        }


        @Override
        public JtDataTable<T> build() {
            requireNonNull(data, "data cannot be null");
            checkArgument(!columns.isEmpty(), "columns cannot be empty");
            return new JtDataTable<>(this);
        }

    }

    public static <T> Builder<T> builder(final List<T> data) {
        return new Builder<>(data);
    }


    final @Nonnull List<T> data;
    final @Nonnull Map<String, Function<T,String>> columns;

    private JtDataTable(final @Nonnull Builder<T> builder) {
        super(builder, Set.of(), null);
        this.data = builder.data;
        this.columns = builder.columns;
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
    protected TypeReference<Set<Integer>> getTypeReference() {
        return new TypeReference<>() {
        };
    }


    @SuppressWarnings("unused")
        // used in templates
    String getColumnsJson() {
        return toJson(columns.keySet());
    }

    @SuppressWarnings("unused")
        // used in templates
    String getDataJson() {

        final var result = data.stream().map(row ->
                columns.entrySet().stream().reduce(new HashMap<String,String>(), (map, entry) -> {
                    map.put(entry.getKey(), entry.getValue().apply(row));
                    return map;
                }, (map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                })).toList();

        return toJson(result);

    }

}