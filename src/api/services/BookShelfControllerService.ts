/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponseLong } from '../models/BaseResponseLong';
import type { BookShelfAddRequest } from '../models/BookShelfAddRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class BookShelfControllerService {
    /**
     * @param requestBody
     * @returns BaseResponseLong OK
     * @throws ApiError
     */
    public static addBook(
        requestBody: BookShelfAddRequest,
    ): CancelablePromise<BaseResponseLong> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/bookshelf/add',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
