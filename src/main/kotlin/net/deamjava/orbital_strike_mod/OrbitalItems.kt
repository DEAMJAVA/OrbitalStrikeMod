package net.deamjava.orbital_strike_mod

import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ChargedProjectiles
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.nbt.CompoundTag
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import net.minecraft.world.InteractionResult

object OrbitalItems {

    private const val DEFAULT_RINGS = 10

    fun createStabShot(): ItemStack =
        ItemStack(Items.FISHING_ROD).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Stab Shot"))
            set(DataComponents.DAMAGE, maxDamage - 1)
            set(DataComponents.CUSTOM_DATA, CustomData.of(buildTag {
                putString("item_uuid", UUID.randomUUID().toString())
            }))
        }

    fun createNukeShot(ringSize: Int? = null): ItemStack =
        ItemStack(Items.FISHING_ROD).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Nuke Shot"))
            set(DataComponents.DAMAGE, maxDamage - 1)
            set(DataComponents.CUSTOM_DATA, CustomData.of(buildTag {
                putString("item_uuid", UUID.randomUUID().toString())
                ringSize?.let { putInt("nuke_ring_size", it) }
            }))
        }

    fun createWolfShot(wolfAmount: Int): ItemStack =
        ItemStack(Items.FISHING_ROD).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Orbital Wolf"))
            set(DataComponents.DAMAGE, maxDamage - 1)
            set(DataComponents.CUSTOM_DATA, CustomData.of(buildTag {
                putString("item_uuid", UUID.randomUUID().toString())
                putInt("wolf_amount", wolfAmount)
            }))
        }

    fun createStasis(x: Int, y: Int, z: Int): ItemStack =
        ItemStack(Items.FISHING_ROD).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Stasis"))
            set(DataComponents.DAMAGE, maxDamage - 1)
            set(DataComponents.CUSTOM_DATA, CustomData.of(buildTag {
                putString("item_uuid", UUID.randomUUID().toString())
                putInt("stasis_x", x)
                putInt("stasis_y", y)
                putInt("stasis_z", z)
            }))
        }

    fun createRailgun(type: String, radius: Double): ItemStack =
        ItemStack(Items.CROSSBOW).apply {
            val displayName = if (type.equals("orbital", ignoreCase = true)) "Orbital Railgun" else "Railgun"
            set(DataComponents.CUSTOM_NAME, Component.literal(displayName))
            set(DataComponents.DAMAGE, maxDamage - 1)
            set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(ItemStack(Items.ARROW)))
            set(DataComponents.CUSTOM_DATA, CustomData.of(buildTag {
                putString("item_uuid", UUID.randomUUID().toString())
                putString("railgun_type", type)
                putDouble("railgun_radius", radius)
            }))
        }
    

    fun registerItemUsageHandler() {
        UseItemCallback.EVENT.register { player, world, hand ->
            if (world.isClientSide || player !is ServerPlayer) {
                return@register InteractionResult.PASS
            }

            val stack = player.getItemInHand(hand)

            when (stack.item) {
                Items.CROSSBOW     -> handleCrossbowUse(player, stack, hand)
                Items.FISHING_ROD  -> handleFishingRodUse(player, world as ServerLevel, stack, hand)
                else               -> InteractionResult.PASS
            }
        }
    }
    
    private fun handleCrossbowUse(
        player: ServerPlayer,
        stack: ItemStack,
        hand: InteractionHand,
    ): InteractionResult {
        val name = stack.get(DataComponents.CUSTOM_NAME)
            ?: return InteractionResult.PASS
        if (name.getString() != "Railgun") return InteractionResult.PASS

        val charged = stack.get(DataComponents.CHARGED_PROJECTILES)
        if (charged != null && !charged.isEmpty) {
            handleRailgunFired(player, stack, hand)
            return InteractionResult.CONSUME
        }
        return InteractionResult.PASS
    }

    private fun handleFishingRodUse(
        player: ServerPlayer,
        world: ServerLevel,
        stack: ItemStack,
        hand: InteractionHand,
    ): InteractionResult {
        val name = stack.get(DataComponents.CUSTOM_NAME)?.getString()
            ?: return InteractionResult.PASS

        if (name !in setOf("Stab Shot", "Nuke Shot", "Orbital Wolf", "Stasis")) {
            return InteractionResult.PASS
        }

        val nbt = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
            ?: return InteractionResult.PASS

        if (!nbt.contains("item_uuid")) return InteractionResult.PASS

        val uuidString = nbt.getString("item_uuid")
        if (uuidString.isEmpty) return InteractionResult.PASS

        val itemUUID = UUID.fromString(uuidString.get())
        if (OrbitalStrikeMod.config.isUuidTrackingEnabled && OrbitalStrikeMod.usedItems.contains(itemUUID)) {
            return InteractionResult.PASS
        }

        return when (name) {
            "Orbital Wolf" -> handleOrbitalWolf(player, itemUUID, nbt, hand, stack)
            "Stasis"       -> handleStasis(player, world, itemUUID, nbt, hand, stack)
            else           -> handleProjectileShot(player, world, itemUUID, nbt, name, hand, stack)
        }
    }

    private fun handleProjectileShot(
        player: ServerPlayer,
        world: ServerLevel,
        itemUUID: UUID,
        nbt: CompoundTag,
        itemName: String,
        hand: InteractionHand,
        stack: ItemStack,
    ): InteractionResult {
        val hitResult = player.pick(500.0, 0f, false)
        if (hitResult.type != HitResult.Type.BLOCK) {
            player.displayClientMessage(
                Component.literal("No block in crosshair!").withStyle(ChatFormatting.RED),
                false
            )
            return InteractionResult.PASS
        }

        val pos = (hitResult as BlockHitResult).blockPos
        val targetX = pos.x
        val targetZ = pos.z
        val groundY = pos.y

        return if (!OrbitalStrikeMod.fishingRodCastState.containsKey(itemUUID)) {
            OrbitalStrikeMod.fishingRodCastState[itemUUID] = true
            val delayMs = (OrbitalStrikeMod.config.rodDelay * 1000).toLong()

            when (itemName) {
                "Nuke Shot" -> {
                    val ringSize = if (nbt.contains("nuke_ring_size") && nbt.getInt("nuke_ring_size").isPresent)
                        nbt.getInt("nuke_ring_size").get()
                    else
                        DEFAULT_RINGS
                    Timer().schedule(object : TimerTask() {
                        override fun run() {
                            world.server.execute {
                                OrbitalStrikeLogic.summonNuke(world, targetX, groundY + 72, targetZ, ringSize)
                            }
                        }
                    }, delayMs)
                }
                "Stab Shot" -> {
                    Timer().schedule(object : TimerTask() {
                        override fun run() {
                            world.server.execute {
                                OrbitalStrikeLogic.summonStab(world, targetX, targetZ, groundY)
                            }
                        }
                    }, delayMs)
                }
            }
            InteractionResult.PASS
        } else {
            OrbitalStrikeMod.usedItems.add(itemUUID)
            OrbitalStrikeMod.fishingRodCastState.remove(itemUUID)
            breakItem(player, hand, stack)
            InteractionResult.SUCCESS
        }
    }

    private fun handleOrbitalWolf(
        player: ServerPlayer,
        itemUUID: UUID,
        nbt: CompoundTag,
        hand: InteractionHand,
        stack: ItemStack,
    ): InteractionResult {
        return if (!OrbitalStrikeMod.fishingRodCastState.containsKey(itemUUID)) {
            OrbitalStrikeMod.fishingRodCastState[itemUUID] = true
            val wolfAmount = nbt.getInt("wolf_amount").let { if (it.isPresent && it.get() > 0) it.get() else 150 }
            val serverLevel = player.level() as ServerLevel
            val delayMs = (OrbitalStrikeMod.config.rodDelay * 1000).toLong()

            serverLevel.server.execute {
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        serverLevel.server.execute {
                            OrbitalStrikeLogic.summonWolves(player, wolfAmount)
                        }
                    }
                }, delayMs)
            }
            InteractionResult.PASS
        } else {
            OrbitalStrikeMod.usedItems.add(itemUUID)
            OrbitalStrikeMod.fishingRodCastState.remove(itemUUID)
            breakItem(player, hand, stack)
            InteractionResult.SUCCESS
        }
    }

    private fun handleStasis(
        player: ServerPlayer,
        world: ServerLevel,
        itemUUID: UUID,
        nbt: CompoundTag,
        hand: InteractionHand,
        stack: ItemStack,
    ): InteractionResult {
        if (OrbitalStrikeMod.fishingRodCastState.containsKey(itemUUID)) {
            OrbitalStrikeMod.usedItems.add(itemUUID)
            OrbitalStrikeMod.fishingRodCastState.remove(itemUUID)
            breakItem(player, hand, stack)
            return InteractionResult.SUCCESS
        }

        OrbitalStrikeMod.fishingRodCastState[itemUUID] = true
        val storedX: Int = nbt.getInt("stasis_x").let { if (it.isPresent) it.get() else Int.MIN_VALUE }
        val storedY: Int = nbt.getInt("stasis_y").let { if (it.isPresent) it.get() else Int.MIN_VALUE }
        val storedZ: Int = nbt.getInt("stasis_z").let { if (it.isPresent) it.get() else Int.MIN_VALUE }

        val hitResult = player.pick(10000.0, 0f, false)
        if (hitResult.type == HitResult.Type.BLOCK) {


            val crosshairPos = (hitResult as BlockHitResult).blockPos
            val finalX: Int = if (storedX == Int.MIN_VALUE) crosshairPos.x else storedX
            val finalY: Int = if (storedY == Int.MIN_VALUE) crosshairPos.y else storedY
            val finalZ: Int = if (storedZ == Int.MIN_VALUE) crosshairPos.z else storedZ
            val delayMs = (OrbitalStrikeMod.config.rodDelay * 1000).toLong()

            world.server.execute {
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        world.server.execute {
                            OrbitalStrikeLogic.teleportStasis(player, finalX, finalY, finalZ)
                        }
                    }
                }, delayMs)
            }
        } else if (storedX == Int.MIN_VALUE || storedY == Int.MIN_VALUE || storedZ == Int.MIN_VALUE) {
            player.displayClientMessage(
                Component.literal("No block in crosshair to determine coordinates!").withStyle(ChatFormatting.BLACK),
                false
            )
        }

        return InteractionResult.PASS
    }

    private fun handleRailgunFired(player: ServerPlayer, crossbow: ItemStack, hand: InteractionHand) {
        val nbt = crossbow.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return
        if (!nbt.contains("railgun_type")) return

        val radius: Double = nbt.getDouble("railgun_radius").let { if (it.isPresent && it.get() > 0.0) it.get() else 1.0 }
        val hitResult = player.pick(10000.0, 0f, false)
        if (hitResult.type != HitResult.Type.BLOCK) return

        val targetPos = (hitResult as BlockHitResult).blockPos
        val targetLoc = OrbitalStrikeLogic.Location(
            targetPos.x.toDouble(),
            targetPos.y.toDouble(),
            targetPos.z.toDouble()
        )
        val serverLevel = player.level() as ServerLevel
        val delayMs = OrbitalStrikeMod.config
            .parseRailgunDelayToMillis(OrbitalStrikeMod.config.railgunDelay)

        serverLevel.server.execute {
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    serverLevel.server.execute {
                        OrbitalStrikeLogic.fireStraightRailgun(player, targetLoc, radius)
                    }
                }
            }, delayMs)
        }
        
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
        player.awardStat(Stats.ITEM_USED.get(crossbow.item))
        breakItem(player, hand, crossbow)
    }

    private fun breakItem(player: ServerPlayer, hand: InteractionHand, stack: ItemStack) {
        player.awardStat(Stats.ITEM_BROKEN.get(stack.item))
        if (hand == InteractionHand.MAIN_HAND) {
            player.setItemInHand(hand, ItemStack.EMPTY)
        } else {
            player.setItemInHand(hand, ItemStack.EMPTY)
        }
    }

    private inline fun buildTag(block: CompoundTag.() -> Unit): CompoundTag =
        CompoundTag().apply(block)
}