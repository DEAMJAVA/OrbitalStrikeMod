package net.deamjava.orbital_strike_mod

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class OrbitalStrikeMod : ModInitializer {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("Orbital Strike Mod")
        val authorizations: HashMap<Any?, Long> = HashMap()
        val usedItems: HashSet<Any?> = HashSet()
        val fishingRodCastState: HashMap<Any?, Any?> = HashMap()
        val creativeModeTimestamps: HashMap<Any?, Any?> = HashMap()
        lateinit var config: OrbitalStrikeConfig

    }
    override fun onInitialize() {
        LOGGER.info("Orbital Strike Mod enabled!")
        config = OrbitalStrikeConfig()
        config.load()
        OrbitalStrikeRecipes.register()
        OrbitalStrikeCommands.register()
        OrbitalItems.registerItemUsageHandler()
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server: MinecraftServer? -> this.cleanupExpiredAuthorizations() })
    }


    private fun cleanupExpiredAuthorizations() {
        val currentTime = System.currentTimeMillis()
        authorizations.entries.removeIf { entry -> currentTime >= entry.value }
    }







}