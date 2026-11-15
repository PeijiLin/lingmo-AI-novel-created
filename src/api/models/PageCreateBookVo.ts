/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CreateBookVo } from './CreateBookVo';
import type { OrderItem } from './OrderItem';
export type PageCreateBookVo = {
    records?: Array<CreateBookVo>;
    total?: number;
    size?: number;
    current?: number;
    orders?: Array<OrderItem>;
    optimizeCountSql?: PageCreateBookVo;
    searchCount?: PageCreateBookVo;
    optimizeJoinOfCountSql?: boolean;
    maxLimit?: number;
    countId?: string;
    /**
     * @deprecated
     */
    pages?: number;
};

