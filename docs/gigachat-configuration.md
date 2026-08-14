# Настройка GigaChat

Приложение использует `chat.giga:gigachat-java:0.1.22` и создаёт ровно один SDK-клиент. Способ подключения выбирается обязательной переменной `GIGACHAT_AUTH_MODE`: `certificate` или `user-password`. Модель всегда задаётся через `GIGACHAT_MODEL`; значения по умолчанию нет.

## Username/password

```powershell
$env:GIGACHAT_AUTH_MODE="user-password"
$env:GIGACHAT_MODEL="GigaChat-2-Max"
$env:GIGACHAT_USER_PASSWORD_API_URL="https://<completion-api-base-url>"
$env:GIGACHAT_AUTH_API_URL="https://<authentication-api-url>"
$env:GIGACHAT_USERNAME="<username>"
$env:GIGACHAT_PASSWORD="<password>"
$env:GIGACHAT_SCOPE="GIGACHAT_API_CORP"

.\mvnw.cmd spring-boot:run
```

`GIGACHAT_SCOPE` принимает только `GIGACHAT_API_PERS`, `GIGACHAT_API_B2B` или `GIGACHAT_API_CORP`. Оба URL должны быть абсолютными HTTP(S) URL.

Реальный strict structured completion для этой схемы подключения запускается отдельно:

```powershell
.\mvnw.cmd verify -Pgigachat-user-password-smoke
```

## PKCS12-сертификаты

Нужны два отдельных читаемых PKCS12-файла: клиентский keystore и truststore.

```powershell
$env:GIGACHAT_AUTH_MODE="certificate"
$env:GIGACHAT_MODEL="GigaChat-2-Max"
$env:GIGACHAT_CERTIFICATE_API_URL="https://<completion-api-base-url>"
$env:GIGACHAT_KEY_STORE_PATH="C:\secrets\gigachat-client.p12"
$env:GIGACHAT_KEY_STORE_PASSWORD="<keystore-password>"
$env:GIGACHAT_TRUST_STORE_PATH="C:\secrets\gigachat-trust.p12"
$env:GIGACHAT_TRUST_STORE_PASSWORD="<truststore-password>"

.\mvnw.cmd spring-boot:run
```

Реальный smoke-тест сертификатного подключения:

```powershell
.\mvnw.cmd verify -Pgigachat-certificate-smoke
```

## Общие параметры

| Переменная | Default | Назначение |
| --- | --- | --- |
| `GIGACHAT_CONNECT_TIMEOUT` | `15s` | Таймаут установления соединения |
| `GIGACHAT_READ_TIMEOUT` | `120s` | Таймаут чтения ответа |
| `GIGACHAT_VERIFY_SSL_CERTS` | `true` | Проверка SSL-сертификатов в режиме `certificate` |
| `GIGACHAT_AUTH_RETRIES` | `1` | Число повторов при ошибке авторизации |

В режиме `certificate` проверка SSL включена по умолчанию и управляется `GIGACHAT_VERIFY_SSL_CERTS`. В режиме `user-password` проверка SSL всегда отключена как для получения токена, так и для completion-запроса; значение `GIGACHAT_VERIFY_SSL_CERTS` в этой ветке игнорируется. Логирование SDK-запросов и ответов жёстко выключено в коде и не настраивается, чтобы промпты и credentials не попадали в логи.

Неактивную ветку задавать не нужно. Например, при `user-password` приложение не проверяет пути и пароли PKCS12. При неполной выбранной ветке, неизвестном scope, неверном URL, отсутствующем файле, модели или auth mode контекст завершается с ошибкой до первого сетевого вызова.

Секреты и PKCS12-файлы нельзя сохранять в репозитории. После локального запуска удалите чувствительные переменные окружения из текущей PowerShell-сессии.
