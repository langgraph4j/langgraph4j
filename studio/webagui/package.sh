#!/bin/sh
set -eu
cd "$(dirname "$0")"
tar -czf langgraph4j-studio-webagui.tgz -C dist .
