// storeOfTags / genreArray ported from MacServerJson.kt.
const KNOWN_GENRES = new Set([
  'RPG', '인디', '액션', '어드벤처', '전략', '시뮬레이션', '퍼즐',
  '캐주얼', '멀티', '레이싱·스포츠', '호러·서바이벌',
]);

export function storeOfTags(tags) {
  const t = tags ?? '';
  if (!t) return '';
  if (t.includes('epic')) return 'epic';
  if (t.includes('steam')) return 'steam';
  return '';
}

export function genreArray(tags) {
  const list = String(tags ?? '').split(',').map((s) => s.trim()).filter((t) => (
    t && t !== 'game' && t !== 'steam' && t !== 'epic'
    && !t.startsWith('steam-appid:') && !t.startsWith('epic-weekly-free')
    && ([...t].some((ch) => ch.codePointAt(0) > 127) || KNOWN_GENRES.has(t))
  ));
  return [...new Set(list)];
}
