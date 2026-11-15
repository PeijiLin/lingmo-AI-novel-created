/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponseLong } from '../models/BaseResponseLong';
import type { BaseResponseMapStringObject } from '../models/BaseResponseMapStringObject';
import type { BaseResponseMapStringString } from '../models/BaseResponseMapStringString';
import type { BaseResponseObject } from '../models/BaseResponseObject';
import type { UserLoginRequest } from '../models/UserLoginRequest';
import type { UserRegisterRequest } from '../models/UserRegisterRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class UserControllerService {
    /**
     * @param requestBody
     * @returns BaseResponseLong OK
     * @throws ApiError
     */
    public static userRegister(
        requestBody: UserRegisterRequest,
    ): CancelablePromise<BaseResponseLong> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/user/register',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponseMapStringString OK
     * @throws ApiError
     */
    public static refreshToken(
        requestBody: Record<string, string>,
    ): CancelablePromise<BaseResponseMapStringString> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/user/refresh-token',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param authorization
     * @returns BaseResponseObject OK
     * @throws ApiError
     */
    public static logout(
        authorization: string,
    ): CancelablePromise<BaseResponseObject> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/user/logout',
            headers: {
                'Authorization': authorization,
            },
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponseMapStringObject OK
     * @throws ApiError
     */
    public static userLogin(
        requestBody: UserLoginRequest,
    ): CancelablePromise<BaseResponseMapStringObject> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/user/login',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
