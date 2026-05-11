package org.bsc.langgraph4j;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;


/**
 * class is a configuration container for defining compile settings and behaviors.
 * It includes various fields and methods to manage checkpoint savers and interrupts, providing both deprecated and current accessors.
 */
public class CompileConfig {


    /**
     * This class is a builder for {@link CompileConfig}. It allows for the configuration of various options
     * to customize the compilation process.
     *
     */

    public static class Builder {
        private final CompileConfig config;

        /**
         * Constructs a new instance of {@code Builder} with the specified compile configuration.
         *
         * @param config The compile configuration to be used. This value must not be {@literal null}.
         */
        protected Builder(CompileConfig config) {
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

        public Builder recursionLimit(int recursionLimit) {
            if (recursionLimit <= 0) {
                throw new IllegalArgumentException("recursionLimit must be > 0!");
            }
            this.config.recursionLimit = recursionLimit;
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
            this.config.interruptsAfter = interruptsAfter.stream().collect(Collectors.toUnmodifiableSet());
            ;
            return this;
        }

        /**
         * Sets whether the thread should be released according to the provided flag.
         *
         * @param releaseThread The flag indicating whether to release the thread.
         * @return The current {@code Builder} instance for method chaining.
         * @see BaseCheckpointSaver#release(RunnableConfig)
         */
        public Builder releaseThread(boolean releaseThread) {
            this.config.releaseThread = releaseThread;
            return this;
        }

        /**
         * Sets whether to interrupt the graph execution before evaluating conditional edges.
         * <p>
         * By default, interruptions happen after a node has finished executing. If this is set to {@code true},
         * the interruption will occur after the node finishes but *before* any of its
         * conditional edges are evaluated. This allows for inspecting the state before a branch is chosen.
         *
         * @param interruptBeforeEdge if {@code true}, interrupt before evaluating edges, otherwise interrupt after.
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder interruptBeforeEdge(boolean interruptBeforeEdge) {
            this.config.interruptBeforeEdge = interruptBeforeEdge;
            return this;
        }

        public Builder graphId(String graphId) {
            this.config.graphId = graphId;
            return this;
        }

        /**
         * Initializes the compilation configuration and returns it.
         *
         * @return the compiled {@link CompileConfig} object
         */
        public CompileConfig build() {
            return config;
        }
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
    public static Builder builder(CompileConfig config) {
        return new Builder(config);
    }

    private String graphId;
    private BaseCheckpointSaver checkpointSaver;
    private Set<String> interruptsBefore = Set.of();
    private Set<String> interruptsAfter = Set.of();
    private boolean releaseThread = true;
    private boolean interruptBeforeEdge = false;
    private int recursionLimit = 25;


    public int recursionLimit() {
        return recursionLimit;
    }

    public Optional<String> graphId() {
        return ofNullable(graphId);
    }

    /**
     * Returns the array of interrupts that will occur before the specified node.
     *
     * @return an unmodifiable {@link Set} of interruptible nodes.
     */
    public Set<String> interruptsBefore() {
        return interruptsBefore;
    }

    /**
     * Returns the array of interrupts that will occur after the specified node.
     *
     * @return an unmodifiable {@link Set} of interruptible nodes.
     */
    public Set<String> interruptsAfter() {
        return interruptsAfter;
    }

    /**
     * Returns the current {@code BaseCheckpointSaver} instance if it is not {@code null},
     * otherwise returns an empty {@link Optional}.
     *
     * @return an {@link Optional} containing the current {@code BaseCheckpointSaver} instance, or an empty {@link Optional} if it is {@code null}
     */
    public Optional<BaseCheckpointSaver> checkpointSaver() {
        return ofNullable(checkpointSaver);
    }

    /**
     * Returns the current state of the thread release flag.
     *
     * @return true if the thread has been released, false otherwise
     * @see BaseCheckpointSaver#release(RunnableConfig)
     */
    public boolean releaseThread() {
        return releaseThread;
    }

    /**
     * return the current state of option concerning whether to interrupt the graph execution before evaluating conditional edges
     *
     * @return true if option is enabled, false otherwise
     */
    public boolean interruptBeforeEdge() {
        return interruptBeforeEdge;
    }


    /**
     * Default constructor for the {@link CompileConfig} class. This constructor is private to enforce that instances of this class are not created outside its package.
     */
    private CompileConfig() {
    }

    /**
     * Creates a new {@code CompileConfig} object as a copy of the provided configuration.
     *
     * @param config The configuration to copy.
     */
    private CompileConfig(CompileConfig config) {
        this.graphId = config.graphId;
        this.checkpointSaver = config.checkpointSaver;
        this.interruptsBefore = config.interruptsBefore;
        this.interruptsAfter = config.interruptsAfter;
        this.releaseThread = config.releaseThread;
        this.interruptBeforeEdge = config.interruptBeforeEdge;
        this.recursionLimit = config.recursionLimit;

    }

}