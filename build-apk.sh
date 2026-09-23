#!/bin/bash
set -e

echo "========================================="
echo "Building Arushi AI Voice Assistant APK..."
echo "========================================="

gradle assembleDebug

echo "========================================="
echo "Build successful!"
echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
echo "========================================="
