plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("io.github.antonovichkorp.chemmod.cli.MainKt")
    applicationName = "chem"
}
