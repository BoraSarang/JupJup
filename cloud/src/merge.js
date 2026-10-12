// Merge logic ported from AppRepository.mergeApps / preferSnippet / MergeUtils.
// Room-independent pure functions so device and cloud share semantics.

export const APP_SUMMARY_LEN = 300;
export const RELEASE_NOTES_MAX = 20000;

const LICENSE_RANK = { OSS: 3, PAID: 2, FREE: 1 };

export function maxLicense(a, b) {
  return (LICENSE_RANK[b] ?? 0) >= (LICENSE_RANK[a] ?? 0) ? b : a;
}

export function sameVersion(a, b) {
  if (a == null || b == null) return a === b;
  return String(a).trim() === String(b).trim();
}

export function normalizeName(s) {
  const cleaned = String(s).toLowerCase().replace(/[^a-z0-9가-힣Ѐ-ӿ぀-ヿ一-鿿]/g, '').slice(0, 64);
  if (cleaned) return cleaned;
  const hex = Buffer.from(String(s).trim().toLowerCase(), 'utf8').toString('hex').slice(0, 32);
  return hex ? `x${hex}` : 'x';
}

export function generateId(name, developer) {
  return `${normalizeName(developer)}-${normalizeName(name)}`.replace(/^-+|-+$/g, '').slice(0, 128);
}

function longer(a, b) {
  if (a == null || a === '') return b ?? null;
  if (b == null || b === '') return a ?? null;
  return b.length > a.length ? b : a;
}

// draft가 null/빈이면 기존 유지, existing의 50% 미만이면 기존 유지.
export function preferSnippet(existing, draft) {
  if (draft == null || draft === '') return existing ?? null;
  if (existing == null || existing === '') return draft;
  if (draft.length * 2 < existing.length) return existing;
  return draft;
}

export function unionTags(a, b) {
  const set = new Set(
    [...String(a ?? '').split(','), ...String(b ?? '').split(',')]
      .map((t) => t.trim()).filter(Boolean),
  );
  return set.size ? [...set].join(',') : null;
}

// existing: stored row, draft: incoming AppDraft.app. Mirrors mergeApps().
export function mergeApps(existing, draft) {
  const versionChanged = draft.version != null && existing.version != null
    && !sameVersion(draft.version, existing.version);
  const pick = (d, e) => (d ?? e);
  return {
    ...draft,
    firstSeenAt: existing.firstSeenAt,
    isNew: versionChanged ? false : existing.isNew,
    licenseOverride: existing.licenseOverride ?? null,
    license: maxLicense(existing.license, draft.license),
    descriptionSnippet: preferSnippet(existing.descriptionSnippet, draft.descriptionSnippet)?.slice(0, APP_SUMMARY_LEN) ?? null,
    descriptionKo: preferSnippet(existing.descriptionKo, draft.descriptionKo)?.slice(0, APP_SUMMARY_LEN) ?? null,
    longDescription: longer(
      existing.longDescription ?? (existing.descriptionSnippet && existing.descriptionSnippet.length > APP_SUMMARY_LEN ? existing.descriptionSnippet : null),
      draft.longDescription ?? (draft.descriptionSnippet && draft.descriptionSnippet.length > APP_SUMMARY_LEN ? draft.descriptionSnippet : null),
    ),
    longDescriptionKo: draft.longDescriptionKo ?? existing.longDescriptionKo
      ?? (existing.descriptionKo && existing.descriptionKo.length > APP_SUMMARY_LEN ? existing.descriptionKo : null),
    releaseNotes: (versionChanged
      ? (draft.releaseNotes ?? existing.releaseNotes)
      : longer(existing.releaseNotes, draft.releaseNotes))?.slice(0, RELEASE_NOTES_MAX) ?? null,
    releaseNotesSummary: versionChanged
      ? (draft.releaseNotesSummary ?? existing.releaseNotesSummary ?? null)
      : (draft.releaseNotesSummary ?? existing.releaseNotesSummary ?? null),
    iconUrl: pick(draft.iconUrl, existing.iconUrl),
    releaseNotesKo: versionChanged ? (draft.releaseNotesKo ?? null) : (draft.releaseNotesKo ?? existing.releaseNotesKo ?? null),
    screenshotUrls: pick(draft.screenshotUrls, existing.screenshotUrls),
    sellerName: pick(draft.sellerName, existing.sellerName),
    fileSize: pick(draft.fileSize, existing.fileSize),
    minOs: pick(draft.minOs, existing.minOs),
    contentRating: pick(draft.contentRating, existing.contentRating),
    stars: pick(draft.stars, existing.stars),
    averageRating: pick(draft.averageRating, existing.averageRating),
    ratingCount: pick(draft.ratingCount, existing.ratingCount),
    primaryLanguage: pick(draft.primaryLanguage, existing.primaryLanguage),
    supportedLanguages: pick(draft.supportedLanguages, existing.supportedLanguages),
    topics: pick(draft.topics, existing.topics),
    forks: pick(draft.forks, existing.forks),
    issues: pick(draft.issues, existing.issues),
    licenseName: pick(draft.licenseName, existing.licenseName),
    homepageUrl: pick(draft.homepageUrl, existing.homepageUrl),
    repoFullName: pick(draft.repoFullName, existing.repoFullName),
    trackId: pick(draft.trackId, existing.trackId),
    price: draft.price > 0 ? draft.price : existing.price,
    category: existing.category,
    tags: unionTags(existing.tags, draft.tags),
    version: draft.version ?? existing.version ?? null,
    prevVersion: versionChanged ? existing.version : (draft.prevVersion ?? existing.prevVersion ?? null),
    releaseDate: pick(draft.releaseDate, existing.releaseDate),
    sourceId: existing.sourceId ?? draft.sourceId ?? null,
  };
}
