/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponseString } from '../models/BaseResponseString';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class MainService {
    /**
     * @returns BaseResponseString OK
     * @throws ApiError
     */
    public static health(): CancelablePromise<BaseResponseString> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/health',
        });
    }
}
