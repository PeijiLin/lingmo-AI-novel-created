/**
 * 纸墨 ui 组件库出口（shadcn-vue + reka-ui 无头 + tokens 皮肤）
 * 纪律：业务代码一律从此处（或 @/components/ui/xxx）引用，不得直接引入裸 reka-ui。
 */
export { default as Button, buttonVariants } from './Button.vue'
export type { ButtonVariant, ButtonSize } from './Button.vue'
export { default as Card } from './Card.vue'
export { default as Input } from './Input.vue'
export { default as Textarea } from './Textarea.vue'
export { default as Separator } from './Separator.vue'
export { default as Badge, badgeVariants } from './Badge.vue'
export type { BadgeVariant } from './Badge.vue'
export { default as Skeleton } from './Skeleton.vue'
export { default as Dialog } from './Dialog.vue'
export { default as Sheet } from './Sheet.vue'
export { default as Tabs } from './Tabs.vue'
export { default as TabsList } from './TabsList.vue'
export { default as TabsTrigger } from './TabsTrigger.vue'
export { default as TabsPane } from './TabsPane.vue'
export { default as Select } from './Select.vue'
export type { SelectOption } from './Select.vue'
export { default as DropdownMenu } from './DropdownMenu.vue'
export { default as DropdownItem } from './DropdownItem.vue'
export { default as ToastHost } from './ToastHost.vue'
