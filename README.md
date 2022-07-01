# Styx

### Пример использования сервиса:

Для начала нужно поднять сервис с помощью docker-compose.yml

У сервиса есть только один  REST - POST "/v2/proxy", который принимает тело запроса следующего вида:

```
url - проксируемое api
httpMethod - один из HTTP методов
timeout - таймаут между запросами. Не обязательный параметр, дефолтное значение - 0
context - объекты вида key/value. На данный момент можно использовать только ключи headers/content. Под каждым ключом 
нужно указать value.
header value - Map<string, string>
content value - объект в виде base64, который будет декодирован исходя из указанного в value headers значения Content-Type
{
    "url": "https://someapi.com/",
    "httpMethod": "POST",
    "timeout": 2000
    "context": [
        {
            "key": "headers",
            "value": {
                "custom": "custom_value",
                "Accept-Language": "en_US",
                "Content-Type": "application/json"
            }
        },
        {
            "key": "content",
            "value": "base64string"       
        }
    ]
}

```
Пример запроса
```
Запрос:
{
    "url": "https://catfact.ninja/fact",
    "httpMethod": "GET"
}

В ответ придет:

{
    "code": 0,
    "originalStatus": 200,
    "url": "https://catfact.ninja/fact",
    "httpMethod": "GET",
    "body": {
        "fact": "A happy cat holds her tail high and steady.",
        "length": 43
    }
}

code - код системы Styx (0 - все ок, отрицательные коды означают различного рода ошибки, 
например при коде -1 придет ответ корректного формата, но по каким-то причинам не проксированный)

originalStatus - HTTP статус ответа вызванного сервиса

url - url запроса

httpMethod - выбранный HTTP метод

body - ответ от запрошенного сервиса
```
