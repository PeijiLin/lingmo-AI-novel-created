/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponse } from '../models/BaseResponse';
import type { BaseResponseBookInfoListVo } from '../models/BaseResponseBookInfoListVo';
import type { BaseResponseCreateBookVo } from '../models/BaseResponseCreateBookVo';
import type { BaseResponseListBookInfoListVo } from '../models/BaseResponseListBookInfoListVo';
import type { BaseResponsePageCreateBookVo } from '../models/BaseResponsePageCreateBookVo';
import type { CreateBook } from '../models/CreateBook';
import type { CreateBookAddRequest } from '../models/CreateBookAddRequest';
import type { CreateBookPageRequest } from '../models/CreateBookPageRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CreationBookControllerService {
    /**
     * @param requestBody
     * @returns BaseResponse OK
     * @throws ApiError
     */
    public static updateCreateBook(
        requestBody: CreateBook,
    ): CancelablePromise<BaseResponse> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/creation/update',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponsePageCreateBookVo OK
     * @throws ApiError
     */
    public static createBookPageByBookId(
        requestBody: CreateBookPageRequest,
    ): CancelablePromise<BaseResponsePageCreateBookVo> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/creation/page/id',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponseCreateBookVo OK
     * @throws ApiError
     */
    public static addCreateBook(
        requestBody: CreateBookAddRequest,
    ): CancelablePromise<BaseResponseCreateBookVo> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/creation/add',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param bookId
     * @returns BaseResponseBookInfoListVo OK
     * @throws ApiError
     */
    public static queryBookInfoById(
        bookId: number,
    ): CancelablePromise<BaseResponseBookInfoListVo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/creation/bookInfo/id',
            query: {
                'bookId': bookId,
            },
        });
    }
    /**
     * @param userId
     * @returns BaseResponseListBookInfoListVo OK
     * @throws ApiError
     */
    public static queryCreateBookListByAuthorId(
        userId: number,
    ): CancelablePromise<BaseResponseListBookInfoListVo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/creation/book/author',
            query: {
                'userId': userId,
            },
        });
    }
}
