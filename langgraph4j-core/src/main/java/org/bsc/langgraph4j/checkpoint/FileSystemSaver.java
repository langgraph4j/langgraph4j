package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.CheckpointListSerializer;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonCheckpointListSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;


/**
 * A CheckpointSaver that stores Checkpoints in the filesystem.
 *
 * <p>
 *     Each RunnableConfig is associated with a file in the provided targetFolder.
 *     The file is named "thread-<i>threadId</i>.saver" if the RunnableConfig has a
 *     threadId, or "thread-$default.saver" if it doesn't.
 * </p>
 *
 */
public class FileSystemSaver extends AbstractCheckpointSaver implements LG4JLoggable {

    private final Path targetFolder;
    private final Serializer<LinkedList<Checkpoint>> checkpointsSerializer;
    private final String extension;

    public FileSystemSaver(Path targetFolder, Serializer<LinkedList<Checkpoint>> checkpointsSerializer) {
        this.targetFolder = requireNonNull(targetFolder, "targetFolder cannot be null");
        this.checkpointsSerializer = requireNonNull(checkpointsSerializer, "checkpoints serializer cannot be null");

        File targetFolderAsFile = targetFolder.toFile();

        if (targetFolderAsFile.exists()) {
            if (targetFolderAsFile.isFile()) {
                throw new IllegalArgumentException("targetFolder '%s' must be a folder".formatted( targetFolder)); // TODO: format"targetFolder must be a directory");
            }
        } else {
            if (!targetFolderAsFile.mkdirs()) {
                throw new IllegalArgumentException("targetFolder '%s' cannot be created".formatted( targetFolder)); // TODO: format"targetFolder cannot be created");
            }
        }

        this.extension = ( checkpointsSerializer instanceof JacksonCheckpointListSerializer ) ? "-saver.json" : "-saver.bin" ;

    }

    public FileSystemSaver(Path targetFolder, StateSerializer<? extends AgentState> stateSerializer) {
        this( targetFolder, CheckpointListSerializer.of( stateSerializer ));
    }

    private String baseName(String threadId) {
        return "thread-%s".formatted( threadId );
    }

    private String baseName(RunnableConfig config) {
        return baseName( threadId(config));
    }

    private Path path(RunnableConfig config) {
        return Paths.get(targetFolder.toString(), baseName(config).concat(extension));
    }

    private File file(RunnableConfig config) {
        return path(config).toFile();
    }

    private Path releasedPath() {
        return targetFolder.resolve("released");
    }

    private void serialize(LinkedList<Checkpoint> checkpoints, File outFile) throws IOException {
        requireNonNull(checkpoints, "checkpoints cannot be null");
        requireNonNull(outFile, "outFile cannot be null");

        final var outFilePath = outFile.toPath();
        Files.writeString( outFilePath, checkpointsSerializer.writeDataAsString(checkpoints) );
    }

    private LinkedList<Checkpoint> deserialize(File file) throws IOException, ClassNotFoundException {
        requireNonNull(file, "file cannot be null");

        final var filePath = file.toPath();
        return checkpointsSerializer.readDataFromString(Files.readString(filePath));
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        int index = checkpoints.indexOf(checkpoint);
        checkpoints.set(index, checkpoint.withSavedAtIfUnset( Instant.now() ));

        final var targetFile = file(config);
        serialize(checkpoints, targetFile);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        insertedCheckpoint(config, checkpoints, checkpoint);
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final var targetFile = file(config);
        return targetFile.exists() ?
                deserialize(targetFile) :
                new LinkedList<>();

    }

    private OptionalInt lastVersion( String ThreadId ) throws IOException  {
        final var versionPattern = Pattern.compile("thread-(.+)-v(\\d+)%s$".formatted(extension));

        final var releasedPath = releasedPath();
        if( !Files.exists(releasedPath) ) {
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
     * Releases the checkpoints associated with the given configuration.
     * This involves copying the current checkpoint file (e.g., "thread-123.saver")
     * to a versioned backup file (e.g., "thread-123-v1.saver", "thread-123-v2.saver", etc.)
     * based on existing versioned files, deleting the original unversioned file,
     * and then clearing the in-memory checkpoints.
     *
     * @param config The configuration for which to release checkpoints.
     * @param checkpoints released checkpoints
     * @throws Exception If an error occurs during file operations or releasing from memory.
     */
    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        final var currentPath = path(config);

        if (!Files.exists(currentPath)) {
            log.warn("file {} doesn't exist. Skipping file operations.", currentPath);
            return new Tag( threadId(config), List.of());
        }

        int lastVersion;
        try {
            lastVersion = lastVersion( threadId(config) ).orElse(0);

        } catch (IOException e) {
            log.error("Failed to list directory {} to determine next version number for backup. Skipping file operations.", targetFolder, e);
            return new Tag( threadId(config), List.of());
        }

        var releasedPath = releasedPath();
        if( !Files.exists(releasedPath) ) {
            Files.createDirectory(releasedPath);
        }

        var releasedFilename = "%s-v%d%s".formatted( baseName(config), ++lastVersion, extension);

        releasedPath = releasedPath.resolve(releasedFilename);

        Files.copy(currentPath, releasedPath, StandardCopyOption.REPLACE_EXISTING);

        Files.delete(currentPath);

        return new Tag( threadId(config), lastVersion, checkpoints );

    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Throwable exception) throws Exception {
        return releaseCheckpoints(config, checkpoints, exception.getMessage());
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return completedFuture(interruptionMetadata);
    }

    @Override
    public Optional<Tag> tag( RunnableConfig config, Integer version) throws Exception {
        requireNonNull(config, "config cannot be null");

        var releasedPath = releasedPath();
        if( !Files.exists(releasedPath) ) {
            log.warn("Released folder {} doesn't exist. Skipping file operations.", releasedPath);
            return Optional.empty();
        }
        if( !Files.isDirectory(releasedPath) ) {
            log.warn("Released folder {} is not a directory. Skipping file operations.", releasedPath);
            return Optional.empty();
        }

        final int version$;
        final var threadId = threadId(config);

        if( version != null ) {
            version$ = version;
        } else {
            final var lastVersion = lastVersion( threadId );

            if(lastVersion.isEmpty()) {
                log.warn("No versions found for threadId '{}'", threadId );
                return Optional.empty();
            }

            version$ = lastVersion.getAsInt();
        }


        final var backupFilename = "%s-v%d%s".formatted( baseName(threadId), version$, extension);

        releasedPath = releasedPath.resolve(backupFilename);

        if (!Files.exists(releasedPath)) {
            log.warn("released file {} does not exist", releasedPath);
            return Optional.empty();
        }

        final var checkpoints = deserialize(releasedPath.toFile());
        return Optional.of(new Tag(threadId, version$, checkpoints));
    }

    /**
     * delete the checkpoint file associated with the given RunnableConfig.
     *
     * @param config the RunnableConfig for which the checkpoint file should be cleared
     * @return true if the file existed and was successfully deleted, false otherwise
     */
    public boolean deleteFile(RunnableConfig config) {
        File targetFile = file(config);
        return targetFile.exists() && targetFile.delete();
    }

}

