package daio7771.everytunnel.chapter2.level1.core

import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/** A span of ticks, from [min] to [max]; [pick] draws one. */
data class Ch2L1Span(val min: Int, val max: Int) {
    fun pick(random: Random): Int = if (max > min) min + random.nextInt(max - min + 1) else min

    companion object {
        fun seconds(min: Double, max: Double) = Ch2L1Span((min * 20).toInt(), (max * 20).toInt())
    }
}

/** What a person's arms and head are doing, synced to the client, which draws it (`client.render.FactoryNpcRenderer`). */
enum class Ch2L1Pose {
    NONE,

    /** At a station: arms out to the keyboard. */
    TYPING,

    /** Talking to someone (no voice): the hands move with the words. */
    TALKING,

    /** Listening to someone: a nod now and then. */
    LISTENING;

    companion object {
        fun of(ordinal: Int): Ch2L1Pose = values().getOrElse(ordinal) { NONE }
    }
}

/**
 * What makes the factory's people look alive rather than walk like machines, from `npcs.json`'s `life` (every key optional):
 *
 * ```json
 * "life": {
 *   "pace_spread": 0.15, "accel_seconds": 0.6, "turn_degrees": 9,
 *   "linger_chance": 0.12, "linger_seconds": [0.6, 1.8],
 *   "glance_seconds": [2, 5],
 *   "player_range": 4.5, "player_look_seconds": [1.5, 3], "player_cooldown_seconds": [8, 16],
 *   "chat_range": 2.4, "chat_chance": 0.4, "chat_seconds": [4, 8], "chat_cooldown_seconds": [30, 60]
 * }
 * ```
 *
 * - **Walking** ([Ch2L1Gait]): each worker walks at their own pace, `walk_speed` times 1 ± up to [paceSpread] (drawn from their id, or the
 *   worker's own `pace`); they ease into it over [accelTicks] and brake before they stop; they turn their body at most [turnDegrees] a
 *   tick, slowing down for a sharp turn; at each point of a path they may stop a moment ([linger], for [lingerTicks]).
 * - **Looking** ([Ch2L1Gaze]): standing, the head turns to look somewhere new every [glanceTicks] (around, at a colleague nearby);
 *   a player who comes within [playerRange] in front of them is followed with the eyes for [playerLookTicks], then not again for
 *   [playerCooldownTicks].
 * - **Talking** ([Ch2L1Chat]): two workers who pass or stand within [chatRange] of each other, neither at a station, stop
 *   ([chatChance] of the times) to face each other and talk with their hands for [chatTicks]; then neither talks again for [chatCooldownTicks].
 */
data class Ch2L1Life(
    val paceSpread: Double = 0.15,
    val accelTicks: Int = 12,
    val turnDegrees: Float = 9f,
    val linger: Double = 0.12,
    val lingerTicks: Ch2L1Span = Ch2L1Span.seconds(0.6, 1.8),
    val glanceTicks: Ch2L1Span = Ch2L1Span.seconds(2.0, 5.0),
    val playerRange: Double = 4.5,
    val playerLookTicks: Ch2L1Span = Ch2L1Span.seconds(1.5, 3.0),
    val playerCooldownTicks: Ch2L1Span = Ch2L1Span.seconds(8.0, 16.0),
    val chatRange: Double = 2.4,
    val chatChance: Double = 0.4,
    val chatTicks: Ch2L1Span = Ch2L1Span.seconds(4.0, 8.0),
    val chatCooldownTicks: Ch2L1Span = Ch2L1Span.seconds(30.0, 60.0),
) {
    /** A worker's own pace, a multiple of `walk_speed`: [own] if the file gives one, otherwise 1 ± up to [paceSpread], drawn from [seed]. */
    fun pace(own: Double?, seed: Long): Double = own ?: (1.0 + (Random(seed).nextDouble() * 2 - 1) * paceSpread)

    companion object {
        val DEFAULT = Ch2L1Life()
    }
}

