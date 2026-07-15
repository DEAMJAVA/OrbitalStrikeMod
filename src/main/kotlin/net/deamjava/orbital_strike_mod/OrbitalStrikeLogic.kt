package net.deamjava.orbital_strike_mod

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import java.util.Random
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import net.minecraft.world.entity.Relative
import kotlin.math.pow

object OrbitalStrikeLogic {

    private val random = Random()

    fun summonNuke(world: Level, centerX: Int, centerY: Int, centerZ: Int, numRings: Int) {
        val baseFuse = 80
        val gravity = -0.03
        val velocityMultiplier = 1.4

        val baseRadii = intArrayOf(12, 22, 32, 42, 52, 62, 72, 82, 92, 102)
        val radii = IntArray(numRings) { i ->
            if (i < baseRadii.size) baseRadii[i]
            else 98 + (i - baseRadii.size + 1) * 10
        }

        spawnTnt(world, centerX + 0.5, centerY.toDouble(), centerZ + 0.5, baseFuse, 0.0, gravity, 0.0)

        for (ringIndex in radii.indices) {
            val r = radii[ringIndex]
            val tntsPerRing = when {
                ringIndex == 0 -> 84
                ringIndex == 1 -> 50
                ringIndex in 2..5 -> 70
                ringIndex == 6 -> 90
                else -> minOf(100, (r * 2 - ringIndex * 1.2).roundToInt())
            }

            repeat(tntsPerRing) {
                val angle = Math.random() * 2.0 * Math.PI
                val dx = cos(angle)
                val dz = sin(angle)
                val vx = dx * (r.toDouble() / baseFuse) * velocityMultiplier
                val vz = dz * (r.toDouble() / baseFuse) * velocityMultiplier
                spawnTnt(world, centerX + 0.5, centerY.toDouble(), centerZ + 0.5, baseFuse, vx, gravity, vz)
            }
        }
    }

    private fun spawnTnt(world: Level, x: Double, y: Double, z: Double, fuse: Int, vx: Double, vy: Double, vz: Double) {
        val tnt = PrimedTnt(world, x, y, z, null)
        tnt.fuse = fuse
        tnt.deltaMovement = Vec3(vx, vy, vz)
        world.addFreshEntity(tnt)
    }

    fun summonStab(world: Level, centerX: Int, centerZ: Int, groundY: Int) {
        val minY = world.minY
        val maxY = minY + world.height - 1

        for (y in maxY downTo minY) {
            val pos = BlockPos(centerX, y, centerZ)
            if (!world.getBlockState(pos).isAir) {
                repeat(2) {
                    val tnt = PrimedTnt(world, centerX + 0.5, y.toDouble(), centerZ + 0.5, null)
                    tnt.fuse = 0
                    world.addFreshEntity(tnt)
                }
            }
        }
    }

    fun summonWolves(player: ServerPlayer, amount: Int) {
        val world = player.level()
        val px = player.x
        val py = player.y
        val pz = player.z

        repeat(amount) {
            val wolf = Wolf(EntityTypes.WOLF, world)
            wolf.absSnapTo(px, py, pz, player.yRot, player.xRot)

            wolf.tame(player)
            wolf.isOrderedToSit = false

            wolf.setItemSlot(EquipmentSlot.BODY, ItemStack(Items.WOLF_ARMOR))

            wolf.addEffect(MobEffectInstance(MobEffects.SPEED, 3600, 1, false, true))
            wolf.addEffect(MobEffectInstance(MobEffects.STRENGTH, 3600, 1, false, true))
            wolf.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 9600, 0, false, true))

            val angle = Math.random() * 2.0 * Math.PI
            val pitch = (Math.random() - 0.5) * Math.PI * 0.5
            val hSpeed = 0.8
            val vx = cos(angle) * cos(pitch) * hSpeed
            val vy = sin(pitch) * 0.6 + 0.3
            val vz = sin(angle) * cos(pitch) * hSpeed
            wolf.deltaMovement = Vec3(vx, vy, vz)

