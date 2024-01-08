package ftt.sql

import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.function.Predicate

class Database(server: MinecraftServer) {

    private val filename = "cityclaim.sqlite";
    private var url: String? = null
    var connection: Connection? = null;

    init {
        val file = server.getSavePath(WorldSavePath.ROOT).resolve(filename).toFile();
        if (!file.exists()) {
            file.createNewFile()
        }
        url = "jdbc:sqlite:" + file.path.replace('\\', '/')
        connection = connect()
    }

    public fun close() {
        connection?.close();
    }

    private fun connect(): Connection? {
        var conn: Connection? = null
        try {
            conn = DriverManager.getConnection(url)
        } catch (e: SQLException) {
            println(e.message)
        }
        return conn
    }

    inline fun <T> prepare(
        sql: String,
        block: (PreparedStatement) -> T,
        exceptionHandler: (SQLException) -> Unit = { e -> e.printStackTrace() }
    ): T? {
        return try {
            val preparedStatement = connection?.prepareStatement(sql)
            preparedStatement?.let { block(it) }
        } catch (e: SQLException) {
            exceptionHandler(e)
            null
        }
    }

}