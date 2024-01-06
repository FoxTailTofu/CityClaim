package ftt.sql

import me.drex.itsours.claim.AbstractClaim
import me.drex.itsours.claim.Claim
import net.minecraft.entity.player.PlayerEntity
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

data class PlayerClaimData(
    val name: String?,
    val uuid: String?,
    val claim: String,
    val cost: Int,
    val daysPerRent: Int,
    val endTime: Long?,
    val renew: Boolean?
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

    fun renewClaim(claim: PlayerClaimData): Int {
        val sql =
            "UPDATE player_claim_data SET  end_time = ? WHERE claim = ? AND (end_time < ? OR end_time IS NULL);"
        val endTime =
            System.currentTimeMillis() + claim.daysPerRent * 24 * 60 * 60 * 1000 // Convert days to milliseconds
        val now = System.currentTimeMillis()
        try {
            connect().use { conn ->
                conn!!.prepareStatement(sql).use { pstmt ->
                    pstmt.setLong(1, endTime)
                    pstmt.setString(2, claim.claim)
                    pstmt.setLong(3, now)
                    return pstmt.executeUpdate();
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return 0
    }

    fun setRenewClaim(player: PlayerEntity, state: Boolean): Int {
        val sql =
            "UPDATE player_claim_data SET renew = ? WHERE uuid = ?;"
        try {
            connect().use { conn ->
                conn!!.prepareStatement(sql).use { pstmt ->
                    pstmt.setInt(1, if (state) 1 else 0)
                    pstmt.setString(2, player.uuid.toString())
                    return pstmt.executeUpdate();
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return 0
    }

    fun rentClaim(claim: AbstractClaim, player: PlayerEntity): Int {
        val target = getClaim(claim) ?: return 0
        val sql =
            "UPDATE player_claim_data SET uuid = ?, name = ?, end_time = ?, renew = 1 WHERE claim = ? AND (end_time < ? OR end_time IS NULL);"

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

        val sql = "INSERT INTO player_claim_data (claim, cost, days_per_rent,renew) VALUES (?, ?, ?, 1);"

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

    fun removeClaimOwner(claim: PlayerClaimData): Int {
        val sql =
            "UPDATE player_claim_data SET uuid = null, end_time = null, renew = 1 WHERE claim = ?;"

        try {
            connect().use { conn ->
                conn!!.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, claim.claim)
                    return pstmt.executeUpdate();
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return 0
    }

    fun getPlayerClaims(player: PlayerEntity): List<PlayerClaimData> {
        val sql = "SELECT * FROM player_claim_data WHERE uuid = ?;";
        val data = mutableListOf<PlayerClaimData>()
        try {
            connect().use { conn ->
                conn!!.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, player.uuidAsString)
                    val rs = pstmt.executeQuery()
                    return getListPlayerClaimData(rs)
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return data;
    }

    fun getClaim(claim: AbstractClaim): PlayerClaimData? {
        val sql = "SELECT * FROM player_claim_data WHERE claim = ?;"

        try {
            connect().use { conn ->
                conn!!.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, getClaimName(claim))
                    val rs = pstmt.executeQuery()
                    return getListPlayerClaimData(rs).firstOrNull()
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return null
    }

    public fun getExpiredClaim(): List<PlayerClaimData> {
        val sql = "SELECT * FROM player_claim_data WHERE end_time < ?"
        try {
            connect().use { conn ->
                conn!!.prepareStatement(sql).use { pstmt ->
                    pstmt.setLong(1, System.currentTimeMillis())
                    val rs = pstmt.executeQuery()
                    return getListPlayerClaimData(rs)
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return getListPlayerClaimData(null)
    }

    public fun getClaimName(claim: AbstractClaim): String {
        val name = claim.fullName
        val x = claim.box.minX.toString()
        val y = claim.box.minY.toString()
        val z = claim.box.minZ.toString()
        return "$name@$x,$y,$z"
    }


    private fun getListPlayerClaimData(rs: ResultSet?): List<PlayerClaimData> {
        val data = mutableListOf<PlayerClaimData>()
        if (rs == null) {
            return data
        }
        while (rs.next()) {
            data.add(
                PlayerClaimData(
                    name = rs.getString("name"),
                    uuid = rs.getString("uuid"),
                    claim = rs.getString("claim"),
                    cost = rs.getInt("cost"),
                    daysPerRent = rs.getInt("days_per_rent"),
                    endTime = rs.getLong("end_time"),
                    renew = rs.getBoolean("renew")
                )
            )
        }
        return data
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
                renew BOOLEAN,
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