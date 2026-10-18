import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.21"
    id("fabric-loom") version "1.17.21"
    id("maven-publish")
}

/** Reads a required value from gradle.properties as a non-null String. */
fun prop(name: String): String = project.property(name) as String

version = prop("mod_version")
group = prop("maven_group")

base {
    archivesName.set(prop("archives_base_name"))
}

val targetJavaVersion = 17
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("every_tunnel") {
            sourceSet("main")
            sourceSet("client")
        }
    }

    runs {
        // Fixed dev username: otherwise every launch gets a random "PlayerNNN" name and
        // UUID, so worlds forget the player's inventory, position and game mode.
        named("client") {
            programArgs("--username", "Dev")
        }

        // Development shortcut: launches the client straight into the singleplayer
        // world saved as "dev_flat" in run/saves/ (a vanilla superflat world, see README).
        // Gradle task: ./gradlew runClientDevWorld
        register("clientDevWorld") {
            client()
            configName = "Minecraft Client (dev_flat world)"
            source("client")
            ideConfigGenerated(true)
            programArgs("--username", "Dev", "--quickPlaySingleplayer", "dev_flat")
        }
    }
}

// The project is Kotlin-only, so build/classes/java/* is never created, but Loom still
// puts those folders on the run classpath and Fabric Loader warns that they are missing.
// Creating them whenever classes are built keeps the dev log clean, both for the
// Gradle run tasks and for the IntelliJ run configurations (which build through Gradle).
listOf("classes", "clientClasses").forEach { name ->
    tasks.named(name) {
        doLast {
            sourceSets.forEach { it.java.destinationDirectory.get().asFile.mkdirs() }
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    // Loom adds the repositories for Minecraft, Fabric and Maven Central by itself.

    // LambDynamicLights (the flashlight's light) and its libraries, published by its
    // author. Separate artifacts instead of the Modrinth jar: dev runs do not load the
    // libraries nested inside a mod jar. Its yumi-commons libraries come from Maven Central.
    maven("https://maven.gegy.dev/releases/") {
        name = "Gegy"
        content {
            includeGroup("dev.lambdaurora.lambdynamiclights")
            includeGroup("dev.lambdaurora")
            includeGroup("io.github.queerbric")
        }
    }
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
    mappings("net.fabricmc:yarn:${prop("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${prop("kotlin_loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")

    // LambDynamicLights (client): compiled against its API only, so the jar does not
    // bundle or require it; fabric.mod.json "recommends" it. Dev runs get the full mod
    // with its libraries (modLocalRuntime is not exposed to anything depending on this mod).
    modCompileOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-api:${prop("lambdynamiclights_version")}")
    modLocalRuntime("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${prop("lambdynamiclights_version")}")

    // Unit tests for the pure Kotlin parts (config and level parsing).
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", prop("minecraft_version"))
    inputs.property("loader_version", prop("loader_version"))
    inputs.property("kotlin_loader_version", prop("kotlin_loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to prop("minecraft_version"),
            "loader_version" to prop("loader_version"),
            "kotlin_loader_version" to prop("kotlin_loader_version")
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE.txt") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = prop("archives_base_name")
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
