package daio7771.everytunnel.client.hud

import daio7771.everytunnel.client.AchievementIcons
import daio7771.everytunnel.client.AchievementStore
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.platform.AchievementHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.toast.Toast
import net.minecraft.client.toast.ToastManager
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.math.MathHelper

/**
 * The "Achievement Unlocked!" toast, for each achievement the server says was earned
 * (`platform.AchievementHooks`), every time; the achievement is also remembered for good
 * ([AchievementStore]). It is drawn the way vanilla draws a challenge advancement in 1.20.1
 * (`AdvancementToast`): the advancement frame, the first line in challenge purple, the
 * achievement's title in white under it, its icon on the left ([AchievementIcons]), and the
 * challenge fanfare (`ui.toast.challenge_complete`) when it appears; gone after [DISPLAY_MS].
 * A title too long for one line shows the first line alone for [TITLE_MS], then the title, as
 * vanilla does.
 *
 * The texts are lang keys: `toast.every_tunnel.achievement_unlocked` and
 * `achievement.every_tunnel.<id>` ([Achievement.id]).
 */
class AchievementToast(private val achievement: Achievement) : Toast {

    private var soundPlayed = false

    override fun draw(context: DrawContext, manager: ToastManager, startTime: Long): Toast.Visibility {
        val font = manager.client.textRenderer
        context.drawTexture(Toast.TEXTURE, 0, 0, 0, 0, width, height)
        val heading = Text.translatable("toast.every_tunnel.achievement_unlocked")
        val lines = font.wrapLines(title(achievement), TEXT_WIDTH)
        if (lines.size == 1) {
            context.drawText(font, heading, TEXT_X, 7, TITLE_COLOR or OPAQUE, false)
            context.drawText(font, lines[0], TEXT_X, 18, -1, false)
        } else if (startTime < TITLE_MS) {
            val alpha = MathHelper.floor(MathHelper.clamp((TITLE_MS - startTime) / FADE_MS, 0f, 1f) * 255f) shl 24 or 0x4000000
            context.drawText(font, heading, TEXT_X, 11, TITLE_COLOR or alpha, false)
        } else {
            val alpha = MathHelper.floor(MathHelper.clamp((startTime - TITLE_MS) / FADE_MS, 0f, 1f) * 252f) shl 24 or 0x4000000
            var y = height / 2 - lines.size * font.fontHeight / 2
            for (line in lines) {
                context.drawText(font, line, TEXT_X, y, 0xFFFFFF or alpha, false)
                y += font.fontHeight
            }
        }
        if (!soundPlayed && startTime > 0L) {
            soundPlayed = true
            manager.client.soundManager.play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f))
        }
        AchievementIcons.draw(context, achievement, 8, 8)
        return if (startTime >= DISPLAY_MS) Toast.Visibility.HIDE else Toast.Visibility.SHOW
    }

    companion object {
        /** Vanilla's challenge purple (`AdvancementToast`). */
        private const val TITLE_COLOR = 0xFF88FF
        private const val OPAQUE = -0x1000000
        private const val TEXT_X = 30
        private const val TEXT_WIDTH = 125
        private const val DISPLAY_MS = 5000L
        private const val TITLE_MS = 1500L
        private const val FADE_MS = 300f

        fun title(achievement: Achievement): Text = Text.translatable("achievement.every_tunnel.${achievement.id}")

        fun description(achievement: Achievement): Text = Text.translatable("achievement.every_tunnel.${achievement.id}.description")

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(AchievementHooks.PACKET_ID) { client, _, buf, _ ->
                val achievement = Achievement.byId(buf.readString()) ?: return@registerGlobalReceiver
                client.execute {
                    AchievementStore.unlock(achievement)
                    client.toastManager.add(AchievementToast(achievement))
                }
            }
        }
    }
}
