// src/utils/aiStreamService.ts
import { getAccessToken, clearTokens } from '@/utils/token';
import {handleRefreshToken} from '@/api/axiosInstance';
import {type AiChatRequest } from "@/api"; // 从你的 axiosInstance 导出刷新函数

/**
 * 发起带认证的流式 POST 请求（用于 AI 生成）
 * @param endpoint 接口路径，如 '/api/book/create/ai/continue'
 * @param requestBody AiChatRequest 对象
 * @returns Promise<string> 完整生成的文本
 */
export async function streamAiPost(
  endpoint: string,
  requestBody: AiChatRequest
): Promise<string> {
  let token = getAccessToken();
  if (!token) {
    try {
      await handleRefreshToken();
      token = getAccessToken();
    } catch (refreshError) {
      console.error('Token 刷新失败:', refreshError);
      clearTokens();
      window.location.href = '/login';
      throw new Error('认证失效，请重新登录');
    }
  }

  const url = `/api${endpoint.startsWith('/') ? endpoint : '/' + endpoint}`;

  // 使用 Promise.race 实现超时，避免 abort 中断流
  const timeoutPromise = new Promise((_, reject) =>
    setTimeout(() => reject(new Error('请求超时')), 30_000)
  );

  const fetchPromise: Promise<Response> = fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(requestBody),
  });

  try {
    const response = await Promise.race([fetchPromise, timeoutPromise]);

    // 处理 HTTP 错误
    if (response.status === 401) {
      clearTokens();
      window.location.href = '/login';
      throw new Error('登录已过期，请重新登录');
    }

    if (!response.ok) {
      let errorMsg = `AI 服务异常 (${response.status})`;
      try {
        const errJson = await response.json();
        errorMsg = errJson.message || errorMsg;
      } catch {}
      throw new Error(errorMsg);
    }

    // 流式读取（保持原逻辑）
    if (!response.body) {
      throw new Error('响应为空');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8', { fatal: false });
    let fullText = '';
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      let boundaryIndex;
      while ((boundaryIndex = buffer.indexOf('\n\n')) !== -1) {
        const event = buffer.slice(0, boundaryIndex);
        buffer = buffer.slice(boundaryIndex + 2);

        const lines = event.split('\n');
        for (const line of lines) {
          // ✅ 关键修复：去掉空格判断
          if (line.startsWith('data:')) {
            const payload = line.slice(5).trimStart(); // 支持 "data:xxx" 和 "data: xxx"
            if (payload === '[DONE]') continue;
            fullText += payload;
          }
        }
      }
    }

// 处理残留
    if (buffer.trim().startsWith('data:')) {
      const payload = buffer.trim().slice(5).trimStart();
      if (payload !== '[DONE]') fullText += payload;
    }
    console.log('✅ AI 返回全文长度:', fullText.length);
    console.log('✅ 内容预览:', fullText.substring(0, 200));
    return fullText;
  } catch (error: any) {
    if (error.name === 'TimeoutError') {
      throw new Error('请求超时，请稍后重试');
    }
    throw error;
  }
}
