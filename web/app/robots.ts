import type { MetadataRoute } from "next";

const SITE = "https://ek-aur.vercel.app";

export default function robots(): MetadataRoute.Robots {
  return {
    // Everything is public and meant to be found — including by AI answer
    // engines — so allow all crawlers; only the API routes are pointless to index.
    rules: [{ userAgent: "*", allow: "/", disallow: ["/api/"] }],
    sitemap: `${SITE}/sitemap.xml`,
    host: SITE,
  };
}
