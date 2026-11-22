package com.linpj.novel.create.agent;

import com.linpj.novel.create.config.PipelineRecordingProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 录制 JSONL 归档器（B0-4：本地 .temp → MinIO 的防丢失层）
 *
 * - 为什么：录制 JSONL 是 S1/S4/S6 判据与消融复现（回放键 = 内容哈希）的唯一数据源，
 *   放本地临时区有丢失风险（清理/换机/进程崩溃后无从补录——模型随机性下"补跑"不等于"原数据"）
 * - 语义：每条本地落盘后异步上传"当日全量快照"到 MinIO（单对象按日覆盖，last-write-wins）；
 *   单线程执行器保证上传串行，快照单调超集，无覆盖竞态
 * - 尽力而为：归档是安全网不是主链路——任何失败只告警，绝不影响创作管道与录制本身
 * - 双开关：recording.enabled 且 recording.archive.enabled 同时为真才动作（无 MinIO 环境零副作用）
 *
 * @author HL
 */
@Component
public class RecordingArchiver {

    private static final Logger log = LoggerFactory.getLogger(RecordingArchiver.class);

    private final MinioClient minioClient;
    private final PipelineRecordingProperties properties;

    /** 归档串行化：同步快照语义（后启覆盖先启，读到的对象恒为某次完整文件状态） */
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "recording-archiver");
                thread.setDaemon(true);
                return thread;
            });

    /** 桶懒创建标记（首次上传时确保桶存在；失败后重置以便重试） */
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public RecordingArchiver(MinioClient minioClient, PipelineRecordingProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 异步归档当前录制文件快照（尽力而为：排队后立即返回，失败仅告警）。
     * 文件不存在 / 归档未启用时零副作用。
     */
    public void archiveAsync(Path file) {
        if (!enabled() || file == null) {
            return;
        }
        try {
            if (!Files.exists(file)) {
                return;
            }
            executor.execute(() -> {
                try {
                    uploadSnapshot(file);
                } catch (Exception e) {
                    log.warn("录制归档失败（不影响管道与录制）: {}, cause={}", file, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("录制归档任务提交失败: {}", e.getMessage());
        }
    }

    /** 当前是否启用归档（双开关与） */
    private boolean enabled() {
        return properties.isEnabled() && properties.getArchive().isEnabled();
    }

    /** 上传当日全量快照（对象键按日固定，覆盖式——串行执行器保证无竞态） */
    private void uploadSnapshot(Path file) throws Exception {
        String bucket = properties.getArchive().getBucket();
        ensureBucket(bucket);
        String objectKey = "recordings-" + LocalDate.now() + ".jsonl";
        try (InputStream in = Files.newInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, Files.size(file), -1)
                    .contentType("application/x-ndjson")
                    .build());
        }
        log.debug("录制快照归档完成: {}/{} ({} bytes)", bucket, objectKey, Files.size(file));
    }

    /** 桶懒创建（不存在则建；失败重置标记让下次重试——只影响归档不影响主链路） */
    private void ensureBucket(String bucket) throws Exception {
        if (bucketReady.get()) {
            return;
        }
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("录制归档桶已创建: {}", bucket);
        }
        bucketReady.set(true);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
