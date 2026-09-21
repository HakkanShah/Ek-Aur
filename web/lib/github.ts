export const REPO = "HakkanShah/Ek-Aur";
export const releasesUrl = `https://github.com/${REPO}/releases`;

export interface LatestRelease {
  version: string | null;
  apkUrl: string | null;
  sizeMB: number | null;
  notes: string | null;
  publishedAt: string | null;
  pageUrl: string;
}

interface GhAsset {
  name?: string;
  size?: number;
  browser_download_url?: string;
}

/**
 * The app's newest release, read once and cached for five minutes so the page
 * never hammers GitHub's 60/hour unauthenticated limit. Returns null when there
 * is no release yet or the call fails — callers fall back to the releases page.
 */
export async function getLatestRelease(): Promise<LatestRelease | null> {
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
      headers: {
        Accept: "application/vnd.github+json",
        "User-Agent": "ek-aur-web",
      },
      next: { revalidate: 300 },
    });
    if (!res.ok) return null;

    const data = (await res.json()) as {
      tag_name?: string;
      body?: string;
      html_url?: string;
      published_at?: string;
      assets?: GhAsset[];
    };

    const apk = (data.assets ?? []).find(
      (a) => typeof a.name === "string" && a.name.toLowerCase().endsWith(".apk"),
    );

    return {
      version: data.tag_name ? data.tag_name.replace(/^v/i, "") : null,
      apkUrl: apk?.browser_download_url ?? null,
      sizeMB: apk?.size ? Math.round((apk.size / 1_048_576) * 10) / 10 : null,
      notes: data.body?.trim() || null,
      publishedAt: data.published_at ?? null,
      pageUrl: data.html_url || releasesUrl,
    };
  } catch {
    return null;
  }
}
