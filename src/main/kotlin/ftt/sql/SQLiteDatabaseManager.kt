package ftt.sql

import me.drex.itsours.claim.AbstractClaim
import me.drex.itsours.claim.Claim
import net.minecraft.entity.player.PlayerEntity
import java.io.File
import java.sql.Connection
import java.sql.Date
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Time
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

data class PlayerClaimData(
    val name: String?, val uuid: String?, val claim: String?, val cost: Int, val daysPerRent: Int, val endTime: Long?
)


class SQLiteDatabaseManager {
    var url: String? = null

    fun createNewDatabase(file: File) {
        url = "jdbc:sqlite:" + file.getPath().replace('\\', '/')
        var conn: Connection? = null
        try {
            conn = DriverManager.getConnection(url)
        } catch (e: SQLException) {
            println(e.message)
        }
        createNewTable()
    }


    fun connect(): Connection? {
        var conn: Connection? = null
        try {
            conn = DriverManager.getConnection(url)
        } catch (e: SQLException) {
            println(e.message)
        }
        return conn
    }

    fun borrowClaim(claim: AbstractClaim, player: PlayerEntity): Int {
        val target = getClaim(claim) ?: return 0
        val sql =
            "UPDATE player_claim_data SET uuid = ?, name = ?, end_time = ? WHERE claim = ? AND (end_time < ? OR end_time IS NULL);"

        val endTime =
            System.currentTimeMillis() + target.daysPerRent.toLong() * 24 * 60 * 60 * 1000 // Convert days to milliseconds
        val now = System.currentTimeMillis()

        try {
            connect().use { conn ->
                conn!!.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, player.uuid.toString())
                    pstmt.setString(2, player.name.string)
                    pstmt.setLong(3, endTime)
                    pstmt.setString(4, getClaimName(claim))
                    pstmt.setLong(5, now)
                    return pstmt.executeUpdate();
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return 0
    }

    fun registerClaim(claim: AbstractClaim, cost: Int, daysPerRent: Int): Boolean {
        if (getClaim(claim) != null) {
            return false
        }

        val sql = "INSERT INTO player_claim_data (claim, cost, days_per_rent) VALUES (?, ?, ?);"

        try {
            connect().use { conn ->
                conn!!.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, getClaimName(claim))
                    pstmt.setInt(2, cost)
                    pstmt.setInt(3, daysPerRent)
                    pstmt.executeUpdate();
                }
            }
            return true
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return false
    }

    fun getClaim(claim: AbstractClaim): PlayerClaimData? {
        val sql = "SELECT * FROM player_claim_data WHERE claim = ?;"

        try {
            connect().use { conn ->
                conn!!.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, getClaimName(claim))
                    val rs = pstmt.executeQuery()
                    return if (rs.next()) {
                        PlayerClaimData(
                            name = rs.getString("name"),
                            uuid = rs.getString("uuid"),
                            claim = rs.getString("claim"),
                            cost = rs.getInt("cost"),
                            daysPerRent = rs.getInt("days_per_rent"),
                            endTime = rs.getLong("end_time")
                        )
                    } else {
                        null
                    }
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getClaimName(claim: AbstractClaim): String {
        val name = claim.name
        val x = claim.box.minX.toString()
        val y = claim.box.minY.toString()
        val z = claim.box.minZ.toString()
        return "$name,$x,$y,$z"
    }

    private fun createNewTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS player_claim_data (
                claim TEXT NOT NULL,
                cost INTEGER NOT NULL,
                days_per_rent INTEGER NOT NULL,
                name TEXT,
                uuid TEXT,
                end_time LONG,
                UNIQUE("claim")
            );
        """.trimIndent()

        try {
            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }


}