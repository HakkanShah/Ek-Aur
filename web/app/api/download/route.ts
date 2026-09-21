import { NextResponse } from "next/server";
import { getLatestRelease, releasesUrl } from "@/lib/github";

export const revalidate = 300;

/**
 * Sends the visitor straight to the newest APK. Always current, because it reads
 * the latest release each time (cached 5 min). Falls back to the releases page
 * when there is no APK asset yet.
 */
export async function GET() {
  const release = await getLatestRelease();
  const target = release?.apkUrl ?? release?.pageUrl ?? releasesUrl;
  return NextResponse.redirect(target, 302);
}
