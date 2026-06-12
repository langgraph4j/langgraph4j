package org.bsc.langgraph4j.checkpoint;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class StorageStrategyTest {

    // ─── Container setup ─────────────────────────────────────────────────────────

    private static final int DYNAMODB_PORT = 8000;
    private static final String TABLE_NAME = "lg4j-test-chunks";
    private static final String S3_BUCKET = "test-checkpoints-bucket";

    @Container
    static final FixedHostPortGenericContainer<?> dynamoContainer =
        new FixedHostPortGenericContainer<>("amazon/dynamodb-local:latest")
            .withFixedExposedPort(8345, DYNAMODB_PORT) // Different port to avoid conflict
            .waitingFor(Wait.forLogMessage(".*Initializing DynamoDB Local.*\\n", 1));

    @Container
    static final MinIOContainer minioContainer = new MinIOContainer("minio/minio:latest")
        .withUserName("minioadmin")
        .withPassword("minioadmin");

    private static DynamoDbClient dynamoDbClient;
    private static S3Client s3Client;
    private static DynamoDBRepository repository;

    @BeforeAll
    static void init() {
        assertTrue(dynamoContainer.isRunning());
        assertTrue(minioContainer.isRunning());

        String dynamoEndpoint = "http://" + dynamoContainer.getHost()
                              + ":" + dynamoContainer.getMappedPort(DYNAMODB_PORT);

        dynamoDbClient = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(dynamoEndpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy", "dummy")))
            .build();

        s3Client = S3Client.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(minioContainer.getS3URL()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(minioContainer.getUserName(), minioContainer.getPassword())))
            .forcePathStyle(true) // Required for MinIO
            .build();

        // Ensure bucket exists
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(S3_BUCKET).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(S3_BUCKET).build());
            } else {
                throw e;
            }
        }

        // Setup generic repo just to create the table
        repository = new DynamoDBRepository(dynamoDbClient, TABLE_NAME, null, null);
        repository.createTableIfNotExists();
    }

    @AfterAll
    static void shutdown() {
        if (dynamoDbClient != null) dynamoDbClient.close();
        if (s3Client != null) s3Client.close();
        dynamoContainer.close();
        minioContainer.close();
    }

    // ─── Unit Tests (Threshold Logic) ──────────────────────────────────────────

    @Test
    void testShouldOffloadToS3_belowThreshold() {
        StorageStrategy strategy = new StorageStrategy(null, TABLE_NAME, s3Client, S3_BUCKET, null, null);
        byte[] data = new byte[100 * 1024]; // 100KB
        assertFalse(strategy.shouldOffloadToS3(data), "100KB should not trigger S3 offload");
    }

    @Test
    void testShouldOffloadToS3_aboveThreshold() {
        StorageStrategy strategy = new StorageStrategy(null, TABLE_NAME, s3Client, S3_BUCKET, null, null);
        byte[] data = new byte[400 * 1024]; // 400KB
        assertTrue(strategy.shouldOffloadToS3(data), "400KB should trigger S3 offload");
    }

    @Test
    void testShouldOffloadToS3_s3Disabled() {
        StorageStrategy strategy = new StorageStrategy(null, TABLE_NAME, null, null, null, null);
        byte[] data = new byte[400 * 1024]; // 400KB
        assertFalse(strategy.shouldOffloadToS3(data), "Should not offload if S3 is disabled");
    }

    // ─── Integration Tests (Store/Retrieve/Delete) ─────────────────────────────

    @Test
    void testSmallPayloadStaysInDynamoDB() {
        StorageStrategy strategy = new StorageStrategy(dynamoDbClient, TABLE_NAME, s3Client, S3_BUCKET, "test-prefix", null);
        
        String chunkKey = "chunk-small-test";
        String s3Key = "s3-small-test";
        byte[] data = "Hello DynamoDB".getBytes();

        String refLoc = strategy.storeData(chunkKey, s3Key, data, false);
        assertEquals("DYNAMODB", refLoc);

        // Retrieve should work
        byte[] retrieved = strategy.retrieveData(chunkKey, refLoc);
        assertNotNull(retrieved);
        assertArrayEquals(data, retrieved);

        // Batch delete should work
        Map<String, List<String>> failed = strategy.batchDeleteData(java.util.Collections.singletonList(new String[]{chunkKey, refLoc}));
        assertTrue(failed.get("failed").isEmpty());

        // Should be gone
        assertNull(strategy.retrieveData(chunkKey, refLoc));
    }

    @Test
    void testLargePayloadRoutedToS3() {
        StorageStrategy strategy = new StorageStrategy(dynamoDbClient, TABLE_NAME, s3Client, S3_BUCKET, "test-prefix", null);
        
        String chunkKey = "chunk-large-test";
        String s3Key = "s3-large-test";
        byte[] data = new byte[StorageStrategy.S3_OFFLOAD_THRESHOLD + 10];
        data[0] = 1;
        data[data.length - 1] = 2;

        String refLoc = strategy.storeData(chunkKey, s3Key, data, false);
        assertEquals("S3", refLoc);

        // Retrieve should work
        byte[] retrieved = strategy.retrieveData(s3Key, refLoc);
        assertNotNull(retrieved);
        assertArrayEquals(data, retrieved);

        // Batch delete should work
        Map<String, List<String>> failed = strategy.batchDeleteData(java.util.Collections.singletonList(new String[]{s3Key, refLoc}));
        assertTrue(failed.get("failed").isEmpty());

        // Should be gone
        assertNull(strategy.retrieveData(s3Key, refLoc));
    }

    @Test
    void testAllowOverwriteConditionalWriteS3() {
        StorageStrategy strategy = new StorageStrategy(dynamoDbClient, TABLE_NAME, s3Client, S3_BUCKET, null, null);
        String chunkKey = "chunk-cond";
        String s3Key = "s3-cond";
        byte[] data1 = new byte[StorageStrategy.S3_OFFLOAD_THRESHOLD + 10];
        data1[0] = 1;
        byte[] data2 = new byte[StorageStrategy.S3_OFFLOAD_THRESHOLD + 10];
        data2[0] = 2;

        // First write succeeds
        strategy.storeData(chunkKey, s3Key, data1, false);

        // Second write with allowOverwrite=false should be ignored (silently skipped in StorageStrategy)
        strategy.storeData(chunkKey, s3Key, data2, false);

        byte[] retrieved = strategy.retrieveData(s3Key, "S3");
        assertEquals(1, retrieved[0], "Should retain first data");

        // Third write with allowOverwrite=true should overwrite
        strategy.storeData(chunkKey, s3Key, data2, true);
        
        retrieved = strategy.retrieveData(s3Key, "S3");
        assertEquals(2, retrieved[0], "Should overwrite data");
    }

    @Test
    void testAllowOverwriteConditionalWriteDynamoDB() {
        StorageStrategy strategy = new StorageStrategy(dynamoDbClient, TABLE_NAME, s3Client, S3_BUCKET, null, null);
        String chunkKey = "chunk-cond-dyn";
        String s3Key = "s3-cond-dyn";
        byte[] data1 = "data1".getBytes();
        byte[] data2 = "data2".getBytes();

        // First write succeeds
        strategy.storeData(chunkKey, s3Key, data1, false);

        // Second write with allowOverwrite=false should be ignored
        strategy.storeData(chunkKey, s3Key, data2, false);

        byte[] retrieved = strategy.retrieveData(chunkKey, "DYNAMODB");
        assertArrayEquals(data1, retrieved, "Should retain first data");

        // Third write with allowOverwrite=true should overwrite
        strategy.storeData(chunkKey, s3Key, data2, true);
        
        retrieved = strategy.retrieveData(chunkKey, "DYNAMODB");
        assertArrayEquals(data2, retrieved, "Should overwrite data");
    }

}
