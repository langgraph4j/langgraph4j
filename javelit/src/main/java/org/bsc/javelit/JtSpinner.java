package org.bsc.javelit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.javelit.core.JtComponent;
import io.javelit.core.JtComponentBuilder;
import io.javelit.core.JtContainer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Language;

import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class JtSpinner extends JtComponent<JtContainer> {

    static class InternalSpinner<T> extends JtComponent<T> {

        private static final Mustache registerTemplate;
        private static final Mustache renderTemplate;

        static {
            final MustacheFactory mf = new DefaultMustacheFactory();
            registerTemplate = mf.compile("InternalSpinner.register.html.mustache");
            renderTemplate = mf.compile("InternalSpinner.render.html.mustache");
        }

        // visible to the template engine
        final String message;
        final boolean showTime;
        final boolean overlay;
        final Supplier<T> onStart;
        final BiFunction<T,Duration,JtComponentBuilder<?,?,?>> onComplete;


        /**
         * Builder for the spinner component.
         */
        static class Builder<T> extends JtComponentBuilder<T, InternalSpinner<T>, Builder<T>> {
            private @Language("markdown") String message;
            private Boolean showTime = false;
            private Boolean overlay = false;
            private Supplier<T> onStart;
            private BiFunction<T,Duration,JtComponentBuilder<?,?,?>> onComplete;
            private JtContainer spinnerContainer;

            /**
             * The message content to display.
             * Markdown is supported.
             */
            public Builder<T> message(final @Language("markdown") @Nonnull String message) {
                this.message = message;
                return this;
            }

            public Builder<T> onStart(Supplier<T> onStart ) {
                this.onStart = onStart;
                return this;
            }

            public Builder<T> onComplete(BiFunction<T,Duration,JtComponentBuilder<?,?,?>> onComplete ) {
                this.onComplete = onComplete;
                return this;
            }

            /**
             * Whether to show the time.
             * @param showTime true to show the time.
             */
            public Builder<T> showTime(boolean showTime) {
                this.showTime = showTime;
                return this;
            }

            /**
             * Whether to show the overlay.
             * @param overlay true to show component as an overlay.
             */
            public Builder<T> overlay(boolean overlay) {
                this.overlay = overlay;
                return this;
            }

            @Override
            public InternalSpinner<T> build() {
                return new InternalSpinner<>(this);
            }


        }

        public static <T> InternalSpinner.Builder<T> builder() {
            return new InternalSpinner.Builder<T>();
        }

        private InternalSpinner(InternalSpinner.Builder<T> builder ) {
            super(builder, null, null);
            this.message = markdownToHtml(builder.message, true);
            this.showTime = builder.showTime;
            this.overlay = builder.overlay;
            this.onStart = builder.onStart;
            this.onComplete = builder.onComplete;

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
        protected TypeReference<T> getTypeReference() {
            return new TypeReference<T>() {
            };
        }

        @Override
        protected void afterUse(@Nonnull JtContainer container) {

            if( onStart != null ) {
                final var start = Instant.now();

                var result = onStart.get();

                super.setCurrentValue(result);

                if( onComplete != null ) {
                    final var duration = Duration.between(start, Instant.now());

                    var componentBuilder = onComplete.apply(result, duration);

                    componentBuilder.use(container);
                }
            }

        }
    }

    final Integer height;
    final Boolean border;
    private static final Mustache registerTemplate;
    private static final Mustache renderTemplate;
    final InternalSpinner.Builder<?> spinnerComponent;

    @SuppressWarnings("unchecked")
    private JtSpinner(Builder builder) {
        super(builder, null, null);
        this.height = builder.height;
        this.border = builder.border;

        this.spinnerComponent = InternalSpinner.builder()
                .key( getInternalKey() )
                .message(builder.message)
                .showTime(builder.showTime )
                .overlay(builder.overlay)
                .onStart((Supplier<Object>) builder.onStart)
                .onComplete((BiFunction<Object, Duration, JtComponentBuilder<?, ?, ?>>) builder.onComplete);

    }

    protected String register() {
        requireNonNull(this.currentValue, "Component has not been fully initialized yet. use() should be called before register().");
        StringWriter writer = new StringWriter();
        registerTemplate.execute(writer, this);
        return writer.toString();
    }

    protected String render() {
        requireNonNull(this.currentValue,"Component has not been fully initialized yet. use() should be called before register().");
        StringWriter writer = new StringWriter();
        renderTemplate.execute(writer, this);
        return writer.toString();
    }

    protected TypeReference<JtContainer> getTypeReference() {
        return new TypeReference<>() {
        };
    }

    @Override
    protected void beforeUse(@Nonnull JtContainer container) {
        this.currentValue = container.inPlaceChild(this.getInternalKey());

    }

    @Override
    protected void afterUse(@Nonnull JtContainer container) {
        spinnerComponent.use( currentValue);
    }

    static {
        MustacheFactory mf = new DefaultMustacheFactory();
        registerTemplate = mf.compile("components/layout/ContainerComponent.register.html.mustache");
        renderTemplate = mf.compile("components/layout/ContainerComponent.render.html.mustache");
    }

    public static class Builder extends JtComponentBuilder<JtContainer, JtSpinner, Builder> {
        @Nullable
        private Integer height;
        @Nullable
        private Boolean border;
        private @Language("markdown") String message;
        private Boolean showTime = true;
        private Boolean overlay = false;
        private Supplier<?> onStart;
        private BiFunction<?,Duration,JtComponentBuilder<?,?,?>> onComplete;

        public Builder() {
        }

        public Builder height(Integer height) {
            this.height = height;
            return this;
        }

        public Builder border(@Nullable Boolean border) {
            this.border = border;
            return this;
        }

        /**
         * The message content to display.
         * Markdown is supported.
         */
        public Builder message(final @Language("markdown") @Nonnull String message) {
            this.message = message;
            return this;
        }

        public <T> Builder onStart(Supplier<T> onStart ) {
            this.onStart = onStart;
            return this;
        }

        public <T> Builder onComplete(BiFunction<T, Duration,JtComponentBuilder<?,?,?>> onComplete ) {
            this.onComplete = onComplete;
            return this;
        }

        /**
         * Whether to show the time.
         * @param showTime true to show the time.
         */
        public Builder showTime(boolean showTime) {
            this.showTime = showTime;
            return this;
        }

        /**
         * Whether to show the overlay.
         */
        public Builder overlay() {
            this.overlay = true;
            return this;
        }

        public JtSpinner build() {
            if (this.border == null) {
                this.border = this.height != null;
            }

            return new JtSpinner(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}



