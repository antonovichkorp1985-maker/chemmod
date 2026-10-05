plugins {
    java
    id("net.neoforged.moddev")
}

base {
    archivesName.set("chemmod")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

neoForge {
    version = "21.1.251"

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
    }

    mods {
        create("chemmod") {
            sourceSet(sourceSets.main.get())
        }
    }
}

val createApi = "maven.modrinth:LNytGWDc:UjX6dr61" // Create 6.0.10 for Minecraft 1.21.1
val createRuntimeEnabled = providers.gradleProperty("chemmod.createRuntime")
    .map(String::toBoolean)
    .orElse(true)

// ModDevGradle adds its repositories at project level, so this repository must
// also live here rather than only in settings.gradle.kts.
repositories {
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}

dependencies {
    implementation(project(":core"))
    add("additionalRuntimeClasspath", project(":core"))

    // The adapter compiles against Create but never embeds it. The property lets
    // the smoke suite prove ChemMod still starts when Create is absent.
    compileOnly(createApi)
    if (createRuntimeEnabled.get()) {
        add("additionalRuntimeClasspath", createApi)
    }

    jarJar(project(":core"))
    jarJar("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    jarJar("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3")
    jarJar("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.7.3")
    add("additionalRuntimeClasspath", "org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    add("additionalRuntimeClasspath", "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3")
    add("additionalRuntimeClasspath", "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.7.3")
}

tasks.processResources {
    val properties = mapOf("modVersion" to project.version.toString())
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(properties)
    }

    from(rootProject.file("LICENSE")) {
        into("META-INF")
        rename("LICENSE", "LICENSE_chemmod")
    }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
        into("META-INF")
    }
}
