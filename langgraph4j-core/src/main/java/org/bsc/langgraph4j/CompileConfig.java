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

    private final BaseCheckpointSaver checkpointSaver;
    private final Set<String> interruptsAfter;
    private final Set<String> interruptsBefore;
    private final boolean releaseThread;
    private final InterruptPredicate interruptBeforePredicate;
    private final InterruptPredicate interruptAfterPredicate;

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
    /**
     * Returns a predicate that determines if an interrupt should occur before a given node.
     * The predicate takes the node ID and the current state as input.
     *
     * @param <State> the type of the agent state, which must extend {@link AgentState}.
     * @return a {@link BiPredicate} that returns {@code true} if an interrupt should occur before the node.
     */
    public <State extends AgentState> BiPredicate<String, State> interruptBeforePredicate() {
        return (nodeId, state) -> {
            if (interruptsBefore.contains(nodeId)) {
                return true;
            }
            return interruptBeforePredicate != null && interruptBeforePredicate.test(nodeId, state);
        };
    }

    /**
     * Returns a predicate that determines if an interrupt should occur after a given node.
     * The predicate takes the node ID and the current state as input.
     *
     * @param <State> the type of the agent state, which must extend {@link AgentState}.
     * @return a {@link BiPredicate} that returns {@code true} if an interrupt should occur after the node.
     */
    public <State extends AgentState> BiPredicate<String, State> interruptAfterPredicate() {
        return (nodeId, state) -> {
            if (interruptsAfter.contains(nodeId)) {
                return true;
            }
            return interruptAfterPredicate != null && interruptAfterPredicate.test(nodeId, state);
        };
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
        return new Builder();
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
        private BaseCheckpointSaver checkpointSaver;
        private Set<String> interruptsAfter;
        private Set<String> interruptsBefore;
        private boolean releaseThread;
        private InterruptPredicate interruptBeforePredicate;
        private InterruptPredicate interruptAfterPredicate;

        protected Builder() {
        }

        /**
         * Constructs a new instance of {@code Builder} with the specified compile configuration.
         *
         * @param config The compile configuration to be used. This value must not be {@literal null}.
         */
        protected Builder( CompileConfig config ) {
            this.checkpointSaver = config.checkpointSaver;
            this.interruptsBefore = config.interruptsBefore;
            this.interruptsAfter = config.interruptsAfter;
            this.releaseThread = config.releaseThread;
            this.interruptBeforePredicate = config.interruptBeforePredicate;
            this.interruptAfterPredicate = config.interruptAfterPredicate;
        }
        /**
         * Sets the checkpoint saver for the configuration.
         *
         * @param checkpointSaver The {@code BaseCheckpointSaver} to set.
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder checkpointSaver(BaseCheckpointSaver checkpointSaver) {
            this.checkpointSaver = checkpointSaver;
            return this;
        }
        /**
         * Sets the actions to be performed before an interruption.
         *
         * @param interruptBefore the actions to be performed before an interruption
         * @return a reference to the current instance of Builder
         */
        public Builder interruptBefore(String... interruptBefore) {
            this.interruptsBefore = Set.of(interruptBefore);
            return this;
        }
        /**
         * Sets the strings that cause an interrupt in the configuration.
         *
         * @param interruptAfter An array of string values representing the interruptions.
         * @return The current Builder instance, allowing method chaining.
         */
        public Builder interruptAfter(String... interruptAfter) {
            this.interruptsAfter = Set.of(interruptAfter);
            return this;
        }

        public <State extends AgentState> Builder interruptBeforePredicate( BiPredicate<String, State> interruptBeforePredicate ) {
            this.interruptBeforePredicate = InterruptPredicate.of(interruptBeforePredicate);
            return this;
        }

        public <State extends AgentState> Builder interruptAfterPredicate( BiPredicate<String, State> interruptAfterPredicate ) {
            this.interruptAfterPredicate = InterruptPredicate.of(interruptAfterPredicate);
            return this;
        }

        /**
         * Sets the collection of interrupts to be executed before the configuration.
         *
         * @param interruptsBefore The collection of interrupt strings.
         * @return This builder instance for method chaining.
         */
        public Builder interruptsBefore(Collection<String> interruptsBefore) {
            this.interruptsBefore = interruptsBefore.stream().collect(Collectors.toUnmodifiableSet());
            return this;
        }
        /**
         * Sets the collection of strings that specify which interrupts should occur after.
         *
         * @param interruptsAfter Collection of interrupt identifiers
         * @return The current Builder instance for method chaining
         */
        public Builder interruptsAfter(Collection<String> interruptsAfter) {
            this.interruptsAfter = interruptsAfter.stream().collect(Collectors.toUnmodifiableSet());;
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
            this.releaseThread = releaseThread;
            return this;
        }

        /**
         * Initializes the compilation configuration and returns it.
         *
         * @return the compiled {@link CompileConfig} object
         */
        public CompileConfig build() {
            return new CompileConfig( this );
        }
    }

    private CompileConfig( Builder builder ) {
        this.checkpointSaver = builder.checkpointSaver;
        this.interruptsBefore = ofNullable(builder.interruptsBefore).orElseGet(Set::of);
        this.interruptsAfter = ofNullable(builder.interruptsAfter).orElseGet(Set::of);
        this.releaseThread = builder.releaseThread;
        this.interruptBeforePredicate = builder.interruptBeforePredicate;
        this.interruptAfterPredicate = builder.interruptAfterPredicate;

    }

}