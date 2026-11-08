# Toolverse — all everyday tools, one app

A mobile-first **maximalist native-style web utility hub** designed for Chrome and Android Sketchware Plus WebView. Repository name: \`tool\`.

## Included editors and utilities

| Area | Tool | Implementation |
|---|---|---|
| Images | Background Eraser | Local color-based flood fill with tap-to-select, tolerance and transparent/solid result |
| Images | Add Text to Photo | Canvas caption/watermark editor with positioning, color, font, shadow |
| Images | Overlay Photos | Multi-layer image compositing with touch drag, scaling, layer ordering |
| Images | Black & White | Live adjustable grayscale, contrast and brightness |
| Images | Blur Background | Radial soft-focus photo filter |
| PDF | Number PDF Pages | PDF page numbering with format, position, size, start number via pdf-lib |
| Text | Case Converter | Sentence, lower, upper, title, camel, Pascal, snake, kebab, dot and alternating |
| SEO | Canonical Tag | Validated canonical HTML output and optional tracking parameter removal |
| SEO | Blogger Sitemap Links | Blogger sitemap URL and optional Atom-feed URL list (not a live crawl) |
| SEO | AMP Quick Audit | Local AMP HTML checklist, not official validation |
| SEO | Hreflang Checker | Parse alternate-language link tags from supplied HTML, not geo simulation |

All tools appear in a searchable index with category filters, light/dark theme, favorites and recents. Image and PDF files are processed locally.

## Run or host

This is a **static website**. Put the repository on Vercel or GitHub Pages with repository root as the site folder. No \`npm install\` or build command. Open the site over HTTPS.

- Main page: \`index.html\`
- Shared visual system: \`assets/styles.css\`
- Navigation + export engine: \`assets/app.js\`
- Tools: \`assets/tools.js\`
- Android native save/share: \`android/ToolBridge.java\`
- Sketchware integration steps: \`android/SETUP.md\`
- Service worker and PWA manifest: \`sw.js\`, \`manifest.webmanifest\`

Use \`#tool/bg\`, \`#tool/add-pages\`, etc. for deep links. The app works as one web workspace. A future release can add dedicated server-rendered URLs for stronger SEO indexing.

## Download & share behavior

1. In an Android Sketchware WebView with the optional native bridge, editors send exported blobs in **48 KB chunks** over the \`ToolBridge\` JavaScript interface, avoiding a single huge base64 transfer.
2. The native bridge writes Android 10+ files to scoped-storage Downloads/Toolverse with MediaStore; it offers Save As on older Android. Share uses Android content URIs.
3. In Chrome/other browsers, use the Web Share API when file sharing is supported, otherwise trigger a standard file download.

The bridge **is not magically attached by publishing**: follow \`android/SETUP.md\`, add the Java class, configure file input, and test on devices.

## Source audit and security

The user's original uploaded HTML sources contained:
- hard-coded credentials in the remote background-removal page;
- fabricated response statuses in the original AMP and locale-adaptive testing screens;
- repeated branding and separate, incompatible navigation for each tool;
- browser-only download patterns that can fail in Android WebView.

This project **does not include or use the original embedded API secret**. Rotate/revoke that previously exposed key. It replaces simulated tests with transparently labelled local markup checks. It does not claim to provide remote geolocation simulation, real-time AMP validation or AI portrait segmentation.

## Quality and release checklist

- Deploy HTTPS, confirm access in Chrome and Sketchware.
- Test image upload, drag, export/share on Android 9, 10, 13, 14 and newer devices as supported.
- Test PDFs while online and offline; pdf-lib is lazy-loaded from a pinned-version CDN and may not be available before first download. For fully offline PDF operation, self-host a vetted copy.
- Review \`privacy.html\` and update for ads, analytics and any later network data collection.
- Set a final real domain and product branding when chosen.
- Do not claim published APK, store compliance, production availability or guaranteed ad revenue from this source repository alone.

## License and original material

This is a new consolidated implementation inspired by the **functional intent** of the uploaded tools, not a deployment of those pages or their embedded credentials. Review and select your distribution license before inviting external contributions.
