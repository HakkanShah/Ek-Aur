# Ek Aur — landing page

The marketing/download site for the Ek Aur app. Built with Next.js (App Router),
Tailwind, and Framer Motion. It matches the app's look (soft light + the Instagram
gradient, Poppins) and its **Download button always serves the latest APK from the
app's GitHub releases**.

## How the download stays current

- `app/api/latest` reads `github.com/HakkanShah/Ek-Aur/releases/latest` (cached 5
  min) and returns the version, size, and APK URL — used for the version labels.
- `app/api/download` 302-redirects to that release's `.apk` asset, so the button
  is always current with no code change. If a release has no APK yet, it falls
  back to the releases page.

Nothing here needs an API token — the repo is public.

## Local dev

```bash
cd web
npm install
npm run dev      # http://localhost:3000
npm run build    # what Vercel runs
```

## Deploying on Vercel (one-time, ~2 minutes)

1. Go to **vercel.com → Add New → Project** and **import** `HakkanShah/Ek-Aur`.
2. When it asks for the **Root Directory**, set it to **`web`** (this folder).
   Vercel auto-detects Next.js — leave build/output settings as detected.
3. No environment variables are needed. Click **Deploy**.
4. You get a free `*.vercel.app` URL. Every push to the repo then auto-deploys;
   add a custom domain later in the project's Domains tab if you want.

That's it — the app's releases and the site's download button are now linked.
