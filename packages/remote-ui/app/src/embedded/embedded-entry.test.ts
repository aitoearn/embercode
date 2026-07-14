import {hrefFromEmbeddedExtras} from './embedded-entry';
import {exportSessionSummaries} from './session-summary-export';

describe('hrefFromEmbeddedExtras', () => {
  it('非 embedded 返回 null', () => {
    expect(hrefFromEmbeddedExtras({route: 'pair'})).toBeNull();
  });

  it('pair 路由', () => {
    expect(hrefFromEmbeddedExtras({embedded: true, route: 'pair'})).toBe(
      '/pair-scan?source=phonecode',
    );
  });

  it('chat 完整参数', () => {
    expect(
      hrefFromEmbeddedExtras({
        embedded: true,
        route: 'chat',
        hostId: 'h1',
        agentId: 'a1',
      }),
    ).toBe('/h/h1/agent/a1');
  });
});

describe('exportSessionSummaries', () => {
  it('映射主机与会话', () => {
    const payload = exportSessionSummaries({
      hosts: [{id: 'h1', label: 'Mac', connected: true}],
      agentsByHost: {
        h1: [{id: 's1', title: 'Fix bug', updatedAt: 100, preview: 'ok'}],
      },
    });
    expect(payload.hosts).toEqual([
      {
        hostId: 'h1',
        hostLabel: 'Mac',
        connectionState: 'Connected',
        sessions: [
          {id: 's1', title: 'Fix bug', updatedAt: 100, preview: 'ok'},
        ],
      },
    ]);
  });
});
