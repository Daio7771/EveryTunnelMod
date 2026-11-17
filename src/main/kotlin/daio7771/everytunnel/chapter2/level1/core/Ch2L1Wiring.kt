package daio7771.everytunnel.chapter2.level1.core

/**
 * Checks that what `tasks.json`, `speakers.json`, `npcs.json`, `cutscenes/meeting.json`, `act3.json`, `patrols.json` and `cutscenes/office.json` name is in `anchors.json`: a task whose block or place is missing, a speaker
 * zone with no box or no emitter, a camera of the first announcement's cinematic that has no anchor. Plain Kotlin; the data
 * loader reports what it finds on every load and `/et reload`, and `Ch2L1WiringTest` holds the jar's files to it.
 */
object Ch2L1Wiring {

    /** One line per thing that does not match; empty when everything the files name exists. */
    fun problems(
        anchors: Ch2L1Anchors, tasks: Ch2L1Tasks, speakers: Ch2L1Speakers, npcs: Ch2L1NpcData = Ch2L1NpcData.EMPTY, act2: Ch2L1Act2? = null,
        act3: Ch2L1Act3? = null, patrols: Ch2L1Patrols? = null, office: Ch2L1OfficeSpec? = null,
    ): List<String> {
        val out = mutableListOf<String>()
        fun point(file: String, id: String) {
            if (anchors.point(id) == null) out += "$file names the anchor \"$id\", which anchors.json does not have"
        }
        for (tube in tasks.tubes) point("tasks.json", tube.anchor)
        point("tasks.json", tasks.terminalAnchor)
        point("tasks.json", tasks.analyzerAnchor)
        tasks.lab.bottles.forEach { point("tasks.json", it.anchor) }
        for (id in listOf(tasks.lab.tankAnchor, tasks.lab.centrifugeAnchor, tasks.lab.gaugeTerminalAnchor)) point("tasks.json", id)
        for ((broom, dirtPath) in listOf(tasks.broomAnchor to tasks.dirtPath, tasks.basementBroomAnchor to tasks.basementDirtPath)) {
            point("tasks.json", broom)
            val dirt = anchors.path(dirtPath)
            when {
                dirt == null -> out += "tasks.json names the path \"$dirtPath\", which anchors.json does not have"
                dirt.points.size > Ch2L1Tasks.MAX_DIRT -> out += "the path \"$dirtPath\" has ${dirt.points.size} points: at most ${Ch2L1Tasks.MAX_DIRT} spots of dirt"
            }
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
        if (act3 != null) {
            val file = "act3.json"
            for (id in listOf(act3.readerAnchor, act3.labDoor, act3.keypadAnchor, act3.officeDoor, act3.sealedDoor, act3.portraitAnchor, act3.chamberAnchor)) point(file, id)
            act3.restrictedDoors.forEach { point(file, it) }
            act3.computers.forEach { point(file, it.anchor) }
            point(file, act3.overtime.reader)
            for (zone in listOf(act3.officeZone, act3.overtime.zone, act3.overtime.basementZone)) {
                if (anchors.zone(zone) == null) out += "$file names the zone \"$zone\", which anchors.json does not have"
            }
        }
        if (office != null) {
            val file = "cutscenes/office.json"
            for (id in listOf(office.playerMark, office.door, office.spawn, office.doorway, office.threshold, office.deskFront, office.cardFrom, office.cardTo)) {
                point(file, id)
            }
        }
        if (patrols != null) {
            val file = "patrols.json"
            fun zone(id: String) {
                if (anchors.zone(id) == null) out += "$file names the zone \"$id\", which anchors.json does not have"
            }
            zone(patrols.watch.stareZone)
            for (f in patrols.watch.forbidden) {
                zone(f.zone)
                point(file, f.back)
            }
            point(file, patrols.watch.backElsewhere)
            for (patrol in patrols.patrols) {
                if (anchors.path(patrol.path) == null) out += "$file names the path \"${patrol.path}\", which anchors.json does not have"
                patrol.spawn?.let { point(file, it) }
                patrol.pauses.forEach { point(file, it.at) }
            }
        }
        return out
    }
}
