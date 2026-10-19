# Какая речь на Android работает без сети

Ветки нет: репозиторий не под git, ветку `research/` создать нельзя. Находка — только этот файл.

Билет: [Какая речь на Android работает без сети?](../issues/02-rech-bez-seti.md).

Ход озвучивает русское значение и английские три формы, затем слушает английскую фразу ученика до вердикта. Учёток нет. Ниже факты платформы и трёх встраиваемых движков. Движок за продукт не выбран: это вход для билета [Обязан ли ход проходить без сети?](../issues/04-hod-bez-seti.md).

Context7 по библиотеке `/websites/developer_android` справку `TextToSpeech` не вернул. Утверждения про платформу взяты со страниц Android Developers и из Android 16 Compatibility Definition. Утверждения про движки взяты с их собственных страниц.

## Озвучка русского

`TextToSpeech` синтезирует речь из текста установленным движком. `setLanguage(Locale)` берёт ближайшее совпадение и не обещает ту же локаль; перед фразой справка велит вызвать `isLanguageAvailable`. Возврат: язык доступен (`LANG_AVAILABLE`, `LANG_COUNTRY_AVAILABLE`, `LANG_COUNTRY_VAR_AVAILABLE`), данных языка нет (`LANG_MISSING_DATA`) или язык не поддерживается (`LANG_NOT_SUPPORTED`). Сбой сети — `ERROR_NETWORK` и `ERROR_NETWORK_TIMEOUT`. Недокачанные данные голоса — `ERROR_NOT_INSTALLED_YET`. ([TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech))

