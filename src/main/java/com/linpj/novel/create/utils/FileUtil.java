package com.linpj.novel.create.utils;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * @author HL
 */
@Component
public class FileUtil {

    @Value("${minio.endpoint}")
    private String endpoint;
    private final MinioClient minioClient;

    @Value("${minio.bucketFileName}")
    private String bucketFileName;

    public FileUtil(MinioClient minioClient) {
        this.minioClient = minioClient;
    }


    /**
     * 进行MultipartFile类型文件的桶上传
     *
     * @param file
     * @return
     */
    public String upload(MultipartFile file) {
        //判断文件格式
        String filename = file.getOriginalFilename();
        String objectName = generateObjectName(filename);

        String bucketName = determineSuffix(filename);
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException("存储桶名称不能为空");
        }

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return generatePreviewUrl(bucketName, objectName);
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败");
        }
    }


    /**
     * 下载
     *
     * @param objectName
     * @param fileName
     * @return
     */
    public String download(String objectName, String fileName) {
        String bucketName = determineSuffix(fileName);
        return generatePreviewUrl(bucketName, objectName);
    }



    /**
     * 根据url进行删除文件
     * @param fileUrl
     */
    public void deleteByUrl(String fileUrl){
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new RuntimeException("文件URL不能为空");
        }

        try {
            Map<String, String> map = generateParams(fileUrl);
            String bucketName = map.get("bucketName");
            String objectName = map.get("objectName");

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("文件删除失败");
        }
    }

    /**
     * 下载文件并返回字节数组
     *
     * @param objectName 对象名称
     * @param fileName   文件名
     * @return 文件字节数组
     */
    public byte[] downloadAsBytes(String objectName, String fileName) {
        String bucketName = determineSuffix(fileName);
        try {
            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 下载文件并返回输入流
     *
     * @param objectName 对象名称
     * @param fileName   文件名
     * @return 文件输入流
     */
    public InputStream downloadAsStream(String objectName, String fileName) {
        String bucketName = determineSuffix(fileName);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取文件流失败", e);
        }
    }

    /**
     * 通过文件链接下载文件并返回字节数组
     *
     * @param fileUrl 文件链接
     * @return 文件字节数组
     */
    public byte[] downloadByUrlAsBytes(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new RuntimeException("文件URL不能为空");
        }

        try {
            Map<String, String> map = generateParams(fileUrl);

            String bucketName = map.get("bucketName");
            String objectName = map.get("objectName");

            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

/**
 * 通过文件链接下载文件并返回输入流
 *
 * @param fileUrl 文件链接
 * @return 文件输入流
 */
    public InputStream downloadByUrlAsStream(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new RuntimeException("文件URL不能为空");
        }

        try {
            Map<String, String> map = generateParams(fileUrl);

            String bucketName = map.get("bucketName");
            String objectName = map.get("objectName");

            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取文件流失败", e);
        }
    }

    public Map<String, String> generateParams(String fileUrl) {
        String bucketName = null;
        String objectName = null;
        try {
            URI uri = new URI(fileUrl);
            String path = uri.getPath();

            if (path == null || path.length() <= 1) {
                throw new RuntimeException( "URL 路径无效");
            }

            String pathWithoutSlash = path.substring(1);

            int firstSlash = pathWithoutSlash.indexOf('/');
            if (firstSlash == -1) {
                throw new RuntimeException("URL 中未包含文件路径");
            }

            bucketName = pathWithoutSlash.substring(0, firstSlash);
            objectName = pathWithoutSlash.substring(firstSlash + 1);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        if (bucketName.isEmpty() || objectName.isEmpty()) {
            throw new RuntimeException("解析出的桶名或对象名为空");
        }
        Map<String, String> map = new HashMap<>();
        map.put("bucketName", bucketName);
        map.put("objectName", objectName);
        return map;
    }

    //生成永久访问链接
    private String generatePreviewUrl(String bucketName, String objectName) {
        // 格式: http://endpoint/bucket/object
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);
    }

    //生成唯一文件名
    private String generateObjectName(String originalFileName) {
        // 获取文件扩展名
        String fileExtension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }

        // 生成唯一文件名：日期 + UUID + 扩展名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        return String.format("uploads/%s/%s_%s%s",
                LocalDate.now().toString().replace("-", ""),
                timestamp, uuid, fileExtension);
    }

    //对文件名进行切割并判断其后缀
    private String determineSuffix(String filename) {

        String substring = filename.substring(filename.lastIndexOf("."));
        List<String> allowedSuffix = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp");
        if (allowedSuffix.contains(substring.toLowerCase())) {
            return bucketFileName;
        } else {
            return bucketFileName;
        }
    }
}
