# SQL to Mongo shell transpiler

Парсер простейшей SQL грамматики с переводом в Mongo Shell синтаксис

* Язык программирования: **Kotlin**
* Время реализации: 7 часов

## Описание

Пример использования транслятора представлен в тестовых сценариях, лежащих в файле `srd/Program.kt`. Этапы трансляции:
1. Разбитие строки SQL запроса на токены с помощью функции `SQLParser.lexer`
2. Парсинг исходной строки во внутреннюю структуру AST с помощью функции `SQLParser.parseSQL`
3. Сериализация AST в представление, распознаваемое Mongo Shell с помощью функции класса `SQLCategory`: `parsedSql.toMongoDB`

Грамматика языка, с которым осуществлялась работа, определена в виде БНФ:

    <SQL>   ::= SELECT <cols> FROM <name> [WHERE <conditional>] [OFFSET <INT>] [LIMIT <INT>]
    <conditional> ::= <conditional> <lop> <conditional> | NOT <conditional> | <name> <cmp> <name>
    <cols>  ::= * | <names>
    <names> ::= <name>, <names> | <name>
    <name>  ::= <STRING> | "<STRING>" | '<STRING>' | `<STRING>`
    <lop> ::= AND | OR
    <cmp> ::= > | < | >= | <= | = | <>
    <INT>   ::= 0 | 1 | ...
    
Для парсинга синтаксических категорий (указаны в угловых скобок, кроме знаков сравнения) использовались
функции специального именования `parseName`, `parseNames`, `parseSql`, ..., а также классы, которые
представляли синтаксическую категорию: `StringCategory`, `NamesCategory`, ... -- все наследники `SyntaxCategory`
### Что сделано:

1. Парсинг `SELECT` запроса с поддержкой конструкций `OFFSET` и `LIMIT`, и без поддержки `WHERE`
2. Сериализация `SELECT` запроса в синтаксис Mongo DB Shell
3. Проверка параметров `OFFSET` и `LIMIT` на корректность
4. Возможность окружения имён таблиц в запросе с помощью одинарных, двойных и косых кавычек с учётом
корректного парсинга в MongoDB синтаксис

### Что не удалось сделать
Поддержка `WHERE` из-за недостатка времени

### Личный результат
Получен первичный опыт разработки на языке Kotlin

### Аналогичные проекты

Имелся опыт в парсинге MySQL запросов вместе с проверкой их семантической корректности с использованием NodeJS:

[https://github.com/kry127/nodejs-mysql-metaserver]()