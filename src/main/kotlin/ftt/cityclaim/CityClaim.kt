package ftt.cityclaim

import com.mojang.brigadier.context.CommandContext
import me.drex.itsours.claim.Claim
import me.drex.itsours.claim.ClaimList
import me.drex.itsours.util.ClaimBox
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3i
import org.slf4j.LoggerFactory
import kotlin.jvm.optionals.getOrNull


object CityClaim : ModInitializer {

    private val logger = LoggerFactory.getLogger("cityclaim")
    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
            dispatcher.register(literal("show_claim").executes { context -> showClaim(context) })
            dispatcher.register(literal("create_claim").executes { context -> createClaim(context) })
            dispatcher.register(literal("get_claim").executes { context -> getClaim(context) })
            dispatcher.register(literal("borrow_claim").executes { context -> borrowClaim(context) })
        }

    }

    private fun showClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claims = ClaimList.getClaimsFrom(player.uuid)
        context.source.sendFeedback({
            Text.literal(claims.joinToString(",", "<", ">", -1, "...", null))
        }, false)

        return 1
    }

    private fun createClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val x = player.x.toInt()
        val y = player.y.toInt()
        val z = player.z.toInt()
        val box = ClaimBox.create(
            Vec3i(x - 5, y - 5, z - 5),
            Vec3i(x + 5, y + 5, z + 5)
        )
        val newClaim = Claim("AAA", player.uuid, box, player.serverWorld)
        ClaimList.addClaim(newClaim)
        context.source.sendFeedback({ Text.literal("bar") }, false)

        return 1
    }

    private fun getClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull() ?: return 0
        val boxX = claim.box.minX.toString() + " ~ " + claim.box.maxX.toString()
        val boxY = claim.box.minY.toString() + " ~ " + claim.box.maxY.toString()
        val boxZ = claim.box.minZ.toString() + " ~ " + claim.box.maxZ.toString()
        context.source.sendFeedback({ Text.literal(claim.name) }, false)
        context.source.sendFeedback({ Text.literal(boxX) }, false)
        context.source.sendFeedback({ Text.literal(boxY) }, false)
        context.source.sendFeedback({ Text.literal(boxZ) }, false)

        return 1
    }


    private fun borrowClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull() ?: return 0
        val trustedRole = claim.roleManager.getRole("trusted")?: return 0
        trustedRole.players().add(player.uuid)

        return 1
    }
}