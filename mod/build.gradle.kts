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
    add("additionalRuntimeClasspath", "org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
}

tasks.processResources {
    val properties = mapOf("modVersion" to project.version.toString())
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(properties)
    }
}
