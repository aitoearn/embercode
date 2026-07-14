import {resolvePhonecodeBootHref} from './phonecode-boot';

describe('resolvePhonecodeBootHref', () => {
  it('无 Bridge 时默认 pair', () => {
    expect(resolvePhonecodeBootHref(undefined)).toBe('/pair-scan?source=phonecode');
  });

  it('chat extras', () => {
    expect(
      resolvePhonecodeBootHref({
        embedded: true,
        route: 'chat',
        hostId: 'srv',
        agentId: 'ag',
      }),
    ).toBe('/h/srv/agent/ag');
  });
});
