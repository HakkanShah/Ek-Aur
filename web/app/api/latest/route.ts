import { NextResponse } from "next/server";
import { getLatestRelease, releasesUrl } from "@/lib/github";

export const revalidate = 300;

export async function GET() {
  const release = await getLatestRelease();
  return NextResponse.json(
    release ?? {
      version: null,
      apkUrl: null,
      sizeMB: null,
      notes: null,
      publishedAt: null,
      pageUrl: releasesUrl,
    },
  );
}
