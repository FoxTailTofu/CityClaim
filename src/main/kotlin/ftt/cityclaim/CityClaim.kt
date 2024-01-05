package ftt.cityclaim

import com.gmail.sneakdevs.diamondeconomy.DiamondUtils
import com.gmail.sneakdevs.diamondeconomy.config.DiamondEconomyConfig
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import me.drex.itsours.claim.ClaimList
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.jvm.optionals.getOrNull
import com.gmail.sneakdevs.diamondeconomy.sql.DatabaseManager as MoneyDB
import ftt.sql.SQLiteDatabaseManager as ClaimDB


object CityClaim : ModInitializer {

    const val MODID = "cityclaim"
    private val logger = LoggerFactory.getLogger("cityclaim")
    private val moneyManager = DiamondUtils.getDatabaseManager();
    private val claimManager = ClaimDB()
    override fun onInitialize() {

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val file = server.getSavePath(WorldSavePath.ROOT).resolve("$MODID.sqlite").toFile();
            if (!file.exists()) {
                file.createNewFile()
            }
            claimManager.createNewDatabase(file)
        };

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(literal("register_claim")
                .then(argument("cost", IntegerArgumentType.integer(1))
                    .then(argument("days_per_rent", IntegerArgumentType.integer(1))
                        .executes { context ->
                            val cost = IntegerArgumentType.getInteger(context, "cost")
                            val daysPerRent = IntegerArgumentType.getInteger(context, "days_per_rent")
                            registerClaim(context, cost, daysPerRent)
                        }
                    )
                )
            )
            dispatcher.register(literal("borrow_claim").executes { context -> borrowClaim(context) })
        }

    }

    private fun registerClaim(context: CommandContext<ServerCommandSource>, cost: Int, daysPerRent: Int): Int {

        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull() ?: return 0
        val result = claimManager.registerClaim(claim, cost, daysPerRent)
        if (result) {
            context.source.sendFeedback({ Text.literal("註冊成功") }, false)
            return 1
        }
        context.source.sendFeedback({ Text.literal("註冊失敗") }, false)
        return 0
    }

    private fun borrowClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull() ?: return 0
        val rentable = claimManager.getClaim(claim) ?: return 0
        val money = moneyManager.getBalanceFromUUID(player.uuid.toString())
        val price = rentable.cost

        if (money < price) {
            context.source.sendFeedback({ Text.literal("窮逼，你只有 $money 元，這塊地要 $price 元才可以租") }, false)
            return 0
        }
        val trustedRole = claim.roleManager.getRole("trusted") ?: return 0

        val result = claimManager.borrowClaim(claim, player)

        if (result == 0) {
            context.source.sendFeedback({ Text.literal("租用失敗") }, false)
            return 0
        }
        moneyManager.changeBalance(player.uuid.toString(), -price)
        trustedRole.players().add(player.uuid)
        context.source.sendFeedback({ Text.literal("你租用了領地 ${claim.name}！剩下 ${money - price} 元") }, false)
        return 1
    }
}