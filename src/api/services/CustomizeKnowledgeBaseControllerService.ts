/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponseVoid } from '../models/BaseResponseVoid';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CustomizeKnowledgeBaseControllerService {
    /**
     * @param multipartFile
     * @param knowledgeBaseId
     * @returns BaseResponseVoid OK
     * @throws ApiError
     */
    public static uploadFile(
        multipartFile: Blob,
        knowledgeBaseId: number,
    ): CancelablePromise<BaseResponseVoid> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/customizeKnowledgeBase/upload/file',
            query: {
                'multipartFile': multipartFile,
                'knowledgeBaseId': knowledgeBaseId,
            },
        });
    }
    /**
     * @param content
     * @param knowledgeBaseId
     * @returns BaseResponseVoid OK
     * @throws ApiError
     */
    public static uploadContent(
        content: string,
        knowledgeBaseId: number,
    ): CancelablePromise<BaseResponseVoid> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/customizeKnowledgeBase/upload/content',
            query: {
                'content': content,
                'knowledgeBaseId': knowledgeBaseId,
            },
        });
    }
}
