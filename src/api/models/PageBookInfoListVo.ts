/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BookInfoListVo } from './BookInfoListVo';
import type { OrderItem } from './OrderItem';
export type PageBookInfoListVo = {
    records?: Array<BookInfoListVo>;
    total?: number;
    size?: number;
    current?: number;
    orders?: Array<OrderItem>;
    optimizeCountSql?: PageBookInfoListVo;
    searchCount?: PageBookInfoListVo;
    optimizeJoinOfCountSql?: boolean;
    maxLimit?: number;
    countId?: string;
    /**
     * @deprecated
     */
    pages?: number;
};

