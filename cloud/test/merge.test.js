import test from 'node:test';
import assert from 'node:assert/strict';
import { mergeApps, preferSnippet, maxLicense, sameVersion, normalizeName, generateId } from '../src/merge.js';

const base = {
  id: 'a', platform: 'macOS', name: 'N', developer: 'D', license: 'FREE', price: 0,
  currency: 'USD', category: '유틸', firstSeenAt: 1, lastUpdatedAt: 2, isNew: true,
};

test('preferSnippet keeps existing when draft is much shorter', () => {
  assert.equal(preferSnippet('a'.repeat(100), 'short'), 'a'.repeat(100));
  assert.equal(preferSnippet('a'.repeat(100), 'b'.repeat(90)), 'b'.repeat(90));
  assert.equal(preferSnippet(null, 'x'), 'x');
  assert.equal(preferSnippet('y', null), 'y');
});

test('merge keeps firstSeen/category/sourceId, ranks license OSS top', () => {
  const m = mergeApps(
    { ...base, license: 'FREE', category: '유틸', sourceId: 's1', firstSeenAt: 5, tags: 'a' },
    { ...base, license: 'OSS', category: '바뀌면안됨', sourceId: 's2', lastUpdatedAt: 9, tags: 'b', price: 0 },
  );
  assert.equal(m.firstSeenAt, 5);
  assert.equal(m.category, '유틸');
  assert.equal(m.sourceId, 's1');
  assert.equal(m.license, 'OSS');
  assert.equal(m.tags, 'a,b');
});

test('version bump clears isNew and swaps release notes', () => {
  const m = mergeApps(
    { ...base, version: '1.0', isNew: true, releaseNotes: 'old' },
    { ...base, version: '2.0', lastUpdatedAt: 3, releaseNotes: 'new' },
  );
  assert.equal(m.isNew, false);
  assert.equal(m.releaseNotes, 'new');
  assert.equal(m.prevVersion, '1.0');
});

test('sameVersion ignores surrounding spaces; normalize keeps CJK', () => {
  assert.equal(sameVersion('4.3.4 ', '4.3.4'), true);
  assert.equal(normalizeName('한글 앱!'), '한글앱');
  assert.ok(generateId('App', 'Dev').length > 0);
  assert.equal(maxLicense('PAID', 'FREE'), 'PAID');
});
