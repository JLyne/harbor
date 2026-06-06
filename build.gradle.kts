import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    java
    alias(libs.plugins.pluginYmlPaper)
}

group = "uk.co.notnull"
version = "1.7.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
	maven {
		url = uri("https://repo.papermc.io/repository/maven-public/")
	}	
    maven {
		url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	}
    mavenLocal()
}

dependencies {
	compileOnly(libs.paperApi)
	compileOnly(libs.placeholderApi)
}

tasks {
    generatePaperPluginDescription {
        useDefaultCentralProxy()
    }
}

paper {
    main = "xyz.nkomarn.harbor.Harbor"
    apiVersion = libs.versions.paperApi.get().replace(".build.+", "")
    authors = listOf("Jim (AnEnragedPigeon)", "TechToolbox (@nkomarn)")
    description = "Harbor redefines how sleep works in your server, making it easier for all the online players to get in bed quickly and skip through the night!"
    
    permissions {
        register("harbor.admin") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("harbor.ignored") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
    }
    
    serverDependencies {
        register("PlaceholderAPI") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
        }
    }
}

tasks {
    compileJava {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        options.encoding = "UTF-8"
    }
}
