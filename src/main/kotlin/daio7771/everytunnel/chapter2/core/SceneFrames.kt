package daio7771.everytunnel.chapter2.core

import daio7771.everytunnel.core.cutscene.Local
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.level.Position
import kotlin.math.cos
import kotlin.math.sin

/** [position] as the scene's someone sees it: so far ahead, to their right, up (the inverse of [SceneFrame.at]). Shared by Chapter 2's scenes. */
fun SceneFrame.toLocal(position: Position): Local {
    val r = Math.toRadians(yaw)
    val fx = -sin(r)
    val fz = cos(r)
    val dx = position.x - x
    val dz = position.z - z
    return Local(dx * fx + dz * fz, -dx * fz + dz * fx, position.y - y)
}
