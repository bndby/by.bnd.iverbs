#!/usr/bin/env bash
# Скачивает комплект Sherpa один раз на сборке и кладёт его в assets.
# Повторный запуск ничего не качает, если файлы уже на месте.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
assets="$root/app/src/main/assets"
jni="$root/app/src/main/jniLibs/arm64-v8a"
cache="$root/build/speech-fetch"
limit=$((110 * 1024 * 1024))

marker=(
  "$assets/ru/tokens.txt"
  "$assets/ru/MODEL_CARD"
  "$assets/en/tokens.txt"
  "$assets/en/MODEL_CARD"
  "$assets/espeak-ng-data/phontab"
  "$assets/moonshine/tokens.txt"
  "$assets/moonshine/LICENSE"
  "$assets/silero_vad.onnx"
  "$jni/libsherpa-onnx-jni.so"
  "$jni/libonnxruntime.so"
  "$jni/libsherpa-onnx-c-api.so"
  "$jni/libsherpa-onnx-cxx-api.so"
)
sized=(
  "$assets/ru/ru_RU-dmitri-medium.onnx:63149192"
  "$assets/en/en_GB-cori-high.onnx:38136448"
  "$assets/moonshine/encoder_model.ort:31326816"
  "$assets/moonshine/decoder_model_merged.ort:109424400"
)

ready=1
for file in "${marker[@]}"; do
  if [[ ! -f "$file" ]]; then
    ready=0
    break
  fi
done
if [[ "$ready" -eq 1 ]]; then
  for item in "${sized[@]}"; do
    file=${item%%:*}
    size=${item##*:}
    if [[ ! -f "$file" ]] || [[ "$(stat -c%s "$file")" -ne "$size" ]]; then
      ready=0
      break
    fi
  done
fi
if [[ "$ready" -eq 1 ]]; then
  exit 0
fi

mkdir -p "$cache"
download() {
  local url=$1
  local dest=$2
  local size=$3
  if [[ -f "$dest" ]] && [[ "$(stat -c%s "$dest")" -eq "$size" ]]; then
    return
  fi
  curl -fL --retry 3 -o "$dest.part" "$url"
  mv "$dest.part" "$dest"
  local actual
  actual=$(stat -c%s "$dest")
  if [[ "$actual" -ne "$size" ]]; then
    echo "размер $dest: $actual, на релизе $size" >&2
    exit 1
  fi
}

download \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-dmitri-medium.tar.bz2" \
  "$cache/dmitri.tar.bz2" 67188551
download \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-cori-high-int8.tar.bz2" \
  "$cache/cori.tar.bz2" 35038210
download \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-en-quantized-2026-02-27.tar.bz2" \
  "$cache/moonshine.tar.bz2" 111266225
download \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx" \
  "$cache/silero_vad.onnx" 643854
download \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-android.tar.bz2" \
  "$cache/sherpa-android.tar.bz2" 46093321

work="$cache/unpacked"
rm -rf "$work"
mkdir -p "$work"
tar -xjf "$cache/dmitri.tar.bz2" -C "$work"
tar -xjf "$cache/cori.tar.bz2" -C "$work"
tar -xjf "$cache/moonshine.tar.bz2" -C "$work"
tar -xjf "$cache/sherpa-android.tar.bz2" -C "$work" ./jniLibs/arm64-v8a

ru="$work/vits-piper-ru_RU-dmitri-medium"
en="$work/vits-piper-en_GB-cori-high-int8"
moon="$work/sherpa-onnx-moonshine-base-en-quantized-2026-02-27"
if ! diff -rq "$ru/espeak-ng-data" "$en/espeak-ng-data" >/dev/null; then
  echo "espeak-ng-data в архивах dmitri и cori различается" >&2
  exit 1
fi

rm -rf "$assets/ru" "$assets/en" "$assets/espeak-ng-data" "$assets/moonshine"
mkdir -p "$assets/ru" "$assets/en" "$assets/moonshine" "$jni"
cp "$ru/ru_RU-dmitri-medium.onnx" "$ru/tokens.txt" "$ru/MODEL_CARD" "$assets/ru/"
cp "$en/en_GB-cori-high.onnx" "$en/tokens.txt" "$en/MODEL_CARD" "$assets/en/"
cp -a "$ru/espeak-ng-data" "$assets/espeak-ng-data"
cp "$moon/encoder_model.ort" "$moon/decoder_model_merged.ort" "$moon/tokens.txt" "$moon/LICENSE" "$assets/moonshine/"
cp "$cache/silero_vad.onnx" "$assets/silero_vad.onnx"
cp "$work/jniLibs/arm64-v8a/"*.so "$jni/"

if [[ -e "$assets/moonshine/test_wavs" ]] || find "$assets" "$jni" -name '*.onnx.json' | grep -q .; then
  echo "в комплект попали test_wavs или onnx.json" >&2
  exit 1
fi

while read -r size path; do
  if [[ "$size" -ge "$limit" ]]; then
    echo "файл не меньше 110 МиБ: $path ($size байт)" >&2
    exit 1
  fi
done < <(find "$assets" "$jni" -type f -printf '%s %p\n')
