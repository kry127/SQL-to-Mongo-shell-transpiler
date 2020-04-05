package ru.spb.kry127.mongosql

import java.text.ParseException
import kotlin.collections.ArrayList

/*
 Можно было бы воспользоваться более продвинутыми вещами, типа ANTLR, или MPS,
 но решил поиграться с Kotlin. Кстати, это мои самые первые строчки на Kotlin :3

 Реализуем простейшую грамматику для данной задачи:

 <SQL>   ::= SELECT <cols> FROM <name> [WHERE <conditional>] [OFFSET <INT>] [LIMIT <INT>]
 <conditional> ::= <conditional> <lop> <conditional> | NOT <conditional> | <name> <cmp> <name>
 <cols>  ::= * | <names>
 <names> ::= <name>, <names> | <name>
 <name>  ::= <STRING> | "<STRING>" | '<STRING>' | `<STRING>`
 <lop> ::= AND | OR
 <cmp> ::= > | < | >= | <= | = | <>
 <INT>   ::= 0 | 1 | ...
 */


/**
 * Определяем синтаксические категории БНФ нотации
 */
open interface SyntaxCategory

enum class Cmp {
    GT, LT, GTE, LTE, EQ, NE
}

enum class Lop {
    AND, OR, NOT
}

class StringCategory (val name : String, val qualified : Boolean) : SyntaxCategory

class NamesCategory (val names : List<StringCategory>) : SyntaxCategory

class ConditionalCategory : SyntaxCategory

/**
 * Метод решения задачи -- парсинг в синтаксическую категорию SQLCategory,
 * затем сериализация представления в MongoDB запрос
 */
class SQLCategory (val table : StringCategory, val columns : NamesCategory?, val condition : ConditionalCategory?,
                   val offset : Int?, val limit : Int?) : SyntaxCategory
{
    fun toMongoDB() : String {
        var prefix : String = "";
        var serializedCondition : String = "";
        var skipAndLimitPostfix : String = "";
        if (table.qualified) {
            prefix = "db['${table.name}']"
        } else {
            prefix = "db.${table.name}"
        }

        if (condition == null) {
            serializedCondition = "{}"
        } else {
            throw NotImplementedError("WHERE is not supported")
        }

        if (columns != null) {
            val transformedNames = columns.names.map {sc -> if (sc.qualified) "'${sc.name}': 1" else "${sc.name}: 1"}
            serializedCondition += ", " + transformedNames.joinToString(prefix = "{", postfix = "}", separator = ",")
        }

        if (offset != null) {
            skipAndLimitPostfix += ".skip($offset)"
        }
        if (limit != null) {
            skipAndLimitPostfix += ".limit($limit)"

        }

        return "${prefix}.find($serializedCondition)$skipAndLimitPostfix"
    }
}


class ParseScanner(val token: String, val tail: String, val offset: Int) {
}

class TokenScanner(val token: SyntaxCategory?, val tokens: ArrayList<String>, val offset: Int) {
}

