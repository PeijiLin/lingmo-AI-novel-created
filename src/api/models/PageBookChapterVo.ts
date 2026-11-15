/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BookChapterVo } from './BookChapterVo';
import type { OrderItem } from './OrderItem';
export type PageBookChapterVo = {
    records?: Array<BookChapterVo>;
    total?: number;
    size?: number;
    current?: number;
    orders?: Array<OrderItem>;
    optimizeCountSql?: PageBookChapterVo;
    searchCount?: PageBookChapterVo;
    optimizeJoinOfCountSql?: boolean;
    maxLimit?: number;
    countId?: string;
    /**
     * @deprecated
     */
    pages?: number;
};