            world.addFreshEntity(wolf)
        }
    }


    fun teleportStasis(player: ServerPlayer, x: Int, y: Int, z: Int) {
        val world = player.level()
        val yaw = player.yRot
        val pitch = player.xRot
        val cx = player.x
        val cy = player.y
        val cz = player.z

        world.playSound(null, cx, cy, cz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f)
        world.sendParticles(ParticleTypes.PORTAL, cx, cy + 1.0, cz, 50, 0.5, 0.5, 0.5, 0.5)

        val noRelatives: Set<Relative> = emptySet()
        player.teleportTo(world , x + 0.5, y.toDouble(), z + 0.5, noRelatives, yaw, pitch, false)

        world.playSound(null, x + 0.5, y.toDouble(), z + 0.5, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f)
        world.sendParticles(ParticleTypes.PORTAL, x + 0.5, (y + 1).toDouble(), z + 0.5, 50, 0.5, 0.5, 0.5, 0.5)
    }

    fun fireStraightRailgun(player: ServerPlayer, target: Location, spreadRadius: Double) {
        val world = player.level()
        val eye = player.eyePosition
        val dir = Vec3(
            target.x - eye.x,
            target.y - eye.y,
            target.z - eye.z
        ).normalize()
        val velocity = OrbitalStrikeMod.config.arrowVelocity

        repeat(45) {
            val ox = (random.nextDouble() - 0.5) * spreadRadius * 2.0
            val oy = (random.nextDouble() - 0.5) * spreadRadius * 2.0
            val oz = (random.nextDouble() - 0.5) * spreadRadius * 2.0

            val spawnPos = eye.add(dir.scale(3.0)).add(ox, oy, oz)

            val arrow = Arrow(world, spawnPos.x, spawnPos.y, spawnPos.z, player.mainHandItem, null)
            arrow.deltaMovement = dir.scale(velocity)
            arrow.owner = player
            arrow.isCritArrow = true
            arrow.setBaseDamage(1500.0)
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED
            world.addFreshEntity(arrow)
        }
    }

    fun summonWitherCannon(world: Level, centerX: Int, centerY: Int, centerZ: Int, size: Int) {
        val apexX = centerX + 0.5
        val apexY = centerY.toDouble()
        val apexZ = centerZ + 0.5

        val skullDensity = 4.0   // skulls per ring — higher = more skulls per ring
        val ringDensity  = 4.0   // rings per size unit — higher = more rings for the same size

        // Number of rings scales with both size and ringDensity
        val numRings = (size * ringDensity).toInt().coerceAtLeast(1)

        // Max cone angle capped at 75 degrees from vertical (= 15 degrees from ground)
        // size drives how wide the cone opens, capped at 75
        val maxAngle = (size * 3.0).coerceAtMost(75.0)

        spawnWitherSkull(world, apexX, apexY, apexZ, 0.0, -1.0, 0.0)

        for (ringIndex in 1..numRings) {
            val baseCount = when {
                ringIndex == 1 -> 8
                ringIndex == 2 -> 12
                ringIndex in 3..5 -> 16
                else -> 20
            }
            val skullsPerRing = (baseCount * skullDensity).toInt().coerceAtLeast(1)

            // Evenly distribute rings from 5 degrees up to maxAngle
            val coneAngle = Math.toRadians(
                5.0 + (ringIndex.toDouble() / numRings) * (maxAngle - 5.0)
            )

            val downSpeed = 1.0
            val lateralSpeed = Math.tan(coneAngle) * downSpeed

            repeat(skullsPerRing) { i ->
                val angle = (i.toDouble() / skullsPerRing) * 2.0 * Math.PI
                val vx = Math.cos(angle) * lateralSpeed
                val vz = Math.sin(angle) * lateralSpeed
                spawnWitherSkull(world, apexX, apexY, apexZ, vx, -downSpeed, vz)
            }
        }
    }

    private fun spawnWitherSkull(world: Level, x: Double, y: Double, z: Double, vx: Double, vy: Double, vz: Double) {
        val skull = net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull(
            net.minecraft.world.entity.EntityTypes.WITHER_SKULL,
            world
        )
        skull.setPos(x, y, z)
        skull.setDangerous(true)

        // Normalize direction
        val length = Math.sqrt(vx * vx + vy * vy + vz * vz).coerceAtLeast(0.0001)
        val nx = vx / length
        val ny = vy / length
        val nz = vz / length


        skull.deltaMovement = Vec3(nx * 5.0, ny * 5.0, nz * 5.0)

        world.addFreshEntity(skull)
    }

//    private fun spawnWitherSkull(world: Level, x: Double, y: Double, z: Double, vx: Double, vy: Double, vz: Double) {
//        val skull = net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull(
//            net.minecraft.world.entity.EntityType.WITHER_SKULL,
//            world
//        )
//        skull.setPos(x, y, z)
//        skull.setDangerous(true)
//        try {
//            val xField = net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile::class.java
//                .getDeclaredField("xPower")
//            val yField = net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile::class.java
//                .getDeclaredField("yPower")
//            val zField = net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile::class.java
//                .getDeclaredField("zPower")
//            xField.isAccessible = true
//            yField.isAccessible = true
//            zField.isAccessible = true
//            xField.set(skull, vx)
//            yField.set(skull, vy)
//            zField.set(skull, vz)
//        } catch (e: Exception) {
//            skull.deltaMovement = Vec3(vx, vy, vz)
//        }
//        world.addFreshEntity(skull)
//    }

    data class Location(val x: Double, val y: Double, val z: Double)
}

