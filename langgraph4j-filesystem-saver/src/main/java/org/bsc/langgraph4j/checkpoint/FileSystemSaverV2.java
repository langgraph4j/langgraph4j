/*
 * Persists active and released checkpoint lists as files in a caller-selected directory.
 * Graph execution and state serialization belong to core; this class owns the file envelope.
 * Files use UTF-8 JSON and share the lifetime of the configured directory.
 */
package org.bsc.langgraph4j.checkpoint;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.CheckpointListSerializer;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;


/**
 * File-backed implementation of the V2 checkpoint saver contract.
 * The base saver coordinates checkpoint operations while this class stores each thread in a JSON file.
 * Instances may be reopened against the same directory with a compatible serializer.
 * File operations are not coordinated across saver instances or processes sharing a directory.
 */
public class FileSystemSaverV2 extends AbstractCheckpointSaver implements LG4JLoggable {

    /** Directory containing active thread files and the released archive. */
    private final Path targetFolder;
    /** Serializer for the checkpoint list nested in each JSON file. */
    private final Serializer<LinkedList<Checkpoint>> checkpointsSerializer;
    /** Mapper used only for the outer file envelope. */
    private final JsonMapper jsonMapper = new JsonMapper();

    /**
     * Opens or creates a checkpoint directory for a checkpoint-list serializer.
     *
     * @param targetFolder directory holding checkpoint files; must not be null or an existing file
     * @param checkpointsSerializer serializer used to write and read checkpoint lists; must not be null
     * @throws IllegalArgumentException if the directory cannot be created or is a regular file
     */
    public FileSystemSaverV2(Path targetFolder, Serializer<LinkedList<Checkpoint>> checkpointsSerializer) {
        this.targetFolder = requireNonNull(targetFolder, "targetFolder cannot be null");
        this.checkpointsSerializer = requireNonNull(checkpointsSerializer, "checkpoints serializer cannot be null");

        File targetFolderAsFile = targetFolder.toFile();

        if (targetFolderAsFile.exists()) {
            if (targetFolderAsFile.isFile()) {
                throw new IllegalArgumentException("targetFolder '%s' must be a folder".formatted(targetFolder));
            }
        } else {
            if (!targetFolderAsFile.mkdirs()) {
                throw new IllegalArgumentException("targetFolder '%s' cannot be created".formatted(targetFolder));
            }
        }

    }

    /**
     * Opens or creates a checkpoint directory using a state serializer.
     * The matching checkpoint-list serializer is selected by the core serializer factory.
     *
     * @param targetFolder directory holding checkpoint files; must not be null or an existing file
     * @param stateSerializer serializer for agent state; must be compatible with later reads
     * @throws IllegalArgumentException if the directory cannot be created or is a regular file
     */
    public FileSystemSaverV2(Path targetFolder, StateSerializer<? extends AgentState> stateSerializer) {
        this(targetFolder, CheckpointListSerializer.of(stateSerializer));
    }

    /** Returns the extension used by both active and archived JSON envelopes. */
    private String extension() {
        return ".json";
    }

    /** Formats the active-file stem for a thread identifier. */
    private String baseName(String threadId) {
        return "thread-%s".formatted(threadId);
    }

    /** Formats the active-file stem from a runnable configuration. */
    private String baseName(RunnableConfig config) {
        return baseName(threadId(config));
    }

    /** Resolves the active checkpoint file for a configuration. */
    private Path path(RunnableConfig config) {
        return Paths.get(targetFolder.toString(), baseName(config).concat(extension()));
    }

    /** Adapts the active checkpoint path for Jackson file APIs. */
    private File file(RunnableConfig config) {
        return path(config).toFile();
    }

    /** Resolves the directory of versioned released files. */
    private Path releasedPath() {
        return targetFolder.resolve("released");
    }

    /**
     * Writes an active checkpoint envelope in UTF-8 JSON.
     * JSON serializers contribute a nested JSON value; other serializers contribute a string value.
     *
     * @param interrupted whether the thread has an active interruption
     * @param error whether the thread ended with an error
     * @param message optional status message, stored as JSON null when absent
     * @param checkpoints checkpoint list to persist; must not be null
     * @param outFile destination file; must not be null
     * @throws IOException if the file cannot be written
     */
    private void serialize(boolean interrupted,
                           boolean error,
                           String message,
                           LinkedList<Checkpoint> checkpoints,
                           File outFile) throws IOException {
        requireNonNull(checkpoints, "checkpoints cannot be null");
        requireNonNull(outFile, "outFile cannot be null");

        try(final var gen = jsonMapper.createGenerator(outFile, com.fasterxml.jackson.core.JsonEncoding.UTF8)) {
            gen.writeStartObject();
            gen.writeBooleanField("interrupted", interrupted);
            gen.writeBooleanField("error", error);
            if( message != null ) {
                gen.writeStringField("message", message);
            }
            else {
                gen.writeNullField("message");
            }
            gen.writeFieldName("checkpoints");

            final String checkpointsSerialized = checkpointsSerializer.writeDataAsString(checkpoints);
            if( checkpointsSerializer.contentType().equals("application/json") ) {
                gen.writeRawValue(checkpointsSerialized);
            } else {
                gen.writeString(checkpointsSerialized);
            }
            gen.writeEndObject();

        }
    }

