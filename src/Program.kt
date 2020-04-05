package ru.spb.kry127.MongoSQL

import ru.spb.kry127.mongosql.SQLParser

fun test(sql : String) {
    val tokens = SQLParser.lexer(sql)
    val parsedSql = SQLParser.parseSQL(tokens)
    val mongoDB = parsedSql.toMongoDB()
    println("$sql -> $mongoDB")
}

fun main() {
    test("SELECT * FROM table")
    // test("SELECT * FROM table OFFSET -5") // error!
    // test("SELECT * FROM table LIMIT -23") // error!
    test("SELECT * FROM table OFFSET 8") // error!
    test("SELECT * FROM table LIMIT 64") // error!
    test("SELECT * FROM myNiceTable OFFSET 8 LIMIT 10")
    test("SELECT * FROM `sales management` OFFSET 8 LIMIT 10")
    test("SELECT X , Y , Z FROM `sales management` OFFSET 8 LIMIT 10")
    test("SELECT X, Y, Z FROM `sales management` OFFSET 8 LIMIT 10")
    test("SELECT X, \"looong spacy name\", Z FROM `sales management` OFFSET 8 LIMIT 10")
}