С API 21 голос берут из `getVoices()` и ставят через `setVoice`. `Voice.isNetworkConnectionRequired()` отвечает, нужна ли голосу сеть. Устаревший ключ `KEY_FEATURE_EMBEDDED_SYNTHESIS`: если движок его поддерживает, синтез идёт на устройстве, без сетевых запросов. С API 21 тот же выбор — голос, у которого `isNetworkConnectionRequired()` ложно. `KEY_FEATURE_NETWORK_SYNTHESIS` и голос с сетью — синтез через сеть. Ключ `KEY_FEATURE_NOT_INSTALLED`: голосу ещё нужно скачать данные; скачивание запускают `setVoice` или `setLanguage`; до конца загрузки запрос сообщает `ERROR_NOT_INSTALLED_YET` или уходит в другой голос. ([Voice](https://developer.android.com/reference/android/speech/tts/Voice), [TextToSpeech.Engine](https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine))

`ACTION_INSTALL_TTS_DATA` открывает установку файлов, без которых TTS не работает. Ученик может прервать или отказаться. После возврата из этого экрана приложение не должно считать данные установленными и при необходимости проверяет `ACTION_CHECK_TTS_DATA`. ([TextToSpeech.Engine](https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine))

Справка не называет русский язык обязательным и не публикует список голосов образа системы.

Android 16 CDD, обновление 2 декабря 2025. Устройство с `android.hardware.audio.output` обязано поддерживать API TTS (`[C-1-1]`). Наладонное устройство обязано позволять ставить сторонние движки TTS (`[3.11/H-0-1]`). Включить движок для языков, которые уже есть на устройстве, сильно рекомендуется (`[3.11/H-SR-1]`). Русское значение в требованиях не названо. ([Android 16 CDD, раздел 3.11](https://source.android.com/docs/compatibility/16/android-16-cdd))

Sherpa-ONNX публикует русскую модель `vits-piper-ru_RU-dmitri-medium`: архив с GitHub Releases, локальные файлы `ru_RU-dmitri-medium.onnx`, `tokens.txt`, `espeak-ng-data`. Для sherpa-onnx v1.13.8 есть Android TTS Engine APK с этой моделью (arm64-v8a, armeabi-v7a, x86_64, x86). Исходники APK: `android/SherpaOnnxTtsEngine`. Отдельной фразы «синтез не ходит в сеть» на странице нет: описаны скачанные файлы модели и APK, в который модель входит. ([vits-piper-ru_RU-dmitri-medium](https://k2-fsa.github.io/sherpa/onnx/tts/all/Russian/vits-piper-ru_RU-dmitri-medium.html))

README Vosk описывает офлайн-распознавание, не синтез. README whisper.cpp описывает распознавание речи Whisper, не синтез. Озвучку русского значения эти два документа не закрывают. ([Vosk README](https://github.com/alphacep/vosk-api/blob/master/README.md), [whisper.cpp README](https://github.com/ggml-org/whisper.cpp/blob/master/README.md))

## Озвучка английского

Тот же `TextToSpeech`: локаль английского, `isLanguageAvailable`, голос с `isNetworkConnectionRequired() == false`. Коды отсутствия данных, недокачанного голоса и сетевого сбоя те же. Английский голос справочник тоже не обещает на каждом образе. ([TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [Voice](https://developer.android.com/reference/android/speech/tts/Voice))

Sherpa-ONNX публикует английскую модель `vits-piper-en_US-amy-low` тем же способом: архив на Releases и Android TTS Engine APK v1.13.8 с этой моделью. ([vits-piper-en_US-amy-low](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-amy-low.html))

Vosk и whisper.cpp синтез английских трёх форм в прочитанных README не описывают.

## Распознавание английской фразы

`SpeechRecognizer` ходит в службу распознавания. Справка пишет: реализация этого API, скорее всего, отправляет звук на удалённые серверы; для непрерывного распознавания API не предназначен. Приложению нужно разрешение `RECORD_AUDIO`. ([SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer))

Язык передаётся тегом BCP 47 в `RecognizerIntent.EXTRA_LANGUAGE`. Пример в справке — `"en-US"`. Это пример формата тега, не обещание, что английский есть на устройстве. ([RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent))

`EXTRA_PREFER_OFFLINE` просит только офлайн-движок. По умолчанию false: допустимы и сеть, и офлайн. Дальше справка пишет: в зависимости от реализации распознавателя эти значения могут не подействовать. ([RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent))

С API 31 есть отдельный он-девайс распознаватель. `createOnDeviceSpeechRecognizer` бросает `UnsupportedOperationException`, если `isOnDeviceRecognitionAvailable` ложно. В том же API появились `ERROR_LANGUAGE_NOT_SUPPORTED` (язык этому распознавателю недоступен) и `ERROR_LANGUAGE_UNAVAILABLE` (язык поддерживается, но сейчас недоступен, например ещё не скачан). Рядом остаются `ERROR_NETWORK`, `ERROR_NETWORK_TIMEOUT` и `ERROR_INSUFFICIENT_PERMISSIONS`. ([SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [API diff 31](https://developer.android.com/sdk/api_diff/31/changes/android.speech.SpeechRecognizer))

С API 33 `checkRecognitionSupport` возвращает `RecognitionSupport`. `getInstalledOnDeviceLanguages` — язык уже можно слушать на устройстве. `getSupportedOnDeviceLanguages` — язык поддерживается, но его надо скачать (`triggerModelDownload`). `getPendingOnDeviceLanguages` — скачивание запланировано. `getOnlineLanguages` — язык доступен удалённо; он-девайс распознаватель ожидаемо возвращает пустой список. `triggerModelDownload` добавлен в API 33, вариант со слушателем — в API 34. Скачивание может спросить ученика. Если модель уже есть, слушатель сразу получает успех. Ошибка сети или планирования приходит в `onError`. ([RecognitionSupport](https://developer.android.com/reference/android/speech/RecognitionSupport), [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer))

CDD не обязывает каждое наладонное устройство иметь он-девайс распознавание английской фразы. `createOnDeviceSpeechRecognizer` там упомянут условно: если устройство само поддерживает обнаружение ключевого слова, звук с микрофона можно отдать этой он-девайс службе. Это не требование положить английскую модель в образ. ([Android 16 CDD](https://source.android.com/docs/compatibility/16/android-16-cdd))

Sherpa-ONNX. В документации прямо: во время распознавания интернет не нужен, всё считается на устройстве. Сборка описана и для Android. Готовые APK с VAD и непотоковым распознаванием включают модели только для английского, среди них `vad_asr-en-whisper_tiny` и `vad_asr-en-parakeet_tdt_0.6b_v2`; в таблице у обеих комментарий «It supports only English». Файлы моделей качаются с релиза `asr-models` и входят в APK. ([sherpa-onnx](https://k2-fsa.github.io/sherpa/onnx/index.html), [APKs for VAD + non-streaming speech recognition](https://k2-fsa.github.io/sherpa/onnx/vad/apk-asr.html))

Vosk. README: offline open source speech recognition toolkit, больше 20 языков, в списке English и Russian, масштаб до Android-смартфона. Каталог моделей: `vosk-model-small-en-us-0.15`, 40M, пометка «Lightweight wideband model for Android and RPi». Android-библиотека распаковывает модель из assets в хранилище приложения и пишет с микрофона через `AudioRecord` на 16000 Гц. Синтеза README не описывает. ([Vosk README](https://github.com/alphacep/vosk-api/blob/master/README.md), [Vosk Models](https://alphacephei.com/vosk/models), [StorageService.java](https://github.com/alphacep/vosk-api/blob/master/android/lib/src/main/java/org/vosk/android/StorageService.java), [SpeechService.java](https://github.com/alphacep/vosk-api/blob/master/android/lib/src/main/java/org/vosk/android/SpeechService.java))

whisper.cpp. README: локальный вывод модели распознавания Whisper на C/C++, Android есть в списке примеров. Модель скачивается файлом ggml; пример команды — `base.en`. Фраза «fully offline, on-device» в README относится к примеру на iPhone 13. Android-пример: выбрать модель из каталога, положить в `app/src/main/assets/models`, для устройства рекомендует tiny или base, собрать release. Шаги примера распознают файл из assets, живой микрофон не описан. Синтеза README не описывает. ([whisper.cpp README](https://github.com/ggml-org/whisper.cpp/blob/master/README.md), [whisper.android README](https://github.com/ggml-org/whisper.cpp/blob/master/examples/whisper.android/README.md))

## Что должно быть установлено заранее

Озвучка значения и трёх форм через платформу:

- Движок TTS. CDD требует API и возможность поставить сторонний движок, а не конкретный голос в образе. ([Android 16 CDD](https://source.android.com/docs/compatibility/16/android-16-cdd))
- Данные голоса для русской локали и отдельно для английской. Пока их нет, `isLanguageAvailable` возвращает `LANG_MISSING_DATA`, а говорящий запрос — `ERROR_NOT_INSTALLED_YET`. Ставит их `ACTION_INSTALL_TTS_DATA` или скачивание из `setLanguage` / `setVoice`. ([TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [TextToSpeech.Engine](https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine))
- Для озвучки без сети — голос, у которого `isNetworkConnectionRequired()` ложно, и для значения, и для трёх форм. ([Voice](https://developer.android.com/reference/android/speech/tts/Voice))

Распознавание английской фразы через платформу:

- Служба распознавания: `isRecognitionAvailable`.
- Разрешение `RECORD_AUDIO`.
- Для слушания без сети — `isOnDeviceRecognitionAvailable` истинно и английский тег уже в `getInstalledOnDeviceLanguages`. Иначе модель качает `triggerModelDownload`, и это скачивание само идёт по сети и может спросить ученика. ([SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [RecognitionSupport](https://developer.android.com/reference/android/speech/RecognitionSupport))

Если на образе этого нет, встраиваемые модели ставят до хода:

- Sherpa-ONNX: файлы русской TTS-модели, английской TTS-модели и английской ASR-модели на устройстве, в том числе внутри APK.
- Vosk: английская модель на устройстве. Для телефона каталог называет `vosk-model-small-en-us-0.15`. Озвучку этот комплект не даёт.
- whisper.cpp: ggml-модель в assets Android-примера. Озвучку этот комплект не даёт. Пример не описывает микрофон.

## Когда без сети ход невозможен

Ход доходит до вердикта, только если озвучка значения и трёх форм прошла и английская фраза распознана. Без сети это обрывается в таких случаях.

1. Обычный `SpeechRecognizer` по справке скорее шлёт звук на сервер. Без сети служба отвечает `ERROR_NETWORK` или `ERROR_NETWORK_TIMEOUT`. `EXTRA_PREFER_OFFLINE` реализация может проигнорировать.
2. Он-девайс службы нет: `isOnDeviceRecognitionAvailable` ложно, `createOnDeviceSpeechRecognizer` бросает `UnsupportedOperationException`.
3. Английский для он-девайс распознавателя есть в `getSupportedOnDeviceLanguages`, но не в `getInstalledOnDeviceLanguages`. Слушание даёт `ERROR_LANGUAGE_UNAVAILABLE`. Скачать модель без сети нельзя: слушатель `triggerModelDownload` получает ошибку сети или планирования.
4. Этот распознаватель английский не поддерживает: `ERROR_LANGUAGE_NOT_SUPPORTED`.
5. Голос русского значения или английских трёх форм требует сеть, либо данные голоса не стоят (`LANG_MISSING_DATA`, `ERROR_NOT_INSTALLED_YET`). Докачать без сети нельзя. Отказ в `ACTION_INSTALL_TTS_DATA` оставляет данные неустановленными.
6. Нет разрешения микрофона: `ERROR_INSUFFICIENT_PERMISSIONS`. Это не сеть. Слушание через `SpeechRecognizer` при этом не стартует.
7. Файла встраиваемой модели на устройстве нет. Сеть в момент хода этим движкам по их документам не нужна для уже лежащей модели: Sherpa-ONNX прямо говорит это про распознавание; Vosk называет себя offline; whisper.cpp пишет «fully offline» про пример на iPhone, а Android-пример читает файл модели из assets. Если файла нет и скачать его нечем, хода нет.

## Открытые дыры для следующего решения

Справки Android не обещают, что на конкретном образе уже стоят русский голос без сети, английский голос без сети и скачанная английская модель распознавания. Это свойство устройства, не контракт платформы.

Движок не выбран. Платформенный путь без сети держится на уже установленных данных. Документы Sherpa-ONNX покрывают русскую озвучку, английскую озвучку и распознавание английской фразы, если модели лежат на устройстве до хода. Документы Vosk и whisper.cpp покрывают распознавание. Обязанность хода пройти без сети решает билет `04-hod-bez-seti.md`.

Точность на трёх коротких формах документами не измерена. Цифры Vosk на странице моделей — word error rate на librispeech и других корпусах, не на ходе Учим или Проверка.

Живой микрофон в Android-примере whisper.cpp не описан. Отдельной фразы Sherpa-ONNX, что синтез не ходит в сеть, на страницах моделей нет.

Что ученик видит, если микрофон не разрешён, голоса нет или распознавание не стартовало, из этих фактов ещё не следует. «Голоса нет» на системном `TextToSpeech` — недостающие данные, которые качаются. У APK Sherpa-ONNX модель уже внутри. Пока не решено, обязан ли ход пройти без сети, экран сбоя формулировать рано.
