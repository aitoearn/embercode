import {buildSummariesJsonFromHosts} from './phonecode-finish';

describe('buildSummariesJsonFromHosts', () => {
  it('导出 Connected 主机', () => {
    const json = buildSummariesJsonFromHosts({
      hosts: [
        {
          serverId: 'abc',
          label: 'desk',
          connected: true,
          agents: [{id: 'a1', title: 'Fix', updatedAt: 1, preview: 'hi'}],
        },
      ],
    });
    expect(JSON.parse(json)).toEqual({
      hosts: [
        {
          hostId: 'abc',
          hostLabel: 'desk',
          connectionState: 'Connected',
          sessions: [{id: 'a1', title: 'Fix', updatedAt: 1, preview: 'hi'}],
        },
      ],
    });
  });
});
