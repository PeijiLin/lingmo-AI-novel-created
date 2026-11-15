/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { KnowledgeBaseVo } from './KnowledgeBaseVo';
import type { OrderItem } from './OrderItem';
export type PageKnowledgeBaseVo = {
    records?: Array<KnowledgeBaseVo>;
    total?: number;
    size?: number;
    current?: number;
    orders?: Array<OrderItem>;
    optimizeCountSql?: PageKnowledgeBaseVo;
    searchCount?: PageKnowledgeBaseVo;
    optimizeJoinOfCountSql?: boolean;
    maxLimit?: number;
    countId?: string;
    /**
     * @deprecated
     */
    pages?: number;
};