/** Angles of Minecraft's: a yaw of 0 faces +z, 90 faces -x; a positive pitch looks down. */
object Ch2L1Angles {
    /** The yaw that faces the way ([dx], [dz]). */
    fun yawOf(dx: Double, dz: Double): Float = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()

    /** The pitch that looks [dy] up over [horizontal] blocks. */
    fun pitchOf(dy: Double, horizontal: Double): Float = (-Math.toDegrees(atan2(dy, horizontal))).toFloat()

    /** [degrees] brought to -180 .. 180. */
    fun wrap(degrees: Float): Float {
        var d = degrees % 360f
        if (d >= 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    /** [from] turned toward [to] by [ease] of the way, at most [most] degrees; [to] itself once within half a degree. */
    fun ease(from: Float, to: Float, ease: Float, most: Float): Float {
        val d = wrap(to - from)
        if (abs(d) < 0.5f) return to
        return from + (d * ease).coerceIn(-most, most)
    }
}

/**
 * How a person walks, one tick at a time: they ease into their pace ([cruise] blocks a tick, reached after [accelTicks]), brake as they
 * near the place they stop at, slow down for a sharp turn and turn their body at most [turnDegrees] a tick, a little less as they line
 * up. They always move straight at the point (so they never stray from their path into a wall); only how fast changes. Plain Kotlin.
 */
class Ch2L1Gait(private val cruise: Double, private val accelTicks: Int, private val turnDegrees: Float) {

    /** Their speed now, blocks a tick. */
    var speed = 0.0
        private set

    /** One tick: the velocity along the floor and the body's new yaw. */
    data class Step(val vx: Double, val vz: Double, val yaw: Float)

    /**
     * A tick walking at a point ([dx], [dz]) away while facing [yaw]; [stopIn] is how far it is to where they will stop (null: they go on
     * past this point without stopping).
     */
    fun walk(yaw: Float, dx: Double, dz: Double, stopIn: Double?): Step {
        val distance = sqrt(dx * dx + dz * dz)
        if (distance < 1.0e-4) return Step(0.0, 0.0, yaw)
        val heading = Ch2L1Angles.yawOf(dx, dz)
        val off = abs(Ch2L1Angles.wrap(heading - yaw))
        var target = cruise * corner(off)
        if (stopIn != null) target = min(target, cruise * (stopIn / BRAKE).coerceIn(SLOWEST, 1.0))
        val up = cruise / accelTicks.coerceAtLeast(1)
        speed = if (target > speed) min(target, speed + up) else maxOf(target, speed - up * 2)
        val step = min(speed, distance)
        return Step(dx / distance * step, dz / distance * step, turn(yaw, heading))
    }

    /** A tick standing still, turning to [face] if it is not null: the body's new yaw. */
    fun stand(yaw: Float, face: Float?): Float {
        speed = 0.0
        return if (face == null) yaw else turn(yaw, face)
    }

    private fun turn(yaw: Float, to: Float): Float = Ch2L1Angles.ease(yaw, to, TURN_EASE, turnDegrees)

    /** How much of their pace they keep while their body is [off] degrees away from where they are going. */
    private fun corner(off: Float): Double = when {
        off <= STRAIGHT -> 1.0
        off >= SHARP -> SLOWEST
        else -> 1.0 - (off - STRAIGHT) / (SHARP - STRAIGHT) * (1.0 - SLOWEST)
    }

    companion object {
        /** Within this many blocks of a stop they start to brake. */
        const val BRAKE = 1.0

        /** The least of their pace they keep when braking or turning hard. */
        const val SLOWEST = 0.25

        /** Up to this many degrees off their way they walk at their full pace; at [SHARP] or more, at [SLOWEST]. */
        const val STRAIGHT = 25f
        const val SHARP = 100f

        /** The share of what is left of a turn they make in a tick (before [turnDegrees] caps it). */
        const val TURN_EASE = 0.35f
    }
}

/** A place in the world, a person's eyes or a player's. */
data class Ch2L1Point(val x: Double, val y: Double, val z: Double)

/**
 * Where a person looks: their head's [headYaw] and [pitch], eased a little every tick toward a target that changes with what they are
 * doing ([Mode]) and with who is near. Plain Kotlin: told every tick where their body faces, where their eyes are and who is around.
 *
 * - **Walking**: mostly ahead; now and then a short glance to a side.
 * - **Standing**: somewhere new every `glance` span: a colleague nearby, a side, or ahead.
 * - **Typing**: down at the keyboard; now and then they look up a moment.
 * - **Talking**: at whoever they talk to.
 * - **A player close in front** (within `player_range`, less than [SIGHT] degrees off the way the body faces): followed with the eyes for a
 *   moment, then not again until the cooldown is over. Not while talking.
 *
 * The head never turns more than [MOST] degrees away from the body.
 */
class Ch2L1Gaze(private val life: Ch2L1Life, private val random: Random) {

    enum class Mode { WALKING, STANDING, TYPING, TALKING }

    var headYaw = 0f
        private set
    var pitch = 0f
        private set

    /** Where they mean to look: ahead of the body, [offset] degrees aside, or at a [point]. */
    private sealed interface Aim {
        data class Ahead(val offset: Float, val pitch: Float) : Aim
        data class At(val point: Ch2L1Point) : Aim
    }

    private var aim: Aim = Aim.Ahead(0f, 0f)
    private var mode: Mode? = null
    private var timer = 0
    private var watching = 0
    private var cooldown = 0
    private var started = false

    /**
     * One tick. [bodyYaw]: where the body faces; [eyes]: where the head is; [player]: the eyes of the nearest player within reach (null:
     * none); [partner]: the eyes of whoever they are talking to; [around]: where the eyes of the people nearby are, asked only when a
     * standing person picks somewhere new to look.
     */
    fun tick(bodyYaw: Float, eyes: Ch2L1Point, mode: Mode, player: Ch2L1Point?, partner: Ch2L1Point?, around: () -> List<Ch2L1Point>) {
        if (!started) {
            headYaw = bodyYaw
            started = true
        }
        if (cooldown > 0) cooldown--
        val desired = when {
            mode == Mode.TALKING -> {
                if (watching > 0) endWatching()
                this.mode = mode
                partner?.let { Aim.At(it) } ?: Aim.Ahead(0f, 0f)
            }
            watchPlayer(bodyYaw, eyes, player) -> Aim.At(player!!)
            else -> {
                if (mode != this.mode || --timer <= 0) choose(mode, eyes, around)
                this.mode = mode
                aim
            }
        }
        val (yaw, wantPitch) = when (desired) {
            is Aim.Ahead -> (bodyYaw + desired.offset) to desired.pitch
            is Aim.At -> lookAt(eyes, desired.point)
        }
        val limited = bodyYaw + Ch2L1Angles.wrap(yaw - bodyYaw).coerceIn(-MOST, MOST)
        headYaw = Ch2L1Angles.ease(headYaw, limited, EASE, HEAD_TURN)
        pitch += (wantPitch.coerceIn(-40f, 50f) - pitch).times(EASE).coerceIn(-PITCH_TURN, PITCH_TURN)
    }

    /** True while they follow [player] with their eyes: started when a player is close in front and the cooldown is over. */
    private fun watchPlayer(bodyYaw: Float, eyes: Ch2L1Point, player: Ch2L1Point?): Boolean {
        val inSight = player != null && distance(eyes, player) <= life.playerRange &&
            abs(Ch2L1Angles.wrap(lookAt(eyes, player).first - bodyYaw)) <= SIGHT
        if (watching > 0) {
            if (inSight && --watching > 0) return true
            endWatching()
            return false
        }
        if (!inSight || cooldown > 0) return false
        watching = life.playerLookTicks.pick(random).coerceAtLeast(1)
        return true
    }

    /** The end of following a player with the eyes: the cooldown starts and they look somewhere new. */
    private fun endWatching() {
        watching = 0
        cooldown = life.playerCooldownTicks.pick(random)
        timer = 0
    }

    private fun choose(mode: Mode, eyes: Ch2L1Point, around: () -> List<Ch2L1Point>) {
        val roll = random.nextDouble()
        when (mode) {
            Mode.WALKING -> if (roll < 0.25) {
                aim = Aim.Ahead(side(15f, 35f), 0f)
                timer = 15 + random.nextInt(16)
            } else {
                aim = Aim.Ahead(0f, random.nextFloat() * 8f)
                timer = 40 + random.nextInt(61)
            }
            Mode.TYPING -> if (roll < 0.2) {
                aim = Aim.Ahead(side(10f, 45f), random.nextFloat() * 5f)
                timer = 20 + random.nextInt(21)
            } else {
                aim = Aim.Ahead(0f, KEYBOARD)
                timer = 80 + random.nextInt(121)
            }
            else -> {
                val people = if (roll < 0.4) around().filter { distance(eyes, it) > 0.5 } else emptyList()
                aim = when {
                    people.isNotEmpty() -> Aim.At(people[random.nextInt(people.size)])
                    roll < 0.85 -> Aim.Ahead(side(10f, 60f), random.nextFloat() * 20f - 5f)
                    else -> Aim.Ahead(0f, random.nextFloat() * 10f)
                }
                timer = life.glanceTicks.pick(random).coerceAtLeast(1)
            }
        }
    }

    /** An angle between [least] and [most] degrees, to the left or the right. */
    private fun side(least: Float, most: Float): Float =
        (least + random.nextFloat() * (most - least)) * if (random.nextBoolean()) 1f else -1f

    private fun lookAt(eyes: Ch2L1Point, point: Ch2L1Point): Pair<Float, Float> {
        val dx = point.x - eyes.x
        val dz = point.z - eyes.z
        return Ch2L1Angles.yawOf(dx, dz) to Ch2L1Angles.pitchOf(point.y - eyes.y, sqrt(dx * dx + dz * dz))
    }

    private fun distance(a: Ch2L1Point, b: Ch2L1Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        /** The most the head turns away from the body, degrees. */
        const val MOST = 70f

        /** How far off the way the body faces a player can be and still be seen, degrees. */
        const val SIGHT = 100f

        /** How far down a typist looks, at the keyboard. */
        const val KEYBOARD = 25f

        /** The share of what is left the head turns in a tick, and the most it turns (yaw, pitch). */
        const val EASE = 0.2f
        const val HEAD_TURN = 10f
        const val PITCH_TURN = 6f
    }
}

/**
 * A talk between two people (no voice): it lasts the ticks it is given, and who talks and who listens swaps every 1.5 to 3.5 seconds.
 * [tick] once a tick for the pair; false once it is over. Plain Kotlin.
 */
class Ch2L1Chat(ticks: Int, private val random: Random) {

    /** True while the first of the two talks (and the second listens). */
    var firstTalks = random.nextBoolean()
        private set

    private var left = ticks
    private var swapIn = turn()

    fun tick(): Boolean {
        if (--swapIn <= 0) {
            firstTalks = !firstTalks
            swapIn = turn()
        }
        return --left > 0
    }

    private fun turn(): Int = 30 + random.nextInt(41)

    /** Someone free to talk, at ([x], [z]). */
    data class Person(val id: Int, val x: Double, val z: Double)

    companion object {
        /** Pairs of [people] within [range] of each other, the nearest first; no one in two pairs. */
        fun pairs(people: List<Person>, range: Double): List<Pair<Int, Int>> {
            val close = mutableListOf<Triple<Double, Person, Person>>()
            for (i in people.indices) for (j in i + 1 until people.size) {
                val a = people[i]
                val b = people[j]
                val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z))
                if (d <= range) close += Triple(d, a, b)
            }
            val taken = mutableSetOf<Int>()
            val pairs = mutableListOf<Pair<Int, Int>>()
            for ((_, a, b) in close.sortedBy { it.first }) {
                if (a.id in taken || b.id in taken) continue
                taken += a.id
                taken += b.id
                pairs += a.id to b.id
            }
            return pairs
        }
    }
}
