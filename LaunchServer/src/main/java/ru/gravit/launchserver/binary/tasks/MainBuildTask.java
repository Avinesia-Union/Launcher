package ru.gravit.launchserver.binary.tasks;

import javassist.CannotCompileException;
import javassist.NotFoundException;
import ru.gravit.launcher.AutogenConfig;
import ru.gravit.launcher.Launcher;
import ru.gravit.launcher.LauncherConfig;
import ru.gravit.launcher.serialize.HOutput;
import ru.gravit.launchserver.LaunchServer;
import ru.gravit.launchserver.binary.BuildContext;
import ru.gravit.launchserver.binary.JAConfigurator;
import ru.gravit.launchserver.binary.JARLauncherBinary;
import ru.gravit.utils.helper.IOHelper;
import ru.gravit.utils.helper.LogHelper;
import ru.gravit.utils.helper.SecurityHelper;
import ru.gravit.utils.helper.UnpackHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static ru.gravit.utils.helper.IOHelper.newZipEntry;

public class MainBuildTask implements LauncherBuildTask {
    public static LaunchServer server = LaunchServer.server;
    public final Path runtimeDir;
    public final Path guardDir;
    public final Path binaryFile;
    public Path cleanJar;
    private final class RuntimeDirVisitor extends SimpleFileVisitor<Path> {
        private final ZipOutputStream output;
        private final Map<String, byte[]> runtime;

        private RuntimeDirVisitor(ZipOutputStream output, Map<String, byte[]> runtime) {
            this.output = output;
            this.runtime = runtime;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String dirName = IOHelper.toString(runtimeDir.relativize(dir));
            output.putNextEntry(newEntry(dirName + '/'));
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String fileName = IOHelper.toString(runtimeDir.relativize(file));
            runtime.put(fileName, SecurityHelper.digest(SecurityHelper.DigestAlgorithm.MD5, file));

            // Create zip entry and transfer contents
            output.putNextEntry(newEntry(fileName));
            IOHelper.transfer(file, output);

            // Return result
            return super.visitFile(file, attrs);
        }
    }

    // TODO: new native security wrapper and library...
    @SuppressWarnings("unused")
    private final class GuardDirVisitor extends SimpleFileVisitor<Path> {
        private final ZipOutputStream output;
        private final Map<String, byte[]> guard;

        private GuardDirVisitor(ZipOutputStream output, Map<String, byte[]> guard) {
            this.output = output;
            this.guard = guard;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String dirName = IOHelper.toString(guardDir.relativize(dir));
            output.putNextEntry(newGuardEntry(dirName + '/'));
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String fileName = IOHelper.toString(guardDir.relativize(file));
            guard.put(fileName, SecurityHelper.digest(SecurityHelper.DigestAlgorithm.MD5, file));

            // Create zip entry and transfer contents
            output.putNextEntry(newGuardEntry(fileName));
            IOHelper.transfer(file, output);

            // Return result
            return super.visitFile(file, attrs);
        }
    }

    private static ZipEntry newEntry(String fileName) {
        return newZipEntry(Launcher.RUNTIME_DIR + IOHelper.CROSS_SEPARATOR + fileName);
    }

    private static ZipEntry newGuardEntry(String fileName) {
        return newZipEntry(Launcher.GUARD_DIR + IOHelper.CROSS_SEPARATOR + fileName);
    }
    public MainBuildTask() {
        runtimeDir = server.dir.resolve(Launcher.RUNTIME_DIR);
        guardDir = server.dir.resolve(Launcher.GUARD_DIR);
        binaryFile = server.dir.resolve(server.config.binaryName + "-main_task.jar");
    }

