/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CharacterRelationships } from './CharacterRelationships';
export type CharacterCardsAddRequest = {
    bookId?: number;
    name?: string;
    description?: string;
    personality?: string;
    background?: string;
    appearance?: string;
    age?: number;
    gender?: string;
    occupation?: string;
    abilities?: string;
    weaknesses?: string;
    goals?: string;
    relationships?: Array<CharacterRelationships>;
};

