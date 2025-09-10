#!/bin/bash

# Test script to verify both apps build successfully

echo "🏗️  Building JustdialOCR Android app..."
cd JustdialOCR
if ./gradlew assembleDebug; then
    echo "✅ JustdialOCR build successful!"
    echo "📱 APK created at: app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ JustdialOCR build failed!"
    exit 1
fi

echo ""
echo "🏗️  Building React Native app..."
cd ../JdReactNativeSample/android
if ./gradlew assembleDebug; then
    echo "✅ React Native app build successful!"
    echo "📱 APK created at: app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ React Native app build failed!"
    exit 1
fi

echo ""
echo "🎉 Both apps built successfully!"
echo ""
echo "📋 To test the integration:"
echo "1. Install JustdialOCR first: adb install JustdialOCR/app/build/outputs/apk/debug/app-debug.apk"
echo "2. Install React Native app: adb install JdReactNativeSample/android/app/build/outputs/apk/debug/app-debug.apk" 
echo "3. Open React Native app and tap 'Process Cheque' to test integration"