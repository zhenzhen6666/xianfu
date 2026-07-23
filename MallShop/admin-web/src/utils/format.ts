export const money = (value: number | string | null | undefined) => `¥${Number(value || 0).toFixed(2)}`

export const dateTime = (value?: string) => {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value)).replaceAll('/', '-')
}
