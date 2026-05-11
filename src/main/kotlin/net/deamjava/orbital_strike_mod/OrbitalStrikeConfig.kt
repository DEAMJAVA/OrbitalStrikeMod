package net.deamjava.orbital_strike_mod

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import net.minecraft.core.registries.BuiltInRegistries

class OrbitalStrikeConfig {

    val nukeIngredients: MutableMap<Item, Int> = HashMap()
    val stabIngredients: MutableMap<Item, Int> = HashMap()

    var creativeCooldown: Int = 500
        private set
    var isRecipeEnabled: Boolean = false
    var rodDelay: Double = 0.75
    var isDeathBanEnabled: Boolean = false
    var banMessage: String = "You Died! You have been Banned for %death.ban.duration%."
        private set
    var banDuration: String = "-1"
    var banDelay: Int = 0
        private set
    var deathsUntilBan: Int = 1
        private set
    var isReviveBan: Boolean = true
    var isProximityMessagesEnabled: Boolean = false
    var proximityRange: Double = 500.0
    var deathSoundRange: Double = 500.0
    var deathSound: String = "minecraft:entity.wither.spawn"
    var arrowVelocity: Double = 1500.0
    var railgunDelay: String = "4s"

    var isUuidTrackingEnabled: Boolean = false

    fun load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                LOGGER.info("Config file not found, creating default config...")
                createDefaultConfig()
            } else {
                FileReader(CONFIG_PATH.toFile()).use { reader ->
                    val data = GSON.fromJson(reader, ConfigData::class.java)
                    if (data != null) {
                        creativeCooldown = data.creative_cooldown_seconds
                        isRecipeEnabled = data.recipe_enabled
                        rodDelay = data.rod_delay_seconds
                        isDeathBanEnabled = data.death_ban_enabled
                        banMessage = data.ban_message
                        banDuration = data.ban_duration
                        banDelay = data.ban_delay
                        deathsUntilBan = data.deaths_until_ban
                        isReviveBan = data.revive_ban
                        isProximityMessagesEnabled = data.proximity_messages_enabled
                        proximityRange = data.proximity_range
                        deathSoundRange = data.death_sound_range
                        deathSound = data.death_sound
                        arrowVelocity = data.arrow_velocity
                        railgunDelay = data.railgun_delay

                        nukeIngredients.clear()
                        for (entry in data.N_S) {
                            parseItem(entry.material)?.let { nukeIngredients[it] = entry.amount }
                        }

                        stabIngredients.clear()
                        for (entry in data.S_S) {
                            parseItem(entry.material)?.let { stabIngredients[it] = entry.amount }
                        }
                        isUuidTrackingEnabled = data.uuid_tracking_enabled
                        return
                    }
                    LOGGER.warn("Config file is empty or invalid, using defaults")
                    createDefaultConfig()
                }
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to load config, using defaults", e)
            loadDefaults()
        }
    }

    fun save() {
        try {
            val data = ConfigData().apply {
                creative_cooldown_seconds = creativeCooldown
                recipe_enabled = isRecipeEnabled
                rod_delay_seconds = rodDelay
                death_ban_enabled = isDeathBanEnabled
                ban_message = this@OrbitalStrikeConfig.banMessage
                ban_duration = banDuration
                ban_delay = banDelay
                deaths_until_ban = deathsUntilBan
                revive_ban = isReviveBan
                proximity_messages_enabled = isProximityMessagesEnabled
                proximity_range = proximityRange
                death_sound_range = deathSoundRange
                death_sound = this@OrbitalStrikeConfig.deathSound
                arrow_velocity = arrowVelocity
                railgun_delay = railgunDelay

                nukeIngredients.forEach { (item, amount) ->
                    N_S.add(ConfigData.IngredientEntry(BuiltInRegistries.ITEM.getId(item).toString(), amount))
                }
                stabIngredients.forEach { (item, amount) ->
                    S_S.add(ConfigData.IngredientEntry(BuiltInRegistries.ITEM.getId(item).toString(), amount))
                }
                uuid_tracking_enabled = isUuidTrackingEnabled
            }
            FileWriter(CONFIG_PATH.toFile()).use { GSON.toJson(data, it) }
        } catch (e: Exception) {
            LOGGER.error("Failed to save config", e)
        }
    }

    private fun createDefaultConfig() {
        try {
            val data = ConfigData().apply {
                N_S.add(ConfigData.IngredientEntry("minecraft:tnt", 16))
                N_S.add(ConfigData.IngredientEntry("minecraft:netherite_block", 1))
                N_S.add(ConfigData.IngredientEntry("minecraft:redstone_block", 1))
                S_S.add(ConfigData.IngredientEntry("minecraft:tnt", 7))
                S_S.add(ConfigData.IngredientEntry("minecraft:netherite_block", 1))
                S_S.add(ConfigData.IngredientEntry("minecraft:diamond", 1))
            }
            FileWriter(CONFIG_PATH.toFile()).use { GSON.toJson(data, it) }
            loadDefaults()
        } catch (e: Exception) {
            LOGGER.error("Failed to create default config", e)
            loadDefaults()
        }
    }

    private fun loadDefaults() {
        creativeCooldown = 500
        isRecipeEnabled = false
        rodDelay = 0.75
        isDeathBanEnabled = false
        banMessage = "You Died! You have been Banned for %death.ban.duration%."
        banDuration = "-1"
        banDelay = 0
        deathsUntilBan = 1
        isReviveBan = true
        isProximityMessagesEnabled = false
        proximityRange = 500.0
        deathSoundRange = 500.0
        deathSound = "minecraft:entity.wither.spawn"
        arrowVelocity = 1500.0
        railgunDelay = "4s"

        nukeIngredients.clear()
        nukeIngredients[Items.TNT] = 16
        nukeIngredients[Items.NETHERITE_BLOCK] = 1
        nukeIngredients[Items.REDSTONE_BLOCK] = 1

        stabIngredients.clear()
        stabIngredients[Items.TNT] = 7
        stabIngredients[Items.NETHERITE_BLOCK] = 1
        stabIngredients[Items.DIAMOND] = 1

        isUuidTrackingEnabled = false
    }

    private fun parseItem(materialName: String): Item? {
        return try {
            val id = if (materialName.contains(":")) {
                Identifier.parse(materialName)
            } else {
                Identifier.fromNamespaceAndPath("minecraft", materialName.lowercase(Locale.getDefault()))
            }
            val optional = BuiltInRegistries.ITEM.getOptional(id)
            if (optional.isEmpty) return null
            val item = optional.get().asItem()
            if (item === Items.AIR) null else item
        } catch (e: Exception) {
            null
        }
    }

    fun parseBanDurationToMillis(duration: String): Long {
        if (duration == "-2") return -2L
        if (duration == "-1") return -1L
        return try {
            val timeStr = duration.lowercase(Locale.getDefault()).trim()
            if (timeStr.matches("\\d+[smhd]".toRegex())) {
                val value = timeStr.dropLast(1).toInt().toLong()
                when (timeStr.last()) {
                    'd' -> value * 24 * 60 * 60 * 1000
                    'h' -> value * 60 * 60 * 1000
                    'm' -> value * 60 * 1000
                    's' -> value * 1000
                    else -> -1L
                }
            } else -1L
        } catch (e: Exception) { -1L }
    }

    fun formatBanDuration(duration: String): String {
        if (duration == "-2") return "Kicked"
        if (duration == "-1") return "Permanent"
        return try {
            val timeStr = duration.lowercase(Locale.getDefault()).trim()
            if (timeStr.matches("\\d+[smhd]".toRegex())) {
                val value = timeStr.dropLast(1).toInt()
                when (timeStr.last()) {
                    'd' -> "$value ${if (value == 1) "Day" else "Days"}"
                    'h' -> "$value ${if (value == 1) "Hour" else "Hours"}"
                    'm' -> "$value ${if (value == 1) "Minute" else "Minutes"}"
                    's' -> "$value ${if (value == 1) "Second" else "Seconds"}"
                    else -> "Permanent"
                }
            } else "Permanent"
        } catch (e: Exception) { "Permanent" }
    }

    fun parseRailgunDelayToMillis(delay: String): Long {
        return try {
            val timeStr = delay.lowercase(Locale.getDefault()).trim()
            if (timeStr.matches("\\d+[sm]".toRegex())) {
                val value = timeStr.dropLast(1).toInt().toLong()
                when (timeStr.last()) {
                    'm' -> value * 60 * 1000
                    's' -> value * 1000
                    else -> 4000L
                }
            } else 4000L
        } catch (e: Exception) { 4000L }
    }

    data class ConfigData(
        var creative_cooldown_seconds: Int = 500,
        var recipe_enabled: Boolean = false,
        var rod_delay_seconds: Double = 0.75,
        var N_S: MutableList<IngredientEntry> = mutableListOf(),
        var S_S: MutableList<IngredientEntry> = mutableListOf(),
        var death_ban_enabled: Boolean = false,
        var ban_message: String = "You Died! You have been Banned for %death.ban.duration%.",
        var ban_duration: String = "-1",
        var ban_delay: Int = 0,
        var deaths_until_ban: Int = 1,
        var revive_ban: Boolean = true,
        var proximity_messages_enabled: Boolean = false,
        var proximity_range: Double = 500.0,
        var death_sound_range: Double = 500.0,
        var death_sound: String = "minecraft:entity.wither.spawn",
        var arrow_velocity: Double = 1500.0,
        var railgun_delay: String = "4s",

        var uuid_tracking_enabled: Boolean = false
    ) {
        data class IngredientEntry(
            var material: String = "",
            var amount: Int = 0
        )
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger("orbital-strike-mod-config")
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
        private val CONFIG_PATH: Path = FabricLoader.getInstance().configDir.resolve("orbital-strike-mod.json")
    }
}