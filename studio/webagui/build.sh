#!/bin/sh
set -eu
cd "$(dirname "$0")"
#npm run build
npx vite build
