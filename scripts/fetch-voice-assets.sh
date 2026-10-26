#!/usr/bin/env bash
# Descarga el motor de voz local (sherpa-onnx) y el modelo Kokoro multilenguaje.
# Los deja en app/src/main/jniLibs y app/src/main/assets/kokoro (ignorados por git).
# Uso: scripts/fetch-voice-assets.sh   (lo corre también el flujo de GitHub Actions)
set -euo pipefail

VERSION="v1.13.8"
BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download"
MAIN="$(cd "$(dirname "$0")/.." && pwd)/app/src/main"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [ -f "$MAIN/assets/kokoro/model.int8.onnx" ] && [ -f "$MAIN/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" ]; then
  echo "Voz local ya descargada."
  exit 0
fi

echo "Bajando bibliotecas nativas ($VERSION)..."
curl -fsSL --retry 3 -o "$TMP/android.tar.bz2" "$BASE/$VERSION/sherpa-onnx-$VERSION-android.tar.bz2"
mkdir "$TMP/android" && tar xjf "$TMP/android.tar.bz2" -C "$TMP/android"
for abi in arm64-v8a armeabi-v7a; do
  mkdir -p "$MAIN/jniLibs/$abi"
  cp "$TMP/android/jniLibs/$abi/libsherpa-onnx-jni.so" "$TMP/android/jniLibs/$abi/libonnxruntime.so" "$MAIN/jniLibs/$abi/"
done

echo "Bajando el modelo Kokoro multilenguaje v1.0 (int8)..."
curl -fsSL --retry 3 -o "$TMP/kokoro.tar.bz2" "$BASE/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2"
mkdir "$TMP/kokoro" && tar xjf "$TMP/kokoro.tar.bz2" -C "$TMP/kokoro"
SRC="$TMP/kokoro/kokoro-int8-multi-lang-v1_0"
OUT="$MAIN/assets/kokoro"
rm -rf "$OUT" && mkdir -p "$OUT/espeak-ng-data"
cp "$SRC/model.int8.onnx" "$SRC/voices.bin" "$SRC/tokens.txt" "$SRC/LICENSE" "$OUT/"
# espeak-ng: solo se necesitan los diccionarios de español e inglés (los demás pesan sin servir).
(cd "$SRC/espeak-ng-data" && find . -type f ! -name '*_dict' -print0 | xargs -0 -I{} cp --parents {} "$OUT/espeak-ng-data/")
cp "$SRC/espeak-ng-data/es_dict" "$SRC/espeak-ng-data/en_dict" "$OUT/espeak-ng-data/"
echo "Listo: $(du -sh "$OUT" | cut -f1) de modelo en assets."
