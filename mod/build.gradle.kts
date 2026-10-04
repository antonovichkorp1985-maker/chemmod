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

dependencies {
    implementation(project(":core"))
    add("additionalRuntimeClasspath", project(":core"))

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
