package com.linpj.novel.create.agent;

import com.linpj.novel.create.config.PipelineRecordingProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 录制归档器单元测试（B0-4：双开关零副作用 / 桶懒创建与对象键契约 / 失败不外抛）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordingArchiverTest {

    @Mock private MinioClient minioClient;

    private Path dir;

    @AfterEach
    void cleanUp() {
        if (dir == null) {
            return;
        }
        try {
            Files.walk(dir.getParent())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private PipelineRecordingProperties props(boolean recording, boolean archiving) {
        PipelineRecordingProperties properties = new PipelineRecordingProperties();
        properties.setEnabled(recording);
        properties.getArchive().setEnabled(archiving);
        dir = Paths.get(".temp", "test-archiver", UUID.randomUUID().toString());
        properties.setDir(dir.toString());
        return properties;
    }

    private Path writeFile() throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("recordings.jsonl");
        Files.writeString(file, "{\"agent\":\"PLANNER\"}\n");
        return file;
    }

    @Test
    void 录制未开_归档零副作用() throws Exception {
        RecordingArchiver archiver = new RecordingArchiver(minioClient, props(false, true));
        Path file = writeFile();

        archiver.archiveAsync(file);

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void 归档未开_零副作用() throws Exception {
        RecordingArchiver archiver = new RecordingArchiver(minioClient, props(true, false));
        Path file = writeFile();

        archiver.archiveAsync(file);

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void 文件不存在_零副作用() throws Exception {
        RecordingArchiver archiver = new RecordingArchiver(minioClient, props(true, true));

        archiver.archiveAsync(dir.resolve("recordings.jsonl"));

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void 桶不存在则懒创建并按日键上传快照() throws Exception {
        RecordingArchiver archiver = new RecordingArchiver(minioClient, props(true, true));
        Path file = writeFile();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        archiver.archiveAsync(file);

        // 守护线程异步执行：轮询等待上传动作落地（本地测试通常毫秒级）
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(minioClient).putObject(any(PutObjectArgs.class));
                break;
            } catch (AssertionError retryLater) {
                Thread.sleep(50);
            }
        }
        // 桶懒创建发生在上传前（对象键契约：按日全量快照，探针脚本按前缀列举消费）
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertEquals("novel-recordings", captor.getValue().bucket());
        assertEquals("recordings-" + LocalDate.now() + ".jsonl", captor.getValue().object(),
                "对象键 = recordings-{yyyy-MM-dd}.jsonl（当日全量快照）");
    }

    @Test
    void 桶已存在_不重复创建() throws Exception {
        RecordingArchiver archiver = new RecordingArchiver(minioClient, props(true, true));
        Path file = writeFile();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        archiver.archiveAsync(file);

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(minioClient).putObject(any(PutObjectArgs.class));
                break;
            } catch (AssertionError retryLater) {
                Thread.sleep(50);
            }
        }
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void MinIO故障_告警不外抛() throws Exception {
        RecordingArchiver archiver = new RecordingArchiver(minioClient, props(true, true));
        writeFile();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new RuntimeException("连接超时"));

        // 归档是安全网：MinIO 不可达（换环境/断网）绝不能炸录制与创作主链路
        assertDoesNotThrow(() -> archiver.archiveAsync(dir.resolve("recordings.jsonl")));
        Thread.sleep(200); // 留给守护线程消化异常（验证不外抛路径本身）
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }
}
