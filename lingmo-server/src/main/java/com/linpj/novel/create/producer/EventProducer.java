package com.linpj.novel.create.producer;

import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.utils.FileUtil;
import com.linpj.novel.create.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class EventProducer {

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Resource
    private FileUtil fileUtil;

    public void sentEvent(MultipartFile file, Long knowledgeBaseId, String topic) {
        if (file == null || file.isEmpty() || knowledgeBaseId == null || knowledgeBaseId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        // 将文件上传到对象存储服务器，消息体携带 URL（而非 MultipartFile 对象——Gson 无法序列化文件流）
        String url = fileUtil.upload(file);
        Map<String, Object> map = new HashMap<>();
        map.put("file", url);
        map.put("knowledgeBaseId", knowledgeBaseId);
        String json = JsonUtil.toJson(map);
        kafkaTemplate.send(topic, String.valueOf(knowledgeBaseId), json);
    }
}
