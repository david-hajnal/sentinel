plugins {
    java
    application
}

group = "com.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

application {
    mainClass.set("RTPServer") // Replace with your main class
}

repositories {
    mavenCentral()
}

dependencies {
    // JavaCV dependencies
    implementation("org.bytedeco:javacv-platform:1.5.11")

    // OpenCV Java bindings (included in JavaCV)
    implementation("org.bytedeco:opencv:4.10.0-1.5.11")
    implementation("org.bytedeco:opencv-platform:4.10.0-1.5.11")
    implementation("org.bytedeco:ffmpeg-platform:7.1-1.5.11")

    // (Optional) Logging support
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-classic:1.2.11")

    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    testCompileOnly("org.projectlombok:lombok:1.18.36")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.3")

}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform() // Use JUnit 5 platform for running tests
    testLogging {
        events("passed", "skipped", "failed") // Log test events
    }
}
