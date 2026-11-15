/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponseBoolean } from '../models/BaseResponseBoolean';
import type { BaseResponseCharacterCards } from '../models/BaseResponseCharacterCards';
import type { BaseResponseCharacterCardsVo } from '../models/BaseResponseCharacterCardsVo';
import type { BaseResponseListCharacterCardsVo } from '../models/BaseResponseListCharacterCardsVo';
import type { CharacterCards } from '../models/CharacterCards';
import type { CharacterCardsAddRequest } from '../models/CharacterCardsAddRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CharacterCardControllerService {
    /**
     * @param requestBody
     * @returns BaseResponseCharacterCards OK
     * @throws ApiError
     */
    public static updateCharacterCard(
        requestBody: CharacterCards,
    ): CancelablePromise<BaseResponseCharacterCards> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/character-card/modify',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponseCharacterCardsVo OK
     * @throws ApiError
     */
    public static createCharacterCard(
        requestBody: CharacterCardsAddRequest,
    ): CancelablePromise<BaseResponseCharacterCardsVo> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/character-card/add',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param id
     * @returns BaseResponseCharacterCardsVo OK
     * @throws ApiError
     */
    public static getCharacterCard(
        id: number,
    ): CancelablePromise<BaseResponseCharacterCardsVo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/character-card/{id}',
            path: {
                'id': id,
            },
        });
    }
    /**
     * @param id
     * @returns BaseResponseBoolean OK
     * @throws ApiError
     */
    public static deleteCharacterCard(
        id: number,
    ): CancelablePromise<BaseResponseBoolean> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/character-card/{id}',
            path: {
                'id': id,
            },
        });
    }
    /**
     * @param bookId
     * @returns BaseResponseListCharacterCardsVo OK
     * @throws ApiError
     */
    public static getCharacterCards(
        bookId: number,
    ): CancelablePromise<BaseResponseListCharacterCardsVo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/character-card/project/{bookId}',
            path: {
                'bookId': bookId,
            },
        });
    }
    /**
     * @param bookId
     * @param name
     * @returns BaseResponseListCharacterCardsVo OK
     * @throws ApiError
     */
    public static searchCharacterCards(
        bookId: number,
        name: string,
    ): CancelablePromise<BaseResponseListCharacterCardsVo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/character-card/project/{bookId}/search',
            path: {
                'bookId': bookId,
            },
            query: {
                'name': name,
            },
        });
    }
    /**
     * @param bookId
     * @param name
     * @returns BaseResponseCharacterCardsVo OK
     * @throws ApiError
     */
    public static getCharacterCardByName(
        bookId: number,
        name: string,
    ): CancelablePromise<BaseResponseCharacterCardsVo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/character-card/project/{bookId}/name/{name}',
            path: {
                'bookId': bookId,
                'name': name,
            },
        });
    }
}
