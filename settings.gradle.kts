rootProject.name = "build-server-for-gradle"
include("server")
include("plugin")
include("model")

// uncomment to be able to compile the test projects using Gradle CLI or to include them in the IDE build
/*
fun File.eachDir(consumer: (File) -> Unit) {
    val dirs = java.nio.file.Files.list(this.toPath()).filter { path -> java.nio.file.Files.isDirectory(path) }
    dirs?.forEach { f -> consumer.invoke(f.toFile()) }
}

file("testProjects").eachDir { dir ->
    if (!dir.name.startsWith(".")
        //   && dir.name.equals("android-test")
      ) {
        include(dir.name)
        project(":${dir.name}").projectDir = dir
    }
}
*/