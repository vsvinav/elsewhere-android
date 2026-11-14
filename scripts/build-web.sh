#!/bin/sh
# Builds the web app from the sibling checkout and stages it as the app's www/.
set -e
WEB="${WEB:-$(dirname "$0")/../../timeline/web}"
cd "$WEB"
VITE_BASE=/ npm run build
cd - >/dev/null
rm -rf www && cp -R "$WEB/dist" www
# Nobody's record ships inside the app; the first screen imports one.
rm -rf www/data www/data-*
echo "www/ ready from $WEB"
