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
    implementation("org.bytedeco:javacv-platform:1.5.8")

    // OpenCV Java bindings (included in JavaCV)
    implementation("org.bytedeco:opencv-platform:4.5.5-1.5.8")

    // (Optional) Logging support
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("org.bytedeco:javacv-platform:1.5.8")
    implementation("org.bytedeco:ffmpeg-platform:4.5.5-1.5.8")

}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
