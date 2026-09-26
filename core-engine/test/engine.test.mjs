import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AetherCrypto } from '../dist/crypto.js';
import { ProtocolValidator } from '../dist/protocol.js';
import { AetherUpdateEngine } from '../dist/update_checker.js';

test('ProtocolValidator accepts valid CALL_INCOMING message', () => {
  const msg = {
    type: 'CALL_INCOMING',
    payload: {
      callId: 'call-12345',
      appType: 'whatsapp',
      callerName: 'Mehmet Şensoy',
      phoneNumber: '+905320000000',
      timestamp: Date.now(),
      hasVideo: false
    }
  };
  assert.equal(ProtocolValidator.isValidMessage(msg), true);
});

test('ProtocolValidator accepts valid NOTIFICATION_POSTED message', () => {
  const msg = {
    type: 'NOTIFICATION_POSTED',
    payload: {
      id: 'notif-1',
      key: 'com.whatsapp|123',
      packageName: 'com.whatsapp',
      appName: 'WhatsApp',
      title: 'Ali',
      text: 'Yarın buluşuyor muyuz?',
      timestamp: Date.now(),
      canReply: true,
      replyPlaceholder: 'Yanıt yaz...'
    }
  };
  assert.equal(ProtocolValidator.isValidMessage(msg), true);
});

test('ProtocolValidator rejects invalid payload', () => {
  const invalidMsg = {
    type: 'CALL_INCOMING',
    payload: {
      callerName: 12345 // invalid type
    }
  };
  assert.equal(ProtocolValidator.isValidMessage(invalidMsg), false);
  assert.equal(ProtocolValidator.isValidMessage(null), false);
  assert.equal(ProtocolValidator.isValidMessage({ type: 'UNKNOWN_EVENT' }), false);
});

test('AetherCrypto: AES-256-GCM encrypt and decrypt roundtrip', () => {
  const key = Buffer.alloc(32, 'a1b2c3d4e5f60718293a4b5c6d7e8f90');
  const secretData = 'Gizli mesaj: Mac ile Android bağlandı!';

  const envelope = AetherCrypto.encrypt(secretData, key);
  assert.ok(envelope.iv);
  assert.ok(envelope.tag);
  assert.ok(envelope.data);

  const decrypted = AetherCrypto.decrypt(envelope, key);
  assert.equal(decrypted, secretData);
});

test('AetherCrypto: SHA-256 hash consistency', () => {
  const text = 'Hello AetherLink';
  const hash1 = AetherCrypto.sha256(text);
  const hash2 = AetherCrypto.sha256(text);
  assert.equal(hash1, hash2);
  assert.equal(hash1.length, 64);
});

test('AetherUpdateEngine: SemVer comparison logic', () => {
  assert.equal(AetherUpdateEngine.compareSemVer('1.0.0', '1.1.0'), 1); // 1.1.0 > 1.0.0
  assert.equal(AetherUpdateEngine.compareSemVer('v1.2.0', '1.2.0'), 0); // Equal
  assert.equal(AetherUpdateEngine.compareSemVer('2.0.0', '1.9.9'), -1); // Older
  assert.equal(AetherUpdateEngine.compareSemVer('1.0.0', '1.0.1'), 1); // Patch update
  assert.equal(AetherUpdateEngine.compareSemVer('1.0.0-beta', '1.0.0'), 0);
});

test('AetherUpdateEngine: Mock GitHub release update check', async () => {
  const mockFetch = async () => ({
    ok: true,
    status: 200,
    json: async () => ({
      tag_name: 'v1.1.0',
      name: 'AetherLink v1.1.0 - Continuity Calls & Quick Reply',
      body: '## Yenilikler\n* WhatsApp sesli arama desteği eklendi.\n* Pano görsel eşitleme optimize edildi.',
      published_at: '2026-09-26T18:00:00Z',
      html_url: 'https://github.com/mehmetsensoy/aetherlink/releases/tag/v1.1.0',
      prerelease: false,
      assets: [
        {
          id: 101,
          name: 'AetherLink-v1.1.0-macOS.dmg',
          browser_download_url: 'https://github.com/mehmetsensoy/aetherlink/releases/download/v1.1.0/AetherLink-v1.1.0-macOS.dmg',
          size: 15400000,
          content_type: 'application/x-apple-diskimage'
        }
      ]
    })
  });

  const engine = new AetherUpdateEngine('mehmetsensoy', 'aetherlink', '1.0.0', 'macOS');
  const result = await engine.checkForUpdates(mockFetch);

  assert.equal(result.hasUpdate, true);
  assert.equal(result.currentVersion, '1.0.0');
  assert.equal(result.latestVersion, '1.1.0');
  assert.ok(result.downloadUrl.endsWith('.dmg'));
  assert.ok(result.changelogMarkdown.includes('WhatsApp sesli arama'));
});
