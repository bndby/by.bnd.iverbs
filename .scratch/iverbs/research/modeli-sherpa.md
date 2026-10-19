# Какие модели Sherpa-ONNX вложить?

Ветки нет. Репозиторий не под git, поэтому ветку `research/` создать нельзя. Находка — этот файл.

Билет: [Какие модели Sherpa-ONNX вложить?](../issues/09-modeli-sherpa.md).

Страницы и файлы репозитория открыты 19 октября 2026. Факт ниже — только то, что стоит на странице источника, в файле репозитория `k2-fsa/sherpa-onnx` на ветке `master` в этот день или в размере ассета релиза. Точность на трёх коротких формах глагола не измерялась: документы такого замера не содержат, и этот файл его не подставляет. Какой комплект вкладывать в продукт, этот файл не выбирает.

МиБ в таблицах — размер ассета в байтах, делённый на 1024² и округлённый до одного знака. Байт — поле `size` ассета на релизе.

## Как файл попадает в Android и что сказано про сеть

Сборка Android описана и для распознавания, и для синтеза: тот же раздел, папка примера другая. Для синтеза в тексте названы `SherpaOnnxTts` и `SherpaOnnxTtsEngine`. ABI в разделе: `arm64-v8a`, `armv7-eabi` (в каталог `jniLibs` копируется как `armeabi-v7a`), `x86_64`, `x86`. В тексте: нужен один ABI, `arm64-v8a` назван самым обычным. Готовые библиотеки для релиза v1.13.8 — архив `sherpa-onnx-v1.13.8-android.tar.bz2`. ([Build sherpa-onnx for Android](https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html))

Модель кладётся в `android/SherpaOnnx/app/src/main/assets/` до сборки APK. Пример в этом разделе — потоковая модель `sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`, китайский и английский. После распаковки и удаления лишних файлов в каталоге остаются `decoder-epoch-99-avg-1.onnx` (в примере `ls` — 13M), `encoder-epoch-99-avg-1.onnx` (315M), `joiner-epoch-99-avg-1.onnx` (12M), `tokens.txt` (55K). Скрипт сборки APK качает архив на шаге сборки, не во время хода. ([Build sherpa-onnx for Android](https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html), [build-apk-tts.sh.in](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/build-apk-tts.sh.in), [build-apk-vad-asr.sh.in](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/build-apk-vad-asr.sh.in))

