plugins {
    id("net.sf.robocode.java-conventions")
    `java-library`
}

dependencies {
    implementation(project(":robocode.api"))
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.6")
}

description = "Robocode Samples"

java {
    withJavadocJar()
    withSourcesJar()
}

tasks {
    register("copyContent", Copy::class) {
        from("src/main/resources") {
            include("**/*.*")
        }
        from("src/main/java") {
            include("**")
        }
        into("../.sandbox/robots")
    }
    register("copyClasses", Copy::class) {
        dependsOn(configurations.runtimeClasspath)

        from(compileJava)
        into("../.sandbox/robots")
    }
    javadoc {
        source = sourceSets["main"].java
        include("**/*.java")
    }
    jar {
        dependsOn("copyContent")
        dependsOn("copyClasses")
        dependsOn("javadoc")
        from("src/main/java") {
            include("**")
        }
        from("src/main/resources") {
            include("**")
        }
    }
}