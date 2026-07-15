package net.deamjava.orbital_strike_mod

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.core.registries.BuiltInRegistries

object OrbitalStrikeRecipes {

    fun register() {
        OrbitalStrikeMod.LOGGER.info("Orbital Strike Mod Recipes initialized")
    }

    fun hasNukeMaterials(player: Player): Boolean {
        val ingredients = OrbitalStrikeMod.config.nukeIngredients
        return ingredients.all { (item, required) -> countItem(player, item) >= required }
    }

    fun hasStabMaterials(player: Player): Boolean {
        val ingredients = OrbitalStrikeMod.config.stabIngredients
        return ingredients.all { (item, required) -> countItem(player, item) >= required }
    }

    fun consumeNukeMaterials(player: Player) {
        OrbitalStrikeMod.config.nukeIngredients.forEach { (item, amount) ->
            removeItems(player, item, amount)
        }
    }

    fun consumeStabMaterials(player: Player) {
        OrbitalStrikeMod.config.stabIngredients.forEach { (item, amount) ->
            removeItems(player, item, amount)
        }
    }

    private fun countItem(player: Player, item: Item): Int {
        var count = 0
        val inventory = player.inventory
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (stack.`is`(item)) count += stack.count
        }
        return count
    }

    private fun removeItems(player: Player, item: Item, amount: Int) {
        var remaining = amount
        val inventory = player.inventory
        for (i in 0 until inventory.containerSize) {
            if (remaining <= 0) break
            val stack = inventory.getItem(i)
            if (stack.`is`(item)) {
                val toRemove = minOf(remaining, stack.count)
                stack.shrink(toRemove)
                remaining -= toRemove
            }
        }
    }

    fun showRecipes(player: Player) {
        val config = OrbitalStrikeMod.config

        player.sendSystemMessage(Component.literal("=== Orbital Strike Crafting Recipes ===")
            .withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD))
        player.sendSystemMessage(Component.literal(""))

        player.sendSystemMessage(Component.literal("Nuke Shot:")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
        val nukeRecipe = config.nukeIngredients.entries.joinToString(", ") { (item, amount) ->
            "x$amount ${formatItemName(item)}"
        }
        player.sendSystemMessage(Component.literal("  $nukeRecipe")
            .withStyle(ChatFormatting.WHITE))
        player.sendSystemMessage(Component.literal("  Command: /craftf nuke")
            .withStyle(ChatFormatting.GREEN))

        player.sendSystemMessage(Component.literal(""))

        player.sendSystemMessage(Component.literal("Stab Shot:")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
        val stabRecipe = config.stabIngredients.entries.joinToString(", ") { (item, amount) ->
            "x$amount ${formatItemName(item)}"
        }
        player.sendSystemMessage(Component.literal("  $stabRecipe")
            .withStyle(ChatFormatting.WHITE))
        player.sendSystemMessage(Component.literal("  Command: /craftf stab")
            .withStyle(ChatFormatting.GREEN))
    }

    private fun formatItemName(item: Item): String {
        val itemId = BuiltInRegistries.ITEM.getKey(item)?.path ?: return "Unknown"
        return itemId.split("_").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }
}