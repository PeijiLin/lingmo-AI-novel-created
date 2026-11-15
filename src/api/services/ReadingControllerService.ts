/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponseBookContentVo } from '../models/BaseResponseBookContentVo';
import type { BaseResponseMapStringListBookCategoryVo } from '../models/BaseResponseMapStringListBookCategoryVo';
import type { BaseResponsePageBookChapterVo } from '../models/BaseResponsePageBookChapterVo';
import type { BaseResponsePageBookInfoListVo } from '../models/BaseResponsePageBookInfoListVo';
import type { BookPageRequest } from '../models/BookPageRequest';
import type { ChapterPageRequest } from '../models/ChapterPageRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ReadingControllerService {
    /**
     * @param requestBody
     * @returns BaseResponsePageBookInfoListVo OK
     * @throws ApiError
     */
    public static pageBookList(
        requestBody: BookPageRequest,
    ): CancelablePromise<BaseResponsePageBookInfoListVo> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/book/page',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponsePageBookChapterVo OK
     * @throws ApiError
     */
    public static queryBookChapterById(
        requestBody: ChapterPageRequest,
    ): CancelablePromise<BaseResponsePageBookChapterVo> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/book/chapter/page',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param chapterId
     * @returns BaseResponseBookContentVo OK
     * @throws ApiError
     */
    public static queryBookContentById(
        chapterId: number,
    ): CancelablePromise<BaseResponseBookContentVo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/book/content',
            query: {
                'chapterId': chapterId,
            },
        });
    }
    /**
     * @returns BaseResponseMapStringListBookCategoryVo OK
     * @throws ApiError
     */
    public static queryBookCategories(): CancelablePromise<BaseResponseMapStringListBookCategoryVo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/book/category/list',
        });
    }
}
