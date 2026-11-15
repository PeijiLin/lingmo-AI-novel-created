/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AiChatRequest } from '../models/AiChatRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class NovelAiCreationControllerService {
    /**
     * @param requestBody
     * @returns string OK
     * @throws ApiError
     */
    public static polishContent(
        requestBody: AiChatRequest,
    ): CancelablePromise<Array<string>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/book/create/ai/polish',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns string OK
     * @throws ApiError
     */
    public static generateDialogue(
        requestBody: AiChatRequest,
    ): CancelablePromise<Array<string>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/book/create/ai/dialogue',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns string OK
     * @throws ApiError
     */
    public static createContent(
        requestBody: AiChatRequest,
    ): CancelablePromise<Array<string>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/book/create/ai/create',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns string OK
     * @throws ApiError
     */
    public static continueWriting(
        requestBody: AiChatRequest,
    ): CancelablePromise<Array<string>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/book/create/ai/continue',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns string OK
     * @throws ApiError
     */
    public static activateInspiration(
        requestBody: AiChatRequest,
    ): CancelablePromise<Array<string>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/book/create/ai/activate',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