class SQLParser {
    companion object {
        val stringBorders = charArrayOf('\"', '\'', '`')
        val specialSymbols = charArrayOf(',') // treated as separate tokens
        val keywords = arrayOf("SELECT", "FROM", "WHERE", "OFFSET", "LIMIT")

        fun isKeyword(s : String) : Boolean {
            return s.toUpperCase() in keywords
        }

        // iterate through tokens
        private fun nextLexem(scan: ParseScanner): ParseScanner? {
            var offset = scan.offset
            offset += scan.tail.takeWhile(Char::isWhitespace).length
            val str = scan.tail.dropWhile(Char::isWhitespace)
            if (str == "") {
                return null
            }
            if (str.elementAt(0) in stringBorders) {
                val bordType = str.elementAt(0)
                var protected = false
                for (i in 1 until str.length) {
                    if (protected) {
                        protected = false
                        continue
                    }
                    if (str.elementAt(i) == '\\') {
                        protected = true
                    } else if (str.elementAt(i) == bordType) {
                        return ParseScanner(str.substring(0, i + 1), str.substring(i + 1), offset + i + 2)
                    }
                }
                throw ParseException("No closing string symbol '$bordType' at position $offset", offset)
            } else if (str.elementAt(0) in specialSymbols) {
                return ParseScanner(str.substring(0, 1), str.substring(1), offset + 1)
            }
            val token = str.takeWhile { c -> !c.isWhitespace() && !(c in specialSymbols)}
            val rest = str.dropWhile { c -> !c.isWhitespace() && !(c in specialSymbols)}
            offset += token.length
            return ParseScanner(token, rest, offset)

        }

        // возвращает список токенов
        fun lexer(s: String): List<String> {
            val ret: MutableList<String> = ArrayList<String>()
            var current: ParseScanner? = nextLexem(ParseScanner("", s, 0))
            while (current != null) {
                ret += current.token
                current = nextLexem(current)
            }
            return ret
        }

        // парсинг установленной грамматики:
        private fun parseINT(s: String, offset: Int): Int {
            try {
                return s.toInt()
            } catch (nfe: NumberFormatException) {
                // not a valid int
                throw ParseException("Expected INT, found $s at token $offset", offset)
            }
        }

        private fun parseCmp(s: String, offset: Int): Cmp {
            return when (s) {
                ">" -> Cmp.GT
                "<" -> Cmp.LT
                ">=" -> Cmp.GTE
                "<=" -> Cmp.LTE
                "=" -> Cmp.EQ
                "<>" -> Cmp.NE
                else -> {
                    throw ParseException("Expected Cmp, found $s at token $offset", offset)
                }
            }
        }

        private fun parseLop(s: String, offset: Int): Lop {
            return when (s.toUpperCase()) {
                "AND" -> Lop.AND
                "OR" -> Lop.OR
                else -> {
                    throw ParseException("Expected Cmp, found $s at token $offset", offset)
                }
            }
        }

        private fun parseNot(s: String, offset: Int): Lop {
            when (s.toUpperCase()) {
                "NOT" -> return Lop.NOT
                else -> {
                    throw ParseException("Expected NOT, found $s at token $offset", offset)
                }
            }
        }

        private fun parseName(s : String, offset: Int) : StringCategory {
            if (s.elementAt(0) in stringBorders) {
                return StringCategory(s.substring(1, s.length - 1), true)
            }
            return StringCategory(s, false)
        }

        private fun checkRange(expected: String, tokenScanner: TokenScanner, offset: Int) {
            if (offset >= tokenScanner.tokens.size) {
                throw ParseException("Expected $expected, found EOF at token $offset", offset)
            }
        }

        private fun parseNames(tokenScanner: TokenScanner) : TokenScanner {
            val tokens = tokenScanner.tokens
            val offset = tokenScanner.offset
            var id = offset
            var names : ArrayList<StringCategory> = ArrayList()
            if (isKeyword(tokens[id])) {
                throw ParseException("Expected name, found keyword ${tokens[id]} at token $id", id)
            }
            var stringToken = parseName(tokens[id], id)
            while (id < tokens.size) {
                names.add(stringToken)
                id++
                if (id >= tokens.size || tokens[id] != "," || isKeyword(tokens[id])) {
                    return TokenScanner(NamesCategory(names), tokens, id)
                }
                id++
                checkRange("name", tokenScanner, id)
                if (isKeyword(tokens[id])) {
                    throw ParseException("Expected name, found keyword ${tokens[id]} at token $id", id)
                }
                stringToken = parseName(tokens[id], id)
            }
            names.add(stringToken)
            return TokenScanner(NamesCategory(names), tokens, id)
        }

        private fun parseSQL(tokenScanner: TokenScanner) : SQLCategory {
            val tokens = tokenScanner.tokens
            val offset = tokenScanner.offset
            var id = offset
            checkRange("'SELECT'", tokenScanner, id)
            if (tokens[id].toUpperCase() != "SELECT") {
                throw ParseException("Expected 'SELECT' query, but, found ${tokens[id]} at token $id", id)
            }
            id++
            checkRange("<names>", tokenScanner, id)
            var columns : NamesCategory?
            if (tokens[id] == "*") {
                columns = null
                id++
            } else {
                val parsedNamesScanner = parseNames(TokenScanner(null, tokens, id))
                columns = parsedNamesScanner.token as NamesCategory?
                id = parsedNamesScanner.offset
            }
            checkRange("FROM", tokenScanner, id)
            if (tokens[id].toUpperCase() != "FROM") {
                throw ParseException("Expected 'FROM' keyword, but found ${tokens[id]} at token $id", id)
            }
            id++
            checkRange("<name>", tokenScanner, id)
            if (isKeyword(tokens[id])) {
                throw ParseException("Expected <names>, but, found keyword '${tokens[id]}' at token $id", id)
            }
            val tableName = parseName(tokens[id], id)
            id++
            if (id >= tokens.size) {
                return SQLCategory(tableName, columns, null, null, null)
            }
            // optionals
            if (tokens[id].toUpperCase() == "WHERE") {
                id++;
                throw NotImplementedError("Плохо делать тестовые задания в последний день ;(")
            }
            var sqlOffset : Int? = null
            var sqlLimit : Int? = null
            if (id < tokens.size && tokens[id].toUpperCase() == "OFFSET") {
                id++;
                checkRange("<INT>", tokenScanner, id)
                sqlOffset = parseINT(tokens[id], id)
                if (sqlOffset < 0) {
                    throw ParseException("Offset $sqlOffset cannot be negative", id)
                }
                id++;
            }
            if (id < tokens.size && tokens[id].toUpperCase() == "LIMIT") {
                id++;
                checkRange("<INT>", tokenScanner, id)
                sqlLimit = parseINT(tokens[id], id)
                if (sqlLimit < 0) {
                    throw ParseException("Limit $sqlLimit cannot be negative", id)
                }
                id++;
            }
            return SQLCategory(tableName, columns, null, sqlOffset, sqlLimit)
        }

        fun parseSQL(tokens: Collection<String>) : SQLCategory {
            return parseSQL(TokenScanner(null, ArrayList(tokens), 0))
        }

    }
}