    /**
     * Reads a checkpoint list from its JSON envelope using the configured serializer.
     *
     * @param file existing checkpoint file; must not be null
     * @return checkpoint list stored in the file
     * @throws IOException if the envelope cannot be read
     * @throws ClassNotFoundException if a binary serializer cannot resolve a stored class
     */
    private LinkedList<Checkpoint> deserialize(File file) throws IOException, ClassNotFoundException {
        requireNonNull(file, "file cannot be null");

        final var checkpointsNode = jsonMapper.readTree(file).get("checkpoints");

        if( checkpointsSerializer.contentType().equals("application/json") ) {
            return checkpointsSerializer.readDataFromString(checkpointsNode.toString());
        } else {
            return checkpointsSerializer.readDataFromString(checkpointsNode.asText());
        }
    }


    /**
     * Rewrites envelope metadata while retaining its serialized checkpoint value.
     *
     * @param file existing checkpoint file; must not be null
     * @param interrupted replacement interruption flag
     * @param error replacement error flag
     * @param message replacement message, or null for a JSON null value
     * @throws IOException if the envelope cannot be read or rewritten
     */
    private void update(File file,
                        boolean interrupted,
                        boolean error,
                        String message) throws IOException {
        requireNonNull(file, "file cannot be null");

        final var threadNode = jsonMapper.readTree(file).get("checkpoints");

        final String checkpointsSerialized;;
        if( checkpointsSerializer.contentType().equals("application/json") ) {
            checkpointsSerialized = threadNode.toString();
        } else {
            checkpointsSerialized = threadNode.asText();
        }

        try(final var gen = jsonMapper.createGenerator(file, com.fasterxml.jackson.core.JsonEncoding.UTF8)) {
            gen.writeStartObject();
            gen.writeBooleanField("interrupted", interrupted);
            gen.writeBooleanField("error", error);
            if( message != null ) {
                gen.writeStringField("message", message);
            }
            else {
                gen.writeNullField("message");
            }
            gen.writeFieldName("checkpoints");

            if( checkpointsSerializer.contentType().equals("application/json") ) {
                gen.writeRawValue(checkpointsSerialized);
            } else {
                gen.writeString(checkpointsSerialized);
            }
            gen.writeEndObject();

        }

    }

    /** Persists an inserted checkpoint after assigning its save timestamp. */
    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        int index = checkpoints.indexOf(checkpoint);
        checkpoints.set(index, checkpoint.withSavedAtIfUnset(Instant.now()));

