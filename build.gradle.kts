plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("net.neoforged.moddev") version "2.0.147" apply false
}

allprojects {
    group = "io.github.antonovichkorp1985maker.chemmod"
    version = "0.9.0-test.5"
}