Готовый APK из этих скриптов содержит одну модель. Имя TTS-APK: `sherpa-onnx-{version}-{arch}-{lang}-tts-{model}.apk`. Имя VAD+ASR-APK: `sherpa-onnx-{version}-{arch}-vad_asr-{lang}-{model}.apk`. На открытых страницах списков APK версия в ссылках — 1.13.8, архитектуры `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`. Отдельного APK, в котором сразу русская озвучка, английская озвучка и английское распознавание, эти страницы и скрипты не называют. ([APKs for text-to-speech](https://k2-fsa.github.io/sherpa/onnx/tts/apk.html), [APKs for VAD + non-streaming speech recognition](https://k2-fsa.github.io/sherpa/onnx/vad/apk-asr.html))

Пример `SherpaOnnxTts`: `minSdk` 21, `versionName` `1.13.8`. Модель читается из `assets`. Каталог `espeak-ng-data`, если он задан, копируется из assets в каталог приложения на устройстве. Это копирование локальных файлов, не загрузка модели по сети. ([build.gradle](https://github.com/k2-fsa/sherpa-onnx/blob/master/android/SherpaOnnxTts/app/build.gradle), [MainActivity.kt](https://github.com/k2-fsa/sherpa-onnx/blob/master/android/SherpaOnnxTts/app/src/main/java/com/k2fsa/sherpa/onnx/MainActivity.kt))

Про распознавание на странице движка прямо: во время распознавания интернет не нужен, всё считается на устройстве. На странице Android то же для распознавания в реальном времени: интернет не нужен, всё на телефоне. Отдельной такой фразы про синтез на открытых страницах TTS нет. Синтез в этих страницах описан как локальные файлы модели и APK, в который модель уже входит. ([sherpa-onnx](https://k2-fsa.github.io/sherpa/onnx/index.html), [Android](https://k2-fsa.github.io/sherpa/onnx/android/index.html))

Код Next-gen Kaldi на страницах APK назван лицензией Apache-2.0. Там же: модели приходят из разных каркасов, лицензию выбранной модели надо смотреть отдельно. Страница образца модели эту лицензию модели не повторяет. ([APKs for text-to-speech](https://k2-fsa.github.io/sherpa/onnx/tts/apk.html), [APKs for VAD + non-streaming speech recognition](https://k2-fsa.github.io/sherpa/onnx/vad/apk-asr.html))

Каталог готовых моделей: распознавание — релиз `asr-models`, синтез — релиз `tts-models`, VAD — файл `silero_vad.onnx` на релизе `asr-models`. ([Pre-trained models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html))

## Русская озвучка

Индекс образцов называет пять имён. ([Russian](https://k2-fsa.github.io/sherpa/onnx/tts/all/Russian/index.html))

Четыре — VITS Piper. На странице образца: один диктор, 22050 Гц, архив на релизе `tts-models`. Скрипт сборки APK для piper задаёт файл модели как имя после префикса `vits-piper-` плюс `.onnx`, и каталог `espeak-ng-data` внутри каталога модели. На странице `dmitri` это `ru_RU-dmitri-medium.onnx`, `tokens.txt`, `espeak-ng-data`. Скрипт качает архив без суффикса `-int8` и без `-fp16`. Рядом на том же релизе для каждого из четырёх имён лежат такие архивы. Страница образца на них не ссылается. ([generate-tts-apk-script.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py), [tts-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models))

| Модель | Дикторы | Гц | Архив страницы, байт | МиБ | `-int8`, байт | МиБ | `-fp16`, байт | МиБ |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [vits-piper-ru_RU-denis-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/Russian/vits-piper-ru_RU-denis-medium.html) | 1 | 22050 | 67190991 | 64.1 | 21058905 | 20.1 | 35904400 | 34.2 |
| [vits-piper-ru_RU-dmitri-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/Russian/vits-piper-ru_RU-dmitri-medium.html) | 1 | 22050 | 67188551 | 64.1 | 21129441 | 20.2 | 35906518 | 34.2 |
| [vits-piper-ru_RU-irina-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/Russian/vits-piper-ru_RU-irina-medium.html) | 1 | 22050 | 67153308 | 64.0 | 21149417 | 20.2 | 35868633 | 34.2 |
| [vits-piper-ru_RU-ruslan-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/Russian/vits-piper-ru_RU-ruslan-medium.html) | 1 | 22050 | 67210684 | 64.1 | 21127907 | 20.1 | 35924102 | 34.3 |

Страница `dmitri` пишет, что модель конвертирована из `rhasspy/piper-voices`, путь `ru/ru_RU/dmitri/medium`, и показывает Android TTS Engine APK sherpa-onnx v1.13.8 для `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`. Исходники движка: `android/SherpaOnnxTtsEngine`. Карточка того голоса на Hugging Face: язык `ru_RU`, один диктор, samplerate 22050 Гц, лицензия датасета CC0. Шапка README `rhasspy/piper-voices`: `license: mit`, в списке языков есть `ru` и `en`. ([vits-piper-ru_RU-dmitri-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/Russian/vits-piper-ru_RU-dmitri-medium.html), [MODEL_CARD dmitri](https://huggingface.co/rhasspy/piper-voices/resolve/main/ru/ru_RU/dmitri/medium/MODEL_CARD), [piper-voices README](https://huggingface.co/rhasspy/piper-voices/raw/main/README.md))

Пятый пункт индекса — `supertonic-3-ru`. Та же страница образцов для английского, `supertonic-3-en`, ссылается на тот же архив `sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2`, 128774318 байт (122.8 МиБ). На обеих страницах: 10 дикторов, 24000 Гц, идентификаторы 0–9. Файлы в примере: `duration_predictor.int8.onnx`, `text_encoder.int8.onnx`, `vector_estimator.int8.onnx`, `vocoder.int8.onnx`, `tts.json`, `unicode_indexer.bin`, `voice.bin`. Скрипт сборки APK ставит один и тот же каталог модели на каждый язык из своего списка, включая `ru` и `en`, и подставляет код языка в `supertonicLang`. Один сгенерированный APK получает один код. ([supertonic-3-ru](https://k2-fsa.github.io/sherpa/onnx/tts/all/Russian/supertonic-3-ru.html), [supertonic-3-en](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/supertonic-3-en.html), [generate-tts-apk-script.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py))

## Английская озвучка

Индекс образцов перечисляет модели английского. Тип, файлы и упаковка ниже — со страницы образца и из скрипта APK. Скрипт качает архив, на который ссылается страница, и кладёт его в assets `SherpaOnnxTts`. ([English](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/index.html))

### Piper, VITS

Файл модели: имя после `vits-piper-` плюс `.onnx`. Рядом `tokens.txt` и `espeak-ng-data`. На открытых страницах у имён с суффиксом `-low` частота 16000 Гц, у `-medium` и `-high` — 22050 Гц. Число дикторов на странице разное. Код `en_GB` или `en_US` взят из имени модели на странице. Архив страницы — без суффикса. На релизе `tts-models` у каждого имени из этой таблицы есть ещё `-int8.tar.bz2` и `-fp16.tar.bz2`. Скрипт APK качает архив без этих суффиксов. ([generate-tts-apk-script.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py), [tts-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models))

| Модель | Код в имени | Дикторы | Гц | Архив страницы, байт | МиБ | `-int8`, байт | МиБ | `-fp16`, байт | МиБ |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [vits-piper-en_GB-alan-low](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-alan-low.html) | en_GB | 1 | 16000 | 67086942 | 64.0 | 21289969 | 20.3 | 35835596 | 34.2 |
| [vits-piper-en_GB-alan-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-alan-medium.html) | en_GB | 1 | 22050 | 67220121 | 64.1 | 21103831 | 20.1 | 35894428 | 34.2 |
| [vits-piper-en_GB-alba-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-alba-medium.html) | en_GB | 1 | 22050 | 67212349 | 64.1 | 21104326 | 20.1 | 35917308 | 34.3 |
| [vits-piper-en_GB-aru-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-aru-medium.html) | en_GB | 12 | 22050 | 80309222 | 76.6 | 23125986 | 22.1 | 42096585 | 40.1 |
| [vits-piper-en_GB-cori-high](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-cori-high.html) | en_GB | 1 | 22050 | 115574061 | 110.2 | 35038210 | 33.4 | 58620472 | 55.9 |
| [vits-piper-en_GB-cori-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-cori-medium.html) | en_GB | 1 | 22050 | 67257412 | 64.1 | 20768736 | 19.8 | 35960750 | 34.3 |
| [vits-piper-en_GB-dii-high](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-dii-high.html) | en_GB | 1 | 22050 | 67229442 | 64.1 | 21037620 | 20.1 | 35905738 | 34.2 |
| [vits-piper-en_GB-jenny_dioco-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-jenny_dioco-medium.html) | en_GB | 1 | 22050 | 67225842 | 64.1 | 20950036 | 20.0 | 35924431 | 34.3 |
| [vits-piper-en_GB-miro-high](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-miro-high.html) | en_GB | 1 | 22050 | 67194499 | 64.1 | 21347062 | 20.4 | 35861633 | 34.2 |
| [vits-piper-en_GB-northern_english_male-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-northern_english_male-medium.html) | en_GB | 1 | 22050 | 67210490 | 64.1 | 21100291 | 20.1 | 35885055 | 34.2 |
| [vits-piper-en_GB-semaine-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-semaine-medium.html) | en_GB | 4 | 22050 | 80241918 | 76.5 | 23227810 | 22.2 | 42002984 | 40.1 |
| [vits-piper-en_GB-southern_english_female-low](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-southern_english_female-low.html) | en_GB | 1 | 16000 | 67073626 | 64.0 | 21207615 | 20.2 | 35817072 | 34.2 |
| [vits-piper-en_GB-southern_english_female-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-southern_english_female-medium.html) | en_GB | 6 | 22050 | 80275090 | 76.6 | 23714066 | 22.6 | 42077474 | 40.1 |
| [vits-piper-en_GB-southern_english_male-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-southern_english_male-medium.html) | en_GB | 8 | 22050 | 80299212 | 76.6 | 23601346 | 22.5 | 42043651 | 40.1 |
| [vits-piper-en_GB-vctk-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_GB-vctk-medium.html) | en_GB | 109 | 22050 | 80488085 | 76.8 | 23366127 | 22.3 | 42270701 | 40.3 |
| [vits-piper-en_US-amy-low](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-amy-low.html) | en_US | 1 | 16000 | 67095344 | 64.0 | 21099246 | 20.1 | 35850818 | 34.2 |
| [vits-piper-en_US-amy-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-amy-medium.html) | en_US | 1 | 22050 | 67223746 | 64.1 | 21028122 | 20.1 | 35923746 | 34.3 |
| [vits-piper-en_US-arctic-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-arctic-medium.html) | en_US | 18 | 22050 | 80255511 | 76.5 | 23320524 | 22.2 | 42116993 | 40.2 |
| [vits-piper-en_US-bryce-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-bryce-medium.html) | en_US | 1 | 22050 | 67278133 | 64.2 | 20910568 | 19.9 | 35980236 | 34.3 |
| [vits-piper-en_US-danny-low](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-danny-low.html) | en_US | 1 | 16000 | 67107780 | 64.0 | 21184916 | 20.2 | 35828544 | 34.2 |
| [vits-piper-en_US-glados-high](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-glados-high.html) | en_US | 1 | 22050 | 115586982 | 110.2 | 35125025 | 33.5 | 58465308 | 55.8 |
| [vits-piper-en_US-hfc_female-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-hfc_female-medium.html) | en_US | 1 | 22050 | 67228166 | 64.1 | 21021379 | 20.0 | 35950347 | 34.3 |
| [vits-piper-en_US-hfc_male-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-hfc_male-medium.html) | en_US | 1 | 22050 | 67214049 | 64.1 | 20984819 | 20.0 | 35924983 | 34.3 |
| [vits-piper-en_US-joe-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-joe-medium.html) | en_US | 1 | 22050 | 67169394 | 64.1 | 21230019 | 20.2 | 35875903 | 34.2 |
| [vits-piper-en_US-john-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-john-medium.html) | en_US | 1 | 22050 | 67249181 | 64.1 | 20800096 | 19.8 | 35965743 | 34.3 |
| [vits-piper-en_US-kathleen-low](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-kathleen-low.html) | en_US | 1 | 16000 | 67118360 | 64.0 | 21289266 | 20.3 | 35865834 | 34.2 |
| [vits-piper-en_US-kristin-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-kristin-medium.html) | en_US | 1 | 22050 | 67259230 | 64.1 | 20882061 | 19.9 | 35953946 | 34.3 |
| [vits-piper-en_US-kusal-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-kusal-medium.html) | en_US | 1 | 22050 | 67219292 | 64.1 | 21044470 | 20.1 | 35899033 | 34.2 |
| [vits-piper-en_US-l2arctic-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-l2arctic-medium.html) | en_US | 24 | 22050 | 80314104 | 76.6 | 23273574 | 22.2 | 42145488 | 40.2 |
| [vits-piper-en_US-lessac-high](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-lessac-high.html) | en_US | 1 | 22050 | 115545841 | 110.2 | 35022847 | 33.4 | 58440833 | 55.7 |
| [vits-piper-en_US-lessac-low](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-lessac-low.html) | en_US | 1 | 16000 | 67097098 | 64.0 | 21070568 | 20.1 | 35809589 | 34.2 |
| [vits-piper-en_US-lessac-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-lessac-medium.html) | en_US | 1 | 22050 | 67230653 | 64.1 | 20969179 | 20.0 | 35955459 | 34.3 |
| [vits-piper-en_US-libritts-high](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-libritts-high.html) | en_US | 904 | 22050 | 131033598 | 125.0 | 36406088 | 34.7 | 66107085 | 63.0 |
| [vits-piper-en_US-libritts_r-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-libritts_r-medium.html) | en_US | 904 | 22050 | 82038311 | 78.2 | 23398348 | 22.3 | 43071574 | 41.1 |
| [vits-piper-en_US-ljspeech-high](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-ljspeech-high.html) | en_US | 1 | 22050 | 115817679 | 110.5 | 33886472 | 32.3 | 58732972 | 56.0 |
| [vits-piper-en_US-ljspeech-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-ljspeech-medium.html) | en_US | 1 | 22050 | 67169893 | 64.1 | 21090429 | 20.1 | 35830836 | 34.2 |
| [vits-piper-en_US-miro-high](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-miro-high.html) | en_US | 1 | 22050 | 67198472 | 64.1 | 21336806 | 20.3 | 35862796 | 34.2 |
| [vits-piper-en_US-norman-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-norman-medium.html) | en_US | 1 | 22050 | 67203672 | 64.1 | 20987233 | 20.0 | 35897612 | 34.2 |
| [vits-piper-en_US-reza_ibrahim-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-reza_ibrahim-medium.html) | en_US | 1 | 22050 | 67214755 | 64.1 | 21139277 | 20.2 | 35897244 | 34.2 |
| [vits-piper-en_US-ryan-high](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-ryan-high.html) | en_US | 1 | 22050 | 115630708 | 110.3 | 34473341 | 32.9 | 58520438 | 55.8 |
| [vits-piper-en_US-ryan-low](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-ryan-low.html) | en_US | 1 | 16000 | 67100179 | 64.0 | 21212659 | 20.2 | 35839979 | 34.2 |
| [vits-piper-en_US-ryan-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-ryan-medium.html) | en_US | 1 | 22050 | 67213100 | 64.1 | 21083446 | 20.1 | 35928701 | 34.3 |
| [vits-piper-en_US-sam-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-sam-medium.html) | en_US | 1 | 22050 | 67249919 | 64.1 | 20874946 | 19.9 | 35924166 | 34.3 |

`reza_ibrahim-medium` и `sam-medium` есть на индексе English и на странице образца есть блок Android APK. В прочитанном списке `get_piper_models()` этих двух имён нет.

### Не Piper, страница образца открыта

| Модель | Тип в скрипте | Дикторы | Гц | Файлы, которые задаёт скрипт или страница | Архив | Байт | МиБ |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [kitten-nano-en-v0_1](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-nano-en-v0_1.html) | kitten | 8 | 24000 | `model.fp16.onnx`, `voices.bin`, `espeak-ng-data` | `kitten-nano-en-v0_1-fp16.tar.bz2` | 26855312 | 25.6 |
| [kitten-nano-en-v0_2](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-nano-en-v0_2.html) | kitten | 8 | 24000 | `model.fp16.onnx`, `voices.bin`, `espeak-ng-data` | `kitten-nano-en-v0_2-fp16.tar.bz2` | 26586708 | 25.4 |
| [kitten-mini-en-v0_1](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-mini-en-v0_1.html) | kitten | 8 | 24000 | `model.fp16.onnx`, `voices.bin`, `espeak-ng-data` | `kitten-mini-en-v0_1-fp16.tar.bz2` | 156630103 | 149.4 |
| [kitten-nano-en-v0_8-fp32](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-nano-en-v0_8-fp32.html) | kitten | 8 | 24000 | `model.fp32.onnx`, `voices.bin`, `espeak-ng-data` | `kitten-nano-en-v0_8-fp32.tar.bz2` | 63815222 | 60.9 |
| [kitten-nano-en-v0_8-int8](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-nano-en-v0_8-int8.html) | kitten | 8 | 24000 | `model.int8.onnx`, `voices.bin`, `espeak-ng-data` | `kitten-nano-en-v0_8-int8.tar.bz2` | 31220690 | 29.8 |
| [kitten-micro-en-v0_8](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-micro-en-v0_8.html) | kitten | 8 | 24000 | `model.onnx`, `voices.bin`, `espeak-ng-data` | `kitten-micro-en-v0_8.tar.bz2` | 44423643 | 42.4 |
| [kitten-mini-en-v0_8](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-mini-en-v0_8.html) | kitten | 8 | 24000 | `model.onnx`, `voices.bin`, `espeak-ng-data` | `kitten-mini-en-v0_8.tar.bz2` | 67547594 | 64.4 |
| [kokoro-en-v0_19](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kokoro-en-v0_19.html) | kokoro | 11 | 24000 | `model.onnx`, `voices.bin`, `tokens.txt`, `espeak-ng-data` | `kokoro-en-v0_19.tar.bz2` | 319625534 | 304.8 |
| [matcha-icefall-en_US-ljspeech](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/matcha-icefall-en_US-ljspeech.html) | matcha | 1 | 22050 | `model-steps-3.onnx`, `espeak-ng-data`, отдельно `vocos-22khz-univ.onnx` | `matcha-icefall-en_US-ljspeech.tar.bz2` | 76713830 | 73.2 |
| [vits-inflect-en-nano-v2](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-inflect-en-nano-v2.html) | vits | 1 | 24000 | `model.onnx`, `espeak-ng-data` | `vits-inflect-en-nano-v2.tar.bz2` | 22429639 | 21.4 |
| [vits-inflect-en-micro-v2](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-inflect-en-micro-v2.html) | vits | 1 | 24000 | `model.onnx`, `espeak-ng-data` | `vits-inflect-en-micro-v2.tar.bz2` | 42956555 | 41.0 |
| [supertonic-3-en](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/supertonic-3-en.html) | supertonic | 10 | 24000 | те же семь файлов, что у русской страницы | `sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2` | 128774318 | 122.8 |

Вокодер Matcha скрипт качает отдельно с релиза `vocoder-models` и кладёт в корень assets, не в каталог модели. `vocos-22khz-univ.onnx` — 53884024 байт (51.4 МиБ). В комментарии примера в `MainActivity.kt` то же: вокодер в корне ресурсов. ([build-apk-tts.sh.in](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/build-apk-tts.sh.in), [vocoder-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/vocoder-models), [MainActivity.kt](https://github.com/k2-fsa/sherpa-onnx/blob/master/android/SherpaOnnxTts/app/src/main/java/com/k2fsa/sherpa/onnx/MainActivity.kt))

Имена дикторов Kokoro на странице, sid → имя: 0 `af`, 1 `af_bella`, 2 `af_nicole`, 3 `af_sarah`, 4 `af_sky`, 5 `am_adam`, 6 `am_michael`, 7 `bf_emma`, 8 `bf_isabella`, 9 `bm_george`, 10 `bm_lewis`. ([kokoro-en-v0_19](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kokoro-en-v0_19.html))

Имена восьми дикторов Kitten и дикторов многоголосых Piper в этот файл не перенесены: страница даёт число.

### В скрипте APK как английские, страницы на индексе English нет

Адрес `tts/all/English/{имя}.html` 19 октября 2026 ответил 404. Частота и число дикторов с такой страницы не взяты. Скрипт помечает их `lang` `en` и качает архив с релиза `tts-models`.

| Каталог в скрипте | Файлы в скрипте | Архив, байт | МиБ |
| --- | --- | --- | --- |
| `vits-piper-en_US-glados` | `{имя}.onnx`, `espeak-ng-data` | 67208137 | 64.1 |
| `vits-piper-en_GB-sweetbbak-amy` | `{имя}.onnx`, `espeak-ng-data` | 115595629 | 110.2 |
| `vits-coqui-en-ljspeech` | `model.onnx`, `espeak-ng-data` | 115418583 | 110.1 |
| `vits-coqui-en-ljspeech-neon` | `model.onnx`, `espeak-ng-data` | 115418180 | 110.1 |
| `vits-coqui-en-vctk` | `model.onnx`, `espeak-ng-data` | 122412029 | 116.7 |
| `vits-vctk` | `vits-vctk.onnx`, `lexicon.txt` | 151758892 | 144.7 |
| `kokoro-multi-lang-v1_0` | `model.onnx`, `voices.bin`, `espeak-ng-data`, лексиконы `en` и `zh` | 349906910 | 333.7 |
| `kokoro-multi-lang-v1_1` | то же, `model.onnx` | 364816464 | 347.9 |
| `kokoro-int8-multi-lang-v1_1` | то же, `model.int8.onnx` | 147031220 | 140.2 |

У трёх Kokoro в скрипте два языка: `en` и `zh`. ([generate-tts-apk-script.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py))

## Английское распознавание

Пример `SherpaOnnxVadAsr` пишет с микрофона на 16000 Гц и читает модель из assets. Рядом скрипт кладёт `silero_vad.onnx` с релиза `asr-models`, 643854 байт (0.6 МиБ). На страницах моделей NeMo и Moonshine: декодируются одноканальные 16-битные wav, частота файла не обязана быть 16 кГц. В примере Moonshine v2 на странице ресемплер показывает вход 24000 и выход 16000. ([MainActivity.kt VadAsr](https://github.com/k2-fsa/sherpa-onnx/blob/master/android/SherpaOnnxVadAsr/app/src/main/java/com/k2fsa/sherpa/onnx/MainActivity.kt), [build-apk-vad-asr.sh.in](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/build-apk-vad-asr.sh.in), [Moonshine v2](https://k2-fsa.github.io/sherpa/onnx/moonshine/models-v2.html))

Скрипт непотоковых APK — `generate-vad-asr-apk-script.py`. Скрипт потоковых APK — `generate-asr-apk-script.py`. Оба качают `{model_name}.tar.bz2` с релиза `asr-models` в assets. Ниже только те записи, где скрипт или страница прямо называют английский.

### Только английский, непотоковый APK с VAD

| Архив, который качает скрипт | Что страница говорит про язык | Файлы, которые называет страница | Байт | МиБ |
| --- | --- | --- | --- | --- |
| `sherpa-onnx-whisper-tiny.en.tar.bz2` | таблица VAD+ASR: only English | `tiny.en-encoder.int8.onnx` 12M, `tiny.en-decoder.int8.onnx` 105M, плюс пара без `int8`: encoder 36M, decoder 185M, токены `tiny.en-tokens.txt`. Скрипт перед APK удаляет два файла без `int8` | 118071777 | 112.6 |
| `sherpa-onnx-nemo-fast-conformer-ctc-en-24500.tar.bz2` | таблица VAD+ASR: only English; там же: обучена на 8500 часах | список файлов на отдельной открытой странице в этот заход не выписан | 431106517 | 411.1 |
| `sherpa-onnx-moonshine-tiny-en-int8.tar.bz2` | Moonshine v1: supports only English | `preprocess.onnx`, `encode.int8.onnx`, `uncached_decode.int8.onnx`, `cached_decode.int8.onnx`, `tokens.txt` | 107600538 | 102.6 |
| `sherpa-onnx-moonshine-base-en-int8.tar.bz2` | та же страница, only English | те же пять имён в каталоге `base` | 250807309 | 239.2 |
| `sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2` | English; пунктуация и регистр; конвертирована из `nvidia/parakeet-tdt-0.6b-v2` | `encoder.int8.onnx` 622M, `decoder.int8.onnx` 6.9M, `joiner.int8.onnx` 1.7M, `tokens.txt` 9.2K | 482468385 | 460.1 |
| `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2` | страница English | `model.int8.onnx`, `tokens.txt` | 104337827 | 99.5 |
| `sherpa-onnx-medasr-ctc-en-int8-2025-12-25.tar.bz2` | offline English CTC; в скрипте `lang2` — 英语 | `model.int8.onnx`, `tokens.txt` | 131831040 | 125.7 |
| `sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27.tar.bz2` | Moonshine v2, пометка English | страница называет `tokens.txt`; полный список onnx в этот файл не перенесён | 29858559 | 28.5 |
| `sherpa-onnx-moonshine-base-en-quantized-2026-02-27.tar.bz2` | Moonshine v2, пометка English | страница называет `tokens.txt` | 111266225 | 106.1 |
| `sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming.tar.bz2` | English | `encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx`, `tokens.txt` | 501350460 | 478.1 |

Источники строк: [apk-asr](https://k2-fsa.github.io/sherpa/onnx/vad/apk-asr.html), [generate-vad-asr-apk-script.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-vad-asr-apk-script.py), [tiny.en](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/tiny.en.html), [Moonshine v1](https://k2-fsa.github.io/sherpa/onnx/moonshine/models.html), [Moonshine v2](https://k2-fsa.github.io/sherpa/onnx/moonshine/models-v2.html), [NeMo transducer](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html), [NeMo CTC English](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-ctc/nemo/english.html), [MedASR](https://k2-fsa.github.io/sherpa/onnx/medasr/index.html), [asr-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models).

Карточка `nvidia/parakeet-tdt-0.6b-v2`, на которую ссылается страница sherpa: в шапке README `license: cc-by-4.0`, язык `en`. ([parakeet-tdt-0.6b-v2 README](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2/raw/main/README.md))

У `fast-conformer-ctc-en-24500` на том же релизе есть архив с суффиксом `-int8`, 104402565 байт (99.6 МиБ). Скрипт качает имя без `-int8`.

### Только английский, потоковый APK

Скрипт `generate-asr-apk-script.py`, поле `lang` равно `en`. Размер — архив, который качает это имя.

| Архив | Байт | МиБ | Что скрипт удаляет перед APK |
| --- | --- | --- | --- |
| `sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2` | 310414022 | 296.0 | `encoder-epoch-99-avg-1-chunk-16-left-128.onnx`, `decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx`, `joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx` |
| `sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2` | 127887156 | 122.0 | `encoder-epoch-99-avg-1.onnx`, `decoder-epoch-99-avg-1.int8.onnx`, `joiner-epoch-99-avg-1.onnx` |
| `sherpa-onnx-nemo-streaming-fast-conformer-ctc-en-80ms.tar.bz2` | 432030807 | 412.0 | каталог `test_wavs` |
| `sherpa-onnx-nemo-streaming-fast-conformer-ctc-en-480ms.tar.bz2` | 432119316 | 412.1 | каталог `test_wavs` |
| `sherpa-onnx-nemo-streaming-fast-conformer-ctc-en-1040ms.tar.bz2` | 432042787 | 412.0 | каталог `test_wavs` |
| `sherpa-onnx-streaming-zipformer-en-kroko-2025-08-06.tar.bz2` | 57267600 | 54.6 | каталог `test_wavs` |
| `sherpa-onnx-nemotron-speech-streaming-en-0.6b-int8-2026-01-14.tar.bz2` | 468994262 | 447.3 | каталог `test_wavs` |
| `sherpa-onnx-nemotron-speech-streaming-en-0.6b-80ms-int8-2026-04-25.tar.bz2` | 463945379 | 442.5 | каталог `test_wavs` |
| `sherpa-onnx-nemotron-speech-streaming-en-0.6b-160ms-int8-2026-04-25.tar.bz2` | 463945198 | 442.5 | каталог `test_wavs` |
| `sherpa-onnx-nemotron-speech-streaming-en-0.6b-560ms-int8-2026-04-25.tar.bz2` | 463945051 | 442.5 | каталог `test_wavs` |
| `sherpa-onnx-nemotron-speech-streaming-en-0.6b-1120ms-int8-2026-04-25.tar.bz2` | 463945058 | 442.5 | каталог `test_wavs` |

Полный список файлов, которые остаются после удаления, страница в этот заход не приведена. У трёх потоковых fast-conformer на релизе есть парный архив `-int8`: 99459493, 102616827 и 100366946 байт, в том же порядке, что 80 мс, 480 мс и 1040 мс. Скрипт качает имя без `-int8`. ([generate-asr-apk-script.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-asr-apk-script.py), [asr-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models))

### Английский назван среди других языков

Эти архивы тоже входят в Android-скрипт непотокового APK, и страница или скрипт называют английский одним из языков. Это не пометка «only English».

| Архив | Языки на странице или в скрипте | Байт | МиБ |
| --- | --- | --- | --- |
| `sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2` | китайский и английский; это пример в разделе сборки Android | 511274346 | 487.6 |
| `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2` | китайский, английский, японский, корейский, кантонский. В примере: `model.int8.onnx`, `tokens.txt` | 163002883 | 155.5 |
| `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2` | те же пять | 165783878 | 158.1 |
| `sherpa-onnx-paraformer-zh-2023-09-14.tar.bz2` | в скрипте `Chinese,English` | 234051698 | 223.2 |
| `sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2` | в скрипте `Chinese,English` | 77920048 | 74.3 |
| `sherpa-onnx-zipformer-zh-en-2023-11-22.tar.bz2` | в скрипте `Chinese,English` | 312398028 | 297.9 |
| `sherpa-onnx-nemo-fast-conformer-ctc-en-de-es-fr-14288.tar.bz2` | английский, немецкий, испанский, французский | 434219034 | 414.1 |
| `sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k.tar.bz2` | десять языков, среди них английский и русский | 434978386 | 414.8 |
| `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2` | 25 европейских, список на странице включает English (`en`) и Russian (`ru`) | 487170055 | 464.6 |
| `sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10.tar.bz2` | в скрипте `中英粤` | 117203117 | 111.8 |

([generate-vad-asr-apk-script.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-vad-asr-apk-script.py), [generate-asr-apk-script.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-asr-apk-script.py), [apk-asr](https://k2-fsa.github.io/sherpa/onnx/vad/apk-asr.html), [SenseVoice](https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html), [NeMo transducer](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html), [Build sherpa-onnx for Android](https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html))

Скрипт также пакует модели с подписью `1600_languages`, `52_lang`, `31_languages`, `multi_lang`. Открытая в этот заход страница не перечисляет английский внутри этих подписей, поэтому они в таблицы выше не поставлены.

## Чего документы не закрывают

Точность на трёх коротких формах хода не измерена. Ни размер, ни частота, ни подпись «English» этого замера не заменяют.

Готовые APK и скрипты их сборки кладут в пакет одну модель. Страница не показывает один APK, в котором уже лежат русская озвучка, английская озвучка и английское распознавание. Механизм один и тот же: файлы архива в `assets` до `assembleRelease`. Два экземпляра синтеза и один распознаватель в одном примере не собраны.

Страница TTS не повторяет фразу «интернет не нужен», которая стоит на страницах распознавания. Описаны локальные файлы и APK, в который модель входит на сборке.

Лицензию модели страница Sherpa не называет, кроме отсылки «проверьте лицензию выбранной модели» и Apache-2.0 для кода. Для piper и для parakeet-tdt-0.6b-v2 лицензия прочитана на карточке, на которую страница Sherpa ссылается. Для остальных моделей этого захода такая карточка не открывалась. У Moonshine в списке файлов архива есть `LICENSE`; текст лицензии страница не цитирует.

Архивы piper `-int8` и `-fp16` на релизе есть. Скрипт Android и страница образца качают архив без этого суффикса. Страница не пишет, что уменьшенный архив подставляется в пример APK тем же путём.
