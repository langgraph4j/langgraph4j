package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonCheckpointListSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.CheckpointListSerializer;
import org.bsc.langgraph4j.state.AgentState;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;


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
        this( targetFolder, ( stateSerializer instanceof JacksonStateSerializer<? extends AgentState> jsonStateSerializer) ?
            new JacksonCheckpointListSerializer(jsonStateSerializer) :
            new CheckpointListSerializer(stateSerializer));
    }

    private String getBaseName(String threadId) {
        return "thread-%s".formatted( threadId );
    }

    private String getBaseName(RunnableConfig config) {
        return getBaseName( threadId(config));
    }

    private Path getPath(RunnableConfig config) {
        return Paths.get(targetFolder.toString(), getBaseName(config).concat(extension));
    }

    private File getFile(RunnableConfig config) {
        return getPath(config).toFile();
    }

    private void serialize(LinkedList<Checkpoint> checkpoints, File outFile) throws IOException {
        requireNonNull(checkpoints, "checkpoints cannot be null");
        requireNonNull(outFile, "outFile cannot be null");

        final var outFilePath = outFile.toPath();
        if( checkpointsSerializer instanceof JacksonCheckpointListSerializer jsonSerializer  ) {
            Files.writeString( outFilePath, jsonSerializer.writeDataAsString(checkpoints) );
        }
        else {
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(outFilePath))) {
                checkpointsSerializer.write(checkpoints, oos);
            }
        }
    }

    private LinkedList<Checkpoint> deserialize(File file) throws IOException, ClassNotFoundException {
        requireNonNull(file, "file cannot be null");

        final var filePath = file.toPath();
        if( checkpointsSerializer instanceof JacksonCheckpointListSerializer jsonSerializer  ) {
            return jsonSerializer.readDataFromString(Files.readString(filePath));
        }
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(file.toPath()))) {
            return checkpointsSerializer.read(ois);
        }
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        final var targetFile = getFile(config);
        serialize(checkpoints, targetFile);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        insertedCheckpoint(config, checkpoints, checkpoint);
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final var targetFile = getFile(config);
        return targetFile.exists() ?
                deserialize(targetFile) :
                new LinkedList<>();

    }

    private OptionalInt lastVersion( String ThreadId ) throws IOException  {
        final var versionPattern = Pattern.compile("thread-(.+)-v(\\d+)%s$".formatted(  extension));

        try (var stream = Files.list(targetFolder)) {
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
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        final var currentPath = getPath(config);

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

        var backupFilename = "%s-v%d%s".formatted( getBaseName(config), ++lastVersion, extension);

        final Path backupPath = targetFolder.resolve(backupFilename);

        Files.copy(currentPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

        Files.delete(currentPath);

        return new Tag( threadId(config), lastVersion, checkpoints );


    }

    @Override
    public Optional<Tag> tag( RunnableConfig config, Integer version) throws Exception {
        requireNonNull(config, "config cannot be null");

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

        final var backupFilename = "%s-v%d%s".formatted( getBaseName(threadId), version$, extension);

        final Path backupPath = targetFolder.resolve(backupFilename);

        if (!Files.exists(backupPath)) {
            log.warn("Backup file {} does not exist", backupPath);
            return Optional.empty();
        }

        final var checkpoints = deserialize(backupPath.toFile());
        return Optional.of(new Tag(threadId, version$, checkpoints));
    }

    /**
     * delete the checkpoint file associated with the given RunnableConfig.
     *
     * @param config the RunnableConfig for which the checkpoint file should be cleared
     * @return true if the file existed and was successfully deleted, false otherwise
     */
    public boolean deleteFile(RunnableConfig config) {
        File targetFile = getFile(config);
        return targetFile.exists() && targetFile.delete();
    }

    public static Stream<Path> list(Path targetFolder, BiPredicate<String,Integer> filter ) throws IOException {
        requireNonNull(targetFolder, "targetFolder cannot be null");
        requireNonNull(filter, "filter cannot be null");

        final var versionPattern = Pattern.compile("^thread-(.+)-v(\\d+)-saver.(?:json|bin)$");

        return Files.list(targetFolder)
                        .filter( path -> {
                            final var matcher = versionPattern.matcher(path.getFileName().toString());
                            if( !matcher.matches() ) return false;
                            return filter.test( matcher.group(1), Integer.parseInt(matcher.group(2)) );
                        });
    }
}

