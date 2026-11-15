package daio7771.everytunnel.chapter2.level1.core

/**
 * Checks that what `tasks.json`, `speakers.json`, `npcs.json` and `cutscenes/meeting.json` name is in `anchors.json`: a task whose block or place is missing, a speaker
 * zone with no box or no emitter, a camera of the first announcement's cinematic that has no anchor. Plain Kotlin; the data
 * loader reports what it finds on every load and `/et reload`, and `Ch2L1WiringTest` holds the jar's files to it.
 */
object Ch2L1Wiring {

    /** One line per thing that does not match; empty when everything the files name exists. */
    fun problems(
        anchors: Ch2L1Anchors, tasks: Ch2L1Tasks, speakers: Ch2L1Speakers, npcs: Ch2L1NpcData = Ch2L1NpcData.EMPTY, act2: Ch2L1Act2? = null,
    ): List<String> {
        val out = mutableListOf<String>()
        fun point(file: String, id: String) {
            if (anchors.point(id) == null) out += "$file names the anchor \"$id\", which anchors.json does not have"
        }
        for (tube in tasks.tubes) point("tasks.json", tube.anchor)
        point("tasks.json", tasks.terminalAnchor)
        point("tasks.json", tasks.analyzerAnchor)
        point("tasks.json", tasks.broomAnchor)
        val dirt = anchors.path(tasks.dirtPath)
        when {
            dirt == null -> out += "tasks.json names the path \"${tasks.dirtPath}\", which anchors.json does not have"
            dirt.points.size > Ch2L1Tasks.MAX_DIRT -> out += "the path \"${tasks.dirtPath}\" has ${dirt.points.size} points: at most ${Ch2L1Tasks.MAX_DIRT} spots of dirt"
        }
        for (zone in speakers.zones) {
            if (anchors.zone(zone) == null) out += "speakers.json names the zone \"$zone\", which anchors.json does not have"
            val emitter = speakers.emitters[zone]
            if (emitter == null || anchors.point(emitter) == null) out += "the speaker of the zone \"$zone\" has no anchor (\"${emitter ?: "none"}\")"
        }
        speakers.intro?.shots?.forEach { shot ->
            if (shot.camera != Ch2L1IntroScene.PLAYER) point("speakers.json", shot.camera)
            shot.cameraTo?.takeIf { it != Ch2L1IntroScene.PLAYER }?.let { point("speakers.json", it) }
            point("speakers.json", shot.look)
        }
        for (worker in npcs.workers) Ch2L1Routines.resolve(worker, anchors, out)
        for ((group, members) in npcs.groups) {
            for (member in members) point("npcs.json (group $group)", member.at)
        }
        if (act2 != null) {
            val file = "cutscenes/meeting.json"
            fun path(id: String) {
                if (anchors.path(id) == null) out += "$file names the path \"$id\", which anchors.json does not have"
            }
            point(file, act2.follow.waitAnchor)
            path(act2.follow.route)
            if (anchors.zone(act2.meeting.zone) == null) out += "$file names the zone \"${act2.meeting.zone}\", which anchors.json does not have"
            point(file, act2.meeting.coworkerSpot)
            point(file, act2.meeting.playerSpot)
            point(file, act2.meeting.exitAnchor)
            path(act2.meeting.leavePath)
            for (door in act2.meeting.doors) {
                point(file, door)
                point(file, "${door}_inside")
            }
            if (npcs.groups[act2.meeting.group] == null) out += "$file names the group \"${act2.meeting.group}\", which npcs.json does not have"
        }
        return out
    }
}
