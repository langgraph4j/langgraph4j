package org.bsc.langgraph4j;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;


@FunctionalInterface
interface InterruptPredicate {

    <State extends AgentState> boolean test( String nodeId, State state );

    default <State extends AgentState> BiPredicate<String,State> asPredicate() {
        return this::test;
    }

    static  <State extends AgentState> InterruptPredicate of( BiPredicate<String, State> predicate ) {

        return new InterruptPredicate() {

            @Override
            @SuppressWarnings("unchecked")
            public <S extends AgentState> boolean test(String nodeId, S state) {
                return predicate.test( nodeId, (State) state );
            }
        };
    }
}

/**
 * class is a configuration container for defining compile settings and behaviors.
 * It includes various fields and methods to manage checkpoint savers and interrupts, providing both deprecated and current accessors.
 */
public class CompileConfig {

    private BaseCheckpointSaver checkpointSaver;

    private Set<String> interruptsAfter = Set.of();
    private Set<String> interruptsBefore = Set.of();
    private boolean releaseThread = false;
    private InterruptPredicate interruptBeforePredicate;
    private InterruptPredicate interruptAfterPredicate;

    /**
     * Returns the array of interrupts that will occur before the specified node.
     *
     * @return an unmodifiable {@link Set} of interruptible nodes.
     */
    public Set<String> interruptsBefore() { return interruptsBefore; }

    /**
     * Returns the array of interrupts that will occur after the specified node.
     *
     * @return an unmodifiable {@link Set} of interruptible nodes.
     */
    public Set<String> interruptsAfter() { return interruptsAfter; }

    public <State extends AgentState> BiPredicate<String,State> interruptBeforePredicate() {
        return interruptBeforePredicate.asPredicate();
    }

    public <State extends AgentState> BiPredicate<String,State> interruptAfterPredicate() {
        return interruptAfterPredicate.asPredicate();
    }

    /**
     * Returns the current {@code BaseCheckpointSaver} instance if it is not {@code null},
     * otherwise returns an empty {@link Optional}.
     *
     * @return an {@link Optional} containing the current {@code BaseCheckpointSaver} instance, or an empty {@link Optional} if it is {@code null}
     */
    public Optional<BaseCheckpointSaver> checkpointSaver() { return ofNullable(checkpointSaver); }

    /**
     * Returns the current state of the thread release flag.
     *
     * @see BaseCheckpointSaver#release(RunnableConfig)
     * @return true if the thread has been released, false otherwise
     */
    public boolean releaseThread() {
        return releaseThread;
    }
    /**
     * Returns a new {@link Builder} instance with the default {@link CompileConfig}.
     *
     * @return A {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder(new CompileConfig());
    }
    /**
     * Creates a new {@link Builder} instance with the specified Compile configuration.
     *
     * @param config The {@link CompileConfig} to be used for compilation settings.
     * @return A new {@link Builder} instance initialized with the given compilation configuration.
     */
    public static Builder builder( CompileConfig config ) {
        return new Builder(config);
    }

    /**
     * This class is a builder for {@link CompileConfig}. It allows for the configuration of various options
     * to customize the compilation process.
     *
     */
    public static class Builder {
        private final CompileConfig config;
        private InterruptPredicate _interruptBeforePredicate;
        private InterruptPredicate _interruptAfterPredicate;

        /**
         * Constructs a new instance of {@code Builder} with the specified compile configuration.
         *
         * @param config The compile configuration to be used. This value must not be {@literal null}.
         */
        protected Builder( CompileConfig config ) {
            this.config = new CompileConfig(config);
        }
        /**
         * Sets the checkpoint saver for the configuration.
         *
         * @param checkpointSaver The {@code BaseCheckpointSaver} to set.
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder checkpointSaver(BaseCheckpointSaver checkpointSaver) {
            this.config.checkpointSaver = checkpointSaver;
            return this;
        }
        /**
         * Sets the actions to be performed before an interruption.
         *
         * @param interruptBefore the actions to be performed before an interruption
         * @return a reference to the current instance of Builder
         */
        public Builder interruptBefore(String... interruptBefore) {
            this.config.interruptsBefore = Set.of(interruptBefore);
            return this;
        }
        /**
         * Sets the strings that cause an interrupt in the configuration.
         *
         * @param interruptAfter An array of string values representing the interruptions.
         * @return The current Builder instance, allowing method chaining.
         */
        public Builder interruptAfter(String... interruptAfter) {
            this.config.interruptsAfter = Set.of(interruptAfter);
            return this;
        }

        public <State extends AgentState> Builder interruptBeforePredicate( BiPredicate<String, State> interruptBeforePredicate ) {
            this._interruptBeforePredicate = InterruptPredicate.of(interruptBeforePredicate);
            return this;
        }

        public <State extends AgentState> Builder interruptAfterPredicate( BiPredicate<String, State> interruptAfterPredicate ) {
            this._interruptAfterPredicate = InterruptPredicate.of(interruptAfterPredicate);
            return this;
        }

        /**
         * Sets the collection of interrupts to be executed before the configuration.
         *
         * @param interruptsBefore The collection of interrupt strings.
         * @return This builder instance for method chaining.
         */
        public Builder interruptsBefore(Collection<String> interruptsBefore) {
            this.config.interruptsBefore = interruptsBefore.stream().collect(Collectors.toUnmodifiableSet());
            return this;
        }
        /**
         * Sets the collection of strings that specify which interrupts should occur after.
         *
         * @param interruptsAfter Collection of interrupt identifiers
         * @return The current Builder instance for method chaining
         */
        public Builder interruptsAfter(Collection<String> interruptsAfter) {
            this.config.interruptsAfter = interruptsAfter.stream().collect(Collectors.toUnmodifiableSet());;
            return this;
        }

        /**
         * Sets whether the thread should be released according to the provided flag.
         *
         * @param releaseThread The flag indicating whether to release the thread.
         * @see BaseCheckpointSaver#release(RunnableConfig)
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder releaseThread( boolean releaseThread ) {
            this.config.releaseThread = releaseThread;
            return this;
        }

        /**
         * Initializes the compilation configuration and returns it.
         *
         * @return the compiled {@link CompileConfig} object
         */
        public CompileConfig build() {
            this.config.interruptAfterPredicate =  InterruptPredicate.of((nodeId, state) -> {
                if (config.interruptsAfter.contains(nodeId)) {
                    return true;
                }
                return _interruptAfterPredicate != null && _interruptAfterPredicate.test(nodeId, state);
            });
            this.config.interruptBeforePredicate =  InterruptPredicate.of((nodeId, state) -> {
                if (config.interruptsBefore.contains(nodeId)) {
                    return true;
                }
                return _interruptBeforePredicate != null && _interruptBeforePredicate.test(nodeId, state);
            });

            return config;
        }
    }


    /**
     * Default constructor for the {@class CompileConfig} class. This constructor is private to enforce that instances of this class are not created outside its package.
     */
    private CompileConfig() {}

    /**
     * Creates a new {@code CompileConfig} object as a copy of the provided configuration.
     *
     * @param config The configuration to copy.
     */
    private CompileConfig( CompileConfig config ) {
        this.checkpointSaver = config.checkpointSaver;
        this.interruptsBefore = config.interruptsBefore;
        this.interruptsAfter = config.interruptsAfter;
        this.releaseThread = config.releaseThread;
        this.interruptBeforePredicate = config.interruptBeforePredicate;
        this.interruptAfterPredicate = config.interruptAfterPredicate;

    }

}