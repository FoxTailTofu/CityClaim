package ftt.sql

import com.mojang.authlib.GameProfile
import me.drex.itsours.claim.AbstractClaim
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import java.sql.ResultSet


class  CityclaimManager(server: MinecraftServer) {

    private var database: Database;

    init {
        database = Database(server)
        initTable()
    }

    fun stopConnection() {
        database.close();
    }

    fun renewClaim(claim: PlayerClaimData): Int {
        val now = System.currentTimeMillis()
        val endTime = now + claim.daysPerRent * 24 * 60 * 60 * 1000
        val sql = "UPDATE player_claim_data SET  end_time = ? WHERE claim = ? AND (end_time < ? OR end_time IS NULL);"
        return database.prepare(sql, {
            it.setLong(1, endTime)
            it.setString(2, claim.claim)
            it.setLong(3, now)
            it.executeUpdate();
        }) ?: 0
    }


    fun setRenewClaim(player: PlayerEntity, state: Boolean): Int {
        val sql = "UPDATE player_claim_data SET renew = ? WHERE uuid = ?;";
        return database.prepare(sql, {
            it.setInt(1, if (state) 1 else 0)
            it.setString(2, player.uuidAsString)
            it.executeUpdate();
        }) ?: 0
    }

    fun rentClaim(claim: AbstractClaim, player: PlayerEntity): Int {
        val target = getClaim(claim) ?: return 0
        val now = System.currentTimeMillis()
        val endTime = now + target.daysPerRent.toLong() * 24 * 60 * 60 * 1000
        val sql =
            "UPDATE player_claim_data SET uuid = ?, name = ?, end_time = ?, renew = 1 WHERE claim = ? AND (end_time < ? OR end_time IS NULL);"
        val result = database.prepare(sql, {
            it.setString(1, player.uuidAsString)
            it.setString(2, player.name.string)
            it.setLong(3, endTime)
            it.setString(4, getClaimName(claim))
            it.setLong(5, now)
            it.executeUpdate();
        }) ?: 0

        if (result > 0) {
            removeSharedClaim(target);
        }
        return result
    }


    fun registerClaim(claim: AbstractClaim, cost: Int, daysPerRent: Int): Boolean {
        if (getClaim(claim) != null) {
            return false
        }
        val sql = "INSERT INTO player_claim_data (claim, cost, days_per_rent,renew) VALUES (?, ?, ?, 1);"
        return database.prepare(sql, {
            it.setString(1, getClaimName(claim))
            it.setInt(2, cost)
            it.setInt(3, daysPerRent)
            it.executeUpdate() > 0
        }) ?: false
    }

    fun removeClaimOwner(claim: PlayerClaimData): Int {
        removeSharedClaim(claim)
        return database.prepare(
            "UPDATE player_claim_data SET uuid = null, end_time = null, renew = 1 WHERE claim = ?;",
            {
                it.setString(1, claim.claim)
                it.executeUpdate()
            }) ?: 0
    }

    fun shareClaim(claim: PlayerClaimData, profile: GameProfile): Int {
        val sql = "INSERT INTO player_share_claim (claim, name, uuid) VALUES (?, ?, ?);"
        return database.prepare(sql, {
            it.setString(1, claim.claim)
            it.setString(2, profile.name)
            it.setString(3, profile.id.toString())
            it.executeUpdate()
        }, {
            return -1
        }) ?: 0
    }

    fun removeSharedClaim(claim: PlayerClaimData, profile: GameProfile): Int {
        val sql = "DELETE FROM player_share_claim WHERE claim = ? AND uuid = ?;"
        return database.prepare(sql, {
            it.setString(1, claim.claim)
            it.setString(2, profile.id.toString())
            it.executeUpdate()
        }) ?: 0
    }

    fun removeSharedClaim(claim: PlayerClaimData): Int {
        val sql = "DELETE FROM player_share_claim WHERE claim = ?;"
        return database.prepare(sql, {
            it.setString(1, claim.claim)
            it.executeUpdate()
        }) ?: 0
    }

    fun getPlayerClaims(player: PlayerEntity): List<PlayerClaimData> {
        val sql = "SELECT * FROM player_claim_data WHERE uuid = ?;"
        return database.prepare(sql, {
            it.setString(1, player.uuidAsString)
            getListPlayerClaimData(it.executeQuery())
        }) ?: emptyList()
    }

    fun getClaim(claim: AbstractClaim): PlayerClaimData? {
        val sql = "SELECT * FROM player_claim_data WHERE claim = ?;"
        return database.prepare(sql, {
            it.setString(1, getClaimName(claim))
            getListPlayerClaimData(it.executeQuery()).firstOrNull()
        })
    }

    fun getExpiredClaim(): List<PlayerClaimData> {
        val sql = "SELECT * FROM player_claim_data WHERE end_time < ?"
        return database.prepare(sql, {
            it.setLong(1, System.currentTimeMillis())
            getListPlayerClaimData(it.executeQuery())
        }) ?: emptyList()
    }

    fun getClaimName(claim: AbstractClaim): String {
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

    private fun initTable() {
        val mainSql = """
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
        val shareSql = """
            CREATE TABLE IF NOT EXISTS player_share_claim (
                claim TEXT NOT NULL,
                uuid TEXT,
                name TEXT,
                UNIQUE("claim", "uuid")
            );
        """.trimIndent()
        database.prepare(mainSql, {
            it.execute()
        })
        database.prepare(shareSql, {
            it.execute()
        })
    }


}