    @Override
    public String getName() {
        return "main";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Path process(Path cleanJar) throws IOException {
        this.cleanJar = cleanJar;
        try (ZipOutputStream output = new ZipOutputStream(IOHelper.newOutput(binaryFile));
             JAConfigurator jaConfigurator = new JAConfigurator(AutogenConfig.class.getName(), this)) {
            jaConfigurator.pool.insertClassPath(cleanJar.toFile().getAbsolutePath());
            BuildContext context = new BuildContext(output, jaConfigurator, this);
            server.buildHookManager.preHook(context);
            jaConfigurator.setAddress(server.config.getAddress());
            jaConfigurator.setPort(server.config.port);
            jaConfigurator.setProjectName(server.config.projectName);
            jaConfigurator.setSecretKey(SecurityHelper.randomStringAESKey());
            jaConfigurator.setClientPort(32148 + SecurityHelper.newRandom().nextInt(512));
            jaConfigurator.setUsingWrapper(server.config.isUsingWrapper);
            jaConfigurator.setDownloadJava(server.config.isDownloadJava);
            jaConfigurator.setEnv(server.config.env);
            server.buildHookManager.registerAllClientModuleClass(jaConfigurator);
            try (ZipInputStream input = new ZipInputStream(IOHelper.newInput(cleanJar))) {
                ZipEntry e = input.getNextEntry();
                while (e != null) {
                    String filename = e.getName();
                    if (server.buildHookManager.isContainsBlacklist(filename)) {
                        e = input.getNextEntry();
                        continue;
                    }
                    try {
                        output.putNextEntry(IOHelper.newZipEntry(e));
                    } catch (ZipException ex) {
                        LogHelper.error(ex);
                        e = input.getNextEntry();
                        continue;
                    }
                    /*if (filename.endsWith(".class")) {
                        String classname = filename.replace('/', '.').substring(0,
                                filename.length() - ".class".length());
                        byte[] bytes;
                        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(2048)) {
                            IOHelper.transfer(input, outputStream);
                            bytes = outputStream.toByteArray();
                        }
                        bytes = server.buildHookManager.classTransform(bytes, classname, this);
                        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
                            IOHelper.transfer(inputStream, output);
                        }
                    } else */
                        IOHelper.transfer(input, output);
                    context.fileList.add(filename);
                    e = input.getNextEntry();
                }
            }
            // write additional classes
            for (Map.Entry<String, byte[]> ent : server.buildHookManager.getIncludeClass().entrySet()) {
                output.putNextEntry(newZipEntry(ent.getKey().replace('.', '/').concat(".class")));
                output.write(server.buildHookManager.classTransform(ent.getValue(), ent.getKey(), this));
            }
            // map for guard
            Map<String, byte[]> runtime = new HashMap<>(256);
            if (server.buildHookManager.buildRuntime()) {
                // Write launcher guard dir
                IOHelper.walk(runtimeDir, new RuntimeDirVisitor(output, runtime), false);
                // IOHelper.walk(guardDir, new GuardDirVisitor(output, runtime), false);
            }
            // Create launcher config file
            byte[] launcherConfigBytes;
            try (ByteArrayOutputStream configArray = IOHelper.newByteArrayOutput()) {
                try (HOutput configOutput = new HOutput(configArray)) {
                    new LauncherConfig(server.config.getAddress(), server.config.port, server.publicKey, runtime)
                            .write(configOutput);
                }
                launcherConfigBytes = configArray.toByteArray();
            }

            // Write launcher config file
            output.putNextEntry(newZipEntry(Launcher.CONFIG_FILE));
            output.write(launcherConfigBytes);
            ZipEntry e = newZipEntry(jaConfigurator.getZipEntryPath());
            output.putNextEntry(e);
            jaConfigurator.compile();
            output.write(jaConfigurator.getBytecode());
            server.buildHookManager.postHook(context);
        } catch (CannotCompileException | NotFoundException e) {
            LogHelper.error(e);
        }
        return binaryFile;
    }

    @Override
    public boolean allowDelete() {
        return true;
    }

    public void tryUnpack() throws IOException {
        LogHelper.info("Unpacking launcher native guard files and runtime");
        UnpackHelper.unpackZipNoCheck("guard.zip", guardDir);
        UnpackHelper.unpackZipNoCheck("runtime.zip", runtimeDir);
    }
}