import type { MetadataRoute } from "next";

const SITE = "https://ek-aur.vercel.app";

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  return [
    { url: SITE, lastModified: now, changeFrequency: "weekly", priority: 1 },
    { url: `${SITE}/#features`, lastModified: now, changeFrequency: "monthly", priority: 0.7 },
    { url: `${SITE}/#see`, lastModified: now, changeFrequency: "monthly", priority: 0.7 },
    { url: `${SITE}/#install`, lastModified: now, changeFrequency: "monthly", priority: 0.6 },
  ];
}
