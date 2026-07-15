/** PhoneCode mobile stub：排程 UI 未作为本阶段验收项，提供最小可导入实现。 */
export interface CadencePresetOption {
  id: string;
  label: string;
  expression: string;
}

export const CUSTOM_CRON_PRESET_ID = 'custom';

export const CADENCE_PRESET_OPTIONS: CadencePresetOption[] = [
  {id: 'every-minute', label: 'Every minute', expression: '* * * * *'},
  {id: 'every-hour', label: 'Every hour', expression: '0 * * * *'},
  {id: 'daily-9', label: 'Daily 9:00', expression: '0 9 * * *'},
  {id: 'weekdays-9', label: 'Weekdays 9:00', expression: '0 9 * * 1-5'},
  {id: 'mondays-9', label: 'Mondays 9:00', expression: '0 9 * * 1'},
];

export function resolveCronPresetId(cadence: {expression?: string}): string {
  const expression = cadence.expression?.trim() ?? '';
  return (
    CADENCE_PRESET_OPTIONS.find(option => option.expression === expression)?.id ??
    CUSTOM_CRON_PRESET_ID
  );
}

export function resolveCronPresetDisplay(cadence: {expression?: string}): {
  label: string;
} {
  return {
    label:
      CADENCE_PRESET_OPTIONS.find(option => option.id === resolveCronPresetId(cadence))
        ?.label ?? 'Custom cron',
  };
}

export function normalizeScheduleFormCadence(
  cadence: any,
  timezone: string,
): {type: 'cron'; expression: string; timezone: string} {
  if (cadence?.type === 'cron') {
    return {
      type: 'cron',
      expression: String(cadence.expression ?? '* * * * *'),
      timezone: cadence.timezone ?? timezone,
    };
  }
  return {type: 'cron', expression: '0 9 * * *', timezone};
}