        final var targetFile = file(config);
        serialize( false, false, null, checkpoints, targetFile);
    }

    /** Rewrites the active file after a checkpoint replacement. */
    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        insertedCheckpoint(config, checkpoints, checkpoint);
    }

    /** Loads the active file, or returns an empty list before a thread has been persisted. */
    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final var targetFile = file(config);
        return targetFile.exists() ?
                deserialize(targetFile) :
                new LinkedList<>();

    }

    /**
     * Finds the highest archived version for one thread by scanning release filenames.
     *
     * @param ThreadId thread identifier to match exactly
     * @return highest version, or empty when the archive or matching files do not exist
     * @throws IOException if the release directory cannot be listed
     */
    private OptionalInt lastVersion(String ThreadId) throws IOException {
        final var versionPattern = Pattern.compile("thread-(.+)-v(\\d+)%s$".formatted(extension()));

        final var releasedPath = releasedPath();
        if (!Files.exists(releasedPath)) {
            return OptionalInt.empty();
        }

        try (var stream = Files.list(releasedPath)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .map(versionPattern::matcher)
                    .filter(Matcher::matches)
                    .filter(matcher -> matcher.group(1).equals(ThreadId))
                    .mapToInt(matcher -> Integer.parseInt(matcher.group(2)))
                    .max();
        }

    }

    /**
     * Copies the active file to the next numbered release file and removes the active file.
     * A missing active file or unreadable release directory yields an unversioned empty tag.
     *
     * @param config thread configuration to release
     * @param checkpoints checkpoint list returned in the release tag
     * @param interrupted interruption status for the release
     * @param error error status for the release
     * @param message optional release message
     * @return tag with the archived version and checkpoints, or an empty unversioned tag
     * @throws Exception if copying, updating, or deleting the file fails
     */
    private Tag releaseCheckpoints(RunnableConfig config,
                                   LinkedList<Checkpoint> checkpoints,
                                   boolean interrupted,
                                   boolean error,
                                   @Nullable String message) throws Exception {
        final var currentPath = path(config);

        if (!Files.exists(currentPath)) {
            log.warn("file {} doesn't exist. Skipping file operations.", currentPath);
            return new Tag(threadId(config), List.of());
        }

        int lastVersion;
        try {
            lastVersion = lastVersion(threadId(config)).orElse(0);

        } catch (IOException e) {
            log.error("Failed to list directory {} to determine next version number for backup. Skipping file operations.", targetFolder, e);
            return new Tag(threadId(config), List.of());
        }

        var releasedPath = releasedPath();
        if (!Files.exists(releasedPath)) {
            Files.createDirectory(releasedPath);
        }

        var releasedFilename = "%s-v%d%s".formatted(baseName(config), ++lastVersion, extension());

        releasedPath = releasedPath.resolve(releasedFilename);

        Files.copy(currentPath, releasedPath, StandardCopyOption.REPLACE_EXISTING);

        update( releasedPath.toFile(), false, false, message);

        Files.delete(currentPath);

        return new Tag(threadId(config), lastVersion, checkpoints);

    }

    /**
     * Releases the checkpoints associated with the given configuration.
     * Copies the active file (for example, {@code thread-123.json}) to a numbered
     * file under {@code released}, then deletes the active file.
     *
     * @param config      The configuration for which to release checkpoints.
     * @param checkpoints checkpoints returned in the release tag
     * @param message optional message stored in the released envelope
     * @return tag containing the released version and checkpoints
     * @throws Exception if a file operation fails
     */
    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        return releaseCheckpoints(config, checkpoints, false, false, message);
    }

    /** Releases checkpoints with the exception message recorded in the archive. */
    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Throwable exception) throws Exception {
        return releaseCheckpoints(config, checkpoints, false, true, exception.getMessage());
    }

    /**
     * Marks the active file as interrupted and records the interruption reason.
     * The returned future completes exceptionally if the file cannot be updated.
     *
     * @param config thread configuration with an existing active file
     * @param interruptionMetadata metadata whose optional reason is written to the file
     * @param <State> agent state type carried by the metadata
     * @return completed future containing the metadata, or a failed future on I/O error
     */
    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        try {
            update(file(config), true, false, interruptionMetadata.reason().orElse(null));
            return completedFuture(interruptionMetadata);
        } catch (IOException e) {
            log.error("Failed to register interruption for config {}: {}", config, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Reads a numbered release for a thread, selecting the latest when no version is given.
     *
     * @param config thread configuration; must not be null
     * @param version release version, or null to select the latest available version
     * @return matching release tag, or empty when the release file does not exist
     * @throws Exception if the release directory cannot be scanned or the file cannot be decoded
     */
    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        requireNonNull(config, "config cannot be null");

        var releasedPath = releasedPath();
        if (!Files.exists(releasedPath)) {
            log.warn("Released folder {} doesn't exist. Skipping file operations.", releasedPath);
            return Optional.empty();
        }
        if (!Files.isDirectory(releasedPath)) {
            log.warn("Released folder {} is not a directory. Skipping file operations.", releasedPath);
            return Optional.empty();
        }

        final int version$;
        final var threadId = threadId(config);

        if (version != null) {
            version$ = version;
        } else {
            final var lastVersion = lastVersion(threadId);

            if (lastVersion.isEmpty()) {
                log.warn("No versions found for threadId '{}'", threadId);
                return Optional.empty();
            }

            version$ = lastVersion.getAsInt();
        }


        final var backupFilename = "%s-v%d%s".formatted(baseName(threadId), version$, extension());

        releasedPath = releasedPath.resolve(backupFilename);

        if (!Files.exists(releasedPath)) {
            log.warn("released file {} does not exist", releasedPath);
            return Optional.empty();
        }

        final var checkpoints = deserialize(releasedPath.toFile());
        return Optional.of(new Tag(threadId, version$, checkpoints));
    }

    /**
     * Deletes the active checkpoint file associated with a runnable configuration.
     *
     * @param config configuration identifying the thread file to delete
     * @return true if the file existed and was deleted; false otherwise
     */
    public boolean deleteFile(RunnableConfig config) {
        File targetFile = file(config);
        return targetFile.exists() && targetFile.delete();
    }

}
