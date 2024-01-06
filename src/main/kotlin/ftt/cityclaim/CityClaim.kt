package ftt.cityclaim

import com.gmail.sneakdevs.diamondeconomy.DiamondUtils
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import ftt.sql.PlayerClaimData
import me.drex.itsours.claim.ClaimList
import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrNull
import ftt.sql.SQLiteDatabaseManager as ClaimDB


object CityClaim : ModInitializer {
    private const val MODID = "cityclaim"
    private const val PERMISSION_REGISTER = "${MODID}.register"
    private const val CLAIM_ROLE = "trusted"
    private val logger = LoggerFactory.getLogger("cityclaim")
    private val moneyManager = DiamondUtils.getDatabaseManager();
    private val claimManager = ClaimDB()
    override fun onInitialize() {
        // 初始化
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val file = server.getSavePath(WorldSavePath.ROOT).resolve("$MODID.sqlite").toFile();
            if (!file.exists()) {
                file.createNewFile()
            }
            claimManager.createNewDatabase(file)
        };

        // 處理到期領地租約
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val expiredClaims = claimManager.getExpiredClaim()
            for (claim in expiredClaims) {
                ClaimList.getClaims().forEach { anyClaim ->
                    val anyClaimName = claimManager.getClaimName(anyClaim)
                    if (anyClaimName != claim.claim) {
                        return@forEach
                    }
                    if (claim.renew == true && renewClaim(claim)) {
                        return@forEach
                    }
                    claimManager.removeClaimOwner(claim)
                    anyClaim.roleManager.getRole(CLAIM_ROLE)?.players()?.clear()
                }
            }
        }

        // 指令註冊
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal("city").executes(::showGuide).then(
                    literal("rent").executes(::rentClaim)
                ).then(
                    literal("check").executes(::checkClaim)
                ).then(
                    literal("renew")
                        .then(literal("on").executes { context -> toggleRenewClaim(context, true) })
                        .then(literal("off").executes { context -> toggleRenewClaim(context, false) })
                ).then(
                    literal("register").requires(checkPermission(PERMISSION_REGISTER)).then(
                        argument("cost", IntegerArgumentType.integer(1)).then(
                            argument("period", IntegerArgumentType.integer(1)).executes(::registerClaim)
                        )
                    )
                )
            )
        }

    }

    private fun toggleRenewClaim(context: CommandContext<ServerCommandSource>, state: Boolean): Int {
        val player = context.source.player ?: return 0
        val result = claimManager.setRenewClaim(player, state)
        if (result > 0 && state) {
            sendFeedback(context, "已啟用自動續租")
        } else if (result > 0) {
            sendFeedback(context, "已停用自動續租")
        } else {
            sendFeedback(context, "發生錯誤，請聯繫管理員")
        }
        return 1
    }

    private fun showGuide(context: CommandContext<ServerCommandSource>): Int {
        var message =
            "/city rent 承租目前所在的租地\n" + "/city check 查看目前所在租地的租約\n" + "/city renew 開啟/關閉自動續約（預設開啟）"
        val player = context.source.player
        if (player != null && Permissions.check(player, PERMISSION_REGISTER)) {
            message = message.plus("\n/city register <cost> <period> 註冊租地")
        }
        sendFeedback(context, message, false)
        return 1
    }

    private fun checkPermission(permission: String): Predicate<ServerCommandSource> {
        return Predicate { source ->
            val player = source.player ?: return@Predicate false
            Permissions.check(player, permission)
        }
    }

    private fun registerClaim(context: CommandContext<ServerCommandSource>): Int {
        val cost = IntegerArgumentType.getInteger(context, "cost")
        val daysPerRent = IntegerArgumentType.getInteger(context, "period")

        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()
        if (claim == null) {
            sendFeedback(context, "這裡沒有領地")
            return 0
        }
        val result = claimManager.registerClaim(claim, cost, daysPerRent)
        if (result) {
            sendFeedback(context, "註冊成功！${claim.fullName}，價格：${cost}，租借時間：${daysPerRent}")
            return 1
        }
        sendFeedback(context, "註冊失敗")
        return 0
    }

    private fun renewClaim(claim: PlayerClaimData): Boolean {
        val money = moneyManager.getBalanceFromUUID(claim.uuid)
        val price = claim.cost
        if (money < price) {
            return false
        }
        if (claim.renew == false) {
            return false
        }
        if (claimManager.renewClaim(claim) == 0) {
            return false
        }
        return moneyManager.changeBalance(claim.uuid, -price)
    }

    private fun rentClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()
        val rentedClaim = claimManager.getPlayerClaims(player)
        var canRent = true
        for (playerClaimData in rentedClaim) {
            if ((playerClaimData.endTime ?: 0) > System.currentTimeMillis()) {
                canRent = false
                continue
            }
            if (renewClaim(playerClaimData)) {
                canRent = false
                continue
            }
            claimManager.removeClaimOwner(playerClaimData)
        }

        if (!canRent) {
            sendFeedback(context, "你已經有租借領地了")
            return 0
        }
        if (claim == null) {
            sendFeedback(context, "這裡沒有領地")
            return 0
        }
        val rentableClaim = claimManager.getClaim(claim)
        if (rentableClaim == null) {
            sendFeedback(context, "無法租借該領地")
            return 0
        }
        val money = moneyManager.getBalanceFromUUID(player.uuid.toString())
        val price = rentableClaim.cost
        if (money < price) {
            sendFeedback(context, "你只有 $money 元，這塊地要 $price 元才可以租")
            return 0
        }
        val trustedRole = claim.roleManager.getRole(CLAIM_ROLE) ?: return 0

        val result = claimManager.rentClaim(claim, player)

        if (result == 0) {
            sendFeedback(context, "租用失敗，請確認領地是否可租用")
            return 0
        }
        moneyManager.changeBalance(player.uuid.toString(), -price)
        trustedRole.players().add(player.uuid)
        sendFeedback(
            context,
            "你租用了領地 ${claim.fullName}！剩下 ${money - price} 元，自動續租功能會自動開啟，不需要請關閉"
        )
        return 1
    }

    private fun checkClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()
        if (claim == null) {
            sendFeedback(context, "這裡沒有領地")
            return 0
        }
        val playerClaimData = claimManager.getClaim(claim)
        if (playerClaimData == null) {
            sendFeedback(context, "該領地無法租借")
            return 0
        }

        val timestamp = playerClaimData.endTime ?: 0;
        var dateStr = "可租借"
        if (timestamp > System.currentTimeMillis()) {
            dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Date(timestamp))
        }
        val claimName = playerClaimData.claim.split("@")[0]
        val message = """
            $claimName
            價格：${playerClaimData.cost} 元，租期：${playerClaimData.daysPerRent} 天
            目前／最後租借人：${playerClaimData.name ?: "無"}
            到期時間：${dateStr}
        """.trimIndent()
        sendFeedback(context, message)
        return 1
    }

    private fun sendFeedback(
        context: CommandContext<ServerCommandSource>,
        message: String,
        header: Boolean = true
    ): Unit {
        if (header) {
            return context.source.sendFeedback({
                Text.literal("【租地系統】").withColor(45824).append(Text.literal(message).withColor(16777215))
            }, false);
        }
        return context.source.sendFeedback({ Text.literal(message) }, false);
    }
}