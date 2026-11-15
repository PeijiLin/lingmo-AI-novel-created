/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponse } from '../models/BaseResponse';
import type { BaseResponseLong } from '../models/BaseResponseLong';
import type { BaseResponsePageKnowledgeBaseVo } from '../models/BaseResponsePageKnowledgeBaseVo';
import type { KnowledgeBaseDto } from '../models/KnowledgeBaseDto';
import type { PageRequest } from '../models/PageRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class KnowledgeBaseControllerService {
    /**
     * @param requestBody
     * @returns BaseResponsePageKnowledgeBaseVo OK
     * @throws ApiError
     */
    public static pageKnowledgeBase(
        requestBody: PageRequest,
    ): CancelablePromise<BaseResponsePageKnowledgeBaseVo> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/knowledgeBase/page',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponseLong OK
     * @throws ApiError
     */
    public static addKnowledgeBase(
        requestBody: KnowledgeBaseDto,
    ): CancelablePromise<BaseResponseLong> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/knowledgeBase/add',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param id
     * @returns BaseResponse OK
     * @throws ApiError
     */
    public static removeKnowledgeBase(
        id: number,
    ): CancelablePromise<BaseResponse> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/knowledgeBase/remove',
            query: {
                'id': id,
            },
        });
    }
